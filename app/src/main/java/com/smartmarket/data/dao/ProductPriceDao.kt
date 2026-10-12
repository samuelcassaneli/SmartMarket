package com.smartmarket.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.smartmarket.data.entity.PriceObservation
import com.smartmarket.data.entity.ProductPrice
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductPriceDao {
    @Query("SELECT * FROM product_prices WHERE referenceMonth = :month AND referenceYear = :year")
    fun getPricesForMonth(month: Int, year: Int): Flow<List<ProductPrice>>

    @Query("SELECT * FROM product_prices WHERE referenceMonth = :month AND referenceYear = :year")
    suspend fun getPricesForMonthSync(month: Int, year: Int): List<ProductPrice>

    @Query("SELECT * FROM product_prices WHERE productId = :productId ORDER BY referenceYear DESC, referenceMonth DESC")
    fun getPriceHistoryForProduct(productId: Long): Flow<List<ProductPrice>>

    @Query("SELECT * FROM product_prices WHERE productId = :productId AND referenceMonth = :month AND referenceYear = :year")
    suspend fun getPricesForProductInMonth(productId: Long, month: Int, year: Int): List<ProductPrice>

    @Query("SELECT * FROM product_prices WHERE productId = :productId AND supermarketId = :supermarketId AND referenceMonth = :month AND referenceYear = :year LIMIT 1")
    suspend fun getPrice(productId: Long, supermarketId: Long, month: Int, year: Int): ProductPrice?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrice(price: ProductPrice): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrices(prices: List<ProductPrice>)

    @Query("DELETE FROM product_prices WHERE id = :id")
    suspend fun deletePriceById(id: Long)

    @Query("DELETE FROM product_prices WHERE supermarketId = :supermarketId")
    suspend fun deletePricesBySupermarket(supermarketId: Long)

    @Query("""
        SELECT pp.productId, p.name AS productName, pp.supermarketId, s.name AS supermarketName, MIN(pp.price) AS price
        FROM product_prices pp
        INNER JOIN products p ON p.id = pp.productId
        INNER JOIN supermarkets s ON s.id = pp.supermarketId
        WHERE pp.referenceMonth = :month AND pp.referenceYear = :year
        GROUP BY pp.productId
    """)
    fun getBestPricesForMonth(month: Int, year: Int): Flow<List<BestPrice>>

    // ---- Histórico detalhado ----

    @Insert
    suspend fun insertObservation(observation: PriceObservation): Long

    @Insert
    suspend fun insertObservations(observations: List<PriceObservation>)

    @Query("""
        SELECT o.id, o.productId, o.supermarketId, s.name AS supermarketName, s.colorHex,
               o.price, o.source, o.observedAt, o.note
        FROM price_observations o
        INNER JOIN supermarkets s ON s.id = o.supermarketId
        WHERE o.productId = :productId
        ORDER BY o.observedAt ASC
    """)
    fun getObservationsForProduct(productId: Long): Flow<List<PriceObservationWithMarket>>

    @Query("""
        SELECT :productId AS productId,
               MIN(price) AS minPrice, MAX(price) AS maxPrice, AVG(price) AS avgPrice,
               (SELECT price FROM price_observations WHERE productId = :productId ORDER BY observedAt DESC LIMIT 1) AS lastPrice,
               MAX(observedAt) AS lastObservedAt,
               COUNT(*) AS observationCount
        FROM price_observations WHERE productId = :productId
    """)
    fun getStatsForProduct(productId: Long): Flow<ProductPriceStats>

    /** Menor preço observado antes de [before], para detectar quedas. */
    @Query("SELECT MIN(price) FROM price_observations WHERE productId = :productId AND observedAt < :before")
    suspend fun getMinPriceBefore(productId: Long, before: Long): Double?

    @Query("SELECT * FROM price_observations WHERE observedAt >= :since ORDER BY observedAt DESC")
    suspend fun getObservationsSince(since: Long): List<PriceObservation>

    /** Observações num intervalo (mais recentes primeiro), para a referência da última verificação. */
    @Query("SELECT * FROM price_observations WHERE observedAt >= :from AND observedAt < :to ORDER BY observedAt DESC")
    suspend fun getObservationsBetween(from: Long, to: Long): List<PriceObservation>
}
