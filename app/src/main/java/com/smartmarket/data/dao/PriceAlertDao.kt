package com.smartmarket.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.smartmarket.data.entity.PriceAlert
import kotlinx.coroutines.flow.Flow

@Dao
interface PriceAlertDao {
    @Query("""
        SELECT a.id, a.productId, p.name AS productName, a.targetPrice, a.notifyOnAnyDrop, a.active, a.lastNotifiedPrice
        FROM price_alerts a INNER JOIN products p ON p.id = a.productId
        ORDER BY p.name ASC
    """)
    fun getAlerts(): Flow<List<AlertWithProduct>>

    @Query("SELECT * FROM price_alerts WHERE active = 1")
    suspend fun getActiveAlerts(): List<PriceAlert>

    @Query("SELECT * FROM price_alerts WHERE productId = :productId LIMIT 1")
    fun observeForProduct(productId: Long): Flow<PriceAlert?>

    @Query("SELECT * FROM price_alerts WHERE productId = :productId LIMIT 1")
    suspend fun getForProduct(productId: Long): PriceAlert?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(alert: PriceAlert): Long

    @Query("UPDATE price_alerts SET lastNotifiedPrice = :price WHERE id = :id")
    suspend fun markNotified(id: Long, price: Double)

    @Query("DELETE FROM price_alerts WHERE productId = :productId")
    suspend fun deleteForProduct(productId: Long)

    @Query("SELECT * FROM price_alerts")
    suspend fun getAllSync(): List<PriceAlert>
}
