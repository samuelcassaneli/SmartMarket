package com.smartmarket.domain.ocr

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.pdfbox.text.TextPosition
import org.junit.Test
import java.io.File

/**
 * Diagnóstico: lê PDFs reais com o PDFBox desktop (mesma API do PdfBox-Android), agrupa em linhas
 * com [PdfTextLayout] e roda o [EncarteParser]. Rode com -Pdump -Ppdfs=/caminho/a.pdf,/caminho/b.pdf
 */
class PdfLayoutDumpTest {

    private class Collector : PDFTextStripper() {
        val words = mutableListOf<PdfWord>()
        init { sortByPosition = true }
        override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
            var start = 0
            for (i in 1..textPositions.size) {
                val split = i == textPositions.size || run {
                    val prev = textPositions[i - 1]
                    val cur = textPositions[i]
                    val gap = cur.xDirAdj - (prev.xDirAdj + prev.widthDirAdj)
                    gap > prev.heightDir * 0.18f || (prev.widthOfSpace > 0f && gap > prev.widthOfSpace * 0.45f) ||
                        cur.unicode.isBlank()
                }
                if (split) {
                    val ps = textPositions.subList(start, i).filter { it.unicode.isNotBlank() }
                    if (ps.isNotEmpty()) {
                        val bottom = ps.maxOf { it.yDirAdj }
                        val h = ps.maxOf { it.heightDir }.coerceAtLeast(1f)
                        val space = ps.map { it.widthOfSpace }.filter { it > 0f && !it.isNaN() }.average().toFloat()
                        words += PdfWord(ps.joinToString("") { it.unicode }, ps.minOf { it.xDirAdj }, bottom - h,
                            ps.maxOf { it.xDirAdj + it.widthDirAdj }, bottom, if (space.isNaN()) 0f else space)
                    }
                    start = i
                }
            }
        }
    }

    @Test
    fun dump() {
        val paths = System.getProperty("pdfs") ?: return
        for (path in paths.split(",")) {
            val pages = PDDocument.load(File(path)).use { doc ->
                (1..doc.numberOfPages).map { n ->
                    val c = Collector()
                    c.startPage = n; c.endPage = n
                    c.getText(doc)
                    OcrPage(n - 1, PdfTextLayout.group(c.words))
                }
            }
            val offers = EncarteParser.parse(pages, listOf("Spani", "Atacadão"))
            println("===== ${File(path).name}: ${offers.size} ofertas, linhas por página ${pages.map { it.lines.size }}")
            offers.forEach { println("p${it.page} | ${it.description} | ${it.price} | ${it.retailPrice} | ${it.note}") }
        }
    }
}
