package com.smartmarket.domain.flyers

import com.smartmarket.domain.ocr.ParsedOffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiPriceCheckTest {
    private val local = listOf(
        ParsedOffer("LEITE INTEGRAL GLÓRIA 1L", 4.99),
        ParsedOffer("MACARRÃO ESPAGUETE RENATA 500G", 2.59),
        ParsedOffer("ÓLEO DE SOJA SOYA 900ML", 6.79)
    )

    @Test
    fun neighbourPriceIsReplacedByLocalPrice() {
        val ai = listOf(ParsedOffer("Leite Integral Glória 1L", 2.59), ParsedOffer("Macarrão Espaguete Renata 500g", 2.59))
        val out = AiPriceCheck.reconcile(ai, local)
        assertEquals(4.99, out[0].price, 0.001)
        assertEquals(2.59, out[1].price, 0.001)
    }

    @Test
    fun agreeingOrUnknownOffersStay() {
        val ai = listOf(ParsedOffer("Óleo de Soja Soya 900ml", 6.79), ParsedOffer("Café Pilão 500g", 15.9))
        val out = AiPriceCheck.reconcile(ai, local)
        assertEquals(ai, out)
    }

    @Test
    fun disagreementWithoutEvidenceIsFlagged() {
        val out = AiPriceCheck.reconcile(listOf(ParsedOffer("Leite Integral Glória 1L", 5.49)), local)
        assertEquals(5.49, out[0].price, 0.001)
        assertTrue(out[0].note.contains("conferir preço"))
    }
}
