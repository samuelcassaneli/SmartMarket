package com.smartmarket.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Texto (já normalizado) que o usuário confirmou corresponder a um produto,
 * vindo de nota fiscal, encarte ou site. Consultado antes do fuzzy matching.
 */
@Entity(
    tableName = "product_aliases",
    foreignKeys = [
        ForeignKey(
            entity = Product::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["normalizedAlias"], unique = true),
        Index(value = ["productId"])
    ]
)
data class ProductAlias(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val productId: Long,
    val normalizedAlias: String,
    val source: String = PriceSource.MANUAL,
    val createdAt: Long = System.currentTimeMillis()
)
