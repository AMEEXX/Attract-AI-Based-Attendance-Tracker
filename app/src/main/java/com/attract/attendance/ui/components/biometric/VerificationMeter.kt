package com.attract.attendance.ui.components.biometric

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.attract.attendance.ui.theme.BiometricIndigo
import com.attract.attendance.ui.theme.BiometricSuccess

/**
 * Clean multi-segment progress indicator for adaptive verification checks.
 * Shows progress across observations without misleading head-turn labels.
 */
@Composable
fun VerificationMeter(
    completedChecks: Int,
    totalBudget: Int = 2,
    modifier: Modifier = Modifier
) {
    if (completedChecks <= 0) return

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(totalBudget) { index ->
                val isCompleted = index < completedChecks
                val isCurrent = index == completedChecks

                val segmentColor by animateColorAsState(
                    targetValue = when {
                        isCompleted -> BiometricSuccess
                        isCurrent -> BiometricIndigo
                        else -> Color.White.copy(alpha = 0.15f)
                    },
                    label = "segmentColor"
                )

                Box(
                    modifier = Modifier
                        .width(42.dp)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(segmentColor)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Check $completedChecks of $totalBudget",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.7f)
        )
    }
}
