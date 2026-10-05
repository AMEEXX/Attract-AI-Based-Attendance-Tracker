package com.attract.attendance.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.attract.attendance.ui.theme.Dimens
import com.attract.attendance.ui.theme.Shapes

@Composable
fun AttractPrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val feedbackClick = rememberFeedbackClick(onClick)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.97f else 1.0f, label = "buttonScale")

    Button(
        onClick = feedbackClick,
        modifier = modifier
            .heightIn(min = Dimens.ButtonHeight)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        enabled = enabled,
        shape = Shapes.interactive,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ),
        interactionSource = interactionSource,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
        content = content
    )
}

@Composable
fun AttractOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val feedbackClick = rememberFeedbackClick(onClick)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.97f else 1.0f, label = "buttonScale")

    OutlinedButton(
        onClick = feedbackClick,
        modifier = modifier
            .heightIn(min = Dimens.ButtonHeight)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        enabled = enabled,
        shape = Shapes.interactive,
        interactionSource = interactionSource,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        content = content
    )
}

@Composable
fun AttractTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val feedbackClick = rememberFeedbackClick(onClick)
    TextButton(
        onClick = feedbackClick,
        modifier = modifier,
        enabled = enabled,
        shape = Shapes.interactive,
        content = content
    )
}
