package com.smartmarket.domain.comparator

import com.smartmarket.data.dao.ShoppingItemWithProduct
import com.smartmarket.data.entity.ProductPrice
import com.smartmarket.data.entity.Supermarket

data class ItemMarketDetail(
    val productId: Long,
    val productName: String,
    val category: String,
    val unit: String,
    val quantity: Double,
    val currentUnitPrice: Double?,
    val currentTotalPrice: Double?,
    val previousUnitPrice: Double?,
    val previousTotalPrice: Double?,
    val priceChangePercent: Double?, // Variação em % comparado ao mês anterior
    val isAvailable: Boolean
)

/** Item sem preço em um mercado (ou em nenhum). */
data class MissingItem(val productId: Long, val productName: String)

data class MarketComparisonResult(
    val supermarket: Supermarket,
    val totalPrice: Double,
    val previousTotalPrice: Double,
    /**
     * Variação da lista neste mercado contra o mês anterior, calculada só com os itens
     * que têm preço nos dois meses (comparação justa). Null se não houver itens comparáveis.
     */
    val inflationPercent: Double?,
    val itemsAvailableCount: Int,
    val totalItemsCount: Int,
    val missingItems: List<MissingItem>,
    val itemDetails: List<ItemMarketDetail>,
    val isBestPrice: Boolean = false,
    val differenceFromBest: Double = 0.0,
    val percentageMoreExpensive: Double = 0.0
) {
    val hasAnyPrice: Boolean get() = itemsAvailableCount > 0
    val isComplete: Boolean get() = itemsAvailableCount == totalItemsCount
}

/** Menor preço de um item no mês de referência. */
data class CheapestOffer(val supermarketId: Long, val supermarketName: String, val unitPrice: Double)

/** Divisão sugerida entre 2 mercados, já com as entidades resolvidas. */
data class SplitSuggestion(
    val plan: SplitShoppingOptimizer.SplitPlan,
    val first: Supermarket,
    val second: Supermarket,
    val bestSingle: Supermarket
)

data class MonthComparisonSummary(
    val currentMonth: Int,
    val currentYear: Int,
    val previousMonth: Int,
    val previousYear: Int,
    val listName: String,
    val bestSupermarketCurrent: Supermarket?,
    val bestSupermarketPrevious: Supermarket?,
    /** Economia do mais barato contra o mais caro com a mesma cobertura de itens. */
    val currentPotentialSavings: Double,
    /** Mercado mais caro usado no cálculo da economia. */
    val mostExpensiveComparable: Supermarket? = null,
    val overallMonthVariationPercent: Double, // Variação média dos preços entre os 2 meses
    val marketResults: List<MarketComparisonResult>,
    /** Total no mercado mais barato (0 se não houver preços). */
    val bestTotal: Double = 0.0,
    /** Total ideal comprando cada item onde é mais barato (sem limite de mercados). */
    val idealTotal: Double = 0.0,
    /** Menor preço de cada produto da lista. */
    val cheapestByProduct: Map<Long, CheapestOffer> = emptyMap(),
    /** Itens sem preço em nenhum mercado no mês. */
    val itemsWithoutAnyPrice: List<MissingItem> = emptyList(),
    /** Sugestão de dividir a compra (null quando não compensa). */
    val split: SplitSuggestion? = null
)

/** Resultado enxuto para cartões de lista. */
data class BestMarketTotal(val supermarketId: Long, val total: Double, val covered: Int, val totalItems: Int)

object IntelligentPriceComparator {

