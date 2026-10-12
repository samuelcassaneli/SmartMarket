package com.smartmarket.domain.ai

import android.util.Base64
import com.smartmarket.domain.ocr.OcrLine
import com.smartmarket.domain.ocr.OcrPage
import com.smartmarket.domain.ocr.ParsedOffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.Collections
import java.util.concurrent.TimeUnit

class AiException(message: String, val retryable: Boolean = false) : IOException(message)

/**
 * Cliente mínimo da API Gemini (Google AI Studio). Usa a chave do próprio usuário, enviada só no
 * cabeçalho `x-goog-api-key` (nunca na URL). Na camada gratuita o custo é zero.
 *
 * Velocidade: pede o menor nível de raciocínio aceito pelo modelo, junta páginas de texto num
 * pedido só e, se a cota por minuto estourar, espera só o tempo que a própria API indica.
 */
class GeminiClient(baseClient: OkHttpClient) {

    private val client = baseClient.newBuilder()
        .readTimeout(120, TimeUnit.SECONDS)
        .callTimeout(150, TimeUnit.SECONDS)
        .build()

    /** Modelos que recusaram a configuração de raciocínio (não tenta de novo). */
    private val noThinkingConfig = Collections.synchronizedSet(mutableSetOf<String>())

    /** Lista os modelos da conta e escolhe o melhor "flash" (ou "flash-lite" se [preferLite]). Também valida a chave. */
    suspend fun discoverModel(apiKey: String, preferLite: Boolean = false): String = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("$BASE/models?pageSize=200")
            .header("x-goog-api-key", apiKey.trim())
            .get()
            .build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw AiException(GeminiModels.errorMessage(resp.code, body))
            GeminiModels.pickModel(GeminiModels.parseModelList(body), preferLite)
                ?: throw AiException("Nenhum modelo Gemini Flash disponível nesta chave")
        }
    }

    /** Várias páginas com texto (PDF) num pedido só. */
    suspend fun readTextPages(apiKey: String, model: String, pages: List<OcrPage>, marketName: String?): List<ParsedOffer> {
        val prompt = if (pages.size == 1) GeminiModels.pageTextPrompt(pages[0].lines, marketName)
        else GeminiModels.multiPageTextPrompt(pages.map { it.index to it.lines }, marketName)
        val parts = buildJsonArray { add(buildJsonObject { put("text", prompt) }) }
        val body = generate(apiKey, model, parts, GeminiModels.RESPONSE_SCHEMA)
        return GeminiModels.parseOffers(body, pages.first().index)
    }

    /** Uma página em imagem (Dom, foto, PDF escaneado). */
    suspend fun readImagePage(apiKey: String, model: String, page: OcrPage, marketName: String?): List<ParsedOffer> {
        val image = page.image ?: return readTextPages(apiKey, model, listOf(page), marketName)
        val parts = buildJsonArray {
            add(buildJsonObject { put("text", GeminiModels.imagePrompt(marketName)) })
            add(buildJsonObject {
                putJsonObject("inline_data") {
                    put("mime_type", "image/jpeg")
                    put("data", Base64.encodeToString(image, Base64.NO_WRAP))
                }
            })
        }
        return GeminiModels.parseOffers(generate(apiKey, model, parts, GeminiModels.RESPONSE_SCHEMA), page.index)
    }

    /** Escolhe entre os links da página os que são encartes (só aceita URLs que estavam na página). */
    suspend fun findFlyerLinks(
        apiKey: String,
        model: String,
        pageUrl: String,
        candidates: List<Pair<String, String>>
    ): Pair<List<String>, List<String>> {
        if (candidates.isEmpty()) return emptyList<String>() to emptyList()
        val parts = buildJsonArray { add(buildJsonObject { put("text", GeminiModels.linksPrompt(pageUrl, candidates)) }) }
        val body = generate(apiKey, model, parts, GeminiModels.LINKS_SCHEMA)
        return GeminiModels.parseLinks(body, candidates.map { it.first }.toSet())
    }

    /** Diz quais [candidates] são o mesmo produto que [product] (índices base 0). */
    suspend fun sameProduct(
        apiKey: String,
        model: String,
        product: GeminiModels.MatchProduct,
        candidates: List<GeminiModels.MatchCandidate>
    ): Set<Int> {
        if (candidates.isEmpty()) return emptySet()
        val parts = buildJsonArray { add(buildJsonObject { put("text", GeminiModels.matchPrompt(product, candidates)) }) }
        val body = generate(apiKey, model, parts, GeminiModels.MATCH_SCHEMA, GeminiModels.MATCH_RULES)
        return GeminiModels.parseMatches(body, candidates.size)
    }

    // ------------------------------------------------------------------ HTTP

    private suspend fun generate(
        apiKey: String,
        model: String,
        parts: JsonArray,
        schema: JsonObject,
        system: String = GeminiModels.SYSTEM_RULES
    ): String {
        var attempt = 0
        while (true) {
            attempt++
            val thinking = if (model in noThinkingConfig) null else GeminiModels.fastThinking(model)
            val payload = buildJsonObject {
                putJsonObject("systemInstruction") {
                    putJsonArray("parts") { add(buildJsonObject { put("text", system) }) }
                }
                putJsonArray("contents") {
                    add(buildJsonObject {
                        put("role", "user")
                        put("parts", parts)
                    })
                }
                putJsonObject("generationConfig") {
                    put("temperature", 0)
                    put("maxOutputTokens", 32768)
                    put("responseMimeType", "application/json")
                    put("responseSchema", schema)
                    if (thinking != null) {
                        putJsonObject("thinkingConfig") {
                            when (val v = thinking.second) {
                                is Number -> put(thinking.first, v)
                                else -> put(thinking.first, v.toString())
                            }
                        }
                    }
                }
            }.toString()

            val (code, body) = post(apiKey, model, payload)
            if (code in 200..299) return body
            if (code == 400 && thinking != null && body.contains("thinking", ignoreCase = true)) {
                noThinkingConfig += model // modelo não aceita esse ajuste: repete sem ele
                continue
            }
            val retryable = code == 429 || code >= 500
            if (retryable && attempt < 4) {
                val wait = (GeminiModels.retryDelaySeconds(body) ?: (5 * attempt)).coerceIn(2, 60)
                delay(wait * 1000L)
                continue
            }
            throw AiException(GeminiModels.errorMessage(code, body), retryable)
        }
    }

    private suspend fun post(apiKey: String, model: String, payload: String): Pair<Int, String> = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("$BASE/models/$model:generateContent")
            .header("x-goog-api-key", apiKey.trim())
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(req).execute().use { resp -> resp.code to resp.body?.string().orEmpty() }
    }

    companion object {
        private const val BASE = "https://generativelanguage.googleapis.com/v1beta"
        const val KEY_PAGE_URL = "https://aistudio.google.com/apikey"
    }
}
