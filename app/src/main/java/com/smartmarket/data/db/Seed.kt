package com.smartmarket.data.db

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.smartmarket.data.entity.FlyerKind
import com.smartmarket.data.entity.OnlineSource

/** Dados iniciais, gravados direto em SQL para poder rodar em onCreate e nas migrações. */
object Seed {

    const val ATACADAO_RESENDE_URL = "https://www.atacadao.com.br/loja/resende"

    data class MarketSeed(
        val name: String,
        val matchPattern: String, // LIKE usado para achar o mercado já cadastrado pelo usuário
        val address: String,
        val colorHex: String,
        val onlineSource: String,
        val flyerUrl: String,
        val flyerKind: String
    )

    val RESENDE_MARKETS = listOf(
        MarketSeed(
            name = "Atacadão Resende",
            matchPattern = "%atacad_o%",
            address = "Resende - RJ",
            colorHex = "#E30613",
            onlineSource = OnlineSource.VTEX_ATACADAO,
            flyerUrl = ATACADAO_RESENDE_URL,
            flyerKind = FlyerKind.ATACADAO_STORE
        ),
        MarketSeed(
            name = "Spani Atacadista Resende",
            matchPattern = "%spani%",
            address = "Resende - RJ",
            colorHex = "#F28C00",
            onlineSource = OnlineSource.SPANI_ONLINE,
            flyerUrl = "https://lojas.spani.com.br/lojas/resende/",
            flyerKind = FlyerKind.PDF_LINK
        ),
        MarketSeed(
            name = "Dom Atacadista Resende",
            matchPattern = "%dom atacad%",
            address = "Resende - RJ",
            colorHex = "#0057A8",
            onlineSource = OnlineSource.NONE,
            flyerUrl = "https://domatacadista.net/encartes/lojas/resende",
            flyerKind = FlyerKind.IMAGE_PAGES
        )
    )

    /**
     * Garante que os mercados de Resende existam com as fontes online configuradas.
     * Se o usuário já cadastrou o mercado (ex.: "Spani"), só completa as colunas novas, sem renomear.
     */
    fun insertDefaultMarkets(db: SupportSQLiteDatabase) {
        for (m in RESENDE_MARKETS) {
            val cursor = db.query(
                "SELECT id FROM supermarkets WHERE name LIKE ? ORDER BY id LIMIT 1",
                arrayOf(m.matchPattern)
            )
            val existingId = cursor.use { if (it.moveToFirst()) it.getLong(0) else null }

            if (existingId != null) {
                val values = ContentValues().apply {
                    put("city", "Resende")
                    put("onlineSource", m.onlineSource)
                    put("flyerUrl", m.flyerUrl)
                    put("flyerKind", m.flyerKind)
                }
                db.update("supermarkets", SQLiteDatabase.CONFLICT_IGNORE, values, "id = ?", arrayOf(existingId))
            } else {
                val values = ContentValues().apply {
                    put("name", m.name)
                    put("address", m.address)
                    put("phone", "")
                    put("notes", "")
                    put("colorHex", m.colorHex)
                    put("isFavorite", 1)
                    put("cnpj", "")
                    put("city", "Resende")
                    put("onlineSource", m.onlineSource)
                    put("flyerUrl", m.flyerUrl)
                    put("flyerKind", m.flyerKind)
                }
                db.insert("supermarkets", SQLiteDatabase.CONFLICT_IGNORE, values)
            }
        }
    }

    private data class ProductSeed(val name: String, val category: String, val unit: String, val monthly: Double)

    private val DEFAULT_PRODUCTS = listOf(
        ProductSeed("Arroz Tipo 1 5kg", "Alimentos", "pct", 2.0),
        ProductSeed("Feijão Carioca 1kg", "Alimentos", "pct", 4.0),
        ProductSeed("Óleo de Soja 900ml", "Alimentos", "un", 3.0),
        ProductSeed("Açúcar Refinado 1kg", "Alimentos", "pct", 3.0),
        ProductSeed("Macarrão Espaguete 500g", "Alimentos", "pct", 4.0),
        ProductSeed("Café Torrado e Moído 500g", "Matinais", "pct", 2.0),
        ProductSeed("Leite Integral 1L", "Laticínios", "un", 12.0),
        ProductSeed("Leite Condensado 395g", "Laticínios", "un", 1.0),
        ProductSeed("Detergente Líquido 500ml", "Limpeza", "un", 4.0),
        ProductSeed("Sabão em Pó 1kg", "Limpeza", "cx", 2.0),
        ProductSeed("Amaciante Concentrado 1,5L", "Limpeza", "un", 1.0),
        ProductSeed("Papel Higiênico Folha Dupla c/ 12", "Higiene", "pct", 2.0)
    )

    fun insertDefaultProducts(db: SupportSQLiteDatabase) {
        val now = System.currentTimeMillis()
        for (p in DEFAULT_PRODUCTS) {
            val values = ContentValues().apply {
                put("name", p.name)
                put("normalizedName", p.name.lowercase().trim())
                put("category", p.category)
                put("unit", p.unit)
                put("defaultMonthlyQuantity", p.monthly)
                put("barcode", "")
                put("notes", "")
                put("imageUrl", "")
                put("isFavorite", 0)
                put("createdAt", now)
            }
            db.insert("products", SQLiteDatabase.CONFLICT_IGNORE, values)
        }
    }
}
