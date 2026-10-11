package com.smartmarket.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.smartmarket.data.entity.ShoppingList
import com.smartmarket.data.entity.ShoppingListItem
import kotlinx.coroutines.flow.Flow

@Dao
interface ShoppingListDao {
    @Query("SELECT * FROM shopping_lists ORDER BY referenceYear DESC, referenceMonth DESC, createdAt DESC")
    fun getAllLists(): Flow<List<ShoppingList>>

    @Query("""
        SELECT l.id, l.name, l.referenceMonth, l.referenceYear, l.budgetTarget, l.isArchived,
               COUNT(i.id) AS itemCount,
               COALESCE(SUM(CASE WHEN i.isChecked THEN 1 ELSE 0 END), 0) AS checkedCount
        FROM shopping_lists l
        LEFT JOIN shopping_list_items i ON i.shoppingListId = l.id
        GROUP BY l.id
        ORDER BY l.isArchived ASC, l.referenceYear DESC, l.referenceMonth DESC, l.createdAt DESC
    """)
    fun getListSummaries(): Flow<List<ListSummary>>

    @Query("SELECT * FROM shopping_lists WHERE referenceMonth = :month AND referenceYear = :year ORDER BY createdAt DESC")
    fun getListsByMonthYear(month: Int, year: Int): Flow<List<ShoppingList>>

    @Query("SELECT * FROM shopping_lists WHERE id = :id")
    suspend fun getListById(id: Long): ShoppingList?

    @Query("SELECT * FROM shopping_lists WHERE id = :id")
    fun observeList(id: Long): Flow<ShoppingList?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertList(list: ShoppingList): Long

    @Update
    suspend fun updateList(list: ShoppingList)

    @Delete
    suspend fun deleteList(list: ShoppingList)

    @Query("""
        SELECT sli.id as itemId, sli.shoppingListId, sli.productId, p.name as productName, 
               p.category as productCategory, p.unit as productUnit, p.defaultMonthlyQuantity,
               sli.quantity, sli.isChecked
        FROM shopping_list_items sli
        INNER JOIN products p ON p.id = sli.productId
        WHERE sli.shoppingListId = :listId
        ORDER BY sli.isChecked ASC, p.name ASC
    """)
    fun getItemsForList(listId: Long): Flow<List<ShoppingItemWithProduct>>

    @Query("""
        SELECT sli.id as itemId, sli.shoppingListId, sli.productId, p.name as productName, 
               p.category as productCategory, p.unit as productUnit, p.defaultMonthlyQuantity,
               sli.quantity, sli.isChecked
        FROM shopping_list_items sli
        INNER JOIN products p ON p.id = sli.productId
        WHERE sli.shoppingListId = :listId
    """)
    suspend fun getItemsForListSync(listId: Long): List<ShoppingItemWithProduct>

    @Query("SELECT * FROM shopping_list_items WHERE shoppingListId = :listId AND productId = :productId LIMIT 1")
    suspend fun findItem(listId: Long, productId: Long): ShoppingListItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: ShoppingListItem): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<ShoppingListItem>)

    @Update
    suspend fun updateItem(item: ShoppingListItem)

    @Query("UPDATE shopping_list_items SET quantity = :quantity WHERE id = :itemId")
    suspend fun updateItemQuantity(itemId: Long, quantity: Double)

    @Query("UPDATE shopping_list_items SET isChecked = :isChecked WHERE id = :itemId")
    suspend fun toggleItemCheck(itemId: Long, isChecked: Boolean)

    @Query("DELETE FROM shopping_list_items WHERE id = :itemId")
    suspend fun deleteItem(itemId: Long)

    @Query("DELETE FROM shopping_list_items WHERE shoppingListId = :listId")
    suspend fun clearItemsInList(listId: Long)

    @Query("SELECT * FROM shopping_list_items")
    suspend fun getAllItemsSync(): List<ShoppingListItem>

    @Query("SELECT * FROM shopping_lists")
    suspend fun getAllListsSync(): List<ShoppingList>
}
