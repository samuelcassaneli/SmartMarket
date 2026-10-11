package com.smartmarket.domain.flyers

import com.smartmarket.domain.matching.MatchOutcome
import com.smartmarket.domain.matching.ProductMatcher
import com.smartmarket.domain.ocr.ParsedOffer
import java.net.URLDecoder
import java.net.URLEncoder

/** Oferta lida do encarte com o resultado do casamento com o catálogo. */
data class MatchedOffer(val parsed: ParsedOffer, val outcome: MatchOutcome)

/** Casa as ofertas com os produtos (alias e depois fuzzy), numa única carga do catálogo. */
suspend fun matchOffers(offers: List<ParsedOffer>, matcher: ProductMatcher): List<MatchedOffer> {
    if (offers.isEmpty()) return emptyList()
    val outcomes = matcher.matchAll(offers.map { it.description })
    return offers.zip(outcomes) { p, o -> MatchedOffer(p, o) }
}

/**
 * `FlyerOffer.rawText` guarda a descrição e, entre colchetes no fim, a observação
 * ("Leite Glória 1L [atacado a partir de 12 un]"). Assim não é preciso mudar o schema.
 */
object OfferText {
    private val NOTE = Regex("""\s*\[([^\[\]]+)]\s*$""")

    fun compose(description: String, note: String): String =
        if (note.isBlank()) description else "$description [$note]"

    /** (descrição, observação) */
    fun split(raw: String): Pair<String, String> {
        val m = NOTE.find(raw) ?: return raw.trim() to ""
        return raw.substring(0, m.range.first).trim() to m.groupValues[1].trim()
    }

    fun description(raw: String): String = split(raw).first
}

/**
 * `Flyer.sourceUrl` dos encartes em imagem leva o título do encarte no fragmento
 * ("https://site/encartes#L%C3%A2mina%20Feirinha").
 */
object FlyerTitle {
    fun encode(pageUrl: String, title: String): String =
        if (title.isBlank()) pageUrl else "$pageUrl#" + URLEncoder.encode(title.trim(), "UTF-8").replace("+", "%20")

    fun decode(sourceUrl: String): String? {
        val frag = sourceUrl.substringAfter('#', "")
        if (frag.isBlank()) return null
        return runCatching { URLDecoder.decode(frag, "UTF-8") }.getOrNull()?.takeIf { it.isNotBlank() }
    }
}
