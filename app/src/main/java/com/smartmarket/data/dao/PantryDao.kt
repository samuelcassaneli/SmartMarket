package com.smartmarket.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.smartmarket.data.entity.PantryItem
import kotlinx.coroutines.flow.Flow

@Dao
interface PantryDao {
    @Query("""
        SELECT pa.productId, p.name AS productName, p.category, p.unit, p.defaultMonthlyQuantity,
               pa.quantity, pa.minQuantity, pa.expiresAt, pa.updatedAt
        FROM pantry_items pa
        INNER JOIN products p ON p.id = pa.productId
        ORDER BY (pa.quantity <= pa.minQuantity) DESC, p.category ASC, p.name ASC
    """)
    fun getPantry(): Flow<List<PantryItemWithProduct>>

    @Query("""
        SELECT pa.productId, p.name AS productName, p.category, p.unit, p.defaultMonthlyQuantity,
               pa.quantity, pa.minQuantity, pa.expiresAt, pa.updatedAt
        FROM pantry_items pa
        INNER JOIN products p ON p.id = pa.productId
        WHERE pa.quantity <= pa.minQuantity
        ORDER BY p.name ASC
    """)
    fun getLowStock(): Flow<List<PantryItemWithProduct>>

    @Query("SELECT * FROM pantry_items WHERE productId = :productId")
    suspend fun get(productId: Long): PantryItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: PantryItem)

    @Query("DELETE FROM pantry_items WHERE productId = :productId")
    suspend fun delete(productId: Long)

    @Query("SELECT * FROM pantry_items")
    suspend fun getAllSync(): List<PantryItem>
}
