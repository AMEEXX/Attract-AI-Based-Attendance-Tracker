package com.attract.attendance.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddStudentBottomSheet(
    onDismissRequest: () -> Unit,
    onAddStudent: (name: String, rollNumber: String, serialNumber: String?) -> Unit,
    duplicateRollError: String? = null,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var nameInput by remember { mutableStateOf("") }
    var rollInput by remember { mutableStateOf("") }
    var serialInput by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    val isSaveEnabled = nameInput.isNotBlank() && rollInput.isNotBlank()
    val displayRollError = duplicateRollError ?: localError

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Add Student",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            OutlinedTextField(
                value = nameInput,
                onValueChange = {
                    nameInput = it
                },
                label = { Text("Full Name *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = rollInput,
                onValueChange = {
                    rollInput = it
                    localError = null
                },
                label = { Text("Roll Number *") },
                isError = displayRollError != null,
                supportingText = {
                    displayRollError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = serialInput,
                onValueChange = { serialInput = it },
                label = { Text("Serial Number (Optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismissRequest) {
                    Text("Cancel")
                }
                Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                androidx.compose.material3.Button(
                    onClick = {
                        if (isSaveEnabled) {
                            onAddStudent(nameInput.trim(), rollInput.trim(), serialInput.trim().ifBlank { null })
                        }
                    },
                    enabled = isSaveEnabled
                ) {
                    Text("Save Student")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
