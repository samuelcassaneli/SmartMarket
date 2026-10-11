package com.smartmarket.domain.ai

import android.util.Base64
import com.smartmarket.domain.ocr.OcrPage
import com.smartmarket.domain.ocr.ParsedOffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
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
import java.util.concurrent.TimeUnit

class AiException(message: String, val retryable: Boolean = false) : IOException(message)

/**
 * Cliente mínimo da API Gemini (Google AI Studio). Usa a chave do próprio usuário, enviada só no
 * cabeçalho `x-goog-api-key` (nunca na URL). Na camada gratuita o custo é zero.
 */
class GeminiClient(baseClient: OkHttpClient) {

    private val client = baseClient.newBuilder()
        .readTimeout(120, TimeUnit.SECONDS)
        .callTimeout(150, TimeUnit.SECONDS)
        .build()

    /** Lista os modelos da conta e escolhe o melhor "flash". Também valida a chave. */
    suspend fun discoverModel(apiKey: String): String = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("$BASE/models?pageSize=200")
            .header("x-goog-api-key", apiKey.trim())
            .get()
            .build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw AiException(GeminiModels.errorMessage(resp.code, body))
            GeminiModels.pickModel(GeminiModels.parseModelList(body))
                ?: throw AiException("Nenhum modelo Gemini Flash disponível nesta chave")
        }
    }

    /** Extrai as ofertas de uma página (texto de PDF com posições, ou a imagem da página). */
    suspend fun readPage(apiKey: String, model: String, page: OcrPage, marketName: String?): List<ParsedOffer> {
        val userParts = buildJsonArray {
            if (page.fromPdfText || page.image == null) {
                add(buildJsonObject { put("text", GeminiModels.pageTextPrompt(page.lines, marketName)) })
            } else {
                add(buildJsonObject { put("text", GeminiModels.imagePrompt(marketName)) })
                add(buildJsonObject {
                    putJsonObject("inline_data") {
                        put("mime_type", "image/jpeg")
                        put("data", Base64.encodeToString(page.image, Base64.NO_WRAP))
                    }
                })
            }
        }
        val payload = buildJsonObject {
            putJsonObject("systemInstruction") {
                putJsonArray("parts") { add(buildJsonObject { put("text", GeminiModels.SYSTEM_RULES) }) }
            }
            putJsonArray("contents") {
                add(buildJsonObject {
                    put("role", "user")
                    put("parts", userParts)
                })
            }
            putJsonObject("generationConfig") {
                put("temperature", 0)
                put("responseMimeType", "application/json")
                put("responseSchema", GeminiModels.RESPONSE_SCHEMA)
            }
        }.toString()

        var attempt = 0
        while (true) {
            attempt++
            val result = runCatching { post(apiKey, model, payload) }
            val ex = result.exceptionOrNull()
            if (ex == null) return GeminiModels.parseOffers(result.getOrThrow(), page.index)
            if (ex is AiException && ex.retryable && attempt < 3) {
                delay(20_000L * attempt) // cota por minuto da camada gratuita
                continue
            }
            throw ex
        }
    }

    private suspend fun post(apiKey: String, model: String, payload: String): String = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("$BASE/models/$model:generateContent")
            .header("x-goog-api-key", apiKey.trim())
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw AiException(GeminiModels.errorMessage(resp.code, body), retryable = resp.code == 429 || resp.code >= 500)
            }
            body
        }
    }

    companion object {
        private const val BASE = "https://generativelanguage.googleapis.com/v1beta"
        const val KEY_PAGE_URL = "https://aistudio.google.com/apikey"
    }
}