    fun compareWithPreviousMonth(
        items: List<ShoppingItemWithProduct>,
        supermarkets: List<Supermarket>,
        currentPrices: List<ProductPrice>,
        previousPrices: List<ProductPrice>,
        currentMonth: Int,
        currentYear: Int,
        previousMonth: Int,
        previousYear: Int,
        listName: String
    ): MonthComparisonSummary {
        val empty = MonthComparisonSummary(
            currentMonth = currentMonth,
            currentYear = currentYear,
            previousMonth = previousMonth,
            previousYear = previousYear,
            listName = listName,
            bestSupermarketCurrent = null,
            bestSupermarketPrevious = null,
            currentPotentialSavings = 0.0,
            overallMonthVariationPercent = 0.0,
            marketResults = emptyList(),
            itemsWithoutAnyPrice = items.map { MissingItem(it.productId, it.productName) }
        )
        if (supermarkets.isEmpty() || items.isEmpty()) return empty

        val marketIds = supermarkets.map { it.id }.toSet()
        val curMap = currentPrices
            .filter { it.referenceMonth == currentMonth && it.referenceYear == currentYear && it.supermarketId in marketIds }
            .associateBy { it.supermarketId to it.productId }
        val prevMap = previousPrices
            .filter { it.referenceMonth == previousMonth && it.referenceYear == previousYear && it.supermarketId in marketIds }
            .associateBy { it.supermarketId to it.productId }

        val results = supermarkets.map { market ->
            var curTotal = 0.0
            var prevTotal = 0.0
            var likeCur = 0.0
            var likePrev = 0.0
            var availableCount = 0
            val missing = mutableListOf<MissingItem>()
            val details = items.map { item ->
                val curPrice = curMap[market.id to item.productId]?.price
                val prevPrice = prevMap[market.id to item.productId]?.price
                val curSubtotal = curPrice?.let { it * item.quantity }
                val prevSubtotal = prevPrice?.let { it * item.quantity }
                val changePct = if (curPrice != null && prevPrice != null && prevPrice > 0.0) {
                    (curPrice - prevPrice) / prevPrice * 100.0
                } else null

                if (curSubtotal != null) {
                    curTotal += curSubtotal
                    availableCount++
                } else {
                    missing += MissingItem(item.productId, item.productName)
                }
                if (prevSubtotal != null) prevTotal += prevSubtotal
                if (curSubtotal != null && prevSubtotal != null) {
                    likeCur += curSubtotal
                    likePrev += prevSubtotal
                }
                ItemMarketDetail(
                    productId = item.productId,
                    productName = item.productName,
                    category = item.productCategory,
                    unit = item.productUnit,
                    quantity = item.quantity,
                    currentUnitPrice = curPrice,
                    currentTotalPrice = curSubtotal,
                    previousUnitPrice = prevPrice,
                    previousTotalPrice = prevSubtotal,
                    priceChangePercent = changePct,
                    isAvailable = curPrice != null
                )
            }
            MarketComparisonResult(
                supermarket = market,
                totalPrice = curTotal,
                previousTotalPrice = prevTotal,
                inflationPercent = if (likePrev > 0.0) (likeCur - likePrev) / likePrev * 100.0 else null,
                itemsAvailableCount = availableCount,
                totalItemsCount = items.size,
                missingItems = missing,
                itemDetails = details
            )
        }

        // Ranking: primeiro quem tem mais itens com preço, depois o menor total.
        val ranking = compareByDescending<MarketComparisonResult> { it.itemsAvailableCount }
            .thenBy { it.totalPrice }
            .thenBy { it.supermarket.name }
        val sorted = results.filter { it.hasAnyPrice }.sortedWith(ranking) + results.filter { !it.hasAnyPrice }
        val best = sorted.firstOrNull { it.hasAnyPrice }

        // Economia justa: contra o mais caro com a mesma cobertura do melhor.
        val sameCoverage = sorted.filter { best != null && it.hasAnyPrice && it.itemsAvailableCount == best.itemsAvailableCount }
        val worstComparable = sameCoverage.maxByOrNull { it.totalPrice }
        val savings = if (best != null && worstComparable != null && worstComparable !== best) {
            worstComparable.totalPrice - best.totalPrice
        } else 0.0

        val finalResults = sorted.map { res ->
            if (best == null || !res.hasAnyPrice) return@map res
            val diff = res.totalPrice - best.totalPrice
            res.copy(
                isBestPrice = res === best,
                differenceFromBest = diff,
                percentageMoreExpensive = if (best.totalPrice > 0) diff / best.totalPrice * 100.0 else 0.0
            )
        }

        val bestPrev = results.filter { it.previousTotalPrice > 0 }
            .sortedWith(compareByDescending<MarketComparisonResult> { r -> r.itemDetails.count { it.previousUnitPrice != null } }
                .thenBy { it.previousTotalPrice })
            .firstOrNull()?.supermarket

        // Variação geral: média ponderada dos itens comparáveis em todos os mercados.
        var allLikeCur = 0.0
        var allLikePrev = 0.0
        results.forEach { r ->
            r.itemDetails.forEach { d ->
                if (d.currentTotalPrice != null && d.previousTotalPrice != null) {
                    allLikeCur += d.currentTotalPrice
                    allLikePrev += d.previousTotalPrice
                }
            }
        }
        val overallVar = if (allLikePrev > 0.0) (allLikeCur - allLikePrev) / allLikePrev * 100.0 else 0.0

        // Menor preço por produto
        val byId = supermarkets.associateBy { it.id }
        val cheapest = items.mapNotNull { item ->
            supermarkets.mapNotNull { m -> curMap[m.id to item.productId]?.let { m to it.price } }
                .minWithOrNull(compareBy<Pair<Supermarket, Double>> { it.second }.thenBy { it.first.name })
                ?.let { (m, p) -> item.productId to CheapestOffer(m.id, m.name, p) }
        }.toMap()
        val withoutAny = items.filter { it.productId !in cheapest }.map { MissingItem(it.productId, it.productName) }
        val ideal = items.sumOf { item -> cheapest[item.productId]?.let { it.unitPrice * item.quantity } ?: 0.0 }

        // Divisão inteligente
        val lines = items.map { SplitShoppingOptimizer.Line(it.productId, it.productName, it.quantity) }
        val priceTable = buildPriceTable(curMap.values, marketIds)
        val split = SplitShoppingOptimizer.optimize(lines, priceTable)?.let { plan ->
            val first = byId[plan.first.marketId]
            val second = byId[plan.second.marketId]
            val single = byId[plan.bestSingleMarketId]
            if (first != null && second != null && single != null) SplitSuggestion(plan, first, second, single) else null
        }

        return MonthComparisonSummary(
            currentMonth = currentMonth,
            currentYear = currentYear,
            previousMonth = previousMonth,
            previousYear = previousYear,
            listName = listName,
            bestSupermarketCurrent = best?.supermarket,
            bestSupermarketPrevious = bestPrev,
            currentPotentialSavings = savings,
            mostExpensiveComparable = if (savings > 0) worstComparable?.supermarket else null,
            overallMonthVariationPercent = overallVar,
            marketResults = finalResults,
            bestTotal = best?.totalPrice ?: 0.0,
            idealTotal = ideal,
            cheapestByProduct = cheapest,
            itemsWithoutAnyPrice = withoutAny,
            split = split
        )
    }

    /** Tabela mercado -> (produto -> preço) a partir dos preços do mês. */
    fun buildPriceTable(prices: Collection<ProductPrice>, marketIds: Set<Long>? = null): Map<Long, Map<Long, Double>> =
        prices.filter { marketIds == null || it.supermarketId in marketIds }
            .groupBy { it.supermarketId }
            .mapValues { (_, list) -> list.associate { it.productId to it.price } }

    /**
     * Total no mercado mais barato para uma lista simples (produto -> quantidade),
     * com o mesmo critério do ranking: mais itens com preço, depois menor total.
     */
    fun bestMarketTotal(quantities: Map<Long, Double>, prices: List<ProductPrice>): BestMarketTotal? {
        if (quantities.isEmpty()) return null
        val lines = quantities.map { (id, q) -> SplitShoppingOptimizer.Line(id, "", q) }
        val single = SplitShoppingOptimizer.bestSingle(lines, buildPriceTable(prices)) ?: return null
        return BestMarketTotal(single.marketId, single.total, single.covered, quantities.size)
    }
}
