package com.smartmarket.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Fonte online de busca de produtos de um mercado. */
object OnlineSource {
    const val NONE = "NONE"
    const val VTEX_ATACADAO = "VTEX_ATACADAO"
    const val SPANI_ONLINE = "SPANI_ONLINE"
}

/** Como o encarte do mercado é publicado no site. */
object FlyerKind {
    const val NONE = "NONE"
    const val PDF_LINK = "PDF_LINK"       // página contém link para um PDF
    const val IMAGE_PAGES = "IMAGE_PAGES" // página contém as páginas do encarte como imagens
}

@Entity(tableName = "supermarkets")
data class Supermarket(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val address: String = "",
    val phone: String = "",
    val notes: String = "",
    val colorHex: String = "#1E88E5", // Cor personalizada para identificação nos gráficos e listas
    val isFavorite: Boolean = false,
    @ColumnInfo(defaultValue = "")
    val cnpj: String = "", // somente dígitos; usado para casar notas fiscais
    @ColumnInfo(defaultValue = "")
    val city: String = "",
    @ColumnInfo(defaultValue = OnlineSource.NONE)
    val onlineSource: String = OnlineSource.NONE,
    @ColumnInfo(defaultValue = "")
    val flyerUrl: String = "",
    @ColumnInfo(defaultValue = FlyerKind.NONE)
    val flyerKind: String = FlyerKind.NONE
)
