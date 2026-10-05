package com.attract.attendance.ui.screens.attendance

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.attract.attendance.core.model.RollNumberComparator
import com.attract.attendance.feature.app.ClassWorkspace
import com.attract.attendance.ui.components.AttractCard
import com.attract.attendance.ui.components.AttractPrimaryButton
import com.attract.attendance.ui.theme.Dimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualAttendanceScreen(
    workspace: ClassWorkspace?,
    working: Boolean,
    onBack: () -> Unit,
    onSave: (Set<Long>) -> Unit,
    modifier: Modifier = Modifier
) {
    if (workspace == null) return

    var presentIds by remember(workspace.students) { mutableStateOf(workspace.students.map { it.id }.toSet()) }
    val sortedStudents = remember(workspace.students) {
        workspace.students.sortedWith { a, b -> RollNumberComparator.compare(a.rollNumber, b.rollNumber) }
    }

    var showZeroConfirmDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Manual Attendance", fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 8.dp) {
                Column(modifier = Modifier.padding(Dimens.ScreenPadding)) {
                    AttractPrimaryButton(
                        onClick = {
                            if (presentIds.isEmpty()) {
                                showZeroConfirmDialog = true
                            } else {
                                onSave(presentIds)
                            }
                        },
                        enabled = !working,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save Attendance (${presentIds.size} Present)", fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        if (showZeroConfirmDialog) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showZeroConfirmDialog = false },
                title = { Text("No Students Present", fontWeight = FontWeight.Bold) },
                text = { Text("No students marked present. Save this session anyway?") },
                confirmButton = {
                    androidx.compose.material3.Button(
                        onClick = {
                            showZeroConfirmDialog = false
                            onSave(emptySet())
                        }
                    ) {
                        Text("Save anyway")
                    }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { showZeroConfirmDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(Dimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.SmallGap)
        ) {
            item {
                Text(
                    text = "Tap a student row to toggle between Present and Absent.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            items(sortedStudents, key = { it.id }) { student ->
                val checked = student.id in presentIds
                AttractCard(
                    onClick = {
                        presentIds = if (checked) presentIds - student.id else presentIds + student.id
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { isChecked ->
                                presentIds = if (isChecked) presentIds + student.id else presentIds - student.id
                            }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = student.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = student.rollNumber,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
