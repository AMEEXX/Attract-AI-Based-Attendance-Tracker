package com.attract.attendance.ui.components.biometric

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.attract.attendance.ui.theme.BiometricError
import com.attract.attendance.ui.theme.BiometricSuccess
import com.attract.attendance.ui.theme.Dimens
import com.attract.attendance.ui.theme.Shapes

enum class FrameState {
    NEUTRAL, SUCCESS, ERROR
}

@Composable
fun FaceFrameOverlay(
    frameState: FrameState = FrameState.NEUTRAL,
    modifier: Modifier = Modifier
) {
    val borderColor = when (frameState) {
        FrameState.NEUTRAL -> Color.White.copy(alpha = 0.8f)
        FrameState.SUCCESS -> BiometricSuccess
        FrameState.ERROR -> BiometricError
    }

    Box(
        modifier = modifier.size(Dimens.CameraViewport)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 3.dp.toPx()
            val bracketLength = 32.dp.toPx()
            val cornerRadius = 20.dp.toPx()
            val size = this.size

            // Top-Left Corner Bracket
            drawPath(
                path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(0f, bracketLength)
                    lineTo(0f, cornerRadius)
                    quadraticTo(0f, 0f, cornerRadius, 0f)
                    lineTo(bracketLength, 0f)
                },
                color = borderColor,
                style = Stroke(width = strokeWidth)
            )

            // Top-Right Corner Bracket
            drawPath(
                path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(size.width - bracketLength, 0f)
                    lineTo(size.width - cornerRadius, 0f)
                    quadraticTo(size.width, 0f, size.width, cornerRadius)
                    lineTo(size.width, bracketLength)
                },
                color = borderColor,
                style = Stroke(width = strokeWidth)
            )

            // Bottom-Left Corner Bracket
            drawPath(
                path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(0f, size.height - bracketLength)
                    lineTo(0f, size.height - cornerRadius)
                    quadraticTo(0f, size.height, cornerRadius, size.height)
                    lineTo(bracketLength, size.height)
                },
                color = borderColor,
                style = Stroke(width = strokeWidth)
            )

            // Bottom-Right Corner Bracket
            drawPath(
                path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(size.width - bracketLength, size.height)
                    lineTo(size.width - cornerRadius, size.height)
                    quadraticTo(size.width, size.height, size.width, size.height - cornerRadius)
                    lineTo(size.width, size.height - bracketLength)
                },
                color = borderColor,
                style = Stroke(width = strokeWidth)
            )
        }
    }
}
