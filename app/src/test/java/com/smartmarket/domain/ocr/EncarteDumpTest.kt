package com.smartmarket.domain.ocr

import org.junit.Test

/** Imprime as ofertas extraídas dos encartes reais (diagnóstico; rode com -Pdump). */
class EncarteDumpTest {
    @Test
    fun dump() {
        if (System.getProperty("dump") == null) return
        for (f in listOf("spani_pdf_lines.json", "atacadao_pdf_lines.json")) {
            val offers = EncarteParser.parse(PdfFixtures.load(f), listOf("Spani", "Atacadão"))
            println("===== $f: ${offers.size} ofertas")
            offers.forEach { println("p${it.page} | ${it.description} | ${it.price} | ${it.retailPrice} | ${it.note}") }
        }
    }
}
