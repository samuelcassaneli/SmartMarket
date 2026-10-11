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
    const val ATACADAO_STORE = "ATACADAO_STORE" // página da loja do Atacadão (folhetos em JSON, PDFs públicos)
    const val AUTO = "AUTO"                     // qualquer site: o app procura PDFs/imagens (e pede ajuda à IA)

    /** Tipo pelo endereço: sites conhecidos têm leitura própria; o resto é automático. */
    fun detect(url: String): String {
        val u = url.lowercase()
        return when {
            u.isBlank() -> NONE
            "atacadao.com.br/loja" in u -> ATACADAO_STORE
            "spani" in u -> PDF_LINK
            "domatacadista" in u -> IMAGE_PAGES
            else -> AUTO
        }
    }
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
