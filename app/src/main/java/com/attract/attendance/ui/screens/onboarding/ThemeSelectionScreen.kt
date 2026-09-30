package com.attract.attendance.ui.screens.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.attract.attendance.ui.components.AttractPrimaryButton
import com.attract.attendance.ui.theme.AppThemeMode

@Composable
fun ThemeSelectionScreen(
    onSelectTheme: (AppThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTheme by remember { mutableStateOf<AppThemeMode?>(null) }
    val isPreviewDark = selectedTheme == AppThemeMode.DARK

    val backgroundColor by animateColorAsState(
        targetValue = if (isPreviewDark) Color(0xFF0A0A0E) else Color(0xFFFAFAFB),
        animationSpec = com.attract.attendance.ui.theme.AttractMotion.springSmooth(),
        label = "backgroundColor"
    )
    val textColor by animateColorAsState(
        targetValue = if (isPreviewDark) Color(0xFFF4F4F8) else Color(0xFF14141B),
        animationSpec = com.attract.attendance.ui.theme.AttractMotion.springSmooth(),
        label = "textColor"
    )
    val subtextColor by animateColorAsState(
        targetValue = if (isPreviewDark) Color(0xFF9C9CAC) else Color(0xFF6E6E7C),
        animationSpec = com.attract.attendance.ui.theme.AttractMotion.springSmooth(),
        label = "subtextColor"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 80.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "How should Attract look?",
                style = MaterialTheme.typography.displayLarge,
                color = textColor,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Pick a style — you can change this anytime in Settings.",
                style = MaterialTheme.typography.bodyMedium,
                color = subtextColor,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(36.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ThemeCard(
                    title = "Light",
                    isDarkPreview = false,
                    isSelected = selectedTheme == AppThemeMode.LIGHT,
                    onSelect = { selectedTheme = AppThemeMode.LIGHT },
                    modifier = Modifier.weight(1f)
                )
                ThemeCard(
                    title = "Dark",
                    isDarkPreview = true,
                    isSelected = selectedTheme == AppThemeMode.DARK,
                    onSelect = { selectedTheme = AppThemeMode.DARK },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        AttractPrimaryButton(
            onClick = { selectedTheme?.let(onSelectTheme) },
            enabled = selectedTheme != null,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .align(Alignment.BottomCenter)
        ) {
            Text(
                text = "Continue",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
private fun ThemeCard(
    title: String,
    isDarkPreview: Boolean,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.0f else 0.96f,
        animationSpec = com.attract.attendance.ui.theme.AttractMotion.snappy,
        label = "cardScale"
    )

    val cardBg = if (isDarkPreview) Color(0xFF14141B) else Color(0xFFFFFFFF)
    val cardBorderColor by animateColorAsState(
        targetValue = if (isSelected) Color(0xFF4F46E5) else if (isDarkPreview) Color(0xFF2A2A38) else Color(0xFFE7E7EF),
        animationSpec = com.attract.attendance.ui.theme.AttractMotion.springSnappy(),
        label = "cardBorderColor"
    )
    val cardTextColor = if (isDarkPreview) Color(0xFFF4F4F8) else Color(0xFF14141B)

    Column(
        modifier = modifier
            .scale(scale)
            .border(
                width = if (isSelected) 2.5.dp else 1.dp,
                color = cardBorderColor,
                shape = RoundedCornerShape(24.dp)
            )
            .background(cardBg, RoundedCornerShape(24.dp))
            .clickable { onSelect() }
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Mini Dashboard Preview Window
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .background(
                    color = if (isDarkPreview) Color(0xFF0A0A0E) else Color(0xFFFAFAFB),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Mini Header bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(16.dp)
                        .background(
                            if (isDarkPreview) Color(0xFF8B85F5) else Color(0xFF4F46E5),
                            RoundedCornerShape(4.dp)
                        )
                ) {}
                // Mini Card 1
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .background(
                            if (isDarkPreview) Color(0xFF1C1C26) else Color(0xFFFFFFFF),
                            RoundedCornerShape(8.dp)
                        )
                )
                // Mini Card 2
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .background(
                            if (isDarkPreview) Color(0xFF1C1C26) else Color(0xFFFFFFFF),
                            RoundedCornerShape(8.dp)
                        )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = cardTextColor,
                fontWeight = FontWeight.Bold
            )
            if (isSelected) {
                Spacer(modifier = Modifier.size(8.dp))
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(Color(0xFF4F46E5), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}
