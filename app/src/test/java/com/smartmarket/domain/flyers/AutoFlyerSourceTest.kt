package com.smartmarket.domain.flyers

import com.smartmarket.data.entity.FlyerKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoFlyerSourceTest {

    @Test
    fun `detecta o tipo pelo link`() {
        assertEquals(FlyerKind.ATACADAO_STORE, FlyerKind.detect("https://www.atacadao.com.br/loja/volta-redonda"))
        assertEquals(FlyerKind.PDF_LINK, FlyerKind.detect("https://lojas.spani.com.br/lojas/barra-mansa/"))
        assertEquals(FlyerKind.IMAGE_PAGES, FlyerKind.detect("https://domatacadista.net/encartes/lojas/x"))
        assertEquals(FlyerKind.AUTO, FlyerKind.detect("https://www.mercadoqualquer.com.br/ofertas"))
        assertEquals(FlyerKind.NONE, FlyerKind.detect(""))
    }

    @Test
    fun `pagina do atacadao de outra cidade funciona no modo automatico`() {
        val html = javaClass.classLoader!!.getResource("flyers/atacadao_resende.html")!!.readText()
        val sources = FlyerLinkExtractor.autoSources(html, "https://www.atacadao.com.br/loja/resende", "2026-10-10")
        assertEquals(3, sources.size)
    }

    @Test
    fun `site qualquer com pdf relativo`() {
        val html = """<html><body><a href="/files/encarte-semana.pdf">Encarte da semana</a><img src="/logo.png"></body></html>"""
        val sources = FlyerLinkExtractor.autoSources(html, "https://www.mercado.com.br/ofertas", "2026-10-10")
        assertEquals(listOf("https://www.mercado.com.br/files/encarte-semana.pdf"), sources.single().urls)
        assertTrue(sources.single().isPdf)
    }

    @Test
    fun `site qualquer com imagens de encarte`() {
        val html = """<html><body>
            <img src="/img/logo.png" alt="Logo">
            <img data-src="https://cdn.x.com/tabloide/pagina1.jpg" alt="Tabloide página 1">
            <img src="https://cdn.x.com/tabloide/pagina2.jpg" alt="Tabloide página 2">
        </body></html>"""
        val sources = FlyerLinkExtractor.autoSources(html, "https://x.com/ofertas", "2026-10-10")
        assertEquals(listOf("https://cdn.x.com/tabloide/pagina1.jpg", "https://cdn.x.com/tabloide/pagina2.jpg"), sources.single().urls)
    }
}
