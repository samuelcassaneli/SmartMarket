package com.smartmarket.domain.ai

import com.smartmarket.domain.ocr.Box
import com.smartmarket.domain.ocr.OcrLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiModelsTest {

    @Test
    fun `escolhe o flash estavel mais novo`() {
        val models = listOf(
            GeminiModels.ModelInfo("models/gemini-2.5-flash", listOf("generateContent")),
            GeminiModels.ModelInfo("models/gemini-2.5-flash-lite", listOf("generateContent")),
            GeminiModels.ModelInfo("models/gemini-3-flash-preview", listOf("generateContent")),
            GeminiModels.ModelInfo("models/gemini-2.5-pro", listOf("generateContent")),
            GeminiModels.ModelInfo("models/gemini-2.5-flash-image", listOf("generateContent")),
            GeminiModels.ModelInfo("models/text-embedding-004", listOf("embedContent"))
        )
        assertEquals("gemini-2.5-flash", GeminiModels.pickModel(models))
        assertEquals(
            "gemini-3.1-flash",
            GeminiModels.pickModel(models + GeminiModels.ModelInfo("models/gemini-3.1-flash", listOf("generateContent")))
        )
    }

    @Test
    fun `le a resposta json do generateContent`() {
        val body = """
            {"candidates":[{"content":{"parts":[{"text":"[{\"nome\":\"Amaciante Concentrado Downy 1,5L\",\"preco\":29.9},{\"nome\":\"Alho a granel kg\",\"preco\":21.9,\"preco_antigo\":23.9,\"observacao\":\"no app R$ 18,90\"},{\"nome\":\"Leite Glória 1L\",\"preco\":4.99,\"preco_varejo\":5.09},{\"nome\":\"x\",\"preco\":1.0},{\"nome\":\"Sem preço\",\"preco\":0}]"}]}}]}
        """.trimIndent()
        val offers = GeminiModels.parseOffers(body, page = 2)
        assertEquals(3, offers.size)
        assertEquals(29.9, offers[0].price, 0.001)
        assertTrue(offers[1].note.contains("de R$ 23,90"))
        assertTrue(offers[1].note.contains("no app"))
        assertEquals(5.09, offers[2].retailPrice!!, 0.001)
        assertEquals(2, offers[2].page)
    }

    @Test
    fun `prompt de texto leva posicoes`() {
        val p = GeminiModels.pageTextPrompt(listOf(OcrLine("Alho a granel", Box(2428, 7064, 3016, 7124)), OcrLine("R$21,90", Box(3388, 5980, 4000, 6252))), "Atacadão")
        assertTrue(p.contains("607,1766 | Alho a granel"))
        assertTrue(p.contains("Atacadão"))
    }

    @Test
    fun `mensagens de erro`() {
        assertEquals("Limite gratuito da IA atingido; tente mais tarde", GeminiModels.errorMessage(429, "{}"))
        assertEquals("Chave da IA inválida", GeminiModels.errorMessage(400, """{"error":{"message":"API key not valid. Please pass a valid API key."}}"""))
    }
}
