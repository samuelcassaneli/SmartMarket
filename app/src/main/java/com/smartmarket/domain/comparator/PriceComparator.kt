package com.smartmarket.domain.comparator

import com.smartmarket.data.dao.ShoppingItemWithProduct
import com.smartmarket.data.entity.ProductPrice
import com.smartmarket.data.entity.Supermarket

data class ItemMarketDetail(
    val productName: String,
    val quantity: Double,
    val unitPrice: Double?,
    val totalPrice: Double?,
    val isAvailable: Boolean
)

data class MarketComparisonResult(
    val supermarket: Supermarket,
    val totalPrice: Double,
    val itemsAvailableCount: Int,
    val totalItemsCount: Int,
    val missingItems: List<String>,
    val itemDetails: List<ItemMarketDetail>,
    val isBestPrice: Boolean = false,
    val differenceFromBest: Double = 0.0,
    val percentageMoreExpensive: Double = 0.0
)

data class ComparisonSummary(
    val referenceMonth: Int,
    val referenceYear: Int,
    val listName: String,
    val bestSupermarket: Supermarket?,
    val potentialSavings: Double,
    val marketResults: List<MarketComparisonResult>
)

object PriceComparator {

    fun compare(
        items: List<ShoppingItemWithProduct>,
        supermarkets: List<Supermarket>,
        prices: List<ProductPrice>,
        month: Int,
        year: Int,
        listName: String
    ): ComparisonSummary {
        if (supermarkets.isEmpty() || items.isEmpty()) {
            return ComparisonSummary(
                referenceMonth = month,
                referenceYear = year,
                listName = listName,
                bestSupermarket = null,
                potentialSavings = 0.0,
                marketResults = emptyList()
            )
        }

        // Mapeia preços para busca rápida: (supermarketId, productId) -> price
        val priceMap = prices
            .filter { it.referenceMonth == month && it.referenceYear == year }
            .associateBy { Pair(it.supermarketId, it.productId) }

        val rawResults = supermarkets.map { market ->
            var marketTotal = 0.0
            var availableCount = 0
            val missingList = mutableListOf<String>()
            val details = mutableListOf<ItemMarketDetail>()

            for (item in items) {
                val priceEntry = priceMap[Pair(market.id, item.productId)]
                if (priceEntry != null) {
                    val subtotal = priceEntry.price * item.quantity
                    marketTotal += subtotal
                    availableCount++
                    details.add(
                        ItemMarketDetail(
                            productName = item.productName,
                            quantity = item.quantity,
                            unitPrice = priceEntry.price,
                            totalPrice = subtotal,
                            isAvailable = true
                        )
                    )
                } else {
                    missingList.add(item.productName)
                    details.add(
                        ItemMarketDetail(
                            productName = item.productName,
                            quantity = item.quantity,
                            unitPrice = null,
                            totalPrice = null,
                            isAvailable = false
                        )
                    )
                }
            }

            MarketComparisonResult(
                supermarket = market,
                totalPrice = marketTotal,
                itemsAvailableCount = availableCount,
                totalItemsCount = items.size,
                missingItems = missingList,
                itemDetails = details
            )
        }

        // Ordena pelos que têm mais itens e menor preço
        // Prioriza mercados com cobertura total ou parcial com menor total
        val sorted = rawResults.sortedWith(
            compareByDescending<MarketComparisonResult> { it.itemsAvailableCount }
                .thenBy { it.totalPrice }
        )

        val best = sorted.firstOrNull { it.totalPrice > 0 }
        val worst = sorted.lastOrNull { it.totalPrice > 0 }
        val savings = if (best != null && worst != null && worst != best) {
            worst.totalPrice - best.totalPrice
        } else 0.0

        val finalResults = sorted.map { result ->
            val isBest = result == best && result.totalPrice > 0
            val diff = if (best != null && result.totalPrice > 0) result.totalPrice - best.totalPrice else 0.0
            val pct = if (best != null && best.totalPrice > 0 && diff > 0) (diff / best.totalPrice) * 100.0 else 0.0
            result.copy(
                isBestPrice = isBest,
                differenceFromBest = diff,
                percentageMoreExpensive = pct
            )
        }

        return ComparisonSummary(
            referenceMonth = month,
            referenceYear = year,
            listName = listName,
            bestSupermarket = best?.supermarket,
            potentialSavings = savings,
            marketResults = finalResults
        )
    }
}
