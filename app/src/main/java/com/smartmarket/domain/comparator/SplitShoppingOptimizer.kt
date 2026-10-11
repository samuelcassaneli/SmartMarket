package com.smartmarket.domain.comparator

import kotlin.math.max

/**
 * Sugere dividir a compra entre até 2 mercados (cada item no mais barato dos dois)
 * quando isso economiza de verdade em relação ao melhor mercado único.
 *
 * Função pura: não conhece Room nem Android, para ser testável na JVM.
 */
object SplitShoppingOptimizer {

    const val MIN_SAVINGS_ABS = 10.0
    const val MIN_SAVINGS_PERCENT = 3.0

    /** Item da lista a ser comprado. */
    data class Line(val productId: Long, val name: String, val quantity: Double)

    data class AssignedItem(
        val productId: Long,
        val name: String,
        val quantity: Double,
        val unitPrice: Double
    ) {
        val total: Double get() = unitPrice * quantity
    }

    data class MarketShare(val marketId: Long, val items: List<AssignedItem>) {
        val subtotal: Double get() = items.sumOf { it.total }
    }

    data class SplitPlan(
        val first: MarketShare,
        val second: MarketShare,
        /** Mercado único usado como referência (o melhor da lista). */
        val bestSingleMarketId: Long,
        /** Total do melhor mercado único. */
        val bestSingleTotal: Double,
        /** Custo da divisão considerando só os itens que o mercado único também tem (comparação justa). */
        val comparableSplitTotal: Double,
        /** Economia em R$ contra o melhor mercado único. */
        val savings: Double,
        /** Economia em % do total do mercado único. */
        val savingsPercent: Double,
        /** Itens a mais que a divisão cobre e o mercado único não tem. */
        val extraItemsCovered: Int
    ) {
        val total: Double get() = first.subtotal + second.subtotal
        val shares: List<MarketShare> get() = listOf(first, second)
    }

    /** Resultado do melhor mercado único: maior cobertura, depois menor total. */
    data class SingleBest(val marketId: Long, val total: Double, val covered: Int)

    fun bestSingle(lines: List<Line>, prices: Map<Long, Map<Long, Double>>): SingleBest? =
        prices.mapNotNull { (marketId, table) ->
            val covered = lines.filter { table[it.productId] != null }
            if (covered.isEmpty()) null
            else SingleBest(marketId, covered.sumOf { table.getValue(it.productId) * it.quantity }, covered.size)
        }.sortedWith(compareByDescending<SingleBest> { it.covered }.thenBy { it.total }.thenBy { it.marketId })
            .firstOrNull()

    /**
     * @param lines itens da lista
     * @param prices mercado -> (produto -> preço unitário)
     * @return a melhor divisão, ou null se não compensar (economia ≤ max(R$ 10, 3%)).
     */
    fun optimize(
        lines: List<Line>,
        prices: Map<Long, Map<Long, Double>>,
        minSavingsAbs: Double = MIN_SAVINGS_ABS,
        minSavingsPercent: Double = MIN_SAVINGS_PERCENT
    ): SplitPlan? {
        val validLines = lines.filter { it.quantity > 0.0 }
        if (validLines.size < 2) return null
        val markets = prices.keys.sorted()
        if (markets.size < 2) return null

        val single = bestSingle(validLines, prices) ?: return null
        val singleTable = prices.getValue(single.marketId)
        val singleItems = validLines.filter { singleTable[it.productId] != null }

        var best: SplitPlan? = null
        for (i in markets.indices) {
            for (j in i + 1 until markets.size) {
                val a = markets[i]
                val b = markets[j]
                val ta = prices.getValue(a)
                val tb = prices.getValue(b)
                // Precisa cobrir tudo que o mercado único cobre, senão a comparação é injusta.
                if (singleItems.any { ta[it.productId] == null && tb[it.productId] == null }) continue

                val itemsA = mutableListOf<AssignedItem>()
                val itemsB = mutableListOf<AssignedItem>()
                for (line in validLines) {
                    val pa = ta[line.productId]
                    val pb = tb[line.productId]
                    when {
                        pa != null && (pb == null || pa <= pb) ->
                            itemsA += AssignedItem(line.productId, line.name, line.quantity, pa)
                        pb != null ->
                            itemsB += AssignedItem(line.productId, line.name, line.quantity, pb)
                    }
                }
                // Se um dos mercados não recebe nada, não é divisão.
                if (itemsA.isEmpty() || itemsB.isEmpty()) continue

                val singleIds = singleItems.map { it.productId }.toSet()
                val comparable = (itemsA + itemsB).filter { it.productId in singleIds }.sumOf { it.total }
                val savings = single.total - comparable
                val covered = itemsA.size + itemsB.size
                val plan = SplitPlan(
                    first = MarketShare(a, itemsA),
                    second = MarketShare(b, itemsB),
                    bestSingleMarketId = single.marketId,
                    bestSingleTotal = single.total,
                    comparableSplitTotal = comparable,
                    savings = savings,
                    savingsPercent = if (single.total > 0) savings / single.total * 100.0 else 0.0,
                    extraItemsCovered = covered - singleItems.size
                )
                val current = best
                if (current == null ||
                    plan.savings > current.savings + 0.001 ||
                    (kotlin.math.abs(plan.savings - current.savings) <= 0.001 && plan.extraItemsCovered > current.extraItemsCovered)
                ) {
                    best = plan
                }
            }
        }

        val plan = best ?: return null
        val threshold = max(minSavingsAbs, single.total * minSavingsPercent / 100.0)
        if (plan.savings <= threshold) return null
        // Mercado com maior parte da compra primeiro
        return if (plan.second.subtotal > plan.first.subtotal) plan.copy(first = plan.second, second = plan.first) else plan
    }
}
