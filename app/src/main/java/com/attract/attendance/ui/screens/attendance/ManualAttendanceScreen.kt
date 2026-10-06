package com.attract.attendance.ui.screens.attendance

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.RemoveDone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.attract.attendance.core.model.RollNumberComparator
import com.attract.attendance.feature.app.ClassWorkspace
import com.attract.attendance.ui.components.AttractCard
import com.attract.attendance.ui.components.AttractIconButton
import com.attract.attendance.ui.components.AttractOutlinedButton
import com.attract.attendance.ui.components.AttractPrimaryButton
import com.attract.attendance.ui.components.AttractTextButton
import com.attract.attendance.ui.components.SearchField
import com.attract.attendance.ui.theme.Dimens
import com.attract.attendance.ui.theme.Shapes
import com.attract.attendance.ui.theme.SuccessGreen
import java.time.LocalDate
import java.time.format.DateTimeFormatter

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

    val totalStudents = workspace.students.size
    var presentIds by remember(workspace.students) { mutableStateOf(workspace.students.map { it.id }.toSet()) }
    var searchQuery by remember { mutableStateOf("") }
    var showZeroConfirmDialog by remember { mutableStateOf(false) }

    val sortedStudents = remember(workspace.students) {
        workspace.students.sortedWith { a, b -> RollNumberComparator.compare(a.rollNumber, b.rollNumber) }
    }

    val filteredStudents = remember(sortedStudents, searchQuery) {
        if (searchQuery.isBlank()) sortedStudents
        else sortedStudents.filter {
            it.name.contains(searchQuery, ignoreCase = true) || it.rollNumber.contains(searchQuery, ignoreCase = true)
        }
    }

    val presentCount = presentIds.size
    val progress = if (totalStudents > 0) presentCount.toFloat() / totalStudents.toFloat() else 0f
    val todayFormatted = remember { LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy")) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Manual Attendance", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text(workspace.summary.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    AttractIconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 8.dp) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimens.ScreenPadding, vertical = 12.dp)
                ) {
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
                        Text(
                            "Save Attendance ($presentCount of $totalStudents Present)",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        if (showZeroConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showZeroConfirmDialog = false },
                title = { Text("No Students Present", fontWeight = FontWeight.Bold) },
                text = { Text("No students are marked present. Save this session anyway?") },
                confirmButton = {
                    AttractPrimaryButton(
                        onClick = {
                            showZeroConfirmDialog = false
                            onSave(emptySet())
                        }
                    ) {
                        Text("Save anyway")
                    }
                },
                dismissButton = {
                    AttractTextButton(onClick = { showZeroConfirmDialog = false }) {
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
            verticalArrangement = Arrangement.spacedBy(Dimens.MediumGap)
        ) {
            // 1. Hero Header Card
            item {
                AttractCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = todayFormatted,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "$presentCount of $totalStudents Present",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Surface(
                                shape = CircleShape,
                                color = SuccessGreen.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "${(progress * 100).toInt()}%",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = SuccessGreen,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }

                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = SuccessGreen,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }

            // 2. Search & Quick Actions
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SearchField(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        placeholder = "Search by name or roll number...",
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AttractOutlinedButton(
                            onClick = { presentIds = workspace.students.map { it.id }.toSet() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Mark All Present", style = MaterialTheme.typography.labelMedium)
                        }

                        AttractOutlinedButton(
                            onClick = { presentIds = emptySet() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.RemoveDone, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Clear All", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            // 3. Student List with Segmented Pill Toggle
            items(filteredStudents, key = { it.id }) { student ->
                val isPresent = student.id in presentIds

                val pillBg by animateColorAsState(
                    targetValue = if (isPresent) SuccessGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    label = "pillBg"
                )
                val pillColor by animateColorAsState(
                    targetValue = if (isPresent) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "pillColor"
                )

                AttractCard(
                    onClick = {
                        presentIds = if (isPresent) presentIds - student.id else presentIds + student.id
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Avatar Circle
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = student.name.take(1).uppercase(),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Info
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = student.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Roll: ${student.rollNumber}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Segmented Present/Absent Pill
                        Box(
                            modifier = Modifier
                                .clip(Shapes.pill)
                                .background(pillBg)
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (isPresent) Icons.Default.Check else Icons.Default.Close,
                                    contentDescription = null,
                                    tint = pillColor,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = if (isPresent) "PRESENT" else "ABSENT",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = pillColor
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
