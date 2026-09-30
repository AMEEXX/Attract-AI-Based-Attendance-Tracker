package com.attract.attendance.ui.components.biometric

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.attract.attendance.ui.theme.BiometricSuccess
import com.attract.attendance.ui.theme.Dimens

@Composable
fun PoseProgress(
    completedCount: Int,
    modifier: Modifier = Modifier
) {
    val labels = listOf("Straight", "Left", "Right")

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(3) { index ->
                val isCompleted = index < completedCount
                val isCurrent = index == completedCount

                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isCompleted -> BiometricSuccess
                                isCurrent -> Color.White
                                else -> Color(0xFF444444)
                            }
                        )
                )

                if (index < 2) {
                    Box(
                        modifier = Modifier
                            .width(24.dp)
                            .height(2.dp)
                            .background(
                                if (index < completedCount) BiometricSuccess.copy(alpha = 0.5f)
                                else Color(0xFF333333)
                            )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            labels.forEachIndexed { index, label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (index <= completedCount) Color.White.copy(alpha = 0.9f) else Color.Gray
                )
            }
        }
    }
}
