package com.attract.attendance.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.attract.attendance.ui.components.AttractCard
import com.attract.attendance.ui.theme.AppThemeMode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.attract.attendance.ui.theme.Dimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentThemeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    onResetBiometricData: ((pin: String, confirmText: String, onResult: (Boolean, String) -> Unit) -> Unit)? = null,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showResetDialog by remember { mutableStateOf(false) }
    var resetPin by remember { mutableStateOf("") }
    var resetConfirmText by remember { mutableStateOf("") }
    var resetErrorMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(Dimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.MediumGap)
        ) {
            // Appearance Theme Section
            item {
                Text(
                    text = "Appearance",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(Dimens.SmallGap))
                AttractCard {
                    Column {
                        ThemeSelectionRow(
                            label = "Light theme",
                            selected = currentThemeMode == AppThemeMode.LIGHT,
                            onClick = { onThemeModeChange(AppThemeMode.LIGHT) }
                        )
                        ThemeSelectionRow(
                            label = "Dark theme",
                            selected = currentThemeMode == AppThemeMode.DARK,
                            onClick = { onThemeModeChange(AppThemeMode.DARK) }
                        )
                        ThemeSelectionRow(
                            label = "System default",
                            selected = currentThemeMode == AppThemeMode.SYSTEM,
                            onClick = { onThemeModeChange(AppThemeMode.SYSTEM) }
                        )
                    }
                }
            }

            // Backup & Data Section
            item {
                Spacer(modifier = Modifier.height(Dimens.SmallGap))
                Text(
                    text = "Data & Backup",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(Dimens.SmallGap))
                AttractCard {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onExportBackup)
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Export Database Backup", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onImportBackup)
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Import Database Backup", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        }
                        if (onResetBiometricData != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showResetDialog = true }
                                    .padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Reset All Face Data (Biometrics)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }

            // About Section
            item {
                Spacer(modifier = Modifier.height(Dimens.SmallGap))
                Text(
                    text = "About",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(Dimens.SmallGap))
                AttractCard {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Attract — Face Based Attendance Tracker", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Version 4.0 (2026 Edition)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Offline-first biometrics & attendance tracking", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        if (showResetDialog) {
            AlertDialog(
                onDismissRequest = {
                    showResetDialog = false
                    resetPin = ""
                    resetConfirmText = ""
                    resetErrorMessage = null
                },
                title = { Text("Reset All Biometric Data") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "This will delete all enrolled face templates, mark all students as NOT_ENROLLED, and cancel active face sessions. Attendance history will be kept.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        OutlinedTextField(
                            value = resetPin,
                            onValueChange = { resetPin = it },
                            label = { Text("Teacher PIN") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = resetConfirmText,
                            onValueChange = { resetConfirmText = it },
                            label = { Text("Type RESET to confirm") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (resetErrorMessage != null) {
                            Text(
                                resetErrorMessage.orEmpty(),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (resetConfirmText.trim() != "RESET") {
                                resetErrorMessage = "Type RESET in all caps to confirm"
                                return@Button
                            }
                            onResetBiometricData?.invoke(resetPin, resetConfirmText) { success, msg ->
                                if (success) {
                                    showResetDialog = false
                                    resetPin = ""
                                    resetConfirmText = ""
                                    resetErrorMessage = null
                                } else {
                                    resetErrorMessage = msg
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Reset Data")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showResetDialog = false
                        resetPin = ""
                        resetConfirmText = ""
                        resetErrorMessage = null
                    }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun ThemeSelectionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        RadioButton(selected = selected, onClick = onClick)
    }
}
