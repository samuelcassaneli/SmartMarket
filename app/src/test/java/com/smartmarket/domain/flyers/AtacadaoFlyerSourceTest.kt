package com.smartmarket.domain.flyers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AtacadaoFlyerSourceTest {
    private val html = javaClass.classLoader!!.getResource("flyers/atacadao_resende.html")!!.readText()

    @Test
    fun `extrai os folhetos vigentes da pagina da loja`() {
        val sources = FlyerLinkExtractor.atacadaoSources(html, "2026-10-10")
        assertEquals(3, sources.size)
        assertTrue(sources.all { it.isPdf && it.urls.single().startsWith("https://apigw.cloud.carrefour.com.br/") })
        assertTrue(sources.any { it.title.contains("Ofert") })
    }

    @Test
    fun `ignora folhetos vencidos`() {
        val sources = FlyerLinkExtractor.atacadaoSources(html, "2026-10-12")
        assertEquals(listOf("Ofertããão Atacadão", "Super Ofertas").sorted(), sources.map { it.title }.sorted())
    }
}
