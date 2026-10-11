package com.smartmarket.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

object FlyerStatus {
    const val PROCESSED = "PROCESSED"
    const val FAILED = "FAILED"
}

object OfferStatus {
    const val PENDING = "PENDING"
    const val ACCEPTED = "ACCEPTED"
    const val REJECTED = "REJECTED"
}

/** Encarte baixado automaticamente do site de um mercado. */
@Entity(
    tableName = "flyers",
    foreignKeys = [
        ForeignKey(
            entity = Supermarket::class,
            parentColumns = ["id"],
            childColumns = ["supermarketId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["supermarketId", "contentHash"], unique = true)]
)
data class Flyer(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val supermarketId: Long,
    val sourceUrl: String,
    val contentHash: String,
    val fetchedAt: Long = System.currentTimeMillis(),
    val pageCount: Int = 0,
    val offerCount: Int = 0,
    val status: String = FlyerStatus.PROCESSED,
    val errorMessage: String = ""
)

/** Oferta extraída de um encarte, aguardando revisão do usuário. */
@Entity(
    tableName = "flyer_offers",
    foreignKeys = [
        ForeignKey(
            entity = Flyer::class,
            parentColumns = ["id"],
            childColumns = ["flyerId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Product::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["flyerId"]),
        Index(value = ["productId"])
    ]
)
data class FlyerOffer(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val flyerId: Long,
    val supermarketId: Long,
    val productId: Long? = null,
    val rawText: String,
    val price: Double,
    val confidence: Double = 0.0,
    val status: String = OfferStatus.PENDING
)
