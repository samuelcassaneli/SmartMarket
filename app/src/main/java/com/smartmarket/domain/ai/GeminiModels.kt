package com.smartmarket.domain.ai

import com.smartmarket.domain.ocr.OcrLine
import com.smartmarket.domain.ocr.ParsedOffer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/** Partes puras da leitura por IA (escolha de modelo, prompt e interpretação da resposta). Testáveis na JVM. */
object GeminiModels {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Regras fixas da leitura de encarte. O modelo só extrai; o app decide preço, vínculo e gravação.
     */
    val SYSTEM_RULES = """
        Você lê encartes (folhetos de ofertas) de supermercados brasileiros e extrai os produtos em oferta.
        Regras obrigatórias:
        1. Uma entrada por produto anunciado. Não invente produtos nem preços; use só o que está no encarte.
        2. "nome": nome completo do produto como no encarte, com marca, tipo/variação e tamanho/peso/volume
           (ex.: "Amaciante Concentrado Downy 1,5L"). Não inclua palavras soltas como "Un", "Unidade", "Cada",
           "Sabores", "Fragrâncias", "Tipos" no fim do nome. Corrija erros óbvios de leitura (ex.: "Uiidades").
        3. "preco": preço que qualquer cliente paga na loja, em reais, número com ponto decimal (ex.: 29.9).
           - Se houver preço de varejo e preço de atacado (ex.: "a partir de 6 un"), "preco" = atacado (o menor)
             e "preco_varejo" = varejo.
           - Preços que exigem app, cartão da loja, clube ou cupom NÃO são "preco": coloque-os em "observacao"
             (ex.: "no app R$ 18,90; app + cartão R$ 16,90").
           - "De R$ X por R$ Y": "preco" = Y e "preco_antigo" = X.
           - "Leve 3 pague 2" e semelhantes: "preco" = preço unitário anunciado; regra em "observacao".
        4. Ignore títulos de seção (AÇOUGUE, MERCEARIA...), slogans, datas, avisos legais, logotipos,
           telefones, endereços e textos de validade.
        5. Se o mesmo produto aparecer duas vezes, retorne uma vez só.
        6. Responda somente com o JSON no formato pedido.
    """.trimIndent()

    /** Schema de resposta (subconjunto OpenAPI aceito pelo Gemini). */
    val RESPONSE_SCHEMA: JsonObject = json.parseToJsonElement(
        """
        {"type":"ARRAY","items":{"type":"OBJECT","properties":{
          "nome":{"type":"STRING"},
          "preco":{"type":"NUMBER"},
          "preco_varejo":{"type":"NUMBER","nullable":true},
          "preco_antigo":{"type":"NUMBER","nullable":true},
          "observacao":{"type":"STRING","nullable":true}
        },"required":["nome","preco"]}}
        """.trimIndent()
    ).jsonObject

    /**
     * Texto da página de PDF com posições, para o modelo entender a diagramação (colunas, cards).
     * Formato por linha: "x,y | texto" (coordenadas em pontos, origem no topo).
     */
    fun pageTextPrompt(lines: List<OcrLine>, marketName: String?): String {
        val body = lines.sortedWith(compareBy({ (it.box?.top ?: 0) / 20 }, { it.box?.left ?: 0 }))
            .joinToString("\n") { l -> "${(l.box?.left ?: 0) / 4},${(l.box?.top ?: 0) / 4} | ${l.text}" }
        return buildString {
            append("Página de encarte")
            if (!marketName.isNullOrBlank()) append(" do mercado ").append(marketName)
            append(". Abaixo, o texto da página com a posição de cada linha (x,y em pontos, origem no canto superior esquerdo). ")
            append("Linhas próximas formam o card de um produto.\n\n")
            append(body)
        }
    }

    fun imagePrompt(marketName: String?): String =
        "Imagem de uma página de encarte" + (if (!marketName.isNullOrBlank()) " do mercado $marketName" else "") +
            ". Extraia todos os produtos em oferta com os preços."

