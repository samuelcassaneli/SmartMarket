package com.smartmarket.domain.deals

import com.smartmarket.domain.matching.FuzzyMatcher
import com.smartmarket.domain.matching.SaleForm
import com.smartmarket.domain.online.OnlineOffer

/** Oferta de site que pode ser o produto cadastrado, já com o preço na mesma base (por kg se a granel). */
data class OnlineCandidate(
    val marketId: Long,
    val marketName: String,
    val offer: OnlineOffer,
    val form: SaleForm,
    val price: Double,
    val affinity: Double
)

/**
 * Escolhe, entre os resultados da busca nos sites, o que é de fato o produto cadastrado:
 * mesmo nome (afinidade), mesma forma de venda (granel x pacote) e mesmo tamanho.
 */
object OnlineMatch {

    /** Máximo de ofertas mandadas para a IA conferir por produto. */
    const val MAX_FOR_AI = 15

    fun candidates(
        productName: String,
        productForm: SaleForm,
        found: List<Triple<Long, String, OnlineOffer>>,
        minAffinity: Double
    ): List<OnlineCandidate> = found.mapNotNull { (marketId, marketName, o) ->
        if (!o.available || o.price <= 0.0) return@mapNotNull null
        val affinity = FuzzyMatcher.calculateAffinity(o.name, productName)
        if (affinity < minAffinity) return@mapNotNull null
        val form = SaleForm.ofOffer(o.name, o.measurementUnit, o.unitMultiplier)
        val price = SaleForm.comparablePrice(productForm, form, o.price, o.measurementUnit, o.unitMultiplier)
            ?: return@mapNotNull null
        OnlineCandidate(marketId, marketName, o, form, price, affinity)
    }.sortedByDescending { it.affinity }

    /** Sem IA: por mercado, a de maior afinidade (empate: a mais barata). */
    fun bestByAffinity(candidates: List<OnlineCandidate>): List<OnlineCandidate> =
        candidates.filter { it.affinity >= FuzzyMatcher.AUTO_MATCH_THRESHOLD }
            .groupBy { it.marketId }
            .mapNotNull { (_, list) ->
                list.maxWithOrNull(compareBy<OnlineCandidate> { it.affinity }.thenByDescending { it.price })
            }

    /** Com IA: entre as aprovadas, por mercado, a mais barata (todas são o mesmo produto). */
    fun cheapestApproved(candidates: List<OnlineCandidate>, approved: Set<Int>): List<OnlineCandidate> =
        candidates.filterIndexed { i, _ -> i in approved }
            .groupBy { it.marketId }
            .mapNotNull { (_, list) -> list.minByOrNull { it.price } }

    /** Termo de busca: o nome sem "kg"/"granel" soltos, que atrapalham a busca dos sites. */
    fun searchTerm(name: String): String =
        name.replace(Regex("""(?i)(?<![\d,.])(?<![\d,.]\s)\b(kg|a granel|granel|kilo|quilo)\b"""), " ").replace(Regex("\\s+"), " ").trim()
            .ifBlank { name }
}
