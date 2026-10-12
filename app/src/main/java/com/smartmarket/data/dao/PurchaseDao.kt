package com.smartmarket.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.smartmarket.data.entity.Purchase
import com.smartmarket.data.entity.PurchaseItem
import kotlinx.coroutines.flow.Flow

@Dao
interface PurchaseDao {
    @Query("SELECT * FROM purchases WHERE accessKey = :accessKey LIMIT 1")
    suspend fun findByAccessKey(accessKey: String): Purchase?

    @Query("SELECT * FROM purchases WHERE id = :id")
    suspend fun getById(id: Long): Purchase?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPurchase(purchase: Purchase): Long

    @Insert
    suspend fun insertItems(items: List<PurchaseItem>)

    @Delete
    suspend fun deletePurchase(purchase: Purchase)

    @Query("""
        SELECT pu.id, pu.accessKey, pu.supermarketId, s.name AS supermarketName, s.colorHex,
               pu.storeName, pu.issuedAt, pu.totalAmount, pu.discount, pu.paymentMethod, pu.itemCount
        FROM purchases pu
        LEFT JOIN supermarkets s ON s.id = pu.supermarketId
        ORDER BY pu.issuedAt DESC
    """)
    fun getAllPurchases(): Flow<List<PurchaseWithMarket>>

    @Query("""
        SELECT pu.id, pu.accessKey, pu.supermarketId, s.name AS supermarketName, s.colorHex,
               pu.storeName, pu.issuedAt, pu.totalAmount, pu.discount, pu.paymentMethod, pu.itemCount
        FROM purchases pu
        LEFT JOIN supermarkets s ON s.id = pu.supermarketId
        WHERE pu.id = :id
    """)
    fun observePurchase(id: Long): Flow<PurchaseWithMarket?>

    @Query("""
        SELECT pi.id, pi.purchaseId, pi.productId, p.name AS productName, pi.rawDescription, pi.code,
               pi.quantity, pi.unit, pi.unitPrice, pi.totalPrice
        FROM purchase_items pi
        LEFT JOIN products p ON p.id = pi.productId
        WHERE pi.purchaseId = :purchaseId
        ORDER BY pi.id ASC
    """)
    fun getItemsForPurchase(purchaseId: Long): Flow<List<PurchaseItemWithProduct>>

    // ---- Agregações para o dashboard ----

    @Query("""
        SELECT strftime('%Y-%m', issuedAt / 1000, 'unixepoch', 'localtime') AS yearMonth,
               SUM(totalAmount) AS total, COUNT(*) AS purchaseCount
        FROM purchases
        WHERE issuedAt >= :since
        GROUP BY yearMonth
        ORDER BY yearMonth ASC
    """)
    fun getMonthlySpend(since: Long): Flow<List<MonthlySpend>>

    @Query("""
        SELECT COALESCE(p.category, 'Não vinculado') AS label, SUM(pi.totalPrice) AS total, NULL AS colorHex
        FROM purchase_items pi
        INNER JOIN purchases pu ON pu.id = pi.purchaseId
        LEFT JOIN products p ON p.id = pi.productId
        WHERE pu.issuedAt >= :from AND pu.issuedAt < :to
        GROUP BY label
        ORDER BY total DESC
    """)
    fun getSpendByCategory(from: Long, to: Long): Flow<List<LabeledSpend>>

    @Query("""
        SELECT COALESCE(s.name, pu.storeName) AS label, SUM(pu.totalAmount) AS total, s.colorHex AS colorHex
        FROM purchases pu
        LEFT JOIN supermarkets s ON s.id = pu.supermarketId
        WHERE pu.issuedAt >= :from AND pu.issuedAt < :to
        GROUP BY label
        ORDER BY total DESC
    """)
    fun getSpendByMarket(from: Long, to: Long): Flow<List<LabeledSpend>>

    /** Quantidade comprada de um produto desde [since] (para estimar consumo). */
    @Query("""
        SELECT COALESCE(SUM(pi.quantity), 0) FROM purchase_items pi
        INNER JOIN purchases pu ON pu.id = pi.purchaseId
        WHERE pi.productId = :productId AND pu.issuedAt >= :since
    """)
    suspend fun getQuantityBoughtSince(productId: Long, since: Long): Double

    @Query("SELECT * FROM purchases")
    suspend fun getAllPurchasesSync(): List<Purchase>

    @Query("SELECT * FROM purchase_items")
    suspend fun getAllItemsSync(): List<PurchaseItem>

    /** Preços pagos por produto (mais recentes primeiro). */
    @Query("""
        SELECT pi.productId AS productId, pi.unitPrice AS price, pu.issuedAt AS at
        FROM purchase_items pi INNER JOIN purchases pu ON pu.id = pi.purchaseId
        WHERE pi.productId IS NOT NULL AND pi.unitPrice > 0
        ORDER BY pu.issuedAt DESC
    """)
    suspend fun getPaidPrices(): List<PaidPrice>

    /** Itens de nota vinculados a produtos (mais recentes primeiro): descrição e unidade dão o tamanho/forma de venda. */
    @Query("""
        SELECT pi.productId AS productId, pi.rawDescription AS description, pi.unit AS unit, pi.unitPrice AS price
        FROM purchase_items pi INNER JOIN purchases pu ON pu.id = pi.purchaseId
        WHERE pi.productId IS NOT NULL
        ORDER BY pu.issuedAt DESC
    """)
    suspend fun getPaidItems(): List<PaidItem>

    @Query("""
        SELECT DISTINCT pi.productId FROM purchase_items pi INNER JOIN purchases pu ON pu.id = pi.purchaseId
        WHERE pi.productId IS NOT NULL AND pu.issuedAt >= :since
    """)
    suspend fun getProductsBoughtSince(since: Long): List<Long>
}