    /**
     * Escolhe o melhor modelo "flash" disponível na conta: estável antes de preview/experimental,
     * versão mais nova, e não "lite" quando houver opção (lê melhor), sem modelos de imagem/áudio.
     */
    fun pickModel(models: List<ModelInfo>): String? {
        val candidates = models.filter { m ->
            val n = m.name.lowercase()
            "generateContent" in m.methods && n.contains("gemini") && n.contains("flash") &&
                listOf("image", "tts", "audio", "live", "embedding", "native", "thinking").none { n.contains(it) }
        }
        if (candidates.isEmpty()) return null
        fun version(n: String): Double = Regex("""gemini-(\d+(?:\.\d+)?)""").find(n)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
        return candidates.sortedWith(
            compareBy<ModelInfo> { m -> if (m.name.contains("preview") || m.name.contains("exp")) 1 else 0 }
                .thenByDescending { version(it.name) }
                .thenBy { if (it.name.contains("lite")) 1 else 0 }
                .thenBy { it.name.length }
        ).first().name.removePrefix("models/")
    }

    data class ModelInfo(val name: String, val methods: List<String>)

    fun parseModelList(body: String): List<ModelInfo> {
        val root = json.parseToJsonElement(body).jsonObject
        val models = root["models"] as? JsonArray ?: return emptyList()
        return models.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val name = (o["name"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            val methods = (o["supportedGenerationMethods"] as? JsonArray).orEmpty()
                .mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            ModelInfo(name, methods)
        }
    }

    /** Lê a resposta do generateContent e converte para ofertas (filtra preços impossíveis). */
    fun parseOffers(body: String, page: Int): List<ParsedOffer> {
        val root = json.parseToJsonElement(body).jsonObject
        val text = (root["candidates"] as? JsonArray)?.firstOrNull()?.jsonObject
            ?.get("content")?.jsonObject?.get("parts")?.jsonArray
            ?.mapNotNull { (it as? JsonObject)?.get("text") as? JsonPrimitive }
            ?.joinToString("") { it.content }
            ?: return emptyList()
        val cleaned = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val arr = runCatching { json.parseToJsonElement(cleaned) as? JsonArray }.getOrNull() ?: return emptyList()
        return arr.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val name = (o["nome"] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.length >= 3 } ?: return@mapNotNull null
            val price = (o["preco"] as? JsonPrimitive)?.doubleOrNull?.takeIf { it in 0.3..5000.0 } ?: return@mapNotNull null
            val retail = (o["preco_varejo"] as? JsonPrimitive)?.doubleOrNull?.takeIf { it > price }
            val old = (o["preco_antigo"] as? JsonPrimitive)?.doubleOrNull?.takeIf { it > price }
            val obs = (o["observacao"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
            val notes = listOfNotNull(
                old?.let { "de R$ " + String.format(java.util.Locale.ROOT, "%.2f", it).replace('.', ',') },
                retail?.let { "atacado" },
                obs.takeIf { it.isNotBlank() }
            )
            ParsedOffer(description = name, price = price, retailPrice = retail, note = notes.joinToString(", "), page = page)
        }.distinctBy { it.description.lowercase() + "|" + it.price }
    }

    /** Mensagem de erro amigável a partir da resposta HTTP de erro do Gemini. */
    fun errorMessage(code: Int, body: String): String {
        val msg = runCatching {
            (json.parseToJsonElement(body).jsonObject["error"] as? JsonObject)?.get("message")?.let { (it as JsonPrimitive).content }
        }.getOrNull()
        return when (code) {
            400 -> if (msg?.contains("API key", ignoreCase = true) == true) "Chave da IA inválida" else "Pedido recusado pela IA" + (msg?.let { ": $it" } ?: "")
            401, 403 -> "Chave da IA inválida ou sem permissão"
            429 -> "Limite gratuito da IA atingido; tente mais tarde"
            in 500..599 -> "Serviço da IA indisponível no momento"
            else -> msg ?: "Erro $code na IA"
        }
    }
}
