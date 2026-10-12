package com.smartmarket.domain.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackupSanitizeTest {

    private fun file() = BackupFile(
        schemaVersion = BackupCodec.SCHEMA_VERSION,
        exportedAt = 1,
        supermarkets = listOf(SupermarketDto(1, "Atacadão")),
        products = (1L..41L).map { ProductDto(it, "Produto $it") },
        productPrices = listOf(
            ProductPriceDto(1, 1, 1, 9.9, 10, 2026, updatedAt = 1),
            ProductPriceDto(2, 99, 1, 5.0, 10, 2026),          // produto apagado
            ProductPriceDto(3, 1, 1, 8.9, 10, 2026, updatedAt = 2) // mesma chave única: fica o mais novo
        ),
        shoppingLists = listOf(ShoppingListDto(1, "Mês", 10, 2026)),
        shoppingListItems = listOf(
            ShoppingListItemDto(1, 1, 5),
            ShoppingListItemDto(2, 7, 5),   // lista que não existe
            ShoppingListItemDto(3, 1, 500)  // produto que não existe
        ),
        purchases = listOf(
            PurchaseDto(1, accessKey = "", supermarketId = 3, issuedAt = 1, totalAmount = 10.0),
            PurchaseDto(2, accessKey = "", issuedAt = 2, totalAmount = 20.0)
        ),
        purchaseItems = listOf(PurchaseItemDto(1, 1, productId = 77, rawDescription = "X", quantity = 1.0, unitPrice = 1.0, totalPrice = 1.0))
    )

    @Test
    fun keepsEverythingValidAndDropsOrphans() {
        val s = file().sanitized()
        assertEquals(41, s.products.size)
        assertEquals(listOf(3L), s.productPrices.map { it.id })
        assertEquals(listOf(1L), s.shoppingListItems.map { it.id })
        assertEquals(2, s.purchases.size)
        assertNull(s.purchases[0].accessKey)
        assertNull(s.purchases[0].supermarketId)
        assertNull(s.purchaseItems.single().productId)
    }

    @Test
    fun roundTripKeepsCounts() {
        val f = file()
        val back = BackupCodec.decode(BackupCodec.encode(f))
        assertEquals(f.computeCounts(), back.computeCounts())
    }
}
