package com.smartmarket.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Alerta de preço: avisa quando o produto fica abaixo do alvo ou cai de preço. */
@Entity(
    tableName = "price_alerts",
    foreignKeys = [
        ForeignKey(
            entity = Product::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["productId"], unique = true)]
)
data class PriceAlert(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val productId: Long,
    val targetPrice: Double? = null,
    val notifyOnAnyDrop: Boolean = true,
    val active: Boolean = true,
    val lastNotifiedPrice: Double? = null,
    val createdAt: Long = System.currentTimeMillis()
)
