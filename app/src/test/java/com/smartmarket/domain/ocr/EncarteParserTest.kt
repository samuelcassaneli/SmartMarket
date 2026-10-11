package com.smartmarket.domain.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Casos reais: textos do OCR antigo (screenshots de 10/10/2026) e do PDF
 * "Jornal de Ofertas Mês das Crianças RJ 06 a 12/10/2026".
 */
class EncarteParserTest {

    // ------------------------------------------------------------------ filtro de lixo

    @Test
    fun `lixo real do OCR antigo e descartado`() {
        listOf("o,96", "0,96", "aunidade", "90g Sabores Un", "Un", "unidade", "kg", "R$", "4,99 cada", "12/10")
            .forEach { assertTrue("deveria ser lixo: '$it'", EncarteParser.isJunkDescription(it)) }
    }

    @Test
    fun `avisos legais datas e nomes de mercado sao ruido`() {
        listOf(
            "Imagens meramente ilustrativas",
            "OFERTAS VÁLIDAS PARA A LOJA:",
            "Ofertas válidas de 06 a 12/10",
            "O Ministério da Saúde adverte: este produto contém álcool",
            "Enquanto durarem os estoques",
            "Parcele no cartão de crédito",
            "06 A 12 OUT 2026",
            "Sexta-feira",
            "Spani Atacadista",
            "Resende - RJ",
            "PREÇO DE ATACADO VÁLIDO SOMENTE PARA PRODUTOS DO MESMO TIPO, MARCA, SABOR E FRAGRÂNCIA."
        ).forEach { assertTrue("deveria ser ruído: '$it'", EncarteParser.isJunkDescription(it)) }
    }

    @Test
    fun `nome do mercado informado tambem e ruido`() {
        assertTrue(EncarteParser.isJunkDescription("Supermercado Bom Preço", listOf("Bom Preço")))
    }

    @Test
    fun `descricoes de produto sao mantidas`() {
        listOf(
            "LEITE",
            "Leite Condensado Glória 395g Un",
            "Óleo de Soja Leve 900ml Un",
            "Creme Dental Colgate Total C/4 90g Sabores Un",
            "Papel Higiênico Folha Dupla Cotton Leve 12 Pague 11 Un",
            "Carne Bovina Fraldinha Pul Kg",
            "Refresco Em Pó Tang 18g Sabores Un",
            "Coca-Cola Pet 1L"
        ).forEach { assertFalse("não deveria ser lixo: '$it'", EncarteParser.isJunkDescription(it)) }
    }

    // ------------------------------------------------------------------ preços

    @Test
    fun `formatos de preco`() {
        assertEquals(4.99, EncarteParser.parsePrice("R$ 4,99")!!, 0.001)
        assertEquals(4.99, EncarteParser.parsePrice("4,99")!!, 0.001)
        assertEquals(4.99, EncarteParser.parsePrice("4.99")!!, 0.001)
        assertEquals(4.99, EncarteParser.parsePrice("4 99")!!, 0.001)
        assertEquals(4.99, EncarteParser.parsePrice("4,99 cada")!!, 0.001)
        assertEquals(9.99, EncarteParser.parsePrice("RS 9,99")!!, 0.001)
        assertEquals(47.90, EncarteParser.parsePrice("R$ 47,90 Kg")!!, 0.001)
        assertEquals(1234.56, EncarteParser.parsePrice("1.234,56")!!, 0.001)
        assertEquals(4.99, EncarteParser.parsePrice("Nas compras a partir de 12, a unidade sai a: 4,99")!!, 0.001)
    }

    @Test
    fun `tamanhos e faixa invalida nao sao preco`() {
        assertNull(EncarteParser.parsePrice("Leite 1,5L"))
        assertNull(EncarteParser.parsePrice("Coca-Cola 1,25L"))
        assertNull(EncarteParser.parsePrice("Limpador Triex 1,750L"))
        assertNull(EncarteParser.parsePrice("Papel Alumínio 45x7,5m"))
        assertNull(EncarteParser.parsePrice("Leve 3 pague 2"))
        assertNull(EncarteParser.parsePrice("0,30"))
        assertNull(EncarteParser.parsePrice("2500,00"))
        assertNull(EncarteParser.parsePrice("06.10 a 12.10"))
    }

    @Test
    fun `o lido no lugar de zero`() {
        assertEquals(0.96, EncarteParser.parsePrice("o,96")!!, 0.001)
    }

    @Test
    fun `centavos sobrescritos em linhas separadas sao unidos`() {
        val lines = listOf(
            OcrLine("4", Box(100, 200, 160, 280)),
            OcrLine("99", Box(165, 205, 200, 235))
        )
        val merged = EncarteParser.mergeSplitPrices(lines)
        assertEquals(1, merged.size)
        assertEquals(4.99, EncarteParser.parsePrice(merged.first().text)!!, 0.001)
    }

    // ------------------------------------------------------------------ associação nome-preço

