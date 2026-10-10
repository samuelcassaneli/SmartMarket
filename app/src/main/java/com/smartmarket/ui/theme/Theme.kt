package com.smartmarket.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Paleta Elegante e Moderna: Esmeralda Financeiro, Teal e Acentos Suaves
val EmeraldPrimary = Color(0xFF0F766E)      // Teal 700 elegante
val EmeraldDark = Color(0xFF115E59)         // Teal 800
val EmeraldLight = Color(0xFF14B8A6)        // Teal 500
val EmeraldContainer = Color(0xFFCCFBF1)    // Teal 100 suave

val CoralAccent = Color(0xFFEA580C)         // Laranja vibrante para alertas e tags
val AmberWarning = Color(0xFFD97706)        // Dourado para economias/troféus

val Slate900 = Color(0xFF0F172A)
val Slate800 = Color(0xFF1E293B)
val Slate700 = Color(0xFF334155)
val Slate100 = Color(0xFFF1F5F9)
val Slate50 = Color(0xFFF8FAFC)

private val LightColorScheme = lightColorScheme(
    primary = EmeraldPrimary,
    onPrimary = Color.White,
    primaryContainer = EmeraldContainer,
    onPrimaryContainer = EmeraldDark,
    secondary = EmeraldLight,
    onSecondary = Color.White,
    tertiary = CoralAccent,
    background = Slate50,
    surface = Color.White,
    onBackground = Slate900,
    onSurface = Slate900,
    surfaceVariant = Color(0xFFEEF2F6),
    onSurfaceVariant = Slate700,
    outline = Color(0xFFCBD5E1)
)

private val DarkColorScheme = darkColorScheme(
    primary = EmeraldLight,
    onPrimary = Slate900,
    primaryContainer = EmeraldDark,
    onPrimaryContainer = EmeraldContainer,
    secondary = EmeraldPrimary,
    onSecondary = Color.White,
    tertiary = CoralAccent,
    background = Slate900,
    surface = Slate800,
    onBackground = Color.White,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1E293B),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF475569)
)

@Composable
fun SmartMarketTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
