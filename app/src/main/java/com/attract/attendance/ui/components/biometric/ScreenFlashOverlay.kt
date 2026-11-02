package com.attract.attendance.ui.components.biometric

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Low-light screen flash overlay (Doc 20 WP2.4).
 *
 * Flashes a warm-white screen overlay when low-light conditions require illumination.
 * Placed between camera preview and UI HUD elements.
 */
@Composable
fun ScreenFlashOverlay(
    active: Boolean,
    modifier: Modifier = Modifier,
    color: Color = Color(0xFFFFF4E5),
    targetAlpha: Float = 0.85f
) {
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(active) {
        if (active) {
            alpha.animateTo(
                targetValue = targetAlpha,
                animationSpec = tween(durationMillis = 100, easing = LinearEasing)
            )
        } else {
            alpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 150, easing = LinearEasing)
            )
        }
    }

    if (alpha.value > 0.01f) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(color.copy(alpha = alpha.value))
        )
    }
}
