package com.attract.attendance.ui.components.biometric

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.attract.attendance.ui.theme.AttractBlue
import com.attract.attendance.ui.theme.BiometricSuccess
import com.attract.attendance.ui.theme.Dimens
import com.attract.attendance.ui.theme.SuccessGreen

enum class CaptureButtonMode {
    CLICK, SUBMIT, ENROLL, DISABLED
}

@Composable
fun CaptureButton(
    mode: CaptureButtonMode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor by animateColorAsState(
        targetValue = when (mode) {
            CaptureButtonMode.CLICK -> Color.Transparent
            CaptureButtonMode.SUBMIT -> SuccessGreen
            CaptureButtonMode.ENROLL -> AttractBlue
            CaptureButtonMode.DISABLED -> Color(0xFF333333)
        },
        label = "buttonBgColor"
    )

    val borderColor by animateColorAsState(
        targetValue = when (mode) {
            CaptureButtonMode.CLICK -> Color.White
            CaptureButtonMode.SUBMIT -> BiometricSuccess
            CaptureButtonMode.ENROLL -> Color(0xFF8AB4F8)
            CaptureButtonMode.DISABLED -> Color(0xFF555555)
        },
        label = "buttonBorderColor"
    )

    val text = when (mode) {
        CaptureButtonMode.CLICK -> "CLICK"
        CaptureButtonMode.SUBMIT -> "SUBMIT"
        CaptureButtonMode.ENROLL -> "ENROLL"
        CaptureButtonMode.DISABLED -> "WAIT"
    }

    Box(
        modifier = modifier
            .size(Dimens.CaptureButton)
            .clip(CircleShape)
            .background(backgroundColor)
            .border(4.dp, borderColor, CircleShape)
            .clickable(enabled = mode != CaptureButtonMode.DISABLED, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
    }
}
