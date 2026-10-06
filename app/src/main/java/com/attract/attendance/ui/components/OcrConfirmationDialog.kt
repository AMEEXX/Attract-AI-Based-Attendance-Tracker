package com.attract.attendance.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.attract.attendance.ui.components.AttractIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.attract.attendance.ui.theme.Dimens
import com.attract.attendance.ui.theme.Shapes
import com.attract.attendance.ui.theme.SuccessGreen

data class EditableOcrStudent(
    val id: Int,
    var isSelected: Boolean = true,
    var name: String,
    var rollNumber: String,
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
                        rollNumber = student.rollNumber
                    )
                }
            )
        }
    }

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
                        Text(
                            text = "$selectedCount of ${studentItems.size} selected to add",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    AttractIconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Verify roll numbers and names extracted from the sheet. You can edit any typo before adding.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Student list
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(studentItems, key = { _, item -> item.id }) { index, item ->
                        var isChecked by remember { mutableStateOf(item.isSelected) }
                        var nameText by remember { mutableStateOf(item.name) }
                        var rollText by remember { mutableStateOf(item.rollNumber) }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(Shapes.card)
                                .background(
                                    if (isChecked) MaterialTheme.colorScheme.surfaceVariant
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                )
                                .border(
                                    1.dp,
                                    if (isChecked) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                    Shapes.card
                                )
                                .padding(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        isChecked = checked
                                        item.isSelected = checked
                                    }
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    OutlinedTextField(
                                        value = rollText,
                                        onValueChange = {
                                            rollText = it.uppercase()
                                            item.rollNumber = it.uppercase()
                                        },
                                        label = { Text("Roll No") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    Spacer(modifier = Modifier.height(6.dp))

                                    OutlinedTextField(
                                        value = nameText,
                                        onValueChange = {
                                            nameText = it
                                            item.name = it
                                        },
                                        label = { Text("Student Name") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                AttractIconButton(
                                    onClick = { studentItems.removeAt(index) },
                                    modifier = Modifier.padding(start = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Remove entry",
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                                    )
                                }
                            }
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
