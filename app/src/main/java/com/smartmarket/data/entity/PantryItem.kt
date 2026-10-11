package com.smartmarket.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/** Estoque de casa (despensa) de um produto. */
@Entity(
    tableName = "pantry_items",
    foreignKeys = [
        ForeignKey(
            entity = Product::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class PantryItem(
    @PrimaryKey
    val productId: Long,
    val quantity: Double = 0.0,
    val minQuantity: Double = 0.0,
    val expiresAt: Long? = null,
    val updatedAt: Long = System.currentTimeMillis()
)
