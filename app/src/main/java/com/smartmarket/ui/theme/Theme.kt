package com.smartmarket.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Paleta "clean fintech": neutros slate + um único acento esmeralda.
private val Emerald50 = Color(0xFFECFDF5)
private val Emerald100 = Color(0xFFD1FAE5)
private val Emerald300 = Color(0xFF6EE7B7)
private val Emerald400 = Color(0xFF34D399)
private val Emerald600 = Color(0xFF059669)
private val Emerald700 = Color(0xFF047857)
private val Emerald900 = Color(0xFF064E3B)

private val Slate50 = Color(0xFFF8FAFC)
private val Slate100 = Color(0xFFF1F5F9)
private val Slate200 = Color(0xFFE2E8F0)
private val Slate300 = Color(0xFFCBD5E1)
private val Slate400 = Color(0xFF94A3B8)
private val Slate500 = Color(0xFF64748B)
private val Slate600 = Color(0xFF475569)
private val Slate700 = Color(0xFF334155)
private val Slate800 = Color(0xFF1E293B)
private val Slate850 = Color(0xFF172033)
private val Slate900 = Color(0xFF0F172A)
private val Slate950 = Color(0xFF080D19)

private val LightColorScheme = lightColorScheme(
    primary = Emerald600,
    onPrimary = Color.White,
    primaryContainer = Emerald100,
    onPrimaryContainer = Emerald900,
    secondary = Slate700,
    onSecondary = Color.White,
    secondaryContainer = Slate100,
    onSecondaryContainer = Slate900,
    tertiary = Color(0xFF6366F1),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE0E7FF),
    onTertiaryContainer = Color(0xFF312E81),
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),
    background = Slate50,
    onBackground = Slate900,
    surface = Slate50,
    onSurface = Slate900,
    surfaceVariant = Slate100,
    onSurfaceVariant = Slate500,
    surfaceTint = Color.Transparent,
    outline = Slate300,
    outlineVariant = Slate200,
    inverseSurface = Slate900,
    inverseOnSurface = Slate50,
    inversePrimary = Emerald300,
    surfaceBright = Color.White,
    surfaceDim = Slate200,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Slate100,
    surfaceContainerHigh = Slate200,
    surfaceContainerHighest = Slate300,
    scrim = Color.Black
)

private val DarkColorScheme = darkColorScheme(
    primary = Emerald400,
    onPrimary = Emerald900,
    primaryContainer = Emerald700,
    onPrimaryContainer = Emerald50,
    secondary = Slate300,
    onSecondary = Slate900,
    secondaryContainer = Slate800,
    onSecondaryContainer = Slate100,
    tertiary = Color(0xFFA5B4FC),
    onTertiary = Color(0xFF1E1B4B),
    tertiaryContainer = Color(0xFF3730A3),
    onTertiaryContainer = Color(0xFFE0E7FF),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFEE2E2),
    background = Slate950,
    onBackground = Slate100,
    surface = Slate950,
    onSurface = Slate100,
    surfaceVariant = Slate800,
    onSurfaceVariant = Slate400,
    surfaceTint = Color.Transparent,
    outline = Slate600,
    outlineVariant = Slate800,
    inverseSurface = Slate100,
    inverseOnSurface = Slate900,
    inversePrimary = Emerald600,
    surfaceBright = Slate800,
    surfaceDim = Slate950,
    surfaceContainerLowest = Slate950,
    surfaceContainerLow = Slate900,
    surfaceContainer = Slate850,
    surfaceContainerHigh = Slate800,
    surfaceContainerHighest = Slate700,
    scrim = Color.Black
)

/** Cores semânticas extras (ganho/perda, alerta) que o Material não cobre. */
@Immutable
data class ExtendedColors(
    val positive: Color,
    val onPositiveContainer: Color,
    val positiveContainer: Color,
    val negative: Color,
    val negativeContainer: Color,
    val onNegativeContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color
)

private val LightExtended = ExtendedColors(
    positive = Color(0xFF059669),
    positiveContainer = Color(0xFFD1FAE5),
    onPositiveContainer = Color(0xFF064E3B),
    negative = Color(0xFFDC2626),
    negativeContainer = Color(0xFFFEE2E2),
    onNegativeContainer = Color(0xFF7F1D1D),
    warning = Color(0xFFD97706),
    warningContainer = Color(0xFFFEF3C7),
    onWarningContainer = Color(0xFF78350F)
)

private val DarkExtended = ExtendedColors(
    positive = Color(0xFF34D399),
    positiveContainer = Color(0xFF064E3B),
    onPositiveContainer = Color(0xFFD1FAE5),
    negative = Color(0xFFF87171),
    negativeContainer = Color(0xFF450A0A),
    onNegativeContainer = Color(0xFFFEE2E2),
    warning = Color(0xFFFBBF24),
    warningContainer = Color(0xFF451A03),
    onWarningContainer = Color(0xFFFEF3C7)
)

val LocalExtendedColors = staticCompositionLocalOf { LightExtended }

/** Acesso: `MaterialTheme.extended.positive` */
val MaterialTheme.extended: ExtendedColors
    @Composable get() = LocalExtendedColors.current

@Composable
fun SmartMarketTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val extended = if (darkTheme) DarkExtended else LightExtended

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalExtendedColors provides extended) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = Shapes,
            content = content
        )
    }
}
