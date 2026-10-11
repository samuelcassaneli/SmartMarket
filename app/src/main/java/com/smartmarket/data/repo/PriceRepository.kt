package com.smartmarket.data.repo

import androidx.room.withTransaction
import com.smartmarket.data.db.AppDatabase
import com.smartmarket.data.entity.PriceObservation
import com.smartmarket.data.entity.ProductPrice
import java.util.Calendar

/**
 * Ponto único de gravação de preços. Toda origem (manual, encarte, nota, site)
 * grava a observação no histórico e atualiza o preço de referência do mês.
 */
class PriceRepository(
    private val db: AppDatabase,
    /** Chamado depois de gravar preços novos (usado para checar alertas de preço). */
    private val onPricesRecorded: () -> Unit = {}
) {

    private val priceDao = db.productPriceDao()

    suspend fun recordPrice(
        productId: Long,
        supermarketId: Long,
        price: Double,
        source: String,
        observedAt: Long = System.currentTimeMillis(),
        note: String = ""
    ) = recordPrices(listOf(PriceEntry(productId, supermarketId, price, source, observedAt, note)))

    suspend fun recordPrices(entries: List<PriceEntry>) {
        if (entries.isEmpty()) return
        db.withTransaction {
            for (e in entries) {
                priceDao.insertObservation(
                    PriceObservation(
                        productId = e.productId,
                        supermarketId = e.supermarketId,
                        price = e.price,
                        source = e.source,
                        observedAt = e.observedAt,
                        note = e.note
                    )
                )
                val cal = Calendar.getInstance().apply { timeInMillis = e.observedAt }
                val month = cal.get(Calendar.MONTH) + 1
                val year = cal.get(Calendar.YEAR)
                val existing = priceDao.getPrice(e.productId, e.supermarketId, month, year)
                // Preço do mês: o mais recente vence (observações antigas não sobrescrevem as novas)
                if (existing == null || existing.updatedAt <= e.observedAt) {
                    priceDao.insertPrice(
                        ProductPrice(
                            id = existing?.id ?: 0,
                            productId = e.productId,
                            supermarketId = e.supermarketId,
                            price = e.price,
                            referenceMonth = month,
                            referenceYear = year,
                            updatedAt = e.observedAt,
                            source = e.source
                        )
                    )
                }
            }
        }
        onPricesRecorded()
    }

    /** Edição direta do preço mensal (tela de preços manuais). */
    suspend fun setMonthlyPrice(productId: Long, supermarketId: Long, price: Double, month: Int, year: Int, source: String) {
        val cal = Calendar.getInstance()
        val isCurrentMonth = cal.get(Calendar.MONTH) + 1 == month && cal.get(Calendar.YEAR) == year
        val observedAt = if (isCurrentMonth) System.currentTimeMillis() else Calendar.getInstance().apply {
            set(year, month - 1, 15, 12, 0, 0)
        }.timeInMillis
        db.withTransaction {
            val existing = priceDao.getPrice(productId, supermarketId, month, year)
            priceDao.insertPrice(
                ProductPrice(
                    id = existing?.id ?: 0,
                    productId = productId,
                    supermarketId = supermarketId,
                    price = price,
                    referenceMonth = month,
                    referenceYear = year,
                    updatedAt = observedAt,
                    source = source
                )
            )
            priceDao.insertObservation(
                PriceObservation(productId = productId, supermarketId = supermarketId, price = price, source = source, observedAt = observedAt)
            )
        }
    }
}

data class PriceEntry(
    val productId: Long,
    val supermarketId: Long,
    val price: Double,
    val source: String,
    val observedAt: Long = System.currentTimeMillis(),
    val note: String = ""
)
