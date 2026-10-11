package com.smartmarket.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.smartmarket.ui.components.Fmt
import com.smartmarket.ui.theme.Spacing
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

/** Ponto de uma série: [x] em millis (data) e [y] o valor. */
data class LinePoint(val x: Long, val y: Double)

/** Uma linha do gráfico (ex.: um mercado), com sua cor. */
data class LineSeries(val name: String, val color: Color, val points: List<LinePoint>)

private const val DAY_MS = 86_400_000L

/**
 * Gráfico de linhas com várias séries, eixo de valores à esquerda, eixo de datas embaixo,
 * pontos marcados e tooltip ao tocar (valor, série e data).
 */
@Composable
fun LineChart(
    series: List<LineSeries>,
    modifier: Modifier = Modifier,
    height: Dp = 220.dp,
    valueFormatter: (Double) -> String = Fmt::money,
    dateFormatter: (Long) -> String = Fmt::dateShort,
    showLegend: Boolean = true,
    contentDescription: String? = null
) {
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val progress = rememberEntryProgress(series)
    val axisStyle = MaterialTheme.typography.labelSmall.copy(color = scheme.onSurfaceVariant)
    val tipTitle = MaterialTheme.typography.labelLarge.copy(color = scheme.inverseOnSurface, fontWeight = FontWeight.Bold)
    val tipBody = MaterialTheme.typography.labelSmall.copy(color = scheme.inverseOnSurface.copy(alpha = 0.85f))
    val gridColor = scheme.outlineVariant
    val tipBg = scheme.inverseSurface
    val pointFill = scheme.surfaceContainerLow

    val visible = series.map { s -> s.copy(points = s.points.sortedBy { it.x }) }.filter { it.points.isNotEmpty() }
    val all = visible.flatMap { it.points }
    if (all.isEmpty()) return

    // ---- Escalas
    var minX = all.minOf { it.x }
    var maxX = all.maxOf { it.x }
    val singleDate = maxX == minX
    if (singleDate) { minX -= DAY_MS; maxX += DAY_MS }
    val rawMin = all.minOf { it.y }
    val rawMax = all.maxOf { it.y }
    val span = (rawMax - rawMin).takeIf { it > 0.0001 } ?: max(rawMax * 0.2, 1.0)
    val yMin = (rawMin - span * 0.2).coerceAtLeast(0.0)
    val yMax = rawMax + span * 0.2
    val ticks = (0..3).map { yMin + (yMax - yMin) * it / 3.0 }
    val tickLabels = ticks.map { textMeasurer.measure(valueFormatter(it), axisStyle) }
    val leftPad = tickLabels.maxOf { it.size.width } + with(density) { 8.dp.toPx() }
    val xLabelH = textMeasurer.measure("00/00", axisStyle).size.height

    var selected by remember(series) { mutableStateOf<Pair<Int, Int>?>(null) }

    fun plotRect(w: Float, h: Float): FloatArray {
        val pad = with(density) { 8.dp.toPx() }
        return floatArrayOf(leftPad, pad, w - pad, h - xLabelH - with(density) { 10.dp.toPx() })
    }
    fun xPx(x: Long, r: FloatArray) = r[0] + ((x - minX).toFloat() / (maxX - minX).toFloat()) * (r[2] - r[0])
    fun yPx(y: Double, r: FloatArray) = r[3] - ((y - yMin) / (yMax - yMin)).toFloat() * (r[3] - r[1])

    val description = contentDescription ?: buildString {
        append("Gráfico de linhas. ")
        visible.forEach { s ->
            append("${s.name}: de ${valueFormatter(s.points.first().y)} em ${dateFormatter(s.points.first().x)} ")
            append("a ${valueFormatter(s.points.last().y)} em ${dateFormatter(s.points.last().x)}. ")
        }
    }

    Column(modifier.fillMaxWidth()) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height)
                .semantics { this.contentDescription = description }
                .pointerInput(visible) {
                    detectTapGestures { tap ->
                        val r = plotRect(size.width.toFloat(), size.height.toFloat())
                        var best: Pair<Int, Int>? = null
                        var bestDist = Float.MAX_VALUE
                        visible.forEachIndexed { si, s ->
                            s.points.forEachIndexed { pi, pt ->
                                val dx = abs(xPx(pt.x, r) - tap.x)
                                val dy = abs(yPx(pt.y, r) - tap.y)
                                val d = hypot(dx, dy * 0.5f) // prioriza a proximidade horizontal
                                if (d < bestDist) { bestDist = d; best = si to pi }
                            }
                        }
                        val limit = 40.dp.toPx()
                        selected = if (bestDist <= limit && best != selected) best else null
                    }
                }
        ) {
            val r = plotRect(size.width, size.height)
            val p = progress.value

            // Grade e eixo Y
            ticks.forEachIndexed { i, t ->
                val y = yPx(t, r)
                drawLine(
                    gridColor, Offset(r[0], y), Offset(r[2], y), strokeWidth = 1.dp.toPx(),
                    pathEffect = if (i == 0) null else PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
                )
                val l = tickLabels[i]
                drawText(l, topLeft = Offset(0f, (y - l.size.height / 2f).coerceIn(0f, size.height - l.size.height)))
            }

            // Eixo X: início, meio e fim (ou só a data única)
            val xs = if (singleDate) listOf(0.5f) else listOf(0f, 0.5f, 1f)
            xs.forEach { f ->
                val millis = minX + ((maxX - minX) * f).toLong()
                val l = textMeasurer.measure(dateFormatter(millis), axisStyle)
                val cx = r[0] + (r[2] - r[0]) * f
                val x = (cx - l.size.width / 2f).coerceIn(r[0], (r[2] - l.size.width).coerceAtLeast(r[0]))
                drawText(l, topLeft = Offset(x, r[3] + 8.dp.toPx()))
            }

            // Linhas e pontos, revelados da esquerda para a direita
            clipRect(left = 0f, top = 0f, right = r[0] + (r[2] - r[0] + 8.dp.toPx()) * p, bottom = size.height) {
                visible.forEach { s ->
                    if (s.points.size > 1) {
                        val path = Path()
                        s.points.forEachIndexed { i, pt ->
                            val x = xPx(pt.x, r); val y = yPx(pt.y, r)
                            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        drawPath(path, s.color, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                    s.points.forEach { pt ->
                        val c = Offset(xPx(pt.x, r), yPx(pt.y, r))
                        drawCircle(s.color, radius = 4.dp.toPx(), center = c)
                        drawCircle(pointFill, radius = 2.dp.toPx(), center = c)
                    }
                }
            }

            // Seleção + tooltip
            val sel = selected
            if (sel != null && sel.first < visible.size && sel.second < visible[sel.first].points.size) {
                val s = visible[sel.first]
                val pt = s.points[sel.second]
                val c = Offset(xPx(pt.x, r), yPx(pt.y, r))
                drawLine(s.color.copy(alpha = 0.5f), Offset(c.x, r[1]), Offset(c.x, r[3]), strokeWidth = 1.dp.toPx())
                drawCircle(s.color, radius = 7.dp.toPx(), center = c)
                drawCircle(pointFill, radius = 3.dp.toPx(), center = c)

                val title = textMeasurer.measure(valueFormatter(pt.y), tipTitle)
                val body = textMeasurer.measure("${s.name} · ${Fmt.date(pt.x)}", tipBody)
                val padH = 10.dp.toPx(); val padV = 6.dp.toPx()
                val w = max(title.size.width, body.size.width) + padH * 2
                val h = title.size.height + body.size.height + padV * 2
                var left = c.x - w / 2f
                left = left.coerceIn(0f, (size.width - w).coerceAtLeast(0f))
                var top = c.y - h - 12.dp.toPx()
                if (top < 0f) top = c.y + 12.dp.toPx()
                drawRoundRect(tipBg, topLeft = Offset(left, top), size = Size(w, h), cornerRadius = CornerRadius(8.dp.toPx()))
                drawText(title, topLeft = Offset(left + padH, top + padV))
                drawText(body, topLeft = Offset(left + padH, top + padV + title.size.height))
            }
        }

        if (showLegend && visible.size > 1) {
            Spacer(Modifier.height(Spacing.sm))
            ChartLegend(visible.map { it.name to it.color })
        }
    }
}

/** Legenda horizontal com bolinhas coloridas. */
@Composable
fun ChartLegend(items: List<Pair<String, Color>>, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        items.forEach { (name, color) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                Spacer(Modifier.width(6.dp))
                Text(name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
