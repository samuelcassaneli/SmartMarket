package com.smartmarket.domain.share

import com.smartmarket.domain.share.ListShareFormatter.Line
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ListShareFormatterTest {

    @Test
    fun `formata titulo, categorias, marcacoes e total`() {
        val text = ListShareFormatter.format(
            title = "Compras de Outubro",
            subtitle = "Outubro 2026",
            lines = listOf(
                Line("Feijão Carioca", "Mercearia", 2.0, "un", checked = true),
                Line("Arroz 5kg", "Mercearia", 1.0, "un", checked = false),
                Line("Detergente", "Limpeza", 3.0, "un", checked = false),
                Line("Banana", "Hortifruti", 1.5, "kg", checked = false)
            ),
            estimatedTotal = 1234.5,
            marketName = "Atacadão"
        )
        val expected = """
            *Compras de Outubro*
            Outubro 2026

            *Hortifruti*
            ☐ Banana — 1,5 kg

            *Limpeza*
            ☐ Detergente — 3 un

            *Mercearia*
            ☐ Arroz 5kg — 1 un
            ☑ Feijão Carioca — 2 un

            *Total estimado:* R$ 1.234,50 (Atacadão)
            1 de 4 itens marcados

            _Enviado pelo SmartMarket_
        """.trimIndent()
        assertEquals(expected, text)
    }

    @Test
    fun `lista vazia e sem total`() {
        val text = ListShareFormatter.format("Lista", emptyList())
        assertTrue(text.startsWith("*Lista*"))
        assertTrue(text.contains("(lista vazia)"))
        assertTrue(!text.contains("Total estimado"))
    }

    @Test
    fun `categoria em branco vira Outros`() {
        val text = ListShareFormatter.format("L", listOf(Line("Pilha", " ", 4.0, "un", false)))
        assertTrue(text.contains("*Outros*\n☐ Pilha — 4 un"))
    }
}
