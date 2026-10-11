package com.smartmarket.data.repo

import androidx.room.withTransaction
import com.smartmarket.data.db.AppDatabase
import com.smartmarket.data.entity.PantryItem
import com.smartmarket.data.entity.PriceSource
import com.smartmarket.data.entity.Product
import com.smartmarket.data.entity.Purchase
import com.smartmarket.data.entity.PurchaseItem
import com.smartmarket.data.entity.Supermarket
import com.smartmarket.domain.matching.ProductMatcher
import com.smartmarket.domain.nfce.NfceItem
import com.smartmarket.domain.nfce.NfceReceipt
import com.smartmarket.domain.nfce.NfceText

/** Mercado escolhido na revisão da nota. */
sealed interface MarketSelection {
    data class Existing(val supermarketId: Long) : MarketSelection
    data class New(val name: String) : MarketSelection
    data object None : MarketSelection
}

/**
 * Linha da nota já revisada pelo usuário.
 * - [productId]: produto existente vinculado;
 * - [newProduct]: produto a criar (id = 0) e vincular;
 * - nenhum dos dois: item salvo sem vínculo (novo ou ignorado).
 */
data class ReceiptLine(
    val item: NfceItem,
    val productId: Long? = null,
    val newProduct: Product? = null
)

/** Grava compras importadas de NFC-e e cuida dos efeitos (preços, despensa, aliases). */
class PurchaseRepository(
    private val db: AppDatabase,
    private val priceRepository: PriceRepository,
    private val productMatcher: ProductMatcher
) {
    private val purchaseDao = db.purchaseDao()
    private val productDao = db.productDao()
    private val supermarketDao = db.supermarketDao()
    private val pantryDao = db.pantryDao()

    suspend fun findByAccessKey(accessKey: String): Purchase? =
        if (accessKey.isBlank()) null else purchaseDao.findByAccessKey(accessKey)

    /**
     * Salva a nota numa transação: compra, itens, preços (source NFCE), aliases e despensa.
     * @return id da compra criada.
     * @throws DuplicateReceiptException se a chave já foi importada.
     */
    suspend fun saveReceipt(
        receipt: NfceReceipt,
        market: MarketSelection,
        lines: List<ReceiptLine>,
        addToPantry: Boolean
    ): Long {
        findByAccessKey(receipt.accessKey)?.let { throw DuplicateReceiptException(it.id) }

        return db.withTransaction {
            val supermarketId = resolveMarket(receipt, market)

            // Produtos novos primeiro, para ter os ids
            val resolved = lines.map { line ->
                val productId = line.productId ?: line.newProduct?.let { draft ->
                    productDao.insertProduct(draft.copy(id = 0, normalizedName = draft.name.lowercase().trim()))
                }
                line to productId
            }

            val purchaseId = purchaseDao.insertPurchase(
                Purchase(
                    accessKey = receipt.accessKey.ifBlank { null },
                    supermarketId = supermarketId,
                    cnpj = receipt.cnpj,
                    storeName = receipt.storeName,
                    storeAddress = receipt.address,
                    issuedAt = receipt.issuedAt,
                    totalAmount = receipt.total,
                    discount = receipt.discount,
                    paymentMethod = receipt.paymentMethod,
                    itemCount = lines.size,
                    sourceUrl = receipt.sourceUrl
                )
            )

            purchaseDao.insertItems(resolved.map { (line, productId) ->
                PurchaseItem(
                    purchaseId = purchaseId,
                    productId = productId,
                    rawDescription = line.item.description,
                    code = line.item.code,
                    quantity = line.item.quantity,
                    unit = line.item.unit,
                    unitPrice = line.item.unitPrice,
                    totalPrice = line.item.totalPrice
                )
            })

            val linked = resolved.mapNotNull { (line, productId) -> productId?.let { line.item to it } }

            // Preços: um por produto (se o mesmo produto aparece 2x, vale o último)
            if (supermarketId != null) {
                val entries = linked
                    .filter { (item, _) -> item.unitPrice > 0.0 }
                    .associate { (item, productId) -> productId to item.unitPrice }
                    .map { (productId, price) ->
                        PriceEntry(
                            productId = productId,
                            supermarketId = supermarketId,
                            price = price,
                            source = PriceSource.NFCE,
                            observedAt = receipt.issuedAt,
                            note = "NFC-e ${receipt.number}".trim()
                        )
                    }
                priceRepository.recordPrices(entries)
            }

            // Aprende os vínculos confirmados
            for ((item, productId) in linked) {
                productMatcher.learn(item.description, productId, PriceSource.NFCE)
            }

            // Despensa
            if (addToPantry) {
                val now = System.currentTimeMillis()
                linked.groupBy({ it.second }, { it.first }).forEach { (productId, items) ->
                    val product = productDao.getProductById(productId) ?: return@forEach
                    val add = items.sumOf { NfceText.pantryQuantity(it.quantity, it.unit, product.unit) }
                    val current = pantryDao.get(productId)
                    pantryDao.upsert(
                        (current ?: PantryItem(productId = productId)).copy(
                            quantity = (current?.quantity ?: 0.0) + add,
                            updatedAt = now
                        )
                    )
                }
            }

            purchaseId
        }
    }

    private suspend fun resolveMarket(receipt: NfceReceipt, market: MarketSelection): Long? = when (market) {
        is MarketSelection.Existing -> {
            val existing = supermarketDao.getSupermarketById(market.supermarketId)
            if (existing != null && existing.cnpj.isBlank() && receipt.cnpj.isNotBlank()) {
                // Grava o CNPJ para as próximas notas casarem sozinhas
                supermarketDao.updateSupermarket(
                    existing.copy(
                        cnpj = receipt.cnpj,
                        address = existing.address.ifBlank { receipt.address },
                        city = existing.city.ifBlank { receipt.city }
                    )
                )
            }
            existing?.id
        }
        is MarketSelection.New -> supermarketDao.insertSupermarket(
            Supermarket(
                name = market.name.ifBlank { NfceText.cleanStoreName(receipt.storeName) },
                address = receipt.address,
                cnpj = receipt.cnpj,
                city = receipt.city,
                colorHex = colorFor(market.name)
            )
        )
        MarketSelection.None -> null
    }

    /** Exclui a compra e seus itens. Preços já registrados ficam no histórico. */
    suspend fun deletePurchase(purchaseId: Long) {
        val purchase = purchaseDao.getById(purchaseId) ?: return
        purchaseDao.deletePurchase(purchase)
    }

    private fun colorFor(name: String): String {
        val palette = listOf("#1E88E5", "#43A047", "#F4511E", "#8E24AA", "#00897B", "#FB8C00", "#3949AB", "#D81B60")
        return palette[(name.hashCode() and Int.MAX_VALUE) % palette.size]
    }
}

class DuplicateReceiptException(val purchaseId: Long) : Exception("Esta nota já foi importada.")
