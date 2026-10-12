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
        6. PREÇO CERTO NO PRODUTO CERTO (o erro mais grave é trocar preço entre produtos vizinhos):
           - O encarte é uma grade de cards (quadros). Nome, foto e preço de um produto ficam DENTRO do mesmo card.
           - O preço de um produto está na mesma coluna do nome (faixa horizontal x sobreposta) e logo abaixo ou ao
             lado dele, antes do nome do próximo produto. Nunca use o preço de um card vizinho (coluna ao lado,
             card de cima ou de baixo).
           - Cada preço pertence a um único produto. Se dois produtos parecem ter o mesmo preço, confira se não
             está pegando o preço do vizinho.
           - Preço quebrado em pedaços ("4" + ",99", "R$" + "4,99") é um número só. O preço principal é o de fonte maior.
           - Confira se o valor faz sentido para o produto (ex.: leite 1 L custa uns R$ 4 a 8; macarrão 500 g uns
             R$ 2 a 7; óleo 900 ml uns R$ 5 a 12; carne/kg costuma passar de R$ 15). Se não fizer, você
             provavelmente pegou o preço do produto ao lado: procure o preço correto no card do produto.
           - Se não tiver certeza de qual preço é de qual produto, NÃO inclua o produto.
        7. Responda somente com o JSON no formato pedido.
    """.trimIndent()

    /** Schema de resposta (subconjunto OpenAPI aceito pelo Gemini). */
    val RESPONSE_SCHEMA: JsonObject = json.parseToJsonElement(
        """
        {"type":"ARRAY","items":{"type":"OBJECT","properties":{
          "nome":{"type":"STRING"},
          "preco":{"type":"NUMBER"},
          "preco_varejo":{"type":"NUMBER","nullable":true},
          "preco_antigo":{"type":"NUMBER","nullable":true},
          "observacao":{"type":"STRING","nullable":true},
          "pagina":{"type":"INTEGER","nullable":true}
        },"required":["nome","preco"]}}
        """.trimIndent()
    ).jsonObject

    /**
     * Texto da página de PDF com posições, para o modelo entender a diagramação (colunas, cards).
     * Formato por linha: "x,y | texto" (coordenadas em pontos, origem no topo).
     */
    fun pageTextPrompt(lines: List<OcrLine>, marketName: String?): String {
        val body = lines.sortedWith(compareBy({ (it.box?.top ?: 0) / 20 }, { it.box?.left ?: 0 }))
            .joinToString("\n") { l -> lineWithBox(l) }
        return buildString {
            append("Página de encarte")
            if (!marketName.isNullOrBlank()) append(" do mercado ").append(marketName)
            append(". Abaixo, o texto da página. Cada linha vem como \"x,y,largura,altura | texto\" ")
            append("(pontos, origem no canto superior esquerdo; altura grande = fonte grande, típica de preço). ")
            append("Linhas com faixas x sobrepostas e y próximos formam o card de um produto; use isso para ligar cada preço ao seu produto.\n\n")
            append(body)
        }
    }

    /** Várias páginas de texto num pedido só (mais rápido); cada oferta informa "pagina". */
    fun multiPageTextPrompt(pages: List<Pair<Int, List<OcrLine>>>, marketName: String?): String = buildString {
        append("Páginas de encarte")
        if (!marketName.isNullOrBlank()) append(" do mercado ").append(marketName)
        append(". Para cada página, o texto com cada linha no formato \"x,y,largura,altura | texto\" ")
        append("(pontos, origem no canto superior esquerdo; altura grande = fonte grande, típica de preço). ")
        append("Linhas com faixas x sobrepostas e y próximos formam o card de um produto; use isso para ligar cada preço ao seu produto. ")
        append("Informe em \"pagina\" o número da página de cada oferta.\n")
        for ((index, lines) in pages) {
            append("\n=== Página ").append(index + 1).append(" ===\n")
            lines.sortedWith(compareBy({ (it.box?.top ?: 0) / 20 }, { it.box?.left ?: 0 }))
                .forEach { l -> append(lineWithBox(l)).append('\n') }
        }
    }

    /** "x,y,largura,altura | texto" em pontos (as coordenadas do PDF vêm multiplicadas por 4). */
    fun lineWithBox(l: OcrLine): String {
        val b = l.box ?: return "0,0,0,0 | ${l.text}"
        return "${b.left / 4},${b.top / 4},${b.width / 4},${b.height / 4} | ${l.text}"
    }

    fun imagePrompt(marketName: String?): String =
        "Imagem de uma página de encarte" + (if (!marketName.isNullOrBlank()) " do mercado $marketName" else "") +
            ". Extraia todos os produtos em oferta com os preços. Para cada produto, use só o preço que está dentro " +
            "do mesmo card (mesma moldura/fundo, junto da foto e do nome), nunca o do card vizinho."

    /**
     * Escolhe o melhor modelo "flash" disponível na conta: estável antes de preview/experimental,
     * versão mais nova, e não "lite" quando houver opção (lê melhor), sem modelos de imagem/áudio.
     */
    fun pickModel(models: List<ModelInfo>, preferLite: Boolean = false): String? {
        val candidates = models.filter { m ->
            val n = m.name.lowercase()
            "generateContent" in m.methods && n.contains("gemini") && n.contains("flash") &&
                listOf("image", "tts", "audio", "live", "embedding", "native", "thinking").none { n.contains(it) }
        }
        if (candidates.isEmpty()) return null
        fun version(n: String): Double = Regex("""gemini-(\d+(?:\.\d+)?)""").find(n)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
        return candidates.sortedWith(
            compareBy<ModelInfo> { m -> if (m.name.contains("preview") || m.name.contains("exp")) 1 else 0 }
                .thenBy { if (it.name.contains("lite") != preferLite) 1 else 0 }
                .thenByDescending { version(it.name) }
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
            val pg = (o["pagina"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()?.minus(1) ?: page
            ParsedOffer(description = name, price = price, retailPrice = retail, note = notes.joinToString(", "), page = pg)
        }.distinctBy { it.description.lowercase() + "|" + it.price }
    }

    // ------------------------------------------------------------ Conferência de produto (busca nos sites)

    /** Regras para decidir se uma oferta de site é o MESMO produto cadastrado (para comparar preço). */
    val MATCH_RULES = """
        Você confere se ofertas encontradas nos sites de supermercados brasileiros são O MESMO produto que o usuário
        cadastrou, para comparar preço com o que ele costuma pagar. Regras obrigatórias:
        1. Mesmo tipo e mesma variante: leite integral é diferente de desnatado; amaciante concentrado é diferente do comum;
           alho in natura é diferente de alho frito, pasta de alho, alho picado ou descascado; carne moída é diferente de bife;
           sabão em pó é diferente de líquido; refrigerante zero é diferente do normal.
        2. Mesma forma de venda. Produto vendido por kg / a granel (ex.: "Alho kg", "Banana prata kg", "Tomate kg", nota com
           unidade KG) só casa com oferta vendida por kg / a granel ou embalagem de exatamente 1 kg. Pacotes, bandejas,
           saquinhos ou potes pequenos (60 g, 100 g, 200 g, 500 g...) NÃO servem, mesmo sendo o mesmo alimento.
        3. Produto com tamanho (5 kg, 1,5 L, 395 g, 2 L, c/ 12, 4 rolos) só casa com oferta do mesmo tamanho (até 10% de
           diferença) e da mesma quantidade de unidades. Fardo, caixa ou kit com várias unidades é diferente de unidade avulsa.
        4. Marca: se o cadastro cita uma marca, a oferta precisa ser da mesma marca. Sem marca no cadastro, qualquer marca serve.
        5. Quando o nome do cadastro for vago, use a última nota fiscal (descrição, unidade e preço pago) como pista do
           tamanho e da forma de venda. Um preço muito menor que o pago costuma indicar embalagem menor: desconfie.
        6. Na dúvida, NÃO inclua. É melhor não comparar do que comparar produtos diferentes.
        Responda só com os números das ofertas que são o mesmo produto.
    """.trimIndent()

    val MATCH_SCHEMA: JsonObject = json.parseToJsonElement(
        """{"type":"OBJECT","properties":{"iguais":{"type":"ARRAY","items":{"type":"INTEGER"}}},"required":["iguais"]}"""
    ).jsonObject

    /** Oferta candidata mostrada à IA. */
    data class MatchCandidate(val market: String, val name: String, val price: Double, val saleInfo: String)

    /** Produto cadastrado e o que se sabe dele. */
    data class MatchProduct(
        val name: String,
        val saleInfo: String,
        val lastPaidDescription: String? = null,
        val lastPaidUnit: String? = null,
        val lastPaidPrice: Double? = null
    )

    fun matchPrompt(product: MatchProduct, candidates: List<MatchCandidate>): String = buildString {
        append("Produto cadastrado: ").append(product.name).append('\n')
        append("Forma de venda: ").append(product.saleInfo).append('\n')
        if (product.lastPaidDescription != null) {
            append("Última nota fiscal: \"").append(product.lastPaidDescription).append('"')
            product.lastPaidUnit?.takeIf { it.isNotBlank() }?.let { append(", unidade ").append(it) }
            product.lastPaidPrice?.let { append(", pago ").append(money(it)).append(" por unidade") }
            append('\n')
        }
        append("\nOfertas encontradas (número | mercado | descrição | preço | forma de venda):\n")
        candidates.forEachIndexed { i, c ->
            append(i + 1).append(" | ").append(c.market).append(" | ").append(c.name).append(" | ")
                .append(money(c.price)).append(" | ").append(c.saleInfo).append('\n')
        }
    }

    /** Índices (base 0) aprovados pela IA; ignora números fora da lista. */
    fun parseMatches(body: String, count: Int): Set<Int> {
        val root = json.parseToJsonElement(body).jsonObject
        val text = (root["candidates"] as? JsonArray)?.firstOrNull()?.jsonObject
            ?.get("content")?.jsonObject?.get("parts")?.jsonArray
            ?.mapNotNull { (it as? JsonObject)?.get("text") as? JsonPrimitive }
            ?.joinToString("") { it.content } ?: return emptySet()
        val obj = runCatching { json.parseToJsonElement(text.trim()).jsonObject }.getOrNull() ?: return emptySet()
        return (obj["iguais"] as? JsonArray).orEmpty()
            .mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()?.toInt() }
            .map { it - 1 }
            .filter { it in 0 until count }
            .toSet()
    }

    private fun money(v: Double) = "R$ " + String.format(java.util.Locale.ROOT, "%.2f", v).replace('.', ',')

    val LINKS_SCHEMA: JsonObject = json.parseToJsonElement(
        """{"type":"OBJECT","properties":{"pdfs":{"type":"ARRAY","items":{"type":"STRING"}},"imagens":{"type":"ARRAY","items":{"type":"STRING"}}},"required":["pdfs","imagens"]}"""
    ).jsonObject

    /** Pede à IA os links dos encartes vigentes entre os links da página. */
    fun linksPrompt(pageUrl: String, candidates: List<Pair<String, String>>): String = buildString {
        append("Página de um supermercado: ").append(pageUrl).append('\n')
        append("Abaixo, os links e imagens da página. Responda com as URLs (exatamente como aparecem) dos encartes/folhetos ")
        append("de ofertas vigentes: em \"pdfs\" os arquivos PDF de encarte e em \"imagens\" as imagens das páginas do encarte, ")
        append("na ordem das páginas. Ignore logotipos, banners de categoria, ícones e redes sociais. Se não houver, responda listas vazias.\n\n")
        candidates.forEach { (url, label) -> append(url).append(" | ").append(label).append('\n') }
    }

    /** Lê a resposta da escolha de links; aceita só URLs que estavam na página (nada inventado). */
    fun parseLinks(body: String, allowed: Set<String>): Pair<List<String>, List<String>> {
        val root = json.parseToJsonElement(body).jsonObject
        val text = (root["candidates"] as? JsonArray)?.firstOrNull()?.jsonObject
            ?.get("content")?.jsonObject?.get("parts")?.jsonArray
            ?.mapNotNull { (it as? JsonObject)?.get("text") as? JsonPrimitive }
            ?.joinToString("") { it.content } ?: return emptyList<String>() to emptyList()
        val obj = runCatching { json.parseToJsonElement(text.trim()).jsonObject }.getOrNull() ?: return emptyList<String>() to emptyList()
        fun list(key: String) = (obj[key] as? JsonArray).orEmpty()
            .mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            .filter { it in allowed }
            .distinct()
        return list("pdfs") to list("imagens")
    }

    /**
     * Configuração de raciocínio mais rápida aceita por cada família de modelo:
     * Gemini 3.x usa `thinkingLevel` ("low" é aceito por todos), Gemini 2.5 Flash usa `thinkingBudget` 0.
     */
    fun fastThinking(model: String): Pair<String, Any>? {
        val m = model.lowercase()
        return when {
            Regex("""gemini-[3-9]""").containsMatchIn(m) -> "thinkingLevel" to "low"
            m.contains("gemini-2.5-flash") -> "thinkingBudget" to 0
            else -> null
        }
    }

    /** Tempo pedido pela API para tentar de novo (RetryInfo "23s"), se houver. */
    fun retryDelaySeconds(body: String): Int? = Regex(""""retryDelay"\s*:\s*"(\d+)(?:\.\d+)?s"""").find(body)?.groupValues?.get(1)?.toIntOrNull()

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
