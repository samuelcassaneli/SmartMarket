package com.smartmarket.domain.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regressão com o texto real (camada de texto) dos PDFs do Spani e do Atacadão, out/2026. */
class RealFlyerParserTest {

    private val spani by lazy { EncarteParser.parse(PdfFixtures.load("spani_pdf_lines.json"), listOf("Spani")) }
    private val atacadao by lazy { EncarteParser.parse(PdfFixtures.load("atacadao_pdf_lines.json"), listOf("Atacadão")) }

    private fun List<ParsedOffer>.find(vararg words: String) =
        firstOrNull { o -> words.all { o.description.contains(it, ignoreCase = true) } }

    @Test
    fun `spani le quase todas as ofertas`() {
        assertTrue("ofertas=${spani.size}", spani.size >= 100)
    }

    @Test
    fun `spani precos e atacado`() {
        assertEquals(29.9, spani.find("Amaciante", "Downy")!!.price, 0.001)
        val leiteCond = spani.find("Leite Condensado")!!
        assertEquals(5.29, leiteCond.price, 0.001)
        assertTrue(leiteCond.note.contains("atacado"))
        assertEquals(13.9, spani.find("Papel Higiênico")!!.price, 0.001)
        assertTrue(spani.find("Papel Higiênico")!!.note.contains("leve 12 pague 11"))
    }

    @Test
    fun `atacadao usa preco normal e guarda app e cartao na observacao`() {
        val alho = atacadao.find("Alho")
        assertNotNull(alho)
        assertEquals(21.9, alho!!.price, 0.001)
        assertTrue(alho.note, alho.note.contains("de R$ 23,90"))
        assertTrue(alho.note, alho.note.contains("no app R$ 18,90"))
        assertTrue(alho.note, alho.note.contains("app + cartão R$ 16,90"))
        assertEquals(26.49, atacadao.find("Amaciante", "Comfort")!!.price, 0.001)
    }

    @Test
    fun `rotulos e titulos nao viram produto`() {
        val all = spani + atacadao
        val junk = listOf("sam", "desconto creditado", "preço com oferta", "carrefour ou", "acougue", "mercearia", "qualidade e economia")
        for (o in all) {
            val d = EncarteParser.normalize(o.description)
            assertFalse("lixo: ${o.description}", junk.any { d.startsWith(EncarteParser.normalize(it)) })
        }
        assertTrue("ofertas atacadao=${atacadao.size}", atacadao.size >= 55)
    }

    @Test
    fun `enchimento lido com erro e reconhecido`() {
        assertTrue(EncarteParser.isFillerLike("Uiidades"))
        assertTrue(EncarteParser.isFillerLike("Fragrncias"))
        assertTrue(EncarteParser.isFillerLike("unidade"))
        assertFalse(EncarteParser.isFillerLike("Amaciante"))
        assertTrue(EncarteParser.isJunkDescription("Uiidades"))
    }
}
