package com.smartmarket.domain.matching

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FuzzyMatcherTest {

    private val catalog = listOf(
        "Arroz Tipo 1 5kg",
        "Feijão Carioca 1kg",
        "Leite Integral 1L",
        "Leite Condensado 395g",
        "Café Torrado e Moído 500g",
        "Óleo de Soja 900ml",
        "Açúcar Refinado 1kg",
        "Detergente Líquido 500ml",
        "Sabão em Pó 1kg",
        "Amaciante Concentrado 1,5L",
        "Papel Higiênico Folha Dupla c/ 12"
    )

    private fun best(text: String): String? =
        FuzzyMatcher.rank(text, catalog, { it }).firstOrNull()
            ?.takeIf { it.score >= FuzzyMatcher.AUTO_MATCH_THRESHOLD }?.item

    @Test
    fun `leite condensado nao casa com leite integral`() {
        val score = FuzzyMatcher.calculateAffinity("Leite Condensado", "Leite Integral 1L")
        assertTrue("score=$score", score < FuzzyMatcher.SUGGESTION_THRESHOLD)
        assertEquals("Leite Condensado 395g", best("Leite Condensado"))
    }

    @Test
    fun `abreviacoes de nota fiscal`() {
        assertEquals("Leite Condensado 395g", best("LEITE COND PIRACANJUBA 395G"))
        assertEquals("Leite Integral 1L", best("LEITE UHT INTEGRAL ITALAC 1L"))
        assertEquals("Amaciante Concentrado 1,5L", best("AMAC CONC COMFORT 1,5L"))
        assertEquals("Detergente Líquido 500ml", best("DET LIQ YPE NEUTRO 500ML"))
        assertEquals("Papel Higiênico Folha Dupla c/ 12", best("PAP HIG NEVE FD C/12"))
        assertEquals("Arroz Tipo 1 5kg", best("ARROZ T1 TIO JOAO 5KG"))
        assertEquals("Feijão Carioca 1kg", best("FEIJAO CAR CAMIL 1KG"))
        assertEquals("Açúcar Refinado 1kg", best("ACUCAR REF UNIAO 1KG"))
        assertEquals("Óleo de Soja 900ml", best("OLEO SOJA LIZA 900ML"))
    }

    @Test
    fun `tamanho diferente penaliza`() {
        val same = FuzzyMatcher.calculateAffinity("Arroz Tipo 1 5kg Camil", "Arroz Tipo 1 5kg")
        val other = FuzzyMatcher.calculateAffinity("Arroz Tipo 1 1kg Camil", "Arroz Tipo 1 5kg")
        assertTrue("same=$same other=$other", same > other)
        assertTrue(other < FuzzyMatcher.AUTO_MATCH_THRESHOLD)
    }

    @Test
    fun `pacote com quantidade diferente penaliza`() {
        val s12 = FuzzyMatcher.calculateAffinity("Papel Higiênico Folha Dupla 12 rolos", "Papel Higiênico Folha Dupla c/ 12")
        val s4 = FuzzyMatcher.calculateAffinity("Papel Higiênico Folha Dupla 4 rolos", "Papel Higiênico Folha Dupla c/ 12")
        assertTrue("s12=$s12 s4=$s4", s12 > s4)
    }

    @Test
    fun `lixo de ocr nao casa`() {
        assertNull(best("o,96"))
        assertNull(best("aunidade"))
        assertNull(best("90g Sabores Un"))
    }

    @Test
    fun `texto generico sugere mas nao vincula automatico com variante`() {
        // "LEITE" sozinho é ambíguo entre integral e condensado
        val ranked = FuzzyMatcher.rank("LEITE", catalog, { it })
        assertTrue(ranked.isNotEmpty())
        assertTrue(ranked.all { it.item.startsWith("Leite") })
    }

    @Test
    fun `palavra principal precisa aparecer`() {
        val score = FuzzyMatcher.calculateAffinity("Amaciante Concentrado", "Papel Higiênico Folha Dupla c/ 12")
        assertTrue(score < FuzzyMatcher.SUGGESTION_THRESHOLD)
    }

    @Test
    fun `extrai tamanho e pacote`() {
        assertEquals(395.0, FuzzyMatcher.extractSize("Leite Condensado 395g")!!.amount, 0.01)
        assertEquals(1500.0, FuzzyMatcher.extractSize("Amaciante 1,5L")!!.amount, 0.01)
        assertEquals(5000.0, FuzzyMatcher.extractSize("ARROZ 5KG")!!.amount, 0.01)
        assertEquals(12, FuzzyMatcher.extractPackCount("Papel Higiênico c/ 12"))
        assertEquals(12, FuzzyMatcher.extractPackCount("PAP HIG 12 ROLOS"))
        assertNotNull(FuzzyMatcher.extractSize("Óleo 900ml"))
    }

    @Test
    fun `alias key normaliza`() {
        assertEquals("leite cond piracanjuba 395g", ProductMatcher.aliasKey("LEITE COND. PIRACANJUBA 395G"))
    }
}
