package com.smartmarket.domain.flyers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fixtures: HTML real das páginas salvas em 10/10/2026 (test/resources/flyers). */
class FlyerLinkExtractorTest {

    private fun fixture(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource("flyers/$name")) { "fixture $name ausente" }.readText()

    @Test
    fun `spani - pdf do tabloide de resende`() {
        val links = FlyerLinkExtractor.extractSpaniPdfLinks(fixture("spani_resende.html"))
        assertEquals(
            listOf("https://spani-tabloides-bucket.s3.us-east-1.amazonaws.com/tabloides/resende-sxjwl24ehigptov0tnwlo.pdf"),
            links
        )
    }

    @Test
    fun `spani - sem resende usa qualquer pdf do bucket e normaliza barra dupla`() {
        val html = """
            <a href="https://outro-site.com/folder.pdf">x</a>
            <a href="//spani-tabloides-bucket.s3.us-east-1.amazonaws.com/tabloides/volta-redonda-abc.pdf">y</a>
        """.trimIndent()
        assertEquals(
            listOf("https://spani-tabloides-bucket.s3.us-east-1.amazonaws.com/tabloides/volta-redonda-abc.pdf"),
            FlyerLinkExtractor.extractSpaniPdfLinks(html)
        )
    }

    @Test
    fun `spani - pagina sem tabloide`() {
        assertTrue(FlyerLinkExtractor.extractSpaniPdfLinks("<html><body>Sem encarte</body></html>").isEmpty())
    }

    @Test
    fun `dom - encartes separados pelo __NEXT_DATA__`() {
        val sources = FlyerLinkExtractor.domSources(fixture("dom_resende.html"))
        assertEquals(3, sources.size)
        val main = sources.first()
        assertEquals("dom-267", main.key)
        assertEquals("Encarte", main.title)
        assertEquals(12, main.urls.size)
        assertEquals(
            "https://meu-varejo.sfo3.cdn.digitaloceanspaces.com/dom-atacadista/2026/10/a392d7c5-28fdb34a-5156b534-4c780cd4-5523a733.jpg",
            main.urls.first()
        )
        assertEquals(listOf(12, 2, 4), sources.map { it.urls.size })
        assertTrue(sources.all { !it.isPdf })
    }

    @Test
    fun `dom - paginas normalizadas e do mes mais recente`() {
        val pages = FlyerLinkExtractor.extractDomImagePages(fixture("dom_resende.html"))
        assertEquals(18, pages.size)
        assertTrue(pages.all { it.startsWith("https://meu-varejo.sfo3.cdn.digitaloceanspaces.com/dom-atacadista/2026/10/") })
    }

    @Test
    fun `dom - fallback pelas imagens filtra o mes mais recente`() {
        val html = """
            <img src="//meu-varejo.sfo3.cdn.digitaloceanspaces.com/dom-atacadista/2026/09/antigo.jpg">
            <img src="//meu-varejo.sfo3.cdn.digitaloceanspaces.com/dom-atacadista/2026/10/novo-1.jpg">
            <img src="//meu-varejo.sfo3.cdn.digitaloceanspaces.com/dom-atacadista/2026/10/novo-2.jpg">
        """.trimIndent()
        assertEquals(
            listOf(
                "https://meu-varejo.sfo3.cdn.digitaloceanspaces.com/dom-atacadista/2026/10/novo-1.jpg",
                "https://meu-varejo.sfo3.cdn.digitaloceanspaces.com/dom-atacadista/2026/10/novo-2.jpg"
            ),
            FlyerLinkExtractor.extractDomImagePages(html)
        )
    }

    @Test
    fun `hash da lista de urls e estavel`() {
        val a = FlyerDownloader.hashOfUrls(listOf("https://x/1.jpg", "https://x/2.jpg"))
        val b = FlyerDownloader.hashOfUrls(listOf("https://x/1.jpg", "https://x/2.jpg"))
        val c = FlyerDownloader.hashOfUrls(listOf("https://x/1.jpg", "https://x/3.jpg"))
        assertEquals(a, b)
        assertTrue(a != c)
        assertEquals(64, a.length)
    }

    @Test
    fun `texto da oferta guarda a observacao entre colchetes`() {
        val raw = OfferText.compose("Leite Glória 1L", "atacado a partir de 12 un")
        assertEquals("Leite Glória 1L [atacado a partir de 12 un]", raw)
        assertEquals("Leite Glória 1L" to "atacado a partir de 12 un", OfferText.split(raw))
        assertEquals("Café Pilão 500g" to "", OfferText.split("Café Pilão 500g"))
    }

    @Test
    fun `titulo do encarte no fragmento da url`() {
        val url = FlyerTitle.encode("https://domatacadista.net/encartes/lojas/resende", "Lâmina Feirinha")
        assertEquals("Lâmina Feirinha", FlyerTitle.decode(url))
        assertNull(FlyerTitle.decode("https://spani-tabloides-bucket.s3.us-east-1.amazonaws.com/tabloides/x.pdf"))
    }
}
