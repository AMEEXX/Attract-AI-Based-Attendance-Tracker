package com.attract.attendance.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

enum class AppThemeMode {
    LIGHT, DARK, SYSTEM
}

private val LightScheme = lightColorScheme(
    primary = LightColors.primary,
    onPrimary = LightColors.onPrimary,
    primaryContainer = LightColors.primaryContainer,
    onPrimaryContainer = LightColors.onPrimaryContainer,
    background = LightColors.background,
    onBackground = LightColors.onBackground,
    surface = LightColors.surface,
    onSurface = LightColors.onSurface,
    surfaceVariant = LightColors.surfaceVariant,
    onSurfaceVariant = LightColors.onSurfaceVariant,
    outline = LightColors.outline,
    error = LightColors.error,
    onError = LightColors.onError,
    errorContainer = LightColors.errorContainer,
    onErrorContainer = LightColors.onErrorContainer,
)

private val DarkScheme = darkColorScheme(
    primary = DarkColors.primary,
    onPrimary = DarkColors.onPrimary,
    primaryContainer = DarkColors.primaryContainer,
    onPrimaryContainer = DarkColors.onPrimaryContainer,
    background = DarkColors.background,
    onBackground = DarkColors.onBackground,
    surface = DarkColors.surface,
    onSurface = DarkColors.onSurface,
    surfaceVariant = DarkColors.surfaceVariant,
    onSurfaceVariant = DarkColors.onSurfaceVariant,
    outline = DarkColors.outline,
    error = DarkColors.error,
    onError = DarkColors.onError,
    errorContainer = DarkColors.errorContainer,
    onErrorContainer = DarkColors.onErrorContainer,
)

private val BiometricColorScheme = darkColorScheme(
    primary = CameraScreenColors.glowIndigo,
    onPrimary = Color.Black,
    secondary = CameraScreenColors.glowEmerald,
    background = CameraScreenColors.background,
    onBackground = CameraScreenColors.onBackground,
    surface = CameraScreenColors.surfaceRaised,
    onSurface = CameraScreenColors.onBackground,
    surfaceVariant = Color(0xFF1F1F29),
    onSurfaceVariant = Color(0xFF9C9CAC),
    error = CameraScreenColors.glowCrimson,
)

data class AttractExtraColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val coral: Color,
    val onCoral: Color,
)

private fun extraColorsFor(dark: Boolean) = if (dark) {
    AttractExtraColors(
        success = DarkColors.success, onSuccess = DarkColors.onSuccess,
        successContainer = DarkColors.successContainer, onSuccessContainer = DarkColors.onSuccessContainer,
        warning = DarkColors.warning, onWarning = DarkColors.onWarning,
        warningContainer = DarkColors.warningContainer, onWarningContainer = DarkColors.onWarningContainer,
        coral = DarkColors.coral, onCoral = DarkColors.onCoral,
    )
} else {
    AttractExtraColors(
        success = LightColors.success, onSuccess = LightColors.onSuccess,
        successContainer = LightColors.successContainer, onSuccessContainer = LightColors.onSuccessContainer,
        warning = LightColors.warning, onWarning = LightColors.onWarning,
        warningContainer = LightColors.warningContainer, onWarningContainer = LightColors.onWarningContainer,
        coral = LightColors.coral, onCoral = LightColors.onCoral,
    )
}

val LocalAttractExtraColors = staticCompositionLocalOf { extraColorsFor(dark = false) }

object AttractTheme {
    val extraColors: AttractExtraColors
        @Composable get() = LocalAttractExtraColors.current
}

@Composable
fun AttractTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    isBiometricWorld: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val darkTheme = when {
        isBiometricWorld -> true
        themeMode == AppThemeMode.LIGHT -> false
        themeMode == AppThemeMode.DARK -> true
        else -> isSystemInDarkTheme()
    }

    val colorScheme = when {
        isBiometricWorld -> BiometricColorScheme
        darkTheme -> DarkScheme
        else -> LightScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalAttractExtraColors provides extraColorsFor(darkTheme)) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = MaterialShapes,
            content = content
        )
    }
}

@Composable
fun AttractAppTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    AttractTheme(
        themeMode = if (darkTheme) AppThemeMode.DARK else AppThemeMode.LIGHT,
        content = content
    )
}
