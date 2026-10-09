package com.attract.attendance.ui.screens.history

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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import com.attract.attendance.core.model.AttendanceStatus
import com.attract.attendance.core.model.RollNumberComparator
import com.attract.attendance.core.model.SessionMode
import com.attract.attendance.feature.app.SessionHistory
import com.attract.attendance.ui.components.AttractCard
import com.attract.attendance.ui.components.AttractIconButton
import com.attract.attendance.ui.components.AttractTextButton
import com.attract.attendance.ui.components.feedbackClickable
import com.attract.attendance.ui.theme.AttractBlue
import com.attract.attendance.ui.theme.Dimens
import com.attract.attendance.ui.theme.Shapes
import com.attract.attendance.ui.theme.SuccessGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionHistoryScreen(
    history: SessionHistory?,
    working: Boolean,
    onBack: () -> Unit,
    onCorrect: (sessionId: Long, studentId: Long, newStatus: AttendanceStatus) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var lastValidHistory by remember { mutableStateOf(history) }
    if (history != null) {
        lastValidHistory = history
    }
    val history = history ?: lastValidHistory ?: return

    var showDeleteConfirm by remember { mutableStateOf(false) }
    val sortedRows = remember(history.rows) {
        history.rows.sortedWith { a, b -> RollNumberComparator.compare(a.first.rollNumber, b.first.rollNumber) }
    }

    val presentCount = history.rows.count { it.second == AttendanceStatus.PRESENT }
    val absentCount = history.rows.count { it.second == AttendanceStatus.ABSENT }
    val totalCount = history.rows.size
    val rate = if (totalCount > 0) (presentCount * 100 / totalCount) else 0

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Session #${history.session.id}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = history.session.sessionDate,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    AttractIconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    AttractIconButton(
                        onClick = { showDeleteConfirm = true },
                        enabled = !working
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete Session",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(Dimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.MediumGap)
        ) {
            // 1. Summary Header Card with 3 Stat Tiles
            item {
                AttractCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Mode Chip & Date
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val (modeIcon, modeText, modeColor) = when (history.session.mode) {
                                SessionMode.FACE -> Triple(Icons.Default.CameraAlt, "AI Face Verification", AttractBlue)
                                SessionMode.MANUAL -> Triple(Icons.Default.Edit, "Manual Attendance", MaterialTheme.colorScheme.primary)
                                else -> Triple(Icons.Default.CameraAlt, "Assisted Session", MaterialTheme.colorScheme.secondary)
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .clip(Shapes.pill)
                                    .background(modeColor.copy(alpha = 0.12f))
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Icon(modeIcon, contentDescription = null, tint = modeColor, modifier = Modifier.size(14.dp))
                                Text(modeText, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = modeColor)
                            }

                            Text(
                                text = history.session.sessionDate,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // 3 Stat Tiles: Present / Absent / Rate
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            StatTile(
                                label = "Present",
                                value = "$presentCount",
                                color = SuccessGreen,
                                modifier = Modifier.weight(1f)
                            )
                            StatTile(
                                label = "Absent",
                                value = "$absentCount",
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.weight(1f)
                            )
                            StatTile(
                                label = "Rate",
                                value = "$rate%",
                                color = AttractBlue,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Correction Hint Banner
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Tap any student's status chip to correct attendance.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 2. Student Attendance Rows
            items(sortedRows, key = { it.first.id }) { (student, status) ->
                val isPresent = status == AttendanceStatus.PRESENT

                AttractCard(
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

                        // Student Info
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

                        // Redesigned Tonal Status Chip with Tap-to-Correct
                        val (chipText, chipIcon, chipColor, chipBg) = if (isPresent) {
                            Quadruple("PRESENT", Icons.Default.Check, SuccessGreen, SuccessGreen.copy(alpha = 0.15f))
                        } else {
                            Quadruple("ABSENT", Icons.Default.Close, MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.error.copy(alpha = 0.12f))
                        }

                        Surface(
                            shape = Shapes.pill,
                            color = chipBg,
                            modifier = Modifier
                                .clip(Shapes.pill)
                                .feedbackClickable {
                                    val nextStatus = if (isPresent) AttendanceStatus.ABSENT else AttendanceStatus.PRESENT
                                    onCorrect(history.session.id, student.id, nextStatus)
                                }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(chipIcon, contentDescription = null, tint = chipColor, modifier = Modifier.size(13.dp))
                                Text(
                                    text = chipText,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = chipColor
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text("Delete Attendance Session?", fontWeight = FontWeight.Bold) },
                text = { Text("This will permanently remove attendance records for this session. This action cannot be undone.") },
                confirmButton = {
                    AttractTextButton(
                        onClick = {
                            showDeleteConfirm = false
                            onDelete()
                        }
                    ) {
                        Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    AttractTextButton(onClick = { showDeleteConfirm = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun StatTile(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.08f),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
