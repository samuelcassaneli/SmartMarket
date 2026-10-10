package com.smartmarket.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "products")
data class Product(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val normalizedName: String = name.lowercase().trim(),
    val category: String = "Geral",
    val unit: String = "un" // ex: un, kg, g, L, ml
)
