package com.smartmarket.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.smartmarket.data.entity.Product
import com.smartmarket.data.entity.ProductAlias
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Query("SELECT * FROM products ORDER BY name COLLATE NOCASE ASC")
    fun getAllProducts(): Flow<List<Product>>

    @Query("SELECT * FROM products ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAllProductsSync(): List<Product>

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun getProductById(id: Long): Product?

    @Query("SELECT * FROM products WHERE id = :id")
    fun observeProduct(id: Long): Flow<Product?>

    @Query("SELECT * FROM products WHERE barcode = :barcode AND barcode != '' LIMIT 1")
    suspend fun getProductByBarcode(barcode: String): Product?

    @Query("SELECT * FROM products WHERE category = :category ORDER BY name ASC")
    fun getProductsByCategory(category: String): Flow<List<Product>>

    @Query("SELECT DISTINCT category FROM products ORDER BY category ASC")
    fun getAllCategories(): Flow<List<String>>

    @Query("SELECT * FROM products WHERE normalizedName LIKE '%' || :query || '%' ORDER BY name ASC")
    suspend fun searchProducts(query: String): List<Product>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: Product): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProducts(products: List<Product>): List<Long>

    @Update
    suspend fun updateProduct(product: Product)

    @Query("UPDATE products SET imageUrl = :url WHERE id = :id")
    suspend fun setImageUrl(id: Long, url: String)

    @Delete
    suspend fun deleteProduct(product: Product)

    // ---- Aliases aprendidos ----

    @Query("SELECT * FROM product_aliases")
    suspend fun getAllAliases(): List<ProductAlias>

    @Query("SELECT * FROM product_aliases WHERE normalizedAlias = :alias LIMIT 1")
    suspend fun findAlias(alias: String): ProductAlias?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAlias(alias: ProductAlias): Long

    @Query("DELETE FROM product_aliases WHERE productId = :productId")
    suspend fun deleteAliasesForProduct(productId: Long)
}
