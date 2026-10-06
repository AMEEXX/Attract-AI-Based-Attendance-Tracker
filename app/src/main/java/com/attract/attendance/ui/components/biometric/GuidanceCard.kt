package com.attract.attendance.ui.components.biometric

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.attract.attendance.feature.attendance.SessionScreenState
import com.attract.attendance.ui.theme.BiometricError
import com.attract.attendance.ui.theme.BiometricIndigo
import com.attract.attendance.ui.theme.BiometricSuccess
import com.attract.attendance.ui.theme.BiometricSurface
import com.attract.attendance.ui.theme.BiometricTextPrimary
import com.attract.attendance.ui.theme.BiometricWarning

/**
 * Raised guidance card presenting clear title and subtitle guidance with an accent bar and icon.
 * Replaces the status pill for a premium, accessible biometric interface.
 */
@Composable
fun GuidanceCard(
    state: SessionScreenState,
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
) {
    val accentColor = when (state) {
        SessionScreenState.MATCH_SUCCESS -> BiometricSuccess
        SessionScreenState.ALREADY_PRESENT -> BiometricWarning
        SessionScreenState.CAPTURING -> BiometricWarning
        SessionScreenState.UNKNOWN_STUDENT, SessionScreenState.ERROR -> BiometricError
        SessionScreenState.TEACHER_ASSIST -> BiometricIndigo
        SessionScreenState.AUTO_ENDED -> BiometricWarning
        SessionScreenState.PROCESSING, SessionScreenState.READY, SessionScreenState.FRAMES_COLLECTED -> BiometricIndigo
    }

    val icon: ImageVector? = when (state) {
        SessionScreenState.MATCH_SUCCESS -> Icons.Default.CheckCircle
        SessionScreenState.ALREADY_PRESENT -> Icons.Default.Info
        SessionScreenState.CAPTURING -> Icons.Default.Face
        SessionScreenState.UNKNOWN_STUDENT -> Icons.AutoMirrored.Filled.HelpOutline
        SessionScreenState.ERROR -> Icons.Default.Warning
        SessionScreenState.TEACHER_ASSIST, SessionScreenState.AUTO_ENDED -> Icons.Default.Lock
        SessionScreenState.READY, SessionScreenState.FRAMES_COLLECTED -> Icons.Default.Face
        SessionScreenState.PROCESSING -> null
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(BiometricSurface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left 4dp accent bar
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accentColor)
            )

            Spacer(modifier = Modifier.width(14.dp))

            // State Icon or Progress
            Box(
                modifier = Modifier.size(28.dp),
                contentAlignment = Alignment.Center
            ) {
                if (state == SessionScreenState.PROCESSING) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.5.dp,
                        color = accentColor
                    )
                } else if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Text Content with AnimatedContent
            AnimatedContent(
                targetState = title to subtitle,
                transitionSpec = {
                    (fadeIn() + slideInVertically { it / 3 }) togetherWith
                        (fadeOut() + slideOutVertically { -it / 3 })
                },
                label = "GuidanceCardContent",
                modifier = Modifier.weight(1f)
            ) { (currentTitle, currentSubtitle) ->
                Column {
                    Text(
                        text = currentTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = BiometricTextPrimary
                    )
                    if (!currentSubtitle.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = currentSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.65f)
                        )
                    }
                }
            }
        }
    }
}
