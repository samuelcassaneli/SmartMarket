package com.smartmarket.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.smartmarket.data.entity.Product
import com.smartmarket.data.entity.ProductPrice
import com.smartmarket.data.entity.ShoppingList
import com.smartmarket.data.entity.ShoppingListItem
import com.smartmarket.data.entity.Supermarket
import kotlinx.coroutines.flow.Flow

data class ShoppingItemWithProduct(
    val itemId: Long,
    val shoppingListId: Long,
    val productId: Long,
    val productName: String,
    val productCategory: String,
    val productUnit: String,
    val quantity: Double,
    val isChecked: Boolean
)

data class ProductWithPrices(
    val productId: Long,
    val productName: String,
    val productCategory: String,
    val productUnit: String,
    val prices: List<ProductPrice>
)

@Dao
interface ProductDao {
    @Query("SELECT * FROM products ORDER BY name ASC")
    fun getAllProducts(): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun getProductById(id: Long): Product?

    @Query("SELECT * FROM products WHERE normalizedName LIKE '%' || :query || '%'")
    suspend fun searchProducts(query: String): List<Product>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: Product): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProducts(products: List<Product>): List<Long>

    @Update
    suspend fun updateProduct(product: Product)

    @Delete
    suspend fun deleteProduct(product: Product)
}

@Dao
interface SupermarketDao {
    @Query("SELECT * FROM supermarkets ORDER BY name ASC")
    fun getAllSupermarkets(): Flow<List<Supermarket>>

    @Query("SELECT * FROM supermarkets WHERE id = :id")
    suspend fun getSupermarketById(id: Long): Supermarket?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSupermarket(supermarket: Supermarket): Long

    @Update
    suspend fun updateSupermarket(supermarket: Supermarket)

    @Delete
    suspend fun deleteSupermarket(supermarket: Supermarket)
}

@Dao
interface ProductPriceDao {
    @Query("SELECT * FROM product_prices WHERE referenceMonth = :month AND referenceYear = :year")
    fun getPricesForMonth(month: Int, year: Int): Flow<List<ProductPrice>>

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
}

@Dao
interface ShoppingListDao {
    @Query("SELECT * FROM shopping_lists ORDER BY referenceYear DESC, referenceMonth DESC, createdAt DESC")
    fun getAllLists(): Flow<List<ShoppingList>>

    @Query("SELECT * FROM shopping_lists WHERE id = :id")
    suspend fun getListById(id: Long): ShoppingList?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertList(list: ShoppingList): Long

    @Delete
    suspend fun deleteList(list: ShoppingList)

    @Query("""
        SELECT sli.id as itemId, sli.shoppingListId, sli.productId, p.name as productName, 
               p.category as productCategory, p.unit as productUnit, sli.quantity, sli.isChecked
        FROM shopping_list_items sli
        INNER JOIN products p ON p.id = sli.productId
        WHERE sli.shoppingListId = :listId
        ORDER BY sli.isChecked ASC, p.name ASC
    """)
    fun getItemsForList(listId: Long): Flow<List<ShoppingItemWithProduct>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: ShoppingListItem): Long

    @Update
    suspend fun updateItem(item: ShoppingListItem)

    @Query("UPDATE shopping_list_items SET isChecked = :isChecked WHERE id = :itemId")
    suspend fun toggleItemCheck(itemId: Long, isChecked: Boolean)

    @Query("DELETE FROM shopping_list_items WHERE id = :itemId")
    suspend fun deleteItem(itemId: Long)
}
