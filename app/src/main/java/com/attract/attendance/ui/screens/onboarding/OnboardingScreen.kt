package com.attract.attendance.ui.screens.onboarding

import android.content.Intent
import android.provider.Settings
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.attract.attendance.ui.components.AttractCard
import com.attract.attendance.ui.components.AttractOutlinedButton
import com.attract.attendance.ui.components.AttractPrimaryButton
import com.attract.attendance.ui.components.feedbackClickable
import com.attract.attendance.ui.theme.AppThemeMode
import com.attract.attendance.ui.theme.Dimens
import com.attract.attendance.ui.theme.Shapes
import com.attract.attendance.ui.theme.SuccessGreen

@Composable
fun OnboardingScreen(
    currentThemeMode: AppThemeMode,
    onComplete: (name: String, pin: String, themeMode: AppThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val fragmentActivity = context as? FragmentActivity
    var step by remember { mutableIntStateOf(2) }
    var selectedThemeMode by remember { mutableStateOf(currentThemeMode) }
    var teacherName by remember { mutableStateOf("") }
    var pinInput by remember { mutableStateOf("") }
    var pinConfirmInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }
    var isFingerprintVerified by remember { mutableStateOf(false) }
    var fingerprintStatusMessage by remember { mutableStateOf<String?>(null) }

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
            modifier = Modifier.fillMaxWidth(0.92f)
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
                        text = "Step 1 of 2: Profile & PIN",
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
                        label = { Text("Fallback Security PIN (4–12 digits)") },
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
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (pinError != null) {
                        Spacer(modifier = Modifier.height(Dimens.SmallGap))
                        Text(
                            text = pinError.orEmpty(),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

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
                                val bioManager = BiometricManager.from(context)
                                val canAuth = bioManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                                if (canAuth == BiometricManager.BIOMETRIC_SUCCESS ||
                                    canAuth == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED
                                ) {
                                    step = 3
                                } else {
                                    // Device has no fingerprint sensor
                                    onComplete(teacherName.trim(), pinInput, selectedThemeMode)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Continue to Fingerprint Setup")
                    }
                }
                3 -> {
                    // Step 3: Compulsory Fingerprint Enrollment Verification
                    val bioManager = remember { BiometricManager.from(context) }
                    val canAuth = remember { bioManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) }

                    Text(
                        text = "Step 2 of 2: Register Fingerprint",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Your fingerprint is required to quickly exit pinned attendance sessions and prevent unauthorized student exit.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(Dimens.LargeGap))

                    if (canAuth == BiometricManager.BIOMETRIC_SUCCESS) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isFingerprintVerified) SuccessGreen.copy(alpha = 0.15f)
                                        else MaterialTheme.colorScheme.primaryContainer
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isFingerprintVerified) Icons.Default.CheckCircle else Icons.Default.Fingerprint,
                                    contentDescription = null,
                                    tint = if (isFingerprintVerified) SuccessGreen else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = if (isFingerprintVerified) "Fingerprint Verified & Linked" else "Scan your fingerprint to register",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isFingerprintVerified) SuccessGreen else MaterialTheme.colorScheme.onSurface
                            )
                            if (fingerprintStatusMessage != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = fingerprintStatusMessage.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(Dimens.LargeGap))

                        if (!isFingerprintVerified) {
                            AttractPrimaryButton(
                                onClick = {
                                    if (fragmentActivity != null) {
                                        val executor = ContextCompat.getMainExecutor(context)
                                        val prompt = BiometricPrompt(
                                            fragmentActivity,
                                            executor,
                                            object : BiometricPrompt.AuthenticationCallback() {
                                                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                                    isFingerprintVerified = true
                                                    fingerprintStatusMessage = null
                                                }
                                                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                                                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                                                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON
                                                    ) {
                                                        fingerprintStatusMessage = errString.toString()
                                                    }
                                                }
                                                override fun onAuthenticationFailed() {
                                                    fingerprintStatusMessage = "Fingerprint not recognized. Please try again."
                                                }
                                            }
                                        )
                                        val promptInfo = BiometricPrompt.PromptInfo.Builder()
                                            .setTitle("Teacher Fingerprint Registration")
                                            .setSubtitle("Scan your fingerprint to verify for session exit")
                                            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                                            .setNegativeButtonText("Cancel")
                                            .build()
                                        prompt.authenticate(promptInfo)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Fingerprint, contentDescription = null)
                                Spacer(modifier = Modifier.size(8.dp))
                                Text("Scan Fingerprint")
                            }
                        } else {
                            AttractPrimaryButton(
                                onClick = {
                                    onComplete(teacherName.trim(), pinInput, selectedThemeMode)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Complete Setup")
                            }
                        }
                    } else if (canAuth == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No Fingerprint Enrolled in Android",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "To use fingerprint exit, please add a fingerprint in Android Settings. Or continue using your secure PIN.",
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(Dimens.MediumGap))
                            AttractOutlinedButton(
                                onClick = {
                                    try {
                                        context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
                                    } catch (_: Exception) {}
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Open Android Security Settings")
                            }
                            Spacer(modifier = Modifier.height(Dimens.SmallGap))
                            AttractPrimaryButton(
                                onClick = {
                                    onComplete(teacherName.trim(), pinInput, selectedThemeMode)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Continue with PIN Only")
                            }
                        }
                    } else {
                        // Hardware unavailable
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "No fingerprint hardware detected on this device. Your PIN will be used for session exit.",
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(Dimens.MediumGap))
                            AttractPrimaryButton(
                                onClick = {
                                    onComplete(teacherName.trim(), pinInput, selectedThemeMode)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Complete Setup (PIN Only)")
                            }
                        }
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
            .feedbackClickable(onClick = onClick)
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
