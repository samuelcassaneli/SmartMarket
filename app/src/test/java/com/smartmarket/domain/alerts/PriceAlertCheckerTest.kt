package com.smartmarket.domain.alerts

import com.smartmarket.domain.alerts.PriceAlertChecker.AlertRule
import com.smartmarket.domain.alerts.PriceAlertChecker.Observation
import com.smartmarket.domain.alerts.PriceAlertChecker.Reason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PriceAlertCheckerTest {

    private fun rule(target: Double? = null, anyDrop: Boolean = true, last: Double? = null) =
        AlertRule(alertId = 1, productId = 10, targetPrice = target, notifyOnAnyDrop = anyDrop, lastNotifiedPrice = last)

    private fun obs(price: Double, at: Long = 1_000, market: Long = 2, product: Long = 10) =
        Observation(productId = product, supermarketId = market, price = price, observedAt = at)

    @Test
    fun `dispara quando o preco fica igual ou abaixo do alvo`() {
        val t = PriceAlertChecker.evaluate(rule(target = 5.00, anyDrop = false), obs(4.99), previousMin = 4.50)
        assertEquals(Reason.TARGET, t?.reason)
        assertEquals(4.99, t!!.price, 0.0001)

        val exact = PriceAlertChecker.evaluate(rule(target = 4.99, anyDrop = false), obs(4.99), previousMin = null)
        assertEquals(Reason.TARGET, exact?.reason)
    }

    @Test
    fun `nao dispara acima do alvo sem queda`() {
        assertNull(PriceAlertChecker.evaluate(rule(target = 4.50, anyDrop = false), obs(4.99), previousMin = 4.00))
    }

    @Test
    fun `queda abaixo do menor anterior dispara com qualquer queda`() {
        val t = PriceAlertChecker.evaluate(rule(), obs(4.99), previousMin = 5.99)
        assertEquals(Reason.DROP, t?.reason)
        assertEquals(5.99, t!!.previousPrice!!, 0.0001)
    }

    @Test
    fun `sem historico anterior nao ha queda`() {
        assertNull(PriceAlertChecker.evaluate(rule(), obs(4.99), previousMin = null))
    }

    @Test
    fun `preco igual ao menor anterior nao e queda`() {
        assertNull(PriceAlertChecker.evaluate(rule(), obs(5.99), previousMin = 5.99))
    }

    @Test
    fun `queda desligada nao dispara`() {
        assertNull(PriceAlertChecker.evaluate(rule(anyDrop = false), obs(3.00), previousMin = 5.99))
    }

    @Test
    fun `nao repete o mesmo preco ja notificado`() {
        assertNull(PriceAlertChecker.evaluate(rule(target = 5.0, last = 4.99), obs(4.99), previousMin = 5.99))
        // Novo preço, ainda menor, avisa de novo
        val t = PriceAlertChecker.evaluate(rule(target = 5.0, last = 4.99), obs(4.79), previousMin = 4.99)
        assertEquals(4.79, t!!.price, 0.0001)
    }

    @Test
    fun `escolhe a observacao mais barata do produto`() {
        val recent = listOf(obs(6.50, at = 3, market = 1), obs(4.99, at = 2, market = 2), obs(5.20, at = 1, market = 3), obs(1.0, product = 99))
        val c = PriceAlertChecker.pickCandidate(10, recent)!!
        assertEquals(2L, c.supermarketId)
    }

    @Test
    fun `check avalia so regras com observacoes recentes`() {
        val rules = listOf(rule(), rule().copy(alertId = 2, productId = 20))
        val recent = listOf(obs(4.99, at = 500))
        var askedBefore = -1L
        val triggers = PriceAlertChecker.check(rules, recent) { _, before -> askedBefore = before; 5.99 }
        assertEquals(1, triggers.size)
        assertEquals(1L, triggers[0].alertId)
        assertEquals(500L, askedBefore)
    }

    @Test
    fun `mensagem traz produto, mercado e preco anterior`() {
        val t = PriceAlertChecker.evaluate(rule(), obs(4.99), previousMin = 5.99)!!
        val msg = PriceAlertChecker.message("Leite Condensado", "Spani", t)
        assertTrue(msg, msg.startsWith("Leite Condensado está R$"))
        assertTrue(msg, msg.contains("4,99"))
        assertTrue(msg, msg.contains("no Spani (antes R$"))
        assertTrue(msg, msg.contains("5,99"))
    }
}
