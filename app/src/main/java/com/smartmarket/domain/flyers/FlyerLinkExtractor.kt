package com.smartmarket.domain.flyers

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import org.jsoup.Jsoup

/** Um encarte encontrado na página do mercado. */
data class FlyerSource(
    /** Identificador estável do encarte (id do site ou a URL do PDF). */
    val key: String,
    val title: String,
    /** URLs dos arquivos: um PDF ou as páginas em imagem, na ordem. */
    val urls: List<String>,
    val isPdf: Boolean
)

/**
 * Extrai os links dos encartes do HTML estático das páginas dos mercados. Puro (Jsoup e
 * kotlinx-serialization), testável na JVM com as páginas salvas em `test/resources/flyers`.
 */
object FlyerLinkExtractor {

    private const val SPANI_BUCKET = "spani-tabloides-bucket"
    private val PDF_IN_TEXT = Regex("""(?:https?:)?//[^\s"'<>()]+?\.pdf(?:\?[^\s"'<>()]*)?""", RegexOption.IGNORE_CASE)
    private val DOM_IMAGE = Regex("""(?:https?:)?//meu-varejo[^\s"'<>()\\]+?/dom-atacadista/(\d{4})/(\d{2})/[^\s"'<>()\\]+?\.(?:jpe?g|png|webp)""", RegexOption.IGNORE_CASE)
    private val YEAR_MONTH = Regex("""/(\d{4})/(\d{2})/""")

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** "//host/x" vira "https://host/x"; "http:" vira "https:". */
    fun normalizeUrl(url: String): String {
        val u = url.trim().replace("&amp;", "&")
        return when {
            u.startsWith("//") -> "https:$u"
            u.startsWith("http://") -> "https://" + u.removePrefix("http://")
            else -> u
        }
    }

    // ------------------------------------------------------------------ Spani

    /**
     * PDFs do bucket de tabloides do Spani. Prefere os que contêm "resende"
     * ([cityHint]); se não houver, qualquer PDF do bucket.
     */
    fun extractSpaniPdfLinks(html: String, cityHint: String = "resende"): List<String> {
        val found = extractPdfLinks(html)
        val bucket = found.filter { it.contains(SPANI_BUCKET, ignoreCase = true) }
        val city = bucket.filter { it.contains(cityHint, ignoreCase = true) }
        return when {
            city.isNotEmpty() -> city
            bucket.isNotEmpty() -> bucket
            else -> emptyList()
        }
    }

    /** Todos os links de PDF da página (atributos e textos de script), normalizados e sem repetição. */
    fun extractPdfLinks(html: String): List<String> {
        val found = LinkedHashSet<String>()
        val doc = Jsoup.parse(html)
        doc.select("a[href], iframe[src], embed[src], object[data]").forEach { el ->
            val raw = el.attr("href").ifBlank { el.attr("src") }.ifBlank { el.attr("data") }
            if (raw.contains(".pdf", ignoreCase = true)) found += normalizeUrl(raw)
        }
        // links que só aparecem em scripts/JSON
        PDF_IN_TEXT.findAll(html.replace("\\/", "/")).forEach { found += normalizeUrl(it.value) }
        return found.toList()
    }

    fun spaniSources(html: String, cityHint: String = "resende"): List<FlyerSource> =
        extractSpaniPdfLinks(html, cityHint).map { url ->
            FlyerSource(key = url, title = "Tabloide", urls = listOf(url), isPdf = true)
        }

    // ------------------------------------------------------------------ Dom Atacadista

    /**
     * Todas as páginas (imagens) dos encartes do Dom no mês mais recente, sem repetição e na ordem.
     */
    fun extractDomImagePages(html: String): List<String> = domSources(html).flatMap { it.urls }.distinct()

    /**
     * Encartes do Dom separados. A página (Next.js) traz em `__NEXT_DATA__` a lista
     * `store.encartes[]` com id, descrição e páginas; se não houver, cai para as tags `<img>`.
     * Só mantém o mês mais recente (pasta AAAA/MM das imagens).
     */
    fun domSources(html: String): List<FlyerSource> {
        val fromJson = runCatching { domFromNextData(html) }.getOrDefault(emptyList())
        val sources = fromJson.ifEmpty { domFromImages(html) }
        if (sources.isEmpty()) return emptyList()
        val latest = sources.mapNotNull { latestYearMonth(it.urls) }.maxOrNull() ?: return sources
        return sources.mapNotNull { s ->
            val pages = s.urls.filter { yearMonthOf(it) == latest }
            if (pages.isEmpty()) null else s.copy(urls = pages)
        }
    }

