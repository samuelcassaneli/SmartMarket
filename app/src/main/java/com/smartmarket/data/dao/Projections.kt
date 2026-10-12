package com.smartmarket.data.dao

import com.smartmarket.data.entity.ProductPrice

data class ShoppingItemWithProduct(
    val itemId: Long,
    val shoppingListId: Long,
    val productId: Long,
    val productName: String,
    val productCategory: String,
    val productUnit: String,
    val defaultMonthlyQuantity: Double,
    val quantity: Double,
    val isChecked: Boolean
)

data class ProductWithPrices(
    val productId: Long,
    val productName: String,
    val productCategory: String,
    val productUnit: String,
    val defaultMonthlyQuantity: Double,
    val prices: List<ProductPrice>
)

/** Resumo de uma lista para a tela de listas. */
data class ListSummary(
    val id: Long,
    val name: String,
    val referenceMonth: Int,
    val referenceYear: Int,
    val budgetTarget: Double,
    val isArchived: Boolean,
    val itemCount: Int,
    val checkedCount: Int
)

/** Gasto agregado de um mês (yearMonth = "AAAA-MM"). */
data class MonthlySpend(
    val yearMonth: String,
    val total: Double,
    val purchaseCount: Int
)

/** Gasto agregado por um rótulo (categoria ou mercado). */
data class LabeledSpend(
    val label: String,
    val total: Double,
    val colorHex: String? = null
)

/** Menor e maior preço já observado de um produto, com o último. */
data class ProductPriceStats(
    val productId: Long,
    val minPrice: Double?,
    val maxPrice: Double?,
    val avgPrice: Double?,
    val lastPrice: Double?,
    val lastObservedAt: Long?,
    val observationCount: Int
)

/** Observação de preço com nome do mercado, para histórico e gráficos. */
data class PriceObservationWithMarket(
    val id: Long,
    val productId: Long,
    val supermarketId: Long,
    val supermarketName: String,
    val colorHex: String,
    val price: Double,
    val source: String,
    val observedAt: Long,
    val note: String = ""
)

/** Melhor preço atual (mês de referência) de cada produto, com o mercado. */
data class BestPrice(
    val productId: Long,
    val productName: String,
    val supermarketId: Long,
    val supermarketName: String,
    val price: Double
)

data class PantryItemWithProduct(
    val productId: Long,
    val productName: String,
    val category: String,
    val unit: String,
    val defaultMonthlyQuantity: Double,
    val quantity: Double,
    val minQuantity: Double,
    val expiresAt: Long?,
    val updatedAt: Long
)

data class PurchaseItemWithProduct(
    val id: Long,
    val purchaseId: Long,
    val productId: Long?,
    val productName: String?,
    val rawDescription: String,
    val code: String,
    val quantity: Double,
    val unit: String,
    val unitPrice: Double,
    val totalPrice: Double
)

data class PurchaseWithMarket(
    val id: Long,
    val accessKey: String?,
    val supermarketId: Long?,
    val supermarketName: String?,
    val colorHex: String?,
    val storeName: String,
    val issuedAt: Long,
    val totalAmount: Double,
    val discount: Double,
    val paymentMethod: String,
    val itemCount: Int
)

data class AlertWithProduct(
    val id: Long,
    val productId: Long,
    val productName: String,
    val targetPrice: Double?,
    val notifyOnAnyDrop: Boolean,
    val active: Boolean,
    val lastNotifiedPrice: Double?
)

data class FlyerOfferWithProduct(
    val id: Long,
    val flyerId: Long,
    val supermarketId: Long,
    val supermarketName: String,
    val productId: Long?,
    val productName: String?,
    val rawText: String,
    val price: Double,
    val confidence: Double,
    val status: String,
    val productImageUrl: String? = null
)

data class FlyerWithMarket(
    val id: Long,
    val supermarketId: Long,
    val supermarketName: String,
    val sourceUrl: String,
    val fetchedAt: Long,
    val pageCount: Int,
    val offerCount: Int,
    val pendingCount: Int,
    val status: String,
    val errorMessage: String
)

/** Preço pago num item de nota. */
data class PaidPrice(val productId: Long, val price: Double, val at: Long)

/** Item de nota de um produto (para saber como ele é vendido). */
data class PaidItem(val productId: Long, val description: String, val unit: String, val price: Double)

/** Oferta de encarte encontrada na busca. */
data class FlyerOfferHit(
    val offerId: Long,
    val flyerId: Long,
    val supermarketId: Long,
    val marketName: String,
    val colorHex: String,
    val rawText: String,
    val price: Double,
    val fetchedAt: Long,
    val status: String,
    val productName: String?,
    val productImageUrl: String? = null
)
