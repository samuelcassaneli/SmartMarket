package com.smartmarket.ui.charts

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.smartmarket.ui.components.Fmt
import com.smartmarket.ui.theme.extended
import java.text.NumberFormat
import java.util.Locale

/** Formatação compacta para rótulos de gráfico. */
object ChartFormat {
    private val PT_BR = Locale("pt", "BR")

    /** 950 -> "950"; 1234 -> "1,2 mil"; 12500 -> "12,5 mil" */
    fun compactNumber(value: Double): String {
        val nf = NumberFormat.getNumberInstance(PT_BR)
        return when {
            value >= 1_000_000 -> nf.apply { maximumFractionDigits = 1 }.format(value / 1_000_000) + " mi"
            value >= 1_000 -> nf.apply { maximumFractionDigits = 1 }.format(value / 1_000) + " mil"
            value >= 100 -> nf.apply { maximumFractionDigits = 0 }.format(value)
            else -> nf.apply { maximumFractionDigits = 2; minimumFractionDigits = 0 }.format(value)
        }
    }

    /** "R$ 1,2 mil" / "R$ 950" */
    fun compactMoney(value: Double): String = "R$ " + compactNumber(value)

    /** Preço curto para eixos: "R$ 4,99" */
    fun axisMoney(value: Double): String = Fmt.money(value)
}

/**
 * Paleta categórica para gráficos de participação. Cores distintas entre si,
 * com uma versão para fundo claro e outra para fundo escuro.
 * A primeira é sempre o acento do app.
 */
@Composable
fun categoricalColors(): List<Color> {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f
    val ext = MaterialTheme.extended
    return if (dark) {
        listOf(scheme.primary, scheme.tertiary, ext.warning, Color(0xFF38BDF8), Color(0xFFF472B6))
    } else {
        listOf(scheme.primary, scheme.tertiary, ext.warning, Color(0xFF0284C7), Color(0xFFDB2777))
    }
}

/** Cor neutra para "Outros". */
@Composable
fun othersColor(): Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)

/**
 * Progresso 0..1 da animação de entrada; reinicia quando [key] muda.
 * Leia `.value` dentro do bloco de desenho para não recompor a cada quadro.
 */
@Composable
fun rememberEntryProgress(key: Any?, durationMillis: Int = 800): Animatable<Float, AnimationVector1D> {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(key) {
        anim.snapTo(0f)
        anim.animateTo(1f, tween(durationMillis, easing = FastOutSlowInEasing))
    }
    return anim
}
