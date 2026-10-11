package com.smartmarket.domain.comparator

import com.smartmarket.data.dao.ShoppingItemWithProduct
import com.smartmarket.data.entity.ProductPrice
import com.smartmarket.data.entity.Supermarket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IntelligentPriceComparatorTest {

    private val a = Supermarket(id = 1, name = "Atacadão")
    private val s = Supermarket(id = 2, name = "Spani")
    private val d = Supermarket(id = 3, name = "Dom")

    private fun item(id: Long, name: String, qty: Double) =
        ShoppingItemWithProduct(id, 99, id, name, "Mercearia", "un", 1.0, qty, false)

    private fun price(product: Long, market: Long, value: Double, month: Int = 10, year: Int = 2026) =
        ProductPrice(productId = product, supermarketId = market, price = value, referenceMonth = month, referenceYear = year)

    private val items = listOf(item(1, "Arroz", 1.0), item(2, "Feijão", 2.0), item(3, "Café", 1.0), item(4, "Sal", 1.0))

    private val current = listOf(
        price(1, 1, 25.0), price(2, 1, 8.0), price(3, 1, 18.0),
        price(1, 2, 28.0), price(2, 2, 9.0), price(3, 2, 15.0),
        price(1, 3, 22.0)
    )
    private val previous = listOf(
        price(1, 1, 20.0, 9), price(2, 1, 8.0, 9),
        price(1, 2, 28.0, 9)
    )

    private fun run() = IntelligentPriceComparator.compareWithPreviousMonth(
        items, listOf(a, s, d), current, previous, 10, 2026, 9, 2026, "Outubro"
    )

    @Test
    fun `mercado mais barato entre os de maior cobertura`() {
        val r = run()
        // Atacadão = 25 + 16 + 18 = 59 ; Spani = 28 + 18 + 15 = 61 ; Dom só 1 item
        assertEquals("Atacadão", r.bestSupermarketCurrent?.name)
        assertEquals(59.0, r.bestTotal, 0.001)
        assertTrue(r.marketResults.first().isBestPrice)
        assertEquals(listOf(1L, 2L, 3L), r.marketResults.map { it.supermarket.id })
        val spani = r.marketResults[1]
        assertEquals(2.0, spani.differenceFromBest, 0.001)
        assertEquals(2.0 / 59.0 * 100.0, spani.percentageMoreExpensive, 0.001)
        // Economia comparando só mercados com a mesma cobertura (Dom fica de fora)
        assertEquals(2.0, r.currentPotentialSavings, 0.001)
        assertEquals("Spani", r.mostExpensiveComparable?.name)
    }

    @Test
    fun `itens faltantes por mercado e sem preco em nenhum`() {
        val r = run()
        val dom = r.marketResults.first { it.supermarket.id == 3L }
        assertEquals(1, dom.itemsAvailableCount)
        assertEquals(listOf("Feijão", "Café", "Sal"), dom.missingItems.map { it.productName })
        assertFalse(dom.isComplete)
        assertEquals(listOf("Sal"), r.itemsWithoutAnyPrice.map { it.productName })
        // Menor preço do arroz é no Dom
        assertEquals(3L, r.cheapestByProduct[1L]?.supermarketId)
        // Ideal: 22 + 16 + 15 = 53
        assertEquals(53.0, r.idealTotal, 0.001)
    }

    @Test
    fun `variacao contra o mes anterior usa so itens comparaveis`() {
        val r = run()
        val atac = r.marketResults.first { it.supermarket.id == 1L }
        // Comparáveis: arroz (20 -> 25) e feijão (16 -> 16): 36 -> 41 = +13,89%
        assertNotNull(atac.inflationPercent)
        assertEquals((41.0 - 36.0) / 36.0 * 100.0, atac.inflationPercent!!, 0.001)
        val spani = r.marketResults.first { it.supermarket.id == 2L }
        assertEquals(0.0, spani.inflationPercent!!, 0.001)
        val dom = r.marketResults.first { it.supermarket.id == 3L }
        assertNull(dom.inflationPercent)
        val arroz = atac.itemDetails.first { it.productId == 1L }
        assertEquals(25.0, arroz.priceChangePercent!!, 0.001)
    }

    @Test
    fun `sem mercados retorna resumo vazio com itens sem preco`() {
        val r = IntelligentPriceComparator.compareWithPreviousMonth(items, emptyList(), current, previous, 10, 2026, 9, 2026, "x")
        assertNull(r.bestSupermarketCurrent)
        assertTrue(r.marketResults.isEmpty())
        assertEquals(4, r.itemsWithoutAnyPrice.size)
    }

    @Test
    fun `total do mercado mais barato para cartoes`() {
        val best = IntelligentPriceComparator.bestMarketTotal(mapOf(1L to 1.0, 2L to 2.0, 3L to 1.0), current)
        assertNotNull(best)
        assertEquals(1L, best!!.supermarketId)
        assertEquals(59.0, best.total, 0.001)
        assertEquals(3, best.covered)
    }
}
