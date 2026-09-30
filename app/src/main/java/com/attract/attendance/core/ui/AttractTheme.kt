package com.attract.attendance.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val TealHeader = Color(0xFF00897B)
val TealDark = Color(0xFF00695C)
val SalmonRed = Color(0xFFFF5252)
val LightTealContainer = Color(0xFFE0F2F1)

private val LightColors = lightColorScheme(
    primary = TealHeader,
    onPrimary = Color.White,
    primaryContainer = LightTealContainer,
    onPrimaryContainer = Color(0xFF003730),
    secondary = SalmonRed,
    onSecondary = Color.White,
    tertiary = Color(0xFFFF6B6B),
    surface = Color.White,
    surfaceVariant = Color(0xFFF4F6F6),
    error = Color(0xFFD32F2F),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF4DB6AC),
    onPrimary = Color(0xFF003730),
    primaryContainer = Color(0xFF004D40),
    secondary = Color(0xFFFF8A80),
)

@Composable
fun AttractTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = MaterialTheme.typography,
        content = content,
    )
}
