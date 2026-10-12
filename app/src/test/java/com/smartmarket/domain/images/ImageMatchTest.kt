package com.smartmarket.domain.images

import com.smartmarket.domain.ai.GeminiModels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageMatchTest {
    private fun c(name: String) = ImageCandidate(name, "https://img/${name.hashCode()}.jpg", "t")

    @Test
    fun zeroKetchupNeverUsesRegularPhoto() {
        val cands = listOf(c("Ketchup Heinz Pet 600g"), c("Ketchup Heinz Zero Açúcar Pet 600g"), c("Ketchup Heinz Picante 600g"))
        assertEquals("Ketchup Heinz Zero Açúcar Pet 600g", ImageMatch.rank("Ketchup Heinz Pet Zero 600g", cands).first().name)
        val normal = ImageMatch.rank("Ketchup Heinz Pet 600g", cands)
        assertEquals(listOf("Ketchup Heinz Pet 600g"), normal.map { it.name })
    }

    @Test
    fun sizeBrandAndPackMustMatch() {
        assertNull(ImageMatch.score("Ketchup Heinz 600g", "Ketchup Heinz 1kg"))
        assertNull(ImageMatch.score("Ketchup Heinz 600g", "Ketchup Hellmann's 600g"))
        assertNull(ImageMatch.score("Leite Integral Italac 1L", "Leite Integral Italac 1L c/12"))
        assertTrue(ImageMatch.score("Leite Integral Italac 1L", "Leite UHT Integral Italac 1L")!! > 0)
        assertTrue(ImageMatch.score("Oleo de Soja Soya 900ml", "Óleo de Soja Soya Pet 900ml")!! > 0)
    }

    @Test
    fun cacheKeyIgnoresOrderButNotVariant() {
        assertEquals(ImageMatch.key("Ketchup Heinz Zero 600g"), ImageMatch.key("Heinz Ketchup ZERO 600 g"))
        assertNotEquals(ImageMatch.key("Ketchup Heinz Zero 600g"), ImageMatch.key("Ketchup Heinz 600g"))
        assertNotEquals(ImageMatch.key("Ketchup Heinz 600g"), ImageMatch.key("Ketchup Heinz 400g"))
    }

    @Test
    fun openFoodFactsParsing() {
        val body = """{"products":[{"product_name":"Ketchup Zero","brands":"Heinz,Kraft","quantity":"397 g","image_front_url":"https://images.openfoodfacts.org/a.jpg"},{"product_name":"Sem foto"}]}"""
        val list = OpenFoodFacts.parseSearch(body)
        assertEquals(1, list.size)
        assertEquals("Ketchup Zero Heinz 397 g", list[0].name)
    }

    @Test
    fun aiResponses() {
        fun wrap(t: String) = """{"candidates":[{"content":{"parts":[{"text":${kotlinx.serialization.json.JsonPrimitive(t)}}]}}]}"""
        assertEquals(1, GeminiModels.parseImagePick(wrap("""{"escolha":2}"""), 3))
        assertNull(GeminiModels.parseImagePick(wrap("""{"escolha":0}"""), 3))
        assertNull(GeminiModels.parseImagePick(wrap("""{"escolha":7}"""), 3))
        assertEquals(listOf("a", "b"), GeminiModels.parseInsights(wrap("""{"dicas":["a"," b ",""]}""")))
        assertTrue(GeminiModels.imagePickPrompt("Ketchup Heinz Zero", listOf("X")).contains("1 | X"))
    }
}
