package com.smartmarket.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Uma compra realizada, normalmente importada de uma NFC-e. */
@Entity(
    tableName = "purchases",
    foreignKeys = [
        ForeignKey(
            entity = Supermarket::class,
            parentColumns = ["id"],
            childColumns = ["supermarketId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["accessKey"], unique = true),
        Index(value = ["supermarketId"]),
        Index(value = ["issuedAt"])
    ]
)
data class Purchase(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val accessKey: String? = null, // chave de acesso de 44 dígitos (null para compra manual)
    val supermarketId: Long? = null,
    val cnpj: String = "",
    val storeName: String = "",
    val storeAddress: String = "",
    val issuedAt: Long,
    val totalAmount: Double,
    val discount: Double = 0.0,
    val paymentMethod: String = "",
    val itemCount: Int = 0,
    val sourceUrl: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "purchase_items",
    foreignKeys = [
        ForeignKey(
            entity = Purchase::class,
            parentColumns = ["id"],
            childColumns = ["purchaseId"],
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
        Index(value = ["purchaseId"]),
        Index(value = ["productId"])
    ]
)
data class PurchaseItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val purchaseId: Long,
    val productId: Long? = null,
    val rawDescription: String,
    val code: String = "",
    val quantity: Double,
    val unit: String = "UN",
    val unitPrice: Double,
    val totalPrice: Double
)
