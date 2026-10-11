package com.smartmarket.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "products")
data class Product(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val normalizedName: String = name.lowercase().trim(),
    val category: String = "Geral",
    val unit: String = "un", // ex: un, kg, g, L, ml, pct, cx
    val defaultMonthlyQuantity: Double = 1.0, // Quantidade normal mensal de consumo
    val barcode: String = "",
    val notes: String = "",
    @ColumnInfo(defaultValue = "")
    val imageUrl: String = "",
    @ColumnInfo(defaultValue = "0")
    val isFavorite: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    val createdAt: Long = System.currentTimeMillis()
)
