package com.smartmarket.domain.flyers

import com.smartmarket.domain.matching.FuzzyMatcher
import com.smartmarket.domain.ocr.ParsedOffer
import kotlin.math.abs
import kotlin.math.max

/**
 * Confere os preços lidos pela IA com a leitura local (que liga preço e nome pela posição na página).
 * Corrige o erro típico da IA de pegar o preço do card vizinho: "Leite Integral Glória 1L" com o
 * R$ 2,59 do macarrão ao lado, quando a leitura local achou R$ 4,99 para o leite.
 */
object AiPriceCheck {

    private const val SAME_PRODUCT = 0.70

    fun reconcile(ai: List<ParsedOffer>, local: List<ParsedOffer>): List<ParsedOffer> {
        if (ai.isEmpty() || local.isEmpty()) return ai
        val localByPage = local.groupBy { it.page }
        return ai.map { a ->
            val pageLocal = localByPage[a.page].orEmpty()
            val match = pageLocal
                .map { it to similarity(a.description, it.description) }
                .filter { it.second >= SAME_PRODUCT }
                .maxByOrNull { it.second }?.first
                ?: return@map a
            if (samePrice(a.price, match.price) || samePrice(a.price, match.retailPrice) || samePrice(a.retailPrice, match.price)) {
                return@map a
            }
            // o preço da IA é de outro produto da mesma página? então pegou o vizinho: vale o local
            val belongsToOther = pageLocal.any { o ->
                o !== match && (samePrice(a.price, o.price) || samePrice(a.price, o.retailPrice)) &&
                    similarity(a.description, o.description) < SAME_PRODUCT
            }
            if (belongsToOther) {
                a.copy(price = match.price, retailPrice = match.retailPrice)
            } else {
                val other = "outra leitura: R$ " + String.format(java.util.Locale.ROOT, "%.2f", match.price).replace('.', ',')
                a.copy(note = listOf(a.note, "conferir preço ($other)").filter { it.isNotBlank() }.joinToString(", "))
            }
        }
    }

    private fun similarity(a: String, b: String): Double =
        max(FuzzyMatcher.calculateAffinity(a, b), FuzzyMatcher.calculateAffinity(b, a))

    private fun samePrice(a: Double?, b: Double?): Boolean = a != null && b != null && abs(a - b) < 0.005
}
