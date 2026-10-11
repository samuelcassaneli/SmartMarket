package com.smartmarket.domain.deals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DealDetectorTest {

    @Test
    fun `amaciante mais barato que a ultima compra vira promocao`() {
        val found = listOf(
            FoundPrice(1, "Amaciante Downy 1,5L", 10, "Spani", 20.99, "Encarte"),
            FoundPrice(1, "Amaciante Downy 1,5L", 11, "Atacadão", 24.90, "Site do mercado")
        )
        val deals = DealDetector.detect(found, mapOf(1L to Baseline(lastPaid = 29.90, lastPaidAt = 1L)))
        assertEquals(1, deals.size)
        val d = deals.single()
        assertEquals(20.99, d.newPrice, 0.001)
        assertEquals("Spani", d.marketName)
        assertEquals("última compra", d.comparedWith)
        assertEquals(8.91, d.savings, 0.001)
    }

    @Test
    fun `sem compra compara com a ultima verificacao`() {
        val deals = DealDetector.detect(
            listOf(FoundPrice(2, "Café 500g", 10, "Dom", 18.90, "Encarte")),
            mapOf(2L to Baseline(lastSeen = 22.90))
        )
        assertEquals("última verificação", deals.single().comparedWith)
    }

    @Test
    fun `queda pequena ou preco igual nao avisa`() {
        val base = mapOf(3L to Baseline(lastPaid = 5.00))
        assertTrue(DealDetector.detect(listOf(FoundPrice(3, "Leite", 1, "X", 4.95, "Encarte")), base).isEmpty())
        assertTrue(DealDetector.detect(listOf(FoundPrice(3, "Leite", 1, "X", 5.00, "Encarte")), base).isEmpty())
        assertTrue(DealDetector.detect(listOf(FoundPrice(3, "Leite", 1, "X", 5.40, "Encarte")), base).isEmpty())
    }

    @Test
    fun `queda absurda e ignorada (provavel produto diferente)`() {
        val deals = DealDetector.detect(listOf(FoundPrice(4, "Arroz 5kg", 1, "X", 4.99, "Encarte")), mapOf(4L to Baseline(lastPaid = 25.90)))
        assertTrue(deals.isEmpty())
    }

    @Test
    fun `texto da notificacao`() {
        val deals = DealDetector.detect(
            listOf(FoundPrice(1, "Amaciante", 1, "Spani", 20.99, "Encarte"), FoundPrice(2, "Café", 1, "Dom", 18.90, "Encarte")),
            mapOf(1L to Baseline(lastPaid = 29.90), 2L to Baseline(lastPaid = 22.90))
        )
        val (title, lines) = DealDetector.notificationLines(deals)
        assertEquals("2 produtos mais baratos que antes", title)
        assertEquals("Amaciante: R$ 29,90 → R$ 20,99 (Spani)", lines.first())
    }
}
