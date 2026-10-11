package com.smartmarket.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.smartmarket.ui.components.Fmt
import kotlin.math.min

/** Uma barra: rótulo (ex.: "Out"), valor e se é o destaque (mês atual). */
data class BarEntry(val label: String, val value: Double, val highlighted: Boolean = false)

/**
 * Gráfico de colunas com rótulo de valor em cima de cada barra e rótulo embaixo.
 * A barra destacada usa o acento; as demais, o acento suave.
 */
@Composable
fun BarChart(
    entries: List<BarEntry>,
    modifier: Modifier = Modifier,
    height: Dp = 180.dp,
    valueFormatter: (Double) -> String = ChartFormat::compactNumber,
    contentDescription: String? = null
) {
    val scheme = MaterialTheme.colorScheme
    val textMeasurer = rememberTextMeasurer()
    val progress = rememberEntryProgress(entries)

    val base = MaterialTheme.typography.labelSmall
    val labelStyle = base.copy(color = scheme.onSurfaceVariant)
    val labelStrong = base.copy(color = scheme.onSurface, fontWeight = FontWeight.Bold)
    val valueStyle = base.copy(color = scheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
    val valueStrong = base.copy(color = scheme.primary, fontWeight = FontWeight.Bold)
    val barColor = scheme.primary
    val barSoft = scheme.primary.copy(alpha = 0.28f)
    val baseline = scheme.outlineVariant

    val description = contentDescription
        ?: entries.joinToString(prefix = "Gráfico de barras. ", separator = "; ") { "${it.label}: ${Fmt.money(it.value)}" }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics { this.contentDescription = description }
    ) {
        if (entries.isEmpty()) return@Canvas
        val p = progress.value
        val maxValue = entries.maxOf { it.value }.coerceAtLeast(0.01)

        val labelH = textMeasurer.measure("Mg", labelStyle).size.height
        val valueH = textMeasurer.measure("Mg", valueStyle).size.height
        val top = valueH + 6.dp.toPx()
        val bottom = size.height - labelH - 8.dp.toPx()
        val chartH = (bottom - top).coerceAtLeast(1f)
        val slot = size.width / entries.size
        val barW = min(slot * 0.56f, 40.dp.toPx())
        val radius = min(8.dp.toPx(), barW / 2f)

        drawLine(baseline, Offset(0f, bottom), Offset(size.width, bottom), strokeWidth = 1.dp.toPx())

        entries.forEachIndexed { i, e ->
            val cx = slot * i + slot / 2f
            val h = (e.value / maxValue).toFloat() * chartH * p
            if (h > 0.5f) {
                drawTopRoundedBar(
                    color = if (e.highlighted) barColor else barSoft,
                    left = cx - barW / 2f, top = bottom - h, width = barW, height = h, radius = radius
                )
            }
            if (e.value > 0) {
                val layout = textMeasurer.measure(valueFormatter(e.value), if (e.highlighted) valueStrong else valueStyle)
                val x = (cx - layout.size.width / 2f).coerceIn(0f, (size.width - layout.size.width).coerceAtLeast(0f))
                drawText(layout, topLeft = Offset(x, bottom - h - layout.size.height - 4.dp.toPx()), alpha = p)
            }
            val label = textMeasurer.measure(e.label, if (e.highlighted) labelStrong else labelStyle)
            drawText(label, topLeft = Offset(cx - label.size.width / 2f, bottom + 6.dp.toPx()))
        }
    }
}

/** Barra com cantos de cima arredondados e base reta (um único Path, sem sobreposição de alfa). */
private fun DrawScope.drawTopRoundedBar(
    color: Color,
    left: Float, top: Float, width: Float, height: Float, radius: Float
) {
    val r = min(radius, height)
    val path = Path().apply {
        addRoundRect(
            RoundRect(
                left = left, top = top, right = left + width, bottom = top + height,
                topLeftCornerRadius = CornerRadius(r, r),
                topRightCornerRadius = CornerRadius(r, r),
                bottomRightCornerRadius = CornerRadius.Zero,
                bottomLeftCornerRadius = CornerRadius.Zero
            )
        )
    }
    drawPath(path, color)
}
