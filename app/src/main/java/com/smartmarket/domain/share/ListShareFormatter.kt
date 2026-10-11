package com.smartmarket.domain.share

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Formata uma lista de compras como texto para WhatsApp (negrito com *asteriscos*).
 * Função pura, testável na JVM.
 */
object ListShareFormatter {

    data class Line(
        val name: String,
        val category: String,
        val quantity: Double,
        val unit: String,
        val checked: Boolean
    )

    private val symbols = DecimalFormatSymbols(Locale("pt", "BR")).apply {
        decimalSeparator = ','
        groupingSeparator = '.'
    }

    fun money(value: Double): String = "R$ " + DecimalFormat("#,##0.00", symbols).format(value)

    fun quantity(value: Double): String = DecimalFormat("#,##0.###", symbols).format(value)

    /**
     * @param title nome da lista
     * @param subtitle linha de apoio (ex.: "Outubro 2026")
     * @param estimatedTotal total estimado; omitido se null
     * @param marketName mercado do total estimado
     */
    fun format(
        title: String,
        lines: List<Line>,
        subtitle: String? = null,
        estimatedTotal: Double? = null,
        marketName: String? = null
    ): String = buildString {
        append("*").append(title.trim()).append("*")
        if (!subtitle.isNullOrBlank()) append("\n").append(subtitle.trim())
        if (lines.isEmpty()) {
            append("\n\n(lista vazia)")
        }
        lines.groupBy { it.category.trim().ifBlank { "Outros" } }
            .entries.sortedBy { it.key.lowercase() }
            .forEach { (category, items) ->
                append("\n\n*").append(category).append("*")
                items.sortedWith(compareBy<Line> { it.checked }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                    .forEach { line ->
                        append("\n").append(if (line.checked) "☑" else "☐").append(" ")
                        append(line.name.trim())
                        append(" — ").append(quantity(line.quantity))
                        if (line.unit.isNotBlank()) append(" ").append(line.unit.trim())
                    }
            }
        if (estimatedTotal != null && estimatedTotal > 0.0) {
            append("\n\n*Total estimado:* ").append(money(estimatedTotal))
            if (!marketName.isNullOrBlank()) append(" (").append(marketName.trim()).append(")")
        }
        val done = lines.count { it.checked }
        if (lines.isNotEmpty()) {
            append("\n").append(done).append(" de ").append(lines.size).append(" itens marcados")
        }
        append("\n\n_Enviado pelo SmartMarket_")
    }
}
