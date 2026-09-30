package com.attract.attendance.ui.components.biometric

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.attract.attendance.ui.theme.Dimens
import com.attract.attendance.ui.theme.Shapes

enum class FaceCaptureMode {
    ATTENDANCE, ENROLLMENT
}

@Composable
fun FaceCaptureView(
    mode: FaceCaptureMode,
    frameState: FrameState,
    cameraPreview: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(Dimens.CameraViewport)
            .clip(Shapes.cameraViewport)
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        // Camera Preview Content Layer
        cameraPreview()

        // Reactive Corner Brackets Overlay
        FaceFrameOverlay(frameState = frameState)
    }
}
