package com.smartmarket.domain.backup

import com.smartmarket.data.entity.Flyer
import com.smartmarket.data.entity.FlyerOffer
import com.smartmarket.data.entity.PantryItem
import com.smartmarket.data.entity.PriceAlert
import com.smartmarket.data.entity.PriceObservation
import com.smartmarket.data.entity.Product
import com.smartmarket.data.entity.ProductAlias
import com.smartmarket.data.entity.ProductPrice
import com.smartmarket.data.entity.Purchase
import com.smartmarket.data.entity.PurchaseItem
import com.smartmarket.data.entity.ShoppingList
import com.smartmarket.data.entity.ShoppingListItem
import com.smartmarket.data.entity.Supermarket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BackupRoundTripTest {

    private val market = Supermarket(id = 3, name = "Spani", colorHex = "#E53935", cnpj = "123", city = "Resende", onlineSource = "SPANI_ONLINE", flyerUrl = "https://x", flyerKind = "PDF_LINK")
    private val product = Product(id = 7, name = "Leite Condensado 395g", category = "Mercearia", unit = "un", defaultMonthlyQuantity = 2.0, barcode = "789", isFavorite = true, createdAt = 42)
    private val alias = ProductAlias(id = 1, productId = 7, normalizedAlias = "leite cond moca", source = "NFCE", createdAt = 5)
    private val price = ProductPrice(id = 9, productId = 7, supermarketId = 3, price = 4.99, referenceMonth = 10, referenceYear = 2026, updatedAt = 77, source = "FLYER")
    private val obs = PriceObservation(id = 11, productId = 7, supermarketId = 3, price = 4.99, source = "NFCE", observedAt = 88, note = "nota")
    private val list = ShoppingList(id = 4, name = "Outubro", referenceMonth = 10, referenceYear = 2026, budgetTarget = 500.0, createdAt = 1, isArchived = true)
    private val listItem = ShoppingListItem(id = 6, shoppingListId = 4, productId = 7, quantity = 2.5, isChecked = true)
    private val purchase = Purchase(id = 2, accessKey = "3".repeat(44), supermarketId = 3, issuedAt = 99, totalAmount = 123.45, discount = 1.5, itemCount = 1, createdAt = 100)
    private val manualPurchase = Purchase(id = 5, accessKey = null, supermarketId = null, issuedAt = 10, totalAmount = 10.0, createdAt = 11)
    private val purchaseItem = PurchaseItem(id = 8, purchaseId = 2, productId = null, rawDescription = "LEITE COND", quantity = 1.0, unitPrice = 4.99, totalPrice = 4.99)
    private val pantry = PantryItem(productId = 7, quantity = 1.0, minQuantity = 2.0, expiresAt = null, updatedAt = 3)
    private val pantryExp = PantryItem(productId = 8, quantity = 0.0, minQuantity = 1.0, expiresAt = 12345, updatedAt = 4)
    private val alert = PriceAlert(id = 1, productId = 7, targetPrice = null, notifyOnAnyDrop = true, active = true, lastNotifiedPrice = 4.99, createdAt = 6)
    private val flyer = Flyer(id = 12, supermarketId = 3, sourceUrl = "https://pdf", contentHash = "abc", fetchedAt = 5, pageCount = 4, offerCount = 37, status = "PROCESSED")
    private val offer = FlyerOffer(id = 13, flyerId = 12, supermarketId = 3, productId = null, rawText = "LEITE 4,99", price = 4.99, confidence = 0.8, status = "PENDING")

    private fun sample(): BackupFile {
        val f = BackupFile(
            schemaVersion = BackupCodec.SCHEMA_VERSION,
            exportedAt = 1_700_000_000_000,
            supermarkets = listOf(market.toDto()),
            products = listOf(product.toDto()),
            productAliases = listOf(alias.toDto()),
            productPrices = listOf(price.toDto()),
            priceObservations = listOf(obs.toDto()),
            shoppingLists = listOf(list.toDto()),
            shoppingListItems = listOf(listItem.toDto()),
            purchases = listOf(purchase.toDto(), manualPurchase.toDto()),
            purchaseItems = listOf(purchaseItem.toDto()),
            pantryItems = listOf(pantry.toDto(), pantryExp.toDto()),
            priceAlerts = listOf(alert.toDto()),
            flyers = listOf(flyer.toDto()),
            flyerOffers = listOf(offer.toDto())
        )
        return f.copy(counts = f.computeCounts())
    }

    @Test
    fun `ida e volta preserva todas as entidades`() {
        val decoded = BackupCodec.decode(BackupCodec.encode(sample()))
        assertEquals(sample(), decoded)

        assertEquals(market, decoded.supermarkets.single().toEntity())
        assertEquals(product, decoded.products.single().toEntity())
        assertEquals(alias, decoded.productAliases.single().toEntity())
        assertEquals(price, decoded.productPrices.single().toEntity())
        assertEquals(obs, decoded.priceObservations.single().toEntity())
        assertEquals(list, decoded.shoppingLists.single().toEntity())
        assertEquals(listItem, decoded.shoppingListItems.single().toEntity())
        assertEquals(listOf(purchase, manualPurchase), decoded.purchases.map { it.toEntity() })
        assertEquals(purchaseItem, decoded.purchaseItems.single().toEntity())
        assertEquals(listOf(pantry, pantryExp), decoded.pantryItems.map { it.toEntity() })
        assertEquals(alert, decoded.priceAlerts.single().toEntity())
        assertEquals(flyer, decoded.flyers.single().toEntity())
        assertEquals(offer, decoded.flyerOffers.single().toEntity())
    }

    @Test
    fun `contagens e versao sao gravadas`() {
        val json = BackupCodec.encode(sample())
        assertTrue(json.contains("\"schemaVersion\":3"))
        assertTrue(json.contains("\"exportedAt\":1700000000000"))
        val counts = BackupCodec.decode(json).counts
        assertEquals(2, counts["purchases"])
        assertEquals(1, counts["products"])
        assertEquals(13, counts.size)
    }

    @Test
    fun `rejeita versao diferente`() {
        val newer = BackupCodec.encode(sample().copy(schemaVersion = 99))
        try {
            BackupCodec.decode(newer); fail("deveria rejeitar")
        } catch (e: BackupFormatException) {
            assertTrue(e.message!!.contains("mais nova"))
        }
        try {
            BackupCodec.decode(BackupCodec.encode(sample().copy(schemaVersion = 2))); fail("deveria rejeitar")
        } catch (_: BackupFormatException) {
        }
    }

    @Test
    fun `rejeita arquivo que nao e backup`() {
        try {
            BackupCodec.decode("{\"foo\": 1}"); fail("deveria rejeitar")
        } catch (_: BackupFormatException) {
        }
        try {
            BackupCodec.decode("não é json"); fail("deveria rejeitar")
        } catch (_: BackupFormatException) {
        }
    }

    @Test
    fun `ignora campos desconhecidos de versoes futuras compativeis`() {
        val json = BackupCodec.encode(sample()).replaceFirst("{", "{\"campoNovo\":true,")
        assertEquals(sample(), BackupCodec.decode(json))
    }

    @Test
    fun `retencao mantem os 4 mais recentes`() {
        val names = listOf(
            "smartmarket-backup-2026-09-01.json", "smartmarket-backup-2026-09-08.json",
            "smartmarket-backup-2026-09-15.json", "smartmarket-backup-2026-09-22.json",
            "smartmarket-backup-2026-09-29.json", "smartmarket-backup-2026-10-06.json",
            "outro-arquivo.json"
        )
        assertEquals(
            listOf("smartmarket-backup-2026-09-08.json", "smartmarket-backup-2026-09-01.json"),
            BackupRetention.toDelete(names, keep = 4)
        )
        assertEquals("smartmarket-backup-2026-01-05.json", BackupRetention.fileName(2026, 1, 5))
    }
}
