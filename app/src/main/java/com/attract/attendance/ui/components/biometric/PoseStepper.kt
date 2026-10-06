package com.attract.attendance.ui.components.biometric

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Face
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.attract.attendance.ui.theme.BiometricIndigo
import com.attract.attendance.ui.theme.BiometricSuccess

data class PoseStep(
    val title: String,
    val instruction: String,
)

private val ENROLLMENT_STEPS = listOf(
    PoseStep("Straight", "Look straight at the camera"),
    PoseStep("Left", "Slowly turn your head left"),
    PoseStep("Right", "Now turn your head right"),
)

/**
 * 3-pose biometric enrollment stepper with animated chips, directional glyphs, and connectors.
 * Used exclusively for biometric enrollment (standalone & inline).
 */
@Composable
fun PoseStepper(
    completedCount: Int,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ENROLLMENT_STEPS.forEachIndexed { index, step ->
                val isDone = index < completedCount
                val isCurrent = index == completedCount

                val chipScale = if (isCurrent) pulseScale else 1f
                val chipBg by animateColorAsState(
                    targetValue = when {
                        isDone -> BiometricSuccess
                        isCurrent -> BiometricIndigo
                        else -> Color(0xFF1C1C26)
                    },
                    label = "chipBg"
                )

                val chipBorderColor by animateColorAsState(
                    targetValue = when {
                        isDone -> BiometricSuccess
                        isCurrent -> BiometricIndigo.copy(alpha = 0.8f)
                        else -> Color.White.copy(alpha = 0.15f)
                    },
                    label = "chipBorder"
                )

                // Pose Chip
                Box(
                    modifier = Modifier
                        .scale(chipScale)
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(chipBg)
                        .border(1.5.dp, chipBorderColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (isDone) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Completed",
                            tint = Color.Black,
                            modifier = Modifier.size(22.dp)
                        )
                    } else {
                        val icon = when (index) {
                            0 -> Icons.Default.Face
                            1 -> Icons.AutoMirrored.Filled.ArrowBack
                            else -> Icons.AutoMirrored.Filled.ArrowForward
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = step.title,
                            tint = if (isCurrent) Color.White else Color.White.copy(alpha = 0.4f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Connector line between chips
                if (index < ENROLLMENT_STEPS.size - 1) {
                    val connectorColor by animateColorAsState(
                        targetValue = if (index < completedCount) BiometricSuccess else Color.White.copy(alpha = 0.12f),
                        label = "connectorColor"
                    )
                    Box(
                        modifier = Modifier
                            .width(28.dp)
                            .height(2.dp)
                            .background(connectorColor)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Step Label & Instruction
        val currentStep = ENROLLMENT_STEPS.getOrNull(completedCount)
        if (currentStep != null) {
            Text(
                text = "${currentStep.title} Profile · ${currentStep.instruction}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.85f)
            )
        } else {
            Text(
                text = "All profiles captured successfully",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = BiometricSuccess
            )
        }
    }
}
