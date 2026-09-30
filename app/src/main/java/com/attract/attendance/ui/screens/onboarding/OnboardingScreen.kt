package com.attract.attendance.ui.screens.onboarding

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.attract.attendance.ui.components.AttractCard
import com.attract.attendance.ui.components.AttractPrimaryButton
import com.attract.attendance.ui.theme.AppThemeMode
import com.attract.attendance.ui.theme.Dimens
import com.attract.attendance.ui.theme.Shapes

import androidx.compose.ui.unit.sp

@Composable
fun OnboardingScreen(
    currentThemeMode: AppThemeMode,
    onComplete: (name: String, pin: String, themeMode: AppThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    var step by remember { mutableStateOf(2) }
    var selectedThemeMode by remember { mutableStateOf(currentThemeMode) }
    var teacherName by remember { mutableStateOf("") }
    var pinInput by remember { mutableStateOf("") }
    var pinConfirmInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Dimens.ScreenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "ATTRACT",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 2.sp
        )
        Text(
            text = "Face-based attendance made simple.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(Dimens.SectionGap))

        AttractCard(
            modifier = Modifier.fillMaxWidth(0.9f)
        ) {
            when (step) {
                1 -> {
                    // Step 1: Appearance Selection
                    Text(
                        text = "Choose your appearance",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(Dimens.MediumGap))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.MediumGap)
                    ) {
                        ThemeOptionBox(
                            title = "Light",
                            icon = Icons.Default.LightMode,
                            isSelected = selectedThemeMode == AppThemeMode.LIGHT,
                            onClick = { selectedThemeMode = AppThemeMode.LIGHT },
                            modifier = Modifier.weight(1f)
                        )
                        ThemeOptionBox(
                            title = "Dark",
                            icon = Icons.Default.DarkMode,
                            isSelected = selectedThemeMode == AppThemeMode.DARK,
                            onClick = { selectedThemeMode = AppThemeMode.DARK },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(Dimens.LargeGap))
                    AttractPrimaryButton(
                        onClick = { step = 2 },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Continue")
                    }
                }
                2 -> {
                    // Step 2: Teacher Profile & Security PIN Setup
                    Text(
                        text = "Set up your Profile",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(Dimens.MediumGap))

                    OutlinedTextField(
                        value = teacherName,
                        onValueChange = { teacherName = it },
                        label = { Text("Teacher Name") },
                        placeholder = { Text("Prof. Alex Sharma") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(Dimens.MediumGap))

                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = {
                            pinInput = it.filter(Char::isDigit).take(12)
                            pinError = null
                        },
                        label = { Text("Security PIN (4–12 digits)") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(Dimens.SmallGap))

                    OutlinedTextField(
                        value = pinConfirmInput,
                        onValueChange = {
                            pinConfirmInput = it.filter(Char::isDigit).take(12)
                            pinError = null
                        },
                        label = { Text("Confirm Security PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        isError = pinError != null,
                        supportingText = { pinError?.let { Text(it, color = MaterialTheme.colorScheme.error) } },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(Dimens.LargeGap))

                    AttractPrimaryButton(
                        onClick = {
                            if (teacherName.isBlank()) {
                                pinError = "Please enter your name."
                            } else if (pinInput.length < 4) {
                                pinError = "PIN must be at least 4 digits."
                            } else if (pinInput != pinConfirmInput) {
                                pinError = "PINs do not match."
                            } else {
                                onComplete(teacherName.trim(), pinInput, selectedThemeMode)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Complete Setup")
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeOptionBox(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
    val containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface

    Box(
        modifier = modifier
            .clip(Shapes.card)
            .background(containerColor)
            .border(2.dp, borderColor, Shapes.card)
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp, horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
