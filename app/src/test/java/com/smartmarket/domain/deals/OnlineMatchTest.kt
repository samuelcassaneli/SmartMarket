package com.smartmarket.domain.deals

import com.smartmarket.domain.ai.GeminiModels
import com.smartmarket.domain.deals.OnlineMatch
import com.smartmarket.domain.matching.FuzzyMatcher
import com.smartmarket.domain.matching.SaleForm
import com.smartmarket.domain.online.OnlineOffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineMatchTest {

    private fun offer(name: String, price: Double, unit: String? = null, mult: Double? = null) =
        OnlineOffer(sourceId = "t", name = name, price = price, measurementUnit = unit, unitMultiplier = mult)

    @Test
    fun saleFormFromText() {
        assertEquals(SaleForm.Bulk, SaleForm.ofText("Alho Kg"))
        assertEquals(SaleForm.Bulk, SaleForm.ofText("Alho a granel"))
        assertTrue(SaleForm.ofText("Alho Roxo 60g") is SaleForm.Pack)
        assertTrue(SaleForm.ofText("Arroz Tipo 1 5kg") is SaleForm.Pack)
        assertEquals(12, (SaleForm.ofText("Papel Higiênico c/12") as SaleForm.Pack).count)
        assertEquals(SaleForm.Unknown, SaleForm.ofText("Amaciante Downy"))
    }

    @Test
    fun productFormUsesUnitAndLastReceipt() {
        assertEquals(SaleForm.Bulk, SaleForm.ofProduct("Banana Prata", "kg"))
        assertEquals(SaleForm.Bulk, SaleForm.ofProduct("Tomate", "un", "TOMATE ITALIANO", "KG"))
        val f = SaleForm.ofProduct("Amaciante Downy", "un", "AMAC DOWNY BRISA 1,5L", "UN")
        assertEquals(1500.0, (f as SaleForm.Pack).size!!.amount, 0.1)
    }

    @Test
    fun bulkGarlicNeverMatchesSmallPack() {
        val found = listOf(
            Triple(1L, "Atacadão", offer("Alho Descascado 60g", 4.99)),
            Triple(1L, "Atacadão", offer("Alho Nacional Kg", 12.45, "kg", 0.5)),
            Triple(2L, "Spani", offer("Alho Roxo 200g", 7.90)),
            Triple(2L, "Spani", offer("Alho Granel", 27.90, "kg", 1.0))
        )
        val form = SaleForm.ofProduct("alho Kg", "un")
        val cands = OnlineMatch.candidates("alho Kg", form, found, FuzzyMatcher.SUGGESTION_THRESHOLD)
        assertEquals(listOf("Alho Nacional Kg", "Alho Granel"), cands.map { it.offer.name }.sorted().reversed())
        val best = OnlineMatch.bestByAffinity(cands).associateBy { it.marketId }
        assertEquals(24.90, best[1L]!!.price, 0.001) // 12,45 por 0,5 kg = 24,90/kg
        assertEquals(27.90, best[2L]!!.price, 0.001)
    }

    @Test
    fun sizedProductNeedsSameSize() {
        val form = SaleForm.ofProduct("Arroz Tio João 5kg", "un")
        val found = listOf(
            Triple(1L, "A", offer("Arroz Tio João Tipo 1 1kg", 6.99)),
            Triple(1L, "A", offer("Arroz Tio João Tipo 1 5kg", 29.90)),
            Triple(1L, "A", offer("Arroz Tio João Tipo 1 Fardo 6x5kg", 170.0))
        )
        val best = OnlineMatch.bestByAffinity(OnlineMatch.candidates("Arroz Tio João 5kg", form, found, FuzzyMatcher.AUTO_MATCH_THRESHOLD))
        assertEquals(1, best.size)
        assertEquals(29.90, best.first().price, 0.001)
    }

    @Test
    fun bulkVersusPackLowersAffinity() {
        assertTrue(FuzzyMatcher.calculateAffinity("Alho 60g", "Alho kg") < FuzzyMatcher.AUTO_MATCH_THRESHOLD)
        assertTrue(FuzzyMatcher.calculateAffinity("Alho kg", "Alho kg") >= FuzzyMatcher.AUTO_MATCH_THRESHOLD)
        assertTrue(FuzzyMatcher.calculateAffinity("Alho Nacional 1kg", "Alho kg") >= FuzzyMatcher.AUTO_MATCH_THRESHOLD)
    }

    @Test
    fun comparablePriceRules() {
        assertNull(SaleForm.comparablePrice(SaleForm.Bulk, SaleForm.ofText("Alho 60g"), 4.99))
        assertEquals(19.9, SaleForm.comparablePrice(SaleForm.Bulk, SaleForm.ofText("Alho 1kg"), 19.9)!!, 0.001)
        assertNull(SaleForm.comparablePrice(SaleForm.ofText("Leite 1L"), SaleForm.ofText("Leite 1L c/12"), 60.0))
    }

    @Test
    fun searchTermDropsBareKg() {
        assertEquals("alho", OnlineMatch.searchTerm("alho Kg"))
        assertEquals("Arroz 5 kg", OnlineMatch.searchTerm("Arroz 5 kg"))
        assertEquals("Arroz 5kg", OnlineMatch.searchTerm("Arroz 5kg"))
    }

    @Test
    fun matchPromptAndParse() {
        val prompt = GeminiModels.matchPrompt(
            GeminiModels.MatchProduct("alho Kg", SaleForm.Bulk.describe(), "ALHO KG", "KG", 29.9),
            listOf(GeminiModels.MatchCandidate("Atacadão", "Alho 60g", 4.99, "embalagem de 60 g"))
        )
        assertTrue(prompt.contains("1 | Atacadão | Alho 60g | R$ 4,99"))
        assertTrue(prompt.contains("vendido por kg"))
        val body = """{"candidates":[{"content":{"parts":[{"text":"{\"iguais\":[2,9,0]}"}]}}]}"""
        assertEquals(setOf(1), GeminiModels.parseMatches(body, 3))
    }
}
