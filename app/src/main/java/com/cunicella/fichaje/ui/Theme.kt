package com.cunicella.fichaje.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

/** Colores de estado que Material no trae: en turno, en pausa. Los mismos del prototipo. */
data class StateColors(val on: Color, val onBg: Color, val warn: Color, val warnBg: Color, val muted: Color)

private val DarkStates = StateColors(Color(0xFF4FCB98), Color(0xFF123327), Color(0xFFF2A54E), Color(0xFF382813), Color(0xFF94A1B3))

val LocalStateColors = staticCompositionLocalOf { DarkStates }

val Mono = FontFamily.Monospace

private val Dark = darkColorScheme(
    primary = Color(0xFF86A0FF), onPrimary = Color(0xFF0A1024), primaryContainer = Color(0xFF1F2A4D),
    onPrimaryContainer = Color(0xFFDCE3FB),
    background = Color(0xFF0D1219), onBackground = Color(0xFFE6EBF2),
    surface = Color(0xFF151C26), onSurface = Color(0xFFE6EBF2),
    surfaceVariant = Color(0xFF1E2733), onSurfaceVariant = Color(0xFF94A1B3),
    outline = Color(0xFF29333F), outlineVariant = Color(0xFF29333F), error = Color(0xFFFF8075),
)

/** La app es siempre oscura, también si el teléfono está en modo claro. */
@Composable
fun FichajeTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalStateColors provides DarkStates) {
        MaterialTheme(colorScheme = Dark) {
            Surface(
                color = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onBackground,
                content = content,
            )
        }
    }
}
