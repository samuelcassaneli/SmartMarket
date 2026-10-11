package com.smartmarket.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.smartmarket.data.entity.Supermarket
import kotlinx.coroutines.flow.Flow

@Dao
interface SupermarketDao {
    @Query("SELECT * FROM supermarkets ORDER BY isFavorite DESC, name ASC")
    fun getAllSupermarkets(): Flow<List<Supermarket>>

    @Query("SELECT * FROM supermarkets ORDER BY isFavorite DESC, name ASC")
    suspend fun getAllSupermarketsSync(): List<Supermarket>

    @Query("SELECT * FROM supermarkets WHERE id = :id")
    suspend fun getSupermarketById(id: Long): Supermarket?

    @Query("SELECT * FROM supermarkets WHERE cnpj = :cnpj AND cnpj != '' LIMIT 1")
    suspend fun getSupermarketByCnpj(cnpj: String): Supermarket?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSupermarket(supermarket: Supermarket): Long

    @Update
    suspend fun updateSupermarket(supermarket: Supermarket)

    @Delete
    suspend fun deleteSupermarket(supermarket: Supermarket)
}
