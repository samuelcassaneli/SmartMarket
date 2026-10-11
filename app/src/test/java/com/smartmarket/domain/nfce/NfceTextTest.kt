package com.smartmarket.domain.nfce

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NfceTextTest {

    @Test
    fun `numeros em pt-BR`() {
        assertEquals(1234.56, NfceText.parseNumber("1.234,56")!!, 1e-9)
        assertEquals(0.535, NfceText.parseNumber("Qtde.:0,535")!!, 1e-9)
        assertEquals(5.99, NfceText.parseNumber("Vl. Unit.:   5,99")!!, 1e-9)
        assertEquals(108.95, NfceText.parseNumber("108.95")!!, 1e-9)
        assertEquals(null, NfceText.parseNumber("abc"))
    }

    @Test
    fun `nome limpo do produto`() {
        assertEquals("Banana Prata Org", NfceText.cleanProductName("BANANA PRATA ORG KG"))
        assertEquals("Leite Cond Italac 395g", NfceText.cleanProductName("LEITE COND ITALAC 395G 7898080640017"))
        assertEquals("Arroz T1 5kg", NfceText.cleanProductName("ARROZ T1 5KG"))
    }

    @Test
    fun `nome do mercado e semelhanca`() {
        assertEquals("Spani Atacadista", NfceText.cleanStoreName("SPANI ATACADISTA LTDA"))
        assertTrue(NfceText.storeSimilarity("SPANI ATACADISTA LTDA", "Spani Atacadista Resende") >= 0.8)
        assertTrue(NfceText.storeSimilarity("SPANI ATACADISTA LTDA", "Atacadão Resende") < 0.5)
    }

    @Test
    fun `categoria e unidade sugeridas`() {
        assertEquals("Hortifruti", NfceText.suggestCategory("BANANA PRATA KG"))
        assertEquals("Alimentos", NfceText.suggestCategory("MACARRAO ESPAGUETE 500G"))
        assertEquals("Laticínios", NfceText.suggestCategory("LEITE INTEGRAL 1L"))
        assertEquals("Limpeza", NfceText.suggestCategory("DETERGENTE LIQ YPE 500ML"))
        assertEquals("kg", NfceText.suggestUnit(NfceItem("BANANA", "", 0.5, "KG", 5.0, 2.5)))
        assertEquals("un", NfceText.suggestUnit(NfceItem("ARROZ", "", 1.0, "UN", 5.0, 5.0)))
    }

    @Test
    fun `quantidade na despensa`() {
        assertEquals(0.535, NfceText.pantryQuantity(0.535, "KG", "kg"), 1e-9)
        assertEquals(535.0, NfceText.pantryQuantity(0.535, "KG", "g"), 1e-9)
        assertEquals(1.0, NfceText.pantryQuantity(0.535, "KG", "un"), 1e-9)
        assertEquals(3.0, NfceText.pantryQuantity(3.0, "UN", "pct"), 1e-9)
    }
}
