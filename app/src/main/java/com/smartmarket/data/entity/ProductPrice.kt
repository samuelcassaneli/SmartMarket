package com.smartmarket.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

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
    val updatedAt: Long = System.currentTimeMillis()
)
