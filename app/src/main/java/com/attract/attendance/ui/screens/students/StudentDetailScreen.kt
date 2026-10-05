package com.attract.attendance.ui.screens.students

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Face
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import com.attract.attendance.core.model.EnrollmentStatus
import com.attract.attendance.core.model.StudentSummary
import com.attract.attendance.ui.components.AttractCard
import com.attract.attendance.ui.components.AttractOutlinedButton
import com.attract.attendance.ui.components.StatCard
import com.attract.attendance.ui.theme.AttractBlue
import com.attract.attendance.ui.theme.Dimens
import com.attract.attendance.ui.theme.Shapes
import com.attract.attendance.ui.theme.SuccessGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentDetailScreen(
    student: StudentSummary,
    className: String,
    presentSessions: Int = 0,
    totalSessions: Int = 0,
    onBack: () -> Unit,
    onReEnroll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isEnrolled = student.enrollmentStatus == EnrollmentStatus.ENROLLED
    var showReEnrollDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(student.name, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        if (showReEnrollDialog) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showReEnrollDialog = false },
                title = { Text("Re-register Face Data", fontWeight = FontWeight.Bold) },
                text = { Text("Re-register ${student.name}'s face? This will delete their existing face data.") },
                confirmButton = {
                    androidx.compose.material3.Button(
                        onClick = {
                            showReEnrollDialog = false
                            onReEnroll()
                        }
                    ) {
                        Text("Continue")
                    }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { showReEnrollDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(Dimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.MediumGap)
        ) {
            // Header Profile Card
            item {
                AttractCard {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = student.rollNumber,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = className,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Attendance Stat Summary
            item {
                AttractCard {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Attendance",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        val absentSessions = (totalSessions - presentSessions).coerceAtLeast(0)
                        val attendancePercent = if (totalSessions > 0) {
                            (presentSessions * 100) / totalSessions
                        } else 0
                        val attendanceProgress = if (totalSessions > 0) {
                            (presentSessions.toFloat() / totalSessions.toFloat()).coerceIn(0f, 1f)
                        } else 0f

                        Text(
                            text = if (totalSessions == 0) "No classes held" else "$attendancePercent%",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (totalSessions == 0) MaterialTheme.colorScheme.onSurfaceVariant else if (attendancePercent >= 75) SuccessGreen else MaterialTheme.colorScheme.error
                        )
                        LinearProgressIndicator(
                            progress = { attendanceProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(CircleShape),
                            color = if (attendancePercent >= 75) SuccessGreen else MaterialTheme.colorScheme.error,
                            trackColor = MaterialTheme.colorScheme.surface
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("$presentSessions Present", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text("$absentSessions Absent", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // Face Biometric Enrollment Card
            item {
                AttractCard {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Face Enrollment",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = when (student.enrollmentStatus) {
                                        EnrollmentStatus.ENROLLED -> "Biometrics Encrypted & Registered"
                                        EnrollmentStatus.REENROLL_REQUIRED -> "Re-enrollment Required (Pipeline Upgrade)"
                                        EnrollmentStatus.NOT_ENROLLED -> "Not enrolled yet"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (student.enrollmentStatus == EnrollmentStatus.REENROLL_REQUIRED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when (student.enrollmentStatus) {
                                            EnrollmentStatus.ENROLLED -> SuccessGreen
                                            EnrollmentStatus.REENROLL_REQUIRED -> MaterialTheme.colorScheme.error
                                            EnrollmentStatus.NOT_ENROLLED -> MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                            )
                        }

                        val isEnrolled = student.enrollmentStatus == EnrollmentStatus.ENROLLED
                        AttractOutlinedButton(
                            onClick = {
                                if (isEnrolled) {
                                    showReEnrollDialog = true
                                } else {
                                    onReEnroll()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Face, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(
                                when (student.enrollmentStatus) {
                                    EnrollmentStatus.ENROLLED -> "Re-enroll face biometrics"
                                    EnrollmentStatus.REENROLL_REQUIRED -> "Repair / Re-enroll face"
                                    EnrollmentStatus.NOT_ENROLLED -> "Enroll face biometrics"
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
