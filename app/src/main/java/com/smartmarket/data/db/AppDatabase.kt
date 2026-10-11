package com.smartmarket.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.smartmarket.data.dao.FlyerDao
import com.smartmarket.data.dao.PantryDao
import com.smartmarket.data.dao.PriceAlertDao
import com.smartmarket.data.dao.ProductDao
import com.smartmarket.data.dao.ProductPriceDao
import com.smartmarket.data.dao.PurchaseDao
import com.smartmarket.data.dao.ShoppingListDao
import com.smartmarket.data.dao.SupermarketDao
import com.smartmarket.data.entity.Flyer
import com.smartmarket.data.entity.FlyerOffer
import com.smartmarket.data.entity.PantryItem
import com.smartmarket.data.entity.PriceAlert
import com.smartmarket.data.entity.PriceObservation
import com.smartmarket.data.entity.Product
import com.smartmarket.data.entity.ProductAlias
import com.smartmarket.data.entity.ProductPrice
import com.smartmarket.data.entity.Purchase
import com.smartmarket.data.entity.PurchaseItem
import com.smartmarket.data.entity.ShoppingList
import com.smartmarket.data.entity.ShoppingListItem
import com.smartmarket.data.entity.Supermarket

@Database(
    entities = [
        Product::class,
        Supermarket::class,
        ProductPrice::class,
        ShoppingList::class,
        ShoppingListItem::class,
        PriceObservation::class,
        Purchase::class,
        PurchaseItem::class,
        ProductAlias::class,
        PantryItem::class,
        PriceAlert::class,
        Flyer::class,
        FlyerOffer::class
    ],
    version = 4,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun supermarketDao(): SupermarketDao
    abstract fun productPriceDao(): ProductPriceDao
    abstract fun shoppingListDao(): ShoppingListDao
    abstract fun purchaseDao(): PurchaseDao
    abstract fun pantryDao(): PantryDao
    abstract fun priceAlertDao(): PriceAlertDao
    abstract fun flyerDao(): FlyerDao

    companion object {
        const val NAME = "smartmarket_database"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    NAME
                )
                    .addMigrations(*Migrations.ALL)
                    // v1 só existiu em builds de teste antigos, sem dados reais a preservar
                    .fallbackToDestructiveMigrationFrom(1)
                    .addCallback(SeedCallback)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        /** Instalação nova: cadastra os mercados de Resende e um catálogo inicial de produtos. */
        private object SeedCallback : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                Seed.insertDefaultMarkets(db)
                Seed.insertDefaultProducts(db)
            }
        }
    }
}
