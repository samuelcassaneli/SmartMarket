package com.smartmarket.domain.ocr

import kotlin.math.max
import kotlin.math.min

/** Palavra da camada de texto de um PDF, em pontos com origem no topo da página. */
data class PdfWord(
    val text: String,
    val x0: Float,
    val top: Float,
    val x1: Float,
    val bottom: Float,
    /** Largura do caractere de espaço na fonte da palavra (0 se desconhecida). */
    val spaceWidth: Float = 0f
) {
    val height: Float get() = bottom - top
}

/**
 * Junta palavras da camada de texto em linhas visuais (mesma altura de base e próximas na horizontal),
 * respeitando colunas: palavras distantes na mesma altura ficam em linhas separadas, como os vários
 * cards lado a lado de um encarte. Puro Kotlin, testável na JVM.
 */
object PdfTextLayout {

    /** Fator de escala para converter pontos em [Box] inteiros sem perder precisão. */
    const val SCALE = 4f

    /** Página com texto suficiente para dispensar o OCR. */
    const val MIN_TEXT_CHARS = 40

    private class Line(var x0: Float, var top: Float, var x1: Float, var bottom: Float, val words: MutableList<PdfWord>) {
        val height get() = bottom - top
    }

    fun group(words: List<PdfWord>): List<OcrLine> {
        val clean = words.filter { it.text.isNotBlank() && it.height > 0f }
        if (clean.isEmpty()) return emptyList()
        val lines = mutableListOf<Line>()
        for (w in clean.sortedWith(compareBy<PdfWord> { it.top }.thenBy { it.x0 })) {
            val target = lines.lastOrNull { l ->
                val overlap = min(l.bottom, w.bottom) - max(l.top, w.top)
                val minH = min(l.height, w.height)
                val maxH = max(l.height, w.height)
                val gap = w.x0 - l.x1
                overlap >= minH * 0.5f && maxH / minH <= 1.8f && gap >= -minH * 0.5f && gap <= maxH * 1.0f
            }
            if (target != null) {
                target.words += w
                target.x0 = min(target.x0, w.x0); target.x1 = max(target.x1, w.x1)
                target.top = min(target.top, w.top); target.bottom = max(target.bottom, w.bottom)
            } else {
                lines += Line(w.x0, w.top, w.x1, w.bottom, mutableListOf(w))
            }
        }
        return lines.map { l ->
            val sorted = l.words.sortedBy { it.x0 }
            val sb = StringBuilder()
            sorted.forEachIndexed { i, w ->
                if (i > 0) {
                    val prev = sorted[i - 1]
                    // fragmentos colados (kerning) não ganham espaço; usa a largura do espaço da fonte
                    val gap = w.x0 - prev.x1
                    val space = max(prev.spaceWidth, w.spaceWidth)
                    val bySpace = space > 0f && gap > space * 0.45f
                    val byHeight = gap > min(prev.height, w.height) * 0.18f
                    if (bySpace || byHeight) sb.append(' ')
                }
                sb.append(w.text.trim())
            }
            val text = sb.toString().replace("\\s+".toRegex(), " ")
            OcrLine(
                text,
                Box((l.x0 * SCALE).toInt(), (l.top * SCALE).toInt(), (l.x1 * SCALE).toInt(), (l.bottom * SCALE).toInt())
            )
        }
    }

    fun hasEnoughText(lines: List<OcrLine>): Boolean = lines.sumOf { it.text.length } >= MIN_TEXT_CHARS
}
