package com.attract.attendance.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.attract.attendance.core.model.RosterStudent
import com.attract.attendance.ui.theme.Shapes
import com.attract.attendance.ui.theme.WarningAmber

data class EditableOcrStudent(
    val id: Int,
    val isSelected: Boolean = true,
    val name: String,
    val rollNumber: String,
    val isUncertain: Boolean = false,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrConfirmationDialog(
    initialStudents: List<RosterStudent>,
    onConfirm: (List<RosterStudent>) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val studentItems = remember(initialStudents) {
        mutableStateListOf<EditableOcrStudent>().apply {
            addAll(
                initialStudents.mapIndexed { index, student ->
                    EditableOcrStudent(
                        id = index,
                        isSelected = true,
                        name = student.name,
                        rollNumber = student.rollNumber,
                        isUncertain = student.needsReview || student.rollNumber.contains('?') || student.name.isBlank()
                    )
                }
            )
        }
    }

    var editingStudentId by remember { mutableStateOf<Int?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f)
                .clip(Shapes.card),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Review OCR Students",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        val selectedCount = studentItems.count { it.isSelected }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "$selectedCount of ${studentItems.size} selected",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )

                            if (studentItems.isNotEmpty()) {
                                val allSelected = studentItems.all { it.isSelected }
                                AttractTextButton(
                                    onClick = {
                                        val nextSelected = !allSelected
                                        for (i in studentItems.indices) {
                                            studentItems[i] = studentItems[i].copy(isSelected = nextSelected)
                                        }
                                    }
                                ) {
                                    Text(
                                        text = if (allSelected) "Deselect All" else "Select All",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }

                    AttractIconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Verify roll numbers and names extracted from the sheet. Tap any row to edit typos before adding.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                // High-performance Student List
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(studentItems, key = { it.id }) { item ->
                        val isEditing = editingStudentId == item.id
                        if (isEditing) {
                            EditableOcrRow(
                                item = item,
                                onSave = { newRoll, newName ->
                                    val idx = studentItems.indexOfFirst { it.id == item.id }
                                    if (idx != -1) {
                                        studentItems[idx] = studentItems[idx].copy(
                                            rollNumber = newRoll.uppercase(),
                                            name = newName,
                                            isUncertain = newRoll.contains('?') || newName.isBlank()
                                        )
                                    }
                                    editingStudentId = null
                                },
                                onCancel = { editingStudentId = null }
                            )
                        } else {
                            ViewOcrRow(
                                item = item,
                                onToggleSelect = { checked ->
                                    val idx = studentItems.indexOfFirst { it.id == item.id }
                                    if (idx != -1) {
                                        studentItems[idx] = studentItems[idx].copy(isSelected = checked)
                                    }
                                },
                                onStartEdit = { editingStudentId = item.id },
                                onDelete = {
                                    studentItems.removeAll { it.id == item.id }
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Bottom actions
                val confirmedStudents = studentItems.filter { it.isSelected && it.name.isNotBlank() && it.rollNumber.isNotBlank() }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AttractTextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }

                    AttractPrimaryButton(
                        onClick = {
                            val rosterList = confirmedStudents.mapIndexed { idx, item ->
                                RosterStudent(
                                    sourceRow = idx + 1,
                                    name = item.name.trim(),
                                    rollNumber = item.rollNumber.trim(),
                                    serialNumber = (idx + 1).toString()
                                )
                            }
                            onConfirm(rosterList)
                        },
                        enabled = confirmedStudents.isNotEmpty(),
                        modifier = Modifier.weight(2f)
                    ) {
                        Text("Confirm & Add (${confirmedStudents.size})")
                    }
                }
            }
        }
    }
}

/**
 * Ultra-fast, lightweight view row that renders with zero text-field overhead during scroll.
 */
@Composable
private fun ViewOcrRow(
    item: EditableOcrStudent,
    onToggleSelect: (Boolean) -> Unit,
    onStartEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        onClick = onStartEdit,
        modifier = Modifier.fillMaxWidth(),
        shape = Shapes.card,
        color = if (item.isSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(
            1.dp,
            if (item.isSelected) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Checkbox(
                checked = item.isSelected,
                onCheckedChange = onToggleSelect
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = Shapes.pill,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        contentColor = MaterialTheme.colorScheme.primary
                    ) {
                        Text(
                            text = item.rollNumber.ifBlank { "NO ROLL" },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }

                    if (item.isUncertain) {
                        Surface(
                            shape = Shapes.pill,
                            color = WarningAmber.copy(alpha = 0.18f),
                            contentColor = WarningAmber
                        ) {
                            Text(
                                text = "Review",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = item.name.ifBlank { "(Empty name)" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            AttractIconButton(onClick = onStartEdit) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit student",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            AttractIconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Remove entry",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Rendered ONLY when the user taps edit on a specific student.
 */
@Composable
private fun EditableOcrRow(
    item: EditableOcrStudent,
    onSave: (String, String) -> Unit,
    onCancel: () -> Unit
) {
    var rollText by remember(item.id) { mutableStateOf(item.rollNumber) }
    var nameText by remember(item.id) { mutableStateOf(item.name) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = Shapes.card,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            OutlinedTextField(
                value = rollText,
                onValueChange = { rollText = it.uppercase() },
                label = { Text("Roll Number") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = nameText,
                onValueChange = { nameText = it },
                label = { Text("Student Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                AttractTextButton(onClick = onCancel) {
                    Text("Cancel")
                }
                Spacer(modifier = Modifier.width(8.dp))
                AttractPrimaryButton(onClick = { onSave(rollText, nameText) }) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Done")
                }
            }
        }
    }
}
