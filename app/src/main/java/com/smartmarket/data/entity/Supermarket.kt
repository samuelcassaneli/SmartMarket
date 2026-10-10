package com.smartmarket.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "supermarkets")
data class Supermarket(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val address: String = "",
    val phone: String = "",
    val notes: String = "",
    val colorHex: String = "#1E88E5", // Cor personalizada para identificação nos gráficos e listas
    val isFavorite: Boolean = false
)
