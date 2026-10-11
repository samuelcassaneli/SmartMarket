package com.smartmarket.ui.charts

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.smartmarket.ui.components.Fmt
import com.smartmarket.ui.theme.Spacing

/** Fatia de um gráfico de participação. */
data class ShareSlice(val label: String, val value: Double, val color: Color)

/**
 * Agrupa em top [top] + "Outros", atribuindo as cores da paleta categórica.
 */
@Composable
fun topSlices(items: List<Pair<String, Double>>, top: Int = 5): List<ShareSlice> {
    val palette = categoricalColors()
    val others = othersColor()
    val sorted = items.filter { it.second > 0 }.sortedByDescending { it.second }
    val head = sorted.take(top).mapIndexed { i, (label, v) -> ShareSlice(label, v, palette[i % palette.size]) }
    val restTotal = sorted.drop(top).sumOf { it.second }
    return if (restTotal > 0) head + ShareSlice("Outros", restTotal, others) else head
}

/** Rosca animada; o centro mostra [centerLabel] e [centerValue]. */
@Composable
fun DonutChart(
    slices: List<ShareSlice>,
    centerLabel: String,
    centerValue: String,
    modifier: Modifier = Modifier,
    diameter: Dp = 136.dp,
    thickness: Dp = 16.dp
) {
    val progress = rememberEntryProgress(slices, durationMillis = 900)
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    val total = slices.sumOf { it.value }
    Box(modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(diameter)) {
            val stroke = thickness.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)
            drawArc(track, 0f, 360f, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(stroke))
            if (total <= 0) return@Canvas
            val gap = if (slices.size > 1) 2.5f else 0f
            var start = -90f
            val p = progress.value
            slices.forEach { s ->
                val sweep = (s.value / total * 360.0).toFloat()
                val drawn = ((sweep - gap) * p).coerceAtLeast(0f)
                if (drawn > 0f) {
                    drawArc(
                        s.color, start + gap / 2f, drawn, useCenter = false,
                        topLeft = topLeft, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Butt)
                    )
                }
                start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(centerLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(centerValue, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

/** Rosca + legenda com percentual e valor. */
@Composable
fun DonutWithLegend(
    slices: List<ShareSlice>,
    centerLabel: String,
    modifier: Modifier = Modifier,
    valueFormatter: (Double) -> String = Fmt::money
) {
    val total = slices.sumOf { it.value }
    val description = "Gráfico de rosca. " + slices.joinToString("; ") {
        "${it.label}: ${valueFormatter(it.value)}, ${Fmt.percent(if (total > 0) it.value / total * 100 else 0.0, withSign = false)}"
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically
    ) {
        DonutChart(slices, centerLabel = centerLabel, centerValue = ChartFormat.compactMoney(total))
        Spacer(Modifier.width(Spacing.lg))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            slices.forEach { s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(s.color))
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        s.label,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(Spacing.xs))
                    Text(
                        Fmt.percent(if (total > 0) s.value / total * 100 else 0.0, withSign = false),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** Barras horizontais de participação: rótulo, valor e barra proporcional na cor do item. */
@Composable
fun ShareBars(
    slices: List<ShareSlice>,
    modifier: Modifier = Modifier,
    valueFormatter: (Double) -> String = Fmt::money
) {
    val max = slices.maxOfOrNull { it.value }?.takeIf { it > 0 } ?: 1.0
    val total = slices.sumOf { it.value }
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    var started by remember(slices) { mutableStateOf(false) }
    LaunchedEffect(slices) { started = true }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "Barras de participação. " + slices.joinToString("; ") { "${it.label}: ${valueFormatter(it.value)}" }
            },
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        slices.forEach { s ->
            val fraction by animateFloatAsState(
                targetValue = if (started) (s.value / max).toFloat() else 0f,
                animationSpec = tween(800, easing = FastOutSlowInEasing),
                label = "share"
            )
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        s.label,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (total > 0) {
                        Text(
                            Fmt.percent(s.value / total * 100, withSign = false),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(Spacing.sm))
                    }
                    Text(valueFormatter(s.value), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(track)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(fraction.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(s.color)
                    )
                }
            }
        }
    }
}
