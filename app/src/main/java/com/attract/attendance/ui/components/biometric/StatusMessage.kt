package com.attract.attendance.ui.components.biometric

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.attract.attendance.ui.theme.BiometricError
import com.attract.attendance.ui.theme.BiometricSuccess
import com.attract.attendance.ui.theme.BiometricTextPrimary
import com.attract.attendance.ui.theme.BiometricWarning

enum class StatusMessageType {
    INFO, SUCCESS, WARNING, ERROR
}

@Composable
fun StatusMessage(
    message: String,
    type: StatusMessageType = StatusMessageType.INFO,
    modifier: Modifier = Modifier
) {
    val textColor = when (type) {
        StatusMessageType.INFO -> BiometricTextPrimary
        StatusMessageType.SUCCESS -> BiometricSuccess
        StatusMessageType.WARNING -> BiometricWarning
        StatusMessageType.ERROR -> BiometricError
    }

    Text(
        text = message,
        style = MaterialTheme.typography.bodyLarge,
        color = textColor,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
        modifier = modifier.padding(horizontal = 24.dp)
    )
}
