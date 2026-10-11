package com.smartmarket.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Origem de um preço registrado. */
object PriceSource {
    const val MANUAL = "MANUAL"
    const val OCR = "OCR"       // encarte lido manualmente (foto/PDF)
    const val FLYER = "FLYER"   // encarte baixado automaticamente
    const val NFCE = "NFCE"     // nota fiscal escaneada
    const val ONLINE = "ONLINE" // site do mercado

    fun label(source: String): String = when (source) {
        OCR -> "Encarte"
        FLYER -> "Encarte automático"
        NFCE -> "Nota fiscal"
        ONLINE -> "Site do mercado"
        else -> "Manual"
    }
}

/**
 * Preço de referência mensal de um produto em um mercado (um por mês).
 * É o que alimenta o comparador de listas. O histórico completo fica em [PriceObservation].
 */
@Entity(
    tableName = "product_prices",
    foreignKeys = [
        ForeignKey(
            entity = Product::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Supermarket::class,
            parentColumns = ["id"],
            childColumns = ["supermarketId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["productId"]),
        Index(value = ["supermarketId"]),
        Index(value = ["productId", "supermarketId", "referenceMonth", "referenceYear"], unique = true)
    ]
)
data class ProductPrice(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val productId: Long,
    val supermarketId: Long,
    val price: Double,
    val referenceMonth: Int, // 1 a 12
    val referenceYear: Int,  // ex: 2026
    val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = PriceSource.MANUAL)
    val source: String = PriceSource.MANUAL
)

/** Cada preço visto/pago, com data. Base para histórico, gráficos e alertas. */
@Entity(
    tableName = "price_observations",
    foreignKeys = [
        ForeignKey(
            entity = Product::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Supermarket::class,
            parentColumns = ["id"],
            childColumns = ["supermarketId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["productId", "observedAt"]),
        Index(value = ["supermarketId"])
    ]
)
data class PriceObservation(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val productId: Long,
    val supermarketId: Long,
    val price: Double,
    val source: String = PriceSource.MANUAL,
    val observedAt: Long = System.currentTimeMillis(),
    val note: String = ""
)
