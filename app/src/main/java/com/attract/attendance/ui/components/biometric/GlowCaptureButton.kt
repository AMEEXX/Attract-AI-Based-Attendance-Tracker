package com.attract.attendance.ui.components.biometric

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.attract.attendance.ui.theme.AttractMotion
import com.attract.attendance.ui.theme.CameraScreenColors

enum class GlowButtonState {
    READY,
    SUBMIT,
    SUCCESS,
    WARNING,
    ERROR
}

@Composable
fun GlowCaptureButton(
    buttonState: GlowButtonState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "breathingGlow")
    val breathingAlpha by infiniteTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alphaPulse"
    )

    val targetGlowColor = when (buttonState) {
        GlowButtonState.READY -> CameraScreenColors.glowIndigo
        GlowButtonState.SUBMIT, GlowButtonState.SUCCESS -> CameraScreenColors.glowEmerald
        GlowButtonState.WARNING -> CameraScreenColors.glowAmber
        GlowButtonState.ERROR -> CameraScreenColors.glowCrimson
    }

    val animatedGlowColor by animateColorAsState(
        targetValue = targetGlowColor,
        animationSpec = AttractMotion.springHero(),
        label = "glowColor"
    )

    val isBreathing = buttonState == GlowButtonState.READY
    val currentAlpha = if (isBreathing) breathingAlpha else 0.75f

    Box(
        modifier = modifier.size(120.dp),
        contentAlignment = Alignment.Center
    ) {
        // Radial Gradient Halo Glow
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            animatedGlowColor.copy(alpha = currentAlpha * 0.6f),
                            animatedGlowColor.copy(alpha = currentAlpha * 0.2f),
                            Color.Transparent
                        )
                    )
                )
        )

        // 72dp Hero Circular Button Core
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(animatedGlowColor)
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (buttonState == GlowButtonState.SUBMIT || buttonState == GlowButtonState.SUCCESS) Icons.Default.Check else Icons.Default.CameraAlt,
                contentDescription = "Capture / Submit",
                tint = Color.Black,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}
