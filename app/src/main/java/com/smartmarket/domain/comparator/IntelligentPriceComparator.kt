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

data class MarketComparisonResult(
    val supermarket: Supermarket,
    val totalPrice: Double,
    val previousTotalPrice: Double,
    val inflationPercent: Double, // Quanto a lista subiu/caiu neste mercado
    val itemsAvailableCount: Int,
    val totalItemsCount: Int,
    val missingItems: List<String>,
    val itemDetails: List<ItemMarketDetail>,
    val isBestPrice: Boolean = false,
    val differenceFromBest: Double = 0.0,
    val percentageMoreExpensive: Double = 0.0
)

data class MonthComparisonSummary(
    val currentMonth: Int,
    val currentYear: Int,
    val previousMonth: Int,
    val previousYear: Int,
    val listName: String,
    val bestSupermarketCurrent: Supermarket?,
    val bestSupermarketPrevious: Supermarket?,
    val currentPotentialSavings: Double,
    val overallMonthVariationPercent: Double, // Variação média dos preços entre os 2 meses
    val marketResults: List<MarketComparisonResult>
)

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
        if (supermarkets.isEmpty() || items.isEmpty()) {
            return MonthComparisonSummary(
                currentMonth = currentMonth,
                currentYear = currentYear,
                previousMonth = previousMonth,
                previousYear = previousYear,
                listName = listName,
                bestSupermarketCurrent = null,
                bestSupermarketPrevious = null,
                currentPotentialSavings = 0.0,
                overallMonthVariationPercent = 0.0,
                marketResults = emptyList()
            )
        }

        val curMap = currentPrices
            .filter { it.referenceMonth == currentMonth && it.referenceYear == currentYear }
            .associateBy { Pair(it.supermarketId, it.productId) }

        val prevMap = previousPrices
            .filter { it.referenceMonth == previousMonth && it.referenceYear == previousYear }
            .associateBy { Pair(it.supermarketId, it.productId) }

        val results = supermarkets.map { market ->
            var curTotal = 0.0
            var prevTotal = 0.0
            var availableCount = 0
            val missing = mutableListOf<String>()
            val details = mutableListOf<ItemMarketDetail>()

            for (item in items) {
                val curPrice = curMap[Pair(market.id, item.productId)]?.price
                val prevPrice = prevMap[Pair(market.id, item.productId)]?.price

                val curSubtotal = curPrice?.let { it * item.quantity }
                val prevSubtotal = prevPrice?.let { it * item.quantity }

                val changePct = if (curPrice != null && prevPrice != null && prevPrice > 0.0) {
                    ((curPrice - prevPrice) / prevPrice) * 100.0
                } else null

                if (curSubtotal != null) {
                    curTotal += curSubtotal
                    availableCount++
                } else {
                    missing.add(item.productName)
                }

                if (prevSubtotal != null) {
                    prevTotal += prevSubtotal
                }

                details.add(
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
                )
            }

            val inflation = if (prevTotal > 0.0 && curTotal > 0.0) {
                ((curTotal - prevTotal) / prevTotal) * 100.0
            } else 0.0

            MarketComparisonResult(
                supermarket = market,
                totalPrice = curTotal,
                previousTotalPrice = prevTotal,
                inflationPercent = inflation,
                itemsAvailableCount = availableCount,
                totalItemsCount = items.size,
                missingItems = missing,
                itemDetails = details
            )
        }

        // Mercados válidos que possuem itens cotados
        val validResults = results.filter { it.totalPrice > 0 }
        val sortedCurrent = results.sortedWith(
            compareByDescending<MarketComparisonResult> { it.itemsAvailableCount }
                .thenBy { it.totalPrice }
        )

        val bestCurrent = sortedCurrent.firstOrNull { it.totalPrice > 0 }
        val worstCurrent = sortedCurrent.lastOrNull { it.totalPrice > 0 }

        val bestPrev = results.filter { it.previousTotalPrice > 0 }
            .minByOrNull { it.previousTotalPrice }?.supermarket

        val savings = if (bestCurrent != null && worstCurrent != null && bestCurrent != worstCurrent) {
            worstCurrent.totalPrice - bestCurrent.totalPrice
        } else 0.0

        val finalResults = sortedCurrent.map { res ->
            val isBest = res == bestCurrent && res.totalPrice > 0
            val diff = if (bestCurrent != null && res.totalPrice > 0) res.totalPrice - bestCurrent.totalPrice else 0.0
            val pct = if (bestCurrent != null && bestCurrent.totalPrice > 0 && diff > 0) (diff / bestCurrent.totalPrice) * 100.0 else 0.0
            res.copy(
                isBestPrice = isBest,
                differenceFromBest = diff,
                percentageMoreExpensive = pct
            )
        }

        val totalCurAll = validResults.map { it.totalPrice }.average().takeIf { !it.isNaN() } ?: 0.0
        val totalPrevAll = validResults.map { it.previousTotalPrice }.filter { it > 0 }.average().takeIf { !it.isNaN() } ?: 0.0
        val overallVar = if (totalPrevAll > 0.0) ((totalCurAll - totalPrevAll) / totalPrevAll) * 100.0 else 0.0

        return MonthComparisonSummary(
            currentMonth = currentMonth,
            currentYear = currentYear,
            previousMonth = previousMonth,
            previousYear = previousYear,
            listName = listName,
            bestSupermarketCurrent = bestCurrent?.supermarket,
            bestSupermarketPrevious = bestPrev,
            currentPotentialSavings = savings,
            overallMonthVariationPercent = overallVar,
            marketResults = finalResults
        )
    }
}
