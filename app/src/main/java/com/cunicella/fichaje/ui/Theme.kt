package com.cunicella.fichaje.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

/** Colores de estado que Material no trae: en turno, en pausa. Los mismos del prototipo. */
data class StateColors(val on: Color, val onBg: Color, val warn: Color, val warnBg: Color, val muted: Color)

val LocalStateColors = staticCompositionLocalOf {
    StateColors(Color(0xFF0D7F57), Color(0xFFD8F1E6), Color(0xFFA6570A), Color(0xFFFAE8D2), Color(0xFF5A6779))
}

val Mono = FontFamily.Monospace

private val Light = lightColorScheme(
    primary = Color(0xFF2949C9), onPrimary = Color.White, primaryContainer = Color(0xFFDCE3FB),
    background = Color(0xFFEDF0F5), onBackground = Color(0xFF141D29),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF141D29),
    surfaceVariant = Color(0xFFE2E7EF), onSurfaceVariant = Color(0xFF5A6779),
    outline = Color(0xFFD2D9E3), error = Color(0xFFB8322A),
)
private val Dark = darkColorScheme(
    primary = Color(0xFF86A0FF), onPrimary = Color(0xFF0A1024), primaryContainer = Color(0xFF1F2A4D),
    background = Color(0xFF0D1219), onBackground = Color(0xFFE6EBF2),
    surface = Color(0xFF151C26), onSurface = Color(0xFFE6EBF2),
    surfaceVariant = Color(0xFF1E2733), onSurfaceVariant = Color(0xFF94A1B3),
    outline = Color(0xFF29333F), error = Color(0xFFFF8075),
)

@Composable
fun FichajeTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val states = if (dark) {
        StateColors(Color(0xFF4FCB98), Color(0xFF123327), Color(0xFFF2A54E), Color(0xFF382813), Color(0xFF94A1B3))
    } else {
        LocalStateColors.current
    }
    CompositionLocalProvider(LocalStateColors provides states) {
        MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
    }
}
