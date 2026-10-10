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
    version = 2,
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
                .fallbackToDestructiveMigration()
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

                // Mercados iniciais
                val m1Id = supermarketDao.insertSupermarket(
                    Supermarket(name = "Supermercado Extra", address = "Av. Brasil, 1500", colorHex = "#E53935", isFavorite = true)
                )
                val m2Id = supermarketDao.insertSupermarket(
                    Supermarket(name = "Atacadão", address = "Rodovia BR-101, Km 22", colorHex = "#2E7D32", isFavorite = true)
                )
                val m3Id = supermarketDao.insertSupermarket(
                    Supermarket(name = "Carrefour", address = "Shopping Center Norte", colorHex = "#1E88E5", isFavorite = false)
                )
                val m4Id = supermarketDao.insertSupermarket(
                    Supermarket(name = "Mercadinho do Bairro", address = "Rua das Flores, 88", colorHex = "#FB8C00", isFavorite = false)
                )

                // Produtos com quantidade mensal padrão configurada
                val pArroz = productDao.insertProduct(Product(name = "Arroz Tipo 1 5kg", category = "Alimentos", unit = "pct", defaultMonthlyQuantity = 2.0))
                val pFeijao = productDao.insertProduct(Product(name = "Feijão Carioca 1kg", category = "Alimentos", unit = "pct", defaultMonthlyQuantity = 4.0))
                val pLeite = productDao.insertProduct(Product(name = "Leite Integral 1L", category = "Laticínios", unit = "un", defaultMonthlyQuantity = 12.0))
                val pCafe = productDao.insertProduct(Product(name = "Café Torrado e Moído 500g", category = "Matinais", unit = "pct", defaultMonthlyQuantity = 2.0))
                val pOleo = productDao.insertProduct(Product(name = "Óleo de Soja 900ml", category = "Alimentos", unit = "un", defaultMonthlyQuantity = 3.0))
                val pAcucar = productDao.insertProduct(Product(name = "Açúcar Refinado 1kg", category = "Alimentos", unit = "pct", defaultMonthlyQuantity = 3.0))
                val pDetergente = productDao.insertProduct(Product(name = "Detergente Líquido 500ml", category = "Limpeza", unit = "un", defaultMonthlyQuantity = 4.0))
                val pSabao = productDao.insertProduct(Product(name = "Sabão em Pó 1kg", category = "Limpeza", unit = "cx", defaultMonthlyQuantity = 2.0))
                val pAmaciante = productDao.insertProduct(Product(name = "Amaciante Concentrado 1.5L", category = "Limpeza", unit = "un", defaultMonthlyQuantity = 1.0))
                val pPapel = productDao.insertProduct(Product(name = "Papel Higiênico Folha Dupla c/ 12", category = "Higiene", unit = "pct", defaultMonthlyQuantity = 2.0))

                val curMonth = 10
                val prevMonth = 9
                val curYear = 2026

                // Preços do MÊS PASSADO (Setembro/2026) para permitir comparações históricas
                priceDao.insertPrice(ProductPrice(productId = pArroz, supermarketId = m2Id, price = 23.90, referenceMonth = prevMonth, referenceYear = curYear))
                priceDao.insertPrice(ProductPrice(productId = pFeijao, supermarketId = m2Id, price = 6.49, referenceMonth = prevMonth, referenceYear = curYear))
                priceDao.insertPrice(ProductPrice(productId = pLeite, supermarketId = m2Id, price = 4.19, referenceMonth = prevMonth, referenceYear = curYear))
                priceDao.insertPrice(ProductPrice(productId = pCafe, supermarketId = m2Id, price = 15.90, referenceMonth = prevMonth, referenceYear = curYear))

                priceDao.insertPrice(ProductPrice(productId = pArroz, supermarketId = m1Id, price = 25.50, referenceMonth = prevMonth, referenceYear = curYear))
                priceDao.insertPrice(ProductPrice(productId = pFeijao, supermarketId = m1Id, price = 6.99, referenceMonth = prevMonth, referenceYear = curYear))
                priceDao.insertPrice(ProductPrice(productId = pLeite, supermarketId = m1Id, price = 4.39, referenceMonth = prevMonth, referenceYear = curYear))
                priceDao.insertPrice(ProductPrice(productId = pCafe, supermarketId = m1Id, price = 17.50, referenceMonth = prevMonth, referenceYear = curYear))

                // Preços do MÊS ATUAL (Outubro/2026)
                // Atacadão
                priceDao.insertPrice(ProductPrice(productId = pArroz, supermarketId = m2Id, price = 24.90, referenceMonth = curMonth, referenceYear = curYear))
                priceDao.insertPrice(ProductPrice(productId = pFeijao, supermarketId = m2Id, price = 6.80, referenceMonth = curMonth, referenceYear = curYear))
                priceDao.insertPrice(ProductPrice(productId = pLeite, supermarketId = m2Id, price = 4.49, referenceMonth = curMonth, referenceYear = curYear))
                priceDao.insertPrice(ProductPrice(productId = pCafe, supermarketId = m2Id, price = 16.50, referenceMonth = curMonth, referenceYear = curYear))
                priceDao.insertPrice(ProductPrice(productId = pOleo, supermarketId = m2Id, price = 5.99, referenceMonth = curMonth, referenceYear = curYear))
                priceDao.insertPrice(ProductPrice(productId = pAcucar, supermarketId = m2Id, price = 4.29, referenceMonth = curMonth, referenceYear = curYear))
                priceDao.insertPrice(ProductPrice(productId = pDetergente, supermarketId = m2Id, price = 2.49, referenceMonth = curMonth, referenceYear = curYear))
                priceDao.insertPrice(ProductPrice(productId = pSabao, supermarketId = m2Id, price = 12.90, referenceMonth = curMonth, referenceYear = curYear))
                priceDao.insertPrice(ProductPrice(productId = pAmaciante, supermarketId = m2Id, price = 19.90, referenceMonth = curMonth, referenceYear = curYear))
                priceDao.insertPrice(ProductPrice(productId = pPapel, supermarketId = m2Id, price = 18.50, referenceMonth = curMonth, referenceYear = curYear))

                // Carrefour
                priceDao.insertPrice(ProductPrice(productId = pArroz, supermarketId = m3Id, price = 27.50, referenceMonth = curMonth, referenceYear = curYear))
                priceDao.insertPrice(ProductPrice(productId = pFeijao, supermarketId = m3Id, price = 7.20, referenceMonth = curMonth, referenceYear = curYear))
                priceDao.insertPrice(ProductPrice(productId = pLeite, supermarketId = m3Id, price = 4.89, referenceMonth = curMonth, referenceYear = curYear))
                priceDao.insertPrice(ProductPrice(productId = pCafe, supermarketId = m3Id, price = 17.90, referenceMonth = curMonth, referenceYear = curYear))
                priceDao.insertPrice(ProductPrice(productId = pOleo, supermarketId = m3Id, price = 6.49, referenceMonth = curMonth, referenceYear = curYear))
                priceDao.insertPrice(ProductPrice(productId = pAcucar, supermarketId = m3Id, price = 4.59, referenceMonth = curMonth, referenceYear = curYear))
                priceDao.insertPrice(ProductPrice(productId = pDetergente, supermarketId = m3Id, price = 2.89, referenceMonth = curMonth, referenceYear = curYear))
                priceDao.insertPrice(ProductPrice(productId = pSabao, supermarketId = m3Id, price = 14.50, referenceMonth = curMonth, referenceYear = curYear))
                priceDao.insertPrice(ProductPrice(productId = pAmaciante, supermarketId = m3Id, price = 22.90, referenceMonth = curMonth, referenceYear = curYear))
                priceDao.insertPrice(ProductPrice(productId = pPapel, supermarketId = m3Id, price = 20.90, referenceMonth = curMonth, referenceYear = curYear))

                // Extra
                priceDao.insertPrice(ProductPrice(productId = pArroz, supermarketId = m1Id, price = 26.90, referenceMonth = curMonth, referenceYear = curYear))
                priceDao.insertPrice(ProductPrice(productId = pFeijao, supermarketId = m1Id, price = 7.50, referenceMonth = curMonth, referenceYear = curYear))
                priceDao.insertPrice(ProductPrice(productId = pLeite, supermarketId = m1Id, price = 4.29, referenceMonth = curMonth, referenceYear = curYear))
                priceDao.insertPrice(ProductPrice(productId = pCafe, supermarketId = m1Id, price = 18.20, referenceMonth = curMonth, referenceYear = curYear))
                priceDao.insertPrice(ProductPrice(productId = pOleo, supermarketId = m1Id, price = 6.20, referenceMonth = curMonth, referenceYear = curYear))
                priceDao.insertPrice(ProductPrice(productId = pAcucar, supermarketId = m1Id, price = 4.40, referenceMonth = curMonth, referenceYear = curYear))
                priceDao.insertPrice(ProductPrice(productId = pDetergente, supermarketId = m1Id, price = 2.69, referenceMonth = curMonth, referenceYear = curYear))
                priceDao.insertPrice(ProductPrice(productId = pSabao, supermarketId = m1Id, price = 13.90, referenceMonth = curMonth, referenceYear = curYear))
                priceDao.insertPrice(ProductPrice(productId = pAmaciante, supermarketId = m1Id, price = 21.50, referenceMonth = curMonth, referenceYear = curYear))
                priceDao.insertPrice(ProductPrice(productId = pPapel, supermarketId = m1Id, price = 19.90, referenceMonth = curMonth, referenceYear = curYear))

                // Criar Lista do Mês Passado (Setembro/2026)
                val listPrevId = listDao.insertList(
                    ShoppingList(name = "Compras Essenciais de Setembro", referenceMonth = prevMonth, referenceYear = curYear, notes = "Fechamento do mês anterior")
                )
                listDao.insertItem(ShoppingListItem(shoppingListId = listPrevId, productId = pArroz, quantity = 2.0, isChecked = true))
                listDao.insertItem(ShoppingListItem(shoppingListId = listPrevId, productId = pFeijao, quantity = 3.0, isChecked = true))
                listDao.insertItem(ShoppingListItem(shoppingListId = listPrevId, productId = pLeite, quantity = 10.0, isChecked = true))
                listDao.insertItem(ShoppingListItem(shoppingListId = listPrevId, productId = pCafe, quantity = 2.0, isChecked = true))

                // Criar Lista Atual de Outubro/2026
                val listCurId = listDao.insertList(
                    ShoppingList(name = "Compras do Mês - Família", referenceMonth = curMonth, referenceYear = curYear, budgetTarget = 400.0)
                )
                listDao.insertItem(ShoppingListItem(shoppingListId = listCurId, productId = pArroz, quantity = 2.0))
                listDao.insertItem(ShoppingListItem(shoppingListId = listCurId, productId = pFeijao, quantity = 4.0))
                listDao.insertItem(ShoppingListItem(shoppingListId = listCurId, productId = pLeite, quantity = 12.0))
                listDao.insertItem(ShoppingListItem(shoppingListId = listCurId, productId = pCafe, quantity = 2.0))
                listDao.insertItem(ShoppingListItem(shoppingListId = listCurId, productId = pOleo, quantity = 3.0))
                listDao.insertItem(ShoppingListItem(shoppingListId = listCurId, productId = pAcucar, quantity = 3.0))
                listDao.insertItem(ShoppingListItem(shoppingListId = listCurId, productId = pDetergente, quantity = 4.0))
                listDao.insertItem(ShoppingListItem(shoppingListId = listCurId, productId = pSabao, quantity = 2.0))

                // Outra lista para churrasco/fim de semana
                val listChurrasId = listDao.insertList(
                    ShoppingList(name = "Churrasco Fim de Semana", referenceMonth = curMonth, referenceYear = curYear, budgetTarget = 150.0)
                )
                listDao.insertItem(ShoppingListItem(shoppingListId = listChurrasId, productId = pAcucar, quantity = 1.0))
                listDao.insertItem(ShoppingListItem(shoppingListId = listChurrasId, productId = pDetergente, quantity = 2.0))
            }
        }
    }
}