    @Test
    fun `associa cada nome ao preco logo abaixo e ignora lixo`() {
        val lines = listOf(
            OcrLine("Leite Condensado Glória", Box(100, 100, 400, 130)),
            OcrLine("395g Un", Box(100, 135, 250, 165)),
            OcrLine("Óleo de Soja Leve 900ml", Box(600, 100, 900, 130)),
            OcrLine("5,29", Box(120, 180, 260, 240)),
            OcrLine("aunidade", Box(280, 200, 380, 220)),
            OcrLine("7,99", Box(620, 180, 760, 240)),
            OcrLine("Imagens meramente ilustrativas", Box(100, 900, 600, 920))
        )
        val offers = EncarteParser.parsePage(lines)
        assertEquals(2, offers.size)
        val leite = offers.first { it.description.startsWith("Leite") }
        assertEquals("Leite Condensado Glória 395g", leite.description)
        assertEquals(5.29, leite.price, 0.001)
        val oleo = offers.first { it.description.startsWith("Óleo") }
        assertEquals(7.99, oleo.price, 0.001)
    }

    @Test
    fun `preco a direita do nome`() {
        val lines = listOf(
            OcrLine("Arroz Tipo 1 Camil 5kg", Box(100, 100, 400, 130)),
            OcrLine("R$ 27,90", Box(420, 95, 560, 140)),
            OcrLine("Feijão Carioca Kicaldo 1kg", Box(100, 300, 400, 330)),
            OcrLine("R$ 6,49", Box(420, 295, 560, 340))
        )
        val offers = EncarteParser.parsePage(lines)
        assertEquals(2, offers.size)
        assertEquals(27.90, offers.first { it.description.startsWith("Arroz") }.price, 0.001)
        assertEquals(6.49, offers.first { it.description.startsWith("Feijão") }.price, 0.001)
    }

    @Test
    fun `atacarejo com dois precos registra o menor como atacado`() {
        val lines = listOf(
            OcrLine("Leite Glória 1L Tipos Un", Box(100, 100, 400, 130)),
            OcrLine("Unidade: 5,09", Box(100, 140, 300, 170)),
            OcrLine("Nas compras a partir de 12, a unidade sai a:", Box(100, 175, 400, 195)),
            OcrLine("4,99", Box(100, 200, 250, 260))
        )
        val offers = EncarteParser.parsePage(lines)
        assertEquals(1, offers.size)
        val o = offers.first()
        assertEquals("Leite Glória 1L Tipos", o.description)
        assertEquals(4.99, o.price, 0.001)
        assertEquals(5.09, o.retailPrice!!, 0.001)
        assertEquals("atacado a partir de 12 un", o.note)
    }

    @Test
    fun `leve e pague vira observacao`() {
        val lines = listOf(
            OcrLine("Papel Higiênico Folha Dupla Cotton Leve 12 Pague 11 Un", Box(100, 100, 500, 130)),
            OcrLine("13,90", Box(120, 150, 260, 210))
        )
        val o = EncarteParser.parsePage(lines).single()
        assertEquals("Papel Higiênico Folha Dupla Cotton", o.description)
        assertEquals(13.90, o.price, 0.001)
        assertEquals("leve 12 pague 11", o.note)
    }

    @Test
    fun `preco de embalagem nao vira oferta`() {
        val lines = listOf(
            OcrLine("Cerveja Brahma Multipack C/12 473ml Un", Box(100, 100, 500, 130)),
            OcrLine("45,48", Box(120, 150, 300, 220)),
            OcrLine("NESTA EMBALAGEM A UNIDADE SAI A: 3,79 R$", Box(100, 230, 500, 250))
        )
        val o = EncarteParser.parsePage(lines).single()
        assertEquals(45.48, o.price, 0.001)
        assertEquals("", o.note)
    }

    @Test
    fun `sem coordenadas usa a ordem das linhas`() {
        val lines = listOf(
            "Leite Condensado Glória 395g Un", "R$ 5,29",
            "aunidade", "o,96",
            "Imagens meramente ilustrativas",
            "90g Sabores Un", "19,90",
            "Creme Dental Colgate Total C/4", "90g Sabores Un", "27,96"
        ).map { OcrLine(it) }
        val offers = EncarteParser.parsePage(lines)
        val descriptions = offers.map { it.description }
        assertEquals(listOf("Leite Condensado Glória 395g", "Creme Dental Colgate Total C/4 90g Sabores"), descriptions)
        assertEquals(5.29, offers[0].price, 0.001)
        assertEquals(27.96, offers[1].price, 0.001)
        assertTrue(descriptions.none { it == "aunidade" || it == "o,96" || it == "90g Sabores Un" })
    }

    @Test
    fun `parse remove duplicadas entre paginas`() {
        val page = listOf(OcrLine("Café Pilão 500g"), OcrLine("18,90"))
        val offers = EncarteParser.parse(listOf(OcrPage(0, page), OcrPage(1, page)))
        assertEquals(1, offers.size)
    }
}
