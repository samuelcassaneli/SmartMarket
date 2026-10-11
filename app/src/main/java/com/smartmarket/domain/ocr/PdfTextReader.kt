package com.smartmarket.domain.ocr

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Lê a camada de texto de PDFs (encartes gerados digitalmente, como Spani e Atacadão).
 * O texto vem exato, sem erros de OCR. Páginas escaneadas (sem texto) voltam null.
 */
class PdfTextReader(context: Context) {

    init {
        PDFBoxResourceLoader.init(context.applicationContext)
    }

    /** Uma entrada por página: linhas com caixa, ou null se a página não tem texto útil. */
    suspend fun read(file: File): List<List<OcrLine>?> = withContext(Dispatchers.IO) {
        PDDocument.load(file).use { doc ->
            (1..doc.numberOfPages).map { pageNo ->
                val words = WordCollector().collect(doc, pageNo)
                PdfTextLayout.group(words).takeIf { PdfTextLayout.hasEnoughText(it) }
            }
        }
    }

    private class WordCollector : PDFTextStripper() {
        private val words = mutableListOf<PdfWord>()

        init {
            sortByPosition = true
        }

        fun collect(doc: PDDocument, page: Int): List<PdfWord> {
            words.clear()
            startPage = page
            endPage = page
            getText(doc)
            return words.toList()
        }

        override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
            if (textPositions.isEmpty()) return
            // writeString recebe uma "palavra"; separa de novo se houver salto horizontal grande dentro dela
            var start = 0
            for (i in 1..textPositions.size) {
                val split = i == textPositions.size || run {
                    val prev = textPositions[i - 1]
                    val cur = textPositions[i]
                    val gap = cur.xDirAdj - (prev.xDirAdj + prev.widthDirAdj)
                    gap > prev.heightDir * 0.18f ||
                        (prev.widthOfSpace > 0f && gap > prev.widthOfSpace * 0.45f) ||
                        cur.unicode.isBlank()
                }
                if (split) {
                    val chunk = textPositions.subList(start, i).filter { it.unicode.isNotBlank() }
                    if (chunk.isNotEmpty()) words += toWord(chunk)
                    start = i
                }
            }
        }

        private fun toWord(ps: List<TextPosition>): PdfWord {
            val x0 = ps.minOf { it.xDirAdj }
            val x1 = ps.maxOf { it.xDirAdj + it.widthDirAdj }
            val bottom = ps.maxOf { it.yDirAdj }
            val h = ps.maxOf { it.heightDir }.coerceAtLeast(1f)
            val space = ps.map { it.widthOfSpace }.filter { it > 0f && !it.isNaN() }.average().toFloat()
            return PdfWord(ps.joinToString("") { it.unicode }, x0, bottom - h, x1, bottom, if (space.isNaN()) 0f else space)
        }
    }
}
