package com.smartmarket.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.smartmarket.data.entity.Flyer
import com.smartmarket.data.entity.FlyerOffer
import kotlinx.coroutines.flow.Flow

@Dao
interface FlyerDao {
    @Query("SELECT * FROM flyers WHERE supermarketId = :supermarketId AND contentHash = :hash LIMIT 1")
    suspend fun findByHash(supermarketId: Long, hash: String): Flyer?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFlyer(flyer: Flyer): Long

    @Insert
    suspend fun insertOffers(offers: List<FlyerOffer>)

    @Query("""
        SELECT f.id, f.supermarketId, s.name AS supermarketName, f.sourceUrl, f.fetchedAt, f.pageCount, f.offerCount,
               (SELECT COUNT(*) FROM flyer_offers o WHERE o.flyerId = f.id AND o.status = 'PENDING') AS pendingCount,
               f.status, f.errorMessage
        FROM flyers f INNER JOIN supermarkets s ON s.id = f.supermarketId
        ORDER BY f.fetchedAt DESC
    """)
    fun getFlyers(): Flow<List<FlyerWithMarket>>

    /** Ofertas não recusadas dos encartes recentes (para a busca). */
    @Query("""
        SELECT o.id AS offerId, o.flyerId AS flyerId, f.supermarketId AS supermarketId, s.name AS marketName,
               s.colorHex AS colorHex, o.rawText AS rawText, o.price AS price, f.fetchedAt AS fetchedAt,
               o.status AS status, p.name AS productName
        FROM flyer_offers o
        INNER JOIN flyers f ON f.id = o.flyerId
        INNER JOIN supermarkets s ON s.id = f.supermarketId
        LEFT JOIN products p ON p.id = o.productId
        WHERE o.status != 'REJECTED' AND f.fetchedAt >= :since
        ORDER BY f.fetchedAt DESC
    """)
    suspend fun getRecentOffers(since: Long): List<FlyerOfferHit>

    @Query("""
        SELECT o.id, o.flyerId, o.supermarketId, s.name AS supermarketName, o.productId, p.name AS productName,
               o.rawText, o.price, o.confidence, o.status
        FROM flyer_offers o
        INNER JOIN supermarkets s ON s.id = o.supermarketId
        LEFT JOIN products p ON p.id = o.productId
        WHERE o.flyerId = :flyerId
        ORDER BY (o.productId IS NULL) ASC, o.confidence DESC
    """)
    fun getOffersForFlyer(flyerId: Long): Flow<List<FlyerOfferWithProduct>>

    @Query("SELECT * FROM flyer_offers WHERE id = :id")
    suspend fun getOffer(id: Long): FlyerOffer?

    @Query("UPDATE flyer_offers SET status = :status, productId = :productId WHERE id = :id")
    suspend fun updateOffer(id: Long, status: String, productId: Long?)

    @Query("SELECT COUNT(*) FROM flyer_offers WHERE status = 'PENDING'")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT * FROM flyer_offers WHERE flyerId = :flyerId")
    suspend fun getOffersForFlyerSync(flyerId: Long): List<FlyerOffer>

    @Query("UPDATE flyer_offers SET status = 'REJECTED' WHERE flyerId = :flyerId AND status = 'PENDING'")
    suspend fun rejectPending(flyerId: Long): Int

    @Query("UPDATE flyer_offers SET status = :status WHERE id IN (:ids)")
    suspend fun setStatus(ids: List<Long>, status: String)

    /** Apaga o encarte e as ofertas (cascata); a próxima busca lê de novo. Preços já aceitos ficam. */
    @Query("DELETE FROM flyers WHERE id = :flyerId")
    suspend fun deleteFlyer(flyerId: Long)
}
