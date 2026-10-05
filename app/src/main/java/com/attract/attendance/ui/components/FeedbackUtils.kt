package com.attract.attendance.ui.components

import android.view.SoundEffectConstants
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role

/**
 * Returns a wrapped onClick lambda that triggers device click sound effect
 * and subtle haptic feedback before invoking the original [onClick].
 */
@Composable
fun rememberFeedbackClick(onClick: () -> Unit): () -> Unit {
    val view = LocalView.current
    val haptic = LocalHapticFeedback.current
    return remember(onClick, view, haptic) {
        {
            try {
                view.playSoundEffect(SoundEffectConstants.CLICK)
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            } catch (_: Throwable) {
                // Ignore any sound effect / haptic exceptions
            }
            onClick()
        }
    }
}

/**
 * Modifier extension that plays click sound and haptic feedback on touch.
 */
fun Modifier.feedbackClickable(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    onClick: () -> Unit
): Modifier = composed {
    val feedbackClick = rememberFeedbackClick(onClick)
    this.clickable(
        enabled = enabled,
        onClickLabel = onClickLabel,
        role = role,
        onClick = feedbackClick
    )
}