    private fun domFromNextData(html: String): List<FlyerSource> {
        val doc = Jsoup.parse(html)
        val data = doc.selectFirst("script#__NEXT_DATA__")?.data()?.takeIf { it.isNotBlank() } ?: return emptyList()
        val root = json.parseToJsonElement(data)
        val encartes = findArray(root, "encartes") ?: return emptyList()
        return encartes.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            val id = (obj["id"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            val title = (obj["description"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty().ifBlank { "Encarte" }
            val pages = (obj["pages"] as? JsonArray).orEmpty()
                .mapNotNull { it as? JsonObject }
                .sortedBy { (it["pageNumber"] as? JsonPrimitive)?.intOrNull ?: 0 }
                .mapNotNull { (it["url"] as? JsonPrimitive)?.contentOrNull }
                .map { normalizeUrl(it) }
                .distinct()
            if (pages.isEmpty()) null else FlyerSource(key = "dom-$id", title = title, urls = pages, isPdf = false)
        }
    }

    private fun findArray(el: JsonElement, key: String): JsonArray? = when (el) {
        is JsonObject -> (el[key] as? JsonArray) ?: el.values.firstNotNullOfOrNull { findArray(it, key) }
        is JsonArray -> el.firstNotNullOfOrNull { findArray(it, key) }
        else -> null
    }

    private fun domFromImages(html: String): List<FlyerSource> {
        val urls = LinkedHashSet<String>()
        val doc = Jsoup.parse(html)
        doc.select("img[src], img[data-src], source[srcset]").forEach { el ->
            val raw = el.attr("src").ifBlank { el.attr("data-src") }.ifBlank { el.attr("srcset").substringBefore(' ') }
            if (DOM_IMAGE.containsMatchIn(raw)) urls += normalizeUrl(raw)
        }
        DOM_IMAGE.findAll(html).forEach { urls += normalizeUrl(it.value) }
        if (urls.isEmpty()) return emptyList()
        return listOf(FlyerSource(key = "dom-imagens", title = "Encarte", urls = urls.toList(), isPdf = false))
    }

    /** "AAAA/MM" como inteiro AAAAMM. */
    private fun yearMonthOf(url: String): Int? =
        YEAR_MONTH.find(url)?.let { it.groupValues[1].toInt() * 100 + it.groupValues[2].toInt() }

    private fun latestYearMonth(urls: List<String>): Int? = urls.mapNotNull { yearMonthOf(it) }.maxOrNull()

    // ------------------------------------------------------------------ Atacadão

    /**
     * Folhetos da página da loja do Atacadão (Next.js): `storeInfo.flyers[]` com nome, validade e
     * `urlFinalDocument` (PDF público). Ignora os vencidos em relação a [todayIso] ("AAAA-MM-DD").
     */
    fun atacadaoSources(html: String, todayIso: String): List<FlyerSource> {
        val data = Jsoup.parse(html).selectFirst("script#__NEXT_DATA__")?.data()?.takeIf { it.isNotBlank() }
            ?: return emptyList()
        val flyers = findArray(json.parseToJsonElement(data), "flyers") ?: return emptyList()
        return flyers.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            if ((obj["exclude"] as? JsonPrimitive)?.contentOrNull == "true") return@mapNotNull null
            val id = (obj["id"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            val url = (obj["urlFinalDocument"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.startsWith("http") }
                ?: return@mapNotNull null
            val validity = obj["validity"] as? JsonObject
            val finalDate = (validity?.get("final") as? JsonPrimitive)?.contentOrNull?.take(10)
            if (finalDate != null && finalDate < todayIso) return@mapNotNull null
            val name = (obj["name"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty().ifBlank { "Folheto" }
            FlyerSource(key = "atacadao-$id", title = name, urls = listOf(url), isPdf = true)
        }.distinctBy { it.key }
    }
}
