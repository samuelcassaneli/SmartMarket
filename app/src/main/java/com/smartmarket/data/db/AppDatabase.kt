package com.smartmarket.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.smartmarket.data.dao.ProductDao
import com.smartmarket.data.dao.ProductPriceDao
import com.smartmarket.data.dao.ShoppingListDao
import com.smartmarket.data.dao.SupermarketDao
import com.smartmarket.data.entity.Product
import com.smartmarket.data.entity.ProductPrice
import com.smartmarket.data.entity.ShoppingList
import com.smartmarket.data.entity.ShoppingListItem
import com.smartmarket.data.entity.Supermarket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        Product::class,
        Supermarket::class,
        ProductPrice::class,
        ShoppingList::class,
        ShoppingListItem::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun supermarketDao(): SupermarketDao
    abstract fun productPriceDao(): ProductPriceDao
    abstract fun shoppingListDao(): ShoppingListDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "smartmarket_database"
                )
                .addCallback(DatabaseCallback(scope))
                .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback(
            private val scope: CoroutineScope
        ) : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    scope.launch(Dispatchers.IO) {
                        populateInitialData(database)
                    }
                }
            }

            suspend fun populateInitialData(db: AppDatabase) {
                val productDao = db.productDao()
                val supermarketDao = db.supermarketDao()
                val priceDao = db.productPriceDao()
                val listDao = db.shoppingListDao()

                // Mercados iniciais de exemplo
                val m1Id = supermarketDao.insertSupermarket(Supermarket(name = "Supermercado Extra", colorHex = "#D32F2F"))
                val m2Id = supermarketDao.insertSupermarket(Supermarket(name = "Atacadão", colorHex = "#388E3C"))
                val m3Id = supermarketDao.insertSupermarket(Supermarket(name = "Carrefour", colorHex = "#1976D2"))

                // Produtos comuns
                val pArroz = productDao.insertProduct(Product(name = "Arroz Tipo 1 5kg", category = "Mercearia", unit = "pct"))
                val pFeijao = productDao.insertProduct(Product(name = "Feijão Carioca 1kg", category = "Mercearia", unit = "pct"))
                val pLeite = productDao.insertProduct(Product(name = "Leite Integral 1L", category = "Laticínios", unit = "un"))
                val pCafe = productDao.insertProduct(Product(name = "Café Torrado e Moído 500g", category = "Mercearia", unit = "pct"))
                val pOleo = productDao.insertProduct(Product(name = "Óleo de Soja 900ml", category = "Mercearia", unit = "un"))
                val pAcucar = productDao.insertProduct(Product(name = "Açúcar Refinado 1kg", category = "Mercearia", unit = "pct"))
                val pDetergente = productDao.insertProduct(Product(name = "Detergente Líquido 500ml", category = "Limpeza", unit = "un"))
                val pSabao = productDao.insertProduct(Product(name = "Sabão em Pó 1kg", category = "Limpeza", unit = "cx"))

                val currentMonth = 10
                val currentYear = 2026

                // Preços no Atacadão
                priceDao.insertPrice(ProductPrice(productId = pArroz, supermarketId = m2Id, price = 24.90, referenceMonth = currentMonth, referenceYear = currentYear))
                priceDao.insertPrice(ProductPrice(productId = pFeijao, supermarketId = m2Id, price = 6.80, referenceMonth = currentMonth, referenceYear = currentYear))
                priceDao.insertPrice(ProductPrice(productId = pLeite, supermarketId = m2Id, price = 4.49, referenceMonth = currentMonth, referenceYear = currentYear))
                priceDao.insertPrice(ProductPrice(productId = pCafe, supermarketId = m2Id, price = 16.50, referenceMonth = currentMonth, referenceYear = currentYear))
                priceDao.insertPrice(ProductPrice(productId = pOleo, supermarketId = m2Id, price = 5.99, referenceMonth = currentMonth, referenceYear = currentYear))
                priceDao.insertPrice(ProductPrice(productId = pAcucar, supermarketId = m2Id, price = 4.29, referenceMonth = currentMonth, referenceYear = currentYear))

                // Preços no Carrefour
                priceDao.insertPrice(ProductPrice(productId = pArroz, supermarketId = m3Id, price = 27.50, referenceMonth = currentMonth, referenceYear = currentYear))
                priceDao.insertPrice(ProductPrice(productId = pFeijao, supermarketId = m3Id, price = 7.20, referenceMonth = currentMonth, referenceYear = currentYear))
                priceDao.insertPrice(ProductPrice(productId = pLeite, supermarketId = m3Id, price = 4.89, referenceMonth = currentMonth, referenceYear = currentYear))
                priceDao.insertPrice(ProductPrice(productId = pCafe, supermarketId = m3Id, price = 17.90, referenceMonth = currentMonth, referenceYear = currentYear))
                priceDao.insertPrice(ProductPrice(productId = pOleo, supermarketId = m3Id, price = 6.49, referenceMonth = currentMonth, referenceYear = currentYear))
                priceDao.insertPrice(ProductPrice(productId = pAcucar, supermarketId = m3Id, price = 4.59, referenceMonth = currentMonth, referenceYear = currentYear))

                // Preços no Extra
                priceDao.insertPrice(ProductPrice(productId = pArroz, supermarketId = m1Id, price = 26.90, referenceMonth = currentMonth, referenceYear = currentYear))
                priceDao.insertPrice(ProductPrice(productId = pFeijao, supermarketId = m1Id, price = 7.50, referenceMonth = currentMonth, referenceYear = currentYear))
                priceDao.insertPrice(ProductPrice(productId = pLeite, supermarketId = m1Id, price = 4.29, referenceMonth = currentMonth, referenceYear = currentYear))
                priceDao.insertPrice(ProductPrice(productId = pCafe, supermarketId = m1Id, price = 18.20, referenceMonth = currentMonth, referenceYear = currentYear))
                priceDao.insertPrice(ProductPrice(productId = pOleo, supermarketId = m1Id, price = 6.20, referenceMonth = currentMonth, referenceYear = currentYear))
                priceDao.insertPrice(ProductPrice(productId = pAcucar, supermarketId = m1Id, price = 4.40, referenceMonth = currentMonth, referenceYear = currentYear))

                // Lista de compras de exemplo
                val listId = listDao.insertList(ShoppingList(name = "Compras Básicas do Mês", referenceMonth = currentMonth, referenceYear = currentYear))
                listDao.insertItem(ShoppingListItem(shoppingListId = listId, productId = pArroz, quantity = 2.0))
                listDao.insertItem(ShoppingListItem(shoppingListId = listId, productId = pFeijao, quantity = 3.0))
                listDao.insertItem(ShoppingListItem(shoppingListId = listId, productId = pLeite, quantity = 12.0))
                listDao.insertItem(ShoppingListItem(shoppingListId = listId, productId = pCafe, quantity = 2.0))
                listDao.insertItem(ShoppingListItem(shoppingListId = listId, productId = pOleo, quantity = 4.0))
                listDao.insertItem(ShoppingListItem(shoppingListId = listId, productId = pAcucar, quantity = 3.0))
            }
        }
    }
}
