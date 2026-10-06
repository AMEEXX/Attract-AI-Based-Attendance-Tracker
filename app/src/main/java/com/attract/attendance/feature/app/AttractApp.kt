package com.attract.attendance.feature.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.GroupOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import com.attract.attendance.ui.components.AttractPrimaryButton
import com.attract.attendance.ui.components.AttractOutlinedButton
import com.attract.attendance.ui.components.AttractTextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.attract.attendance.core.model.AttendanceStatus
import com.attract.attendance.core.model.ClassSummary
import com.attract.attendance.core.model.SessionSummary
import com.attract.attendance.core.model.StudentSummary
import com.attract.attendance.ui.theme.AttractTheme
import com.attract.attendance.ui.theme.AppThemeMode
import com.attract.attendance.ui.screens.onboarding.OnboardingScreen
import com.attract.attendance.ui.screens.dashboard.DashboardScreen
import com.attract.attendance.ui.screens.classworkspace.CreateClassScreen
import com.attract.attendance.ui.screens.classworkspace.ClassWorkspaceScreen
import com.attract.attendance.ui.screens.attendance.ManualAttendanceScreen
import com.attract.attendance.ui.screens.history.SessionHistoryScreen
import com.attract.attendance.ui.screens.students.StudentDetailScreen
import com.attract.attendance.ui.screens.settings.SettingsScreen
import com.attract.attendance.ui.screens.recovery.RecoveryScreen

@Composable
fun AttractApp(viewModelFactory: androidx.lifecycle.ViewModelProvider.Factory) {
    val viewModel: AttractViewModel = viewModel(factory = viewModelFactory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    val context = androidx.compose.ui.platform.LocalContext.current
    val driveAuthManager = remember(context) { com.attract.attendance.data.drive.DriveAuthManager(context) }
    var onboardingDriveConnected by remember { mutableStateOf(false) }
    val driveAuthLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        android.util.Log.i("ATTRACT_DRIVE", "Authorization result code: ${result.resultCode}, data: ${result.data}")
        val authResult = driveAuthManager.handleAuthorizationResult(result.resultCode, result.data)
        authResult.onSuccess { email ->
            android.util.Log.i("ATTRACT_DRIVE", "Drive authorization success for $email")
            onboardingDriveConnected = true
            viewModel.onDriveConnected(email, context)
        }.onFailure { error ->
            android.util.Log.e("ATTRACT_DRIVE", "Drive authorization failed: ${error.message}", error)
            viewModel.onDriveSignInFailed(error.message ?: "Google Drive authorization failed")
        }
    }

    val connectDrive = remember(driveAuthManager, driveAuthLauncher, context) {
        {
            driveAuthManager.requestAuthorization(
                onLaunchResolution = { intentSenderRequest ->
                    driveAuthLauncher.launch(intentSenderRequest)
                },
                onDirectSuccess = { email ->
                    onboardingDriveConnected = true
                    viewModel.onDriveConnected(email, context)
                },
                onError = { error ->
                    viewModel.onDriveSignInFailed(error)
                }
            )
        }
    }

    LaunchedEffect(Unit) {
        viewModel.initDriveSync(context)
    }

    val rosterPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val classId = (state.screen as? AppScreen.ClassWorkspace)?.classId
        if (uri != null && classId != null) viewModel.prepareRosterImport(classId, uri)
    }
    val exportReportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val classId = (state.screen as? AppScreen.ClassWorkspace)?.classId
        if (uri != null && classId != null) viewModel.exportClassReport(classId, uri)
    }
    val exportBackupPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) viewModel.exportBackup(uri)
    }
    val importBackupPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importBackup(uri)
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHost.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    val isBiometricScreen = state.screen is AppScreen.FaceAttendance

    val canNavigateBack = when (state.screen) {
        AppScreen.Dashboard, AppScreen.Loading, AppScreen.Onboarding, AppScreen.ThemeSelection, is AppScreen.FaceAttendance -> false
        else -> true
    }

    androidx.activity.compose.BackHandler(enabled = canNavigateBack) {
        viewModel.navigateBack()
    }

    AttractTheme(
        themeMode = state.themeMode,
        isBiometricWorld = isBiometricScreen
    ) {
        Scaffold(snackbarHost = { SnackbarHost(snackbarHost) }) { padding ->
            Surface(modifier = Modifier.fillMaxSize().padding(padding)) {
                AnimatedContent(
                    targetState = state.screen,
                    transitionSpec = {
                        val isBack = targetState == AppScreen.Dashboard ||
                            (initialState is AppScreen.StudentDetail && targetState is AppScreen.ClassWorkspace) ||
                            (initialState is AppScreen.CreateClass && targetState == AppScreen.Dashboard) ||
                            (initialState is AppScreen.Settings && targetState == AppScreen.Dashboard) ||
                            (initialState is AppScreen.ManualAttendance && targetState is AppScreen.ClassWorkspace) ||
                            (initialState is AppScreen.FaceAttendance && targetState is AppScreen.ClassWorkspace) ||
                            (initialState is AppScreen.StandaloneEnrollment && targetState is AppScreen.StudentDetail) ||
                            (initialState is AppScreen.SessionHistory && targetState is AppScreen.ClassWorkspace)

                        val slideSpec = com.attract.attendance.ui.theme.AttractMotion.navTween<androidx.compose.ui.unit.IntOffset>()

                        if (isBack) {
                            slideInHorizontally(animationSpec = slideSpec) { width -> -width / 3 }
                                .togetherWith(slideOutHorizontally(animationSpec = slideSpec) { width -> width / 3 })
                        } else {
                            slideInHorizontally(animationSpec = slideSpec) { width -> width / 3 }
                                .togetherWith(slideOutHorizontally(animationSpec = slideSpec) { width -> -width / 3 })
                        }
                    },
                    label = "TeacherNavHost"
                ) { screen ->
                    when (screen) {
                    AppScreen.Loading -> LoadingScreen()
                    AppScreen.ThemeSelection -> com.attract.attendance.ui.screens.onboarding.ThemeSelectionScreen(
                        onSelectTheme = { mode ->
                            viewModel.completeThemeSelection(mode)
                        }
                    )
                    AppScreen.Onboarding -> OnboardingScreen(
                        currentThemeMode = state.themeMode,
                        onComplete = { name, pin, themeMode ->
                            viewModel.setThemeMode(themeMode)
                            viewModel.createTeacher(name, pin)
                        },
                        onConnectDrive = connectDrive,
                        isDriveConnected = onboardingDriveConnected || state.driveAccountEmail != null
                    )
                    AppScreen.Dashboard -> DashboardScreen(
                        teacherName = state.teacher?.displayName.orEmpty(),
                        classes = state.classes,
                        onSelectClass = viewModel::openClass,
                        onCreateClassClick = viewModel::openCreateClass,
                        onOpenSettings = viewModel::openSettings
                    )
                    AppScreen.CreateClass -> CreateClassScreen(
                        onBack = viewModel::navigateBack,
                        onCreate = viewModel::createClass
                    )
                    AppScreen.Settings -> SettingsScreen(
                        currentThemeMode = state.themeMode,
                        onThemeModeChange = viewModel::setThemeMode,
                        onExportBackup = { exportBackupPicker.launch("attract-backup.json") },
                        onImportBackup = { importBackupPicker.launch(arrayOf("application/json", "*/*")) },
                        onResetBiometricData = viewModel::resetBiometricData,
                        driveAccountEmail = state.driveAccountEmail,
                        driveSyncStatus = state.driveSyncStatus,
                        driveLastSyncMillis = state.driveLastSyncMillis,
                        onConnectDrive = connectDrive,
                        onDisconnectDrive = { viewModel.onDriveDisconnected(context) },
                        onSyncDriveNow = { viewModel.syncDriveNow(context) },
                        onRestoreDriveBackup = { viewModel.restoreDriveBackup(context) },
                        onBack = viewModel::navigateBack
                    )
                    is AppScreen.StudentDetail -> {
                        val currentStudent = state.workspace?.students?.firstOrNull { it.id == screen.studentId }
                        val className = state.workspace?.summary?.name ?: "Class"
                        val attendanceStats by viewModel.observeStudentAttendanceStats(screen.classId, screen.studentId)
                            .collectAsState(initial = 0 to (state.workspace?.summary?.endedSessionCount ?: 0))
                        if (currentStudent != null) {
                            StudentDetailScreen(
                                student = currentStudent,
                                className = className,
                                presentSessions = attendanceStats.first,
                                totalSessions = attendanceStats.second,
                                onBack = viewModel::navigateBack,
                                onReEnroll = { viewModel.openStandaloneEnrollment(screen.classId, screen.studentId) }
                            )
                        } else {
                            LoadingScreen()
                        }
                    }
                    is AppScreen.StandaloneEnrollment -> {
                        val context = androidx.compose.ui.platform.LocalContext.current
                        val repository = remember(context) {
                            (context.applicationContext as? com.attract.attendance.app.AttractApplication)?.container?.repository
                        }
                        val currentStudent = state.workspace?.students?.firstOrNull { it.id == screen.studentId }
                        val currentWorkspace = state.workspace
                        if (currentWorkspace != null && repository != null && currentStudent != null) {
                            com.attract.attendance.feature.attendance.AttendanceScreen(
                                classId = screen.classId,
                                students = currentWorkspace.students,
                                repository = repository,
                                isStandaloneMode = true,
                                targetStudentForStandalone = currentStudent,
                                onEnrollStudent = { studentId, embeddings, qualityScores, onComplete ->
                                    viewModel.enrollStudentFace(studentId, embeddings, qualityScores, onComplete)
                                },
                                onEndSession = { _ -> },
                                onBack = viewModel::navigateBack
                            )
                        } else {
                            LoadingScreen()
                        }
                    }
                    is AppScreen.ClassWorkspace -> ClassWorkspaceScreen(
                        workspace = state.workspace,
                        initialTab = screen.initialTab,
                        onBack = viewModel::navigateBack,
                        onAddStudent = { },
                        onStudentClick = viewModel::openStudentDetail,
                        onImportRoster = { rosterPicker.launch(arrayOf("text/csv", "text/comma-separated-values", "application/csv")) },
                        onImportOcrStudents = { students -> viewModel.importRosterDirectly(screen.classId, students) },
                        onManualAttendance = viewModel::openManualAttendance,
                        onFaceAttendance = viewModel::openFaceAttendance,
                        onSessionOpen = viewModel::openSessionHistory,
                        onAddStudentSubmit = { name, roll, serial, callback ->
                            viewModel.addStudent(
                                com.attract.attendance.data.repository.CreateStudentCommand(
                                    classId = screen.classId,
                                    name = name,
                                    rollNumber = roll,
                                    serialNumber = serial.orEmpty()
                                ),
                                callback
                            )
                        },
                        onExportReport = {
                            val className = state.workspace?.summary?.name ?: "attendance"
                            exportReportPicker.launch("$className-report.csv")
                        },
                        onContinueSession = viewModel::continueSession,
                    )
                    is AppScreen.ManualAttendance -> ManualAttendanceScreen(
                        workspace = state.workspace,
                        working = state.isWorking,
                        onBack = viewModel::navigateBack,
                        onSave = { present ->
                            viewModel.saveManualAttendance(screen.classId, present, screen.sessionDate)
                            com.attract.attendance.data.drive.BackupScheduler.triggerImmediateBackup(context)
                        },
                    )
                    is AppScreen.FaceAttendance -> {
                        val repository = remember(context) {
                            (context.applicationContext as? com.attract.attendance.app.AttractApplication)?.container?.repository
                        }
                        val currentWorkspace = state.workspace
                        if (currentWorkspace != null && repository != null) {
                            com.attract.attendance.feature.attendance.AttendanceScreen(
                                classId = screen.classId,
                                students = currentWorkspace.students,
                                repository = repository,
                                onEnrollStudent = { studentId, embeddings, qualityScores, onComplete ->
                                    viewModel.enrollStudentFace(studentId, embeddings, qualityScores, onComplete)
                                },
                                onEndSession = { present ->
                                    viewModel.saveFaceAttendance(screen.classId, present, screen.sessionDate)
                                    com.attract.attendance.data.drive.BackupScheduler.triggerImmediateBackup(context)
                                },
                                onDiscardSession = { viewModel.discardFaceAttendance(screen.classId) },
                                onSessionAutoEnded = { presentCount ->
                                    viewModel.showMessage("Attendance auto-saved — $presentCount present. Continue from the calendar within 24 hours.")
                                    viewModel.navigateBack()
                                },
                                onBack = viewModel::navigateBack
                            )
                        } else {
                            LoadingScreen()
                        }
                    }
                    is AppScreen.SessionHistory -> SessionHistoryScreen(
                        history = state.sessionHistory,
                        working = state.isWorking,
                        onBack = viewModel::navigateBack,
                        onCorrect = viewModel::correctAttendance,
                        onDelete = { viewModel.deleteSession(screen.sessionId, screen.classId) },
                    )
                }
            }
            }
        }
        state.pendingRosterImport?.let { rosterImport ->
            RosterImportPreviewDialog(
                studentCount = rosterImport.entries.size,
                sample = rosterImport.entries.take(5),
                working = state.isWorking,
                onConfirm = viewModel::confirmRosterImport,
                onDismiss = viewModel::cancelRosterImport,
            )
        }
        state.noEnrolledStudentsWarning?.let { warning ->
            NoEnrolledStudentsDialog(
                warning = warning,
                onDismiss = viewModel::dismissNoEnrolledStudentsWarning,
                onGoToStudents = { viewModel.openStudentsTabFromWarning(warning.classId) },
            )
        }
    }
}

@Composable
private fun LoadingScreen() = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun NoEnrolledStudentsDialog(
    warning: NoEnrolledStudentsDialogState,
    onDismiss: () -> Unit,
    onGoToStudents: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.GroupOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                text = "No Students Found",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Text(
                text = "This class has no students added yet. Please add students or import a roster before starting AI attendance.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            AttractPrimaryButton(onClick = onGoToStudents) {
                Text("Go to Students")
            }
        },
        dismissButton = {
            AttractTextButton(onClick = onDismiss) {
                Text("Dismiss")
            }
        }
    )
}

@Composable
private fun RosterImportPreviewDialog(
    studentCount: Int,
    sample: List<com.attract.attendance.core.model.RosterStudent>,
    working: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        title = { Text("Import $studentCount students?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("The whole roster will be added together. If a duplicate is found when saving, nothing is imported.")
                sample.forEach { student ->
                    Text("${student.rollNumber}  •  ${student.name}", style = MaterialTheme.typography.bodySmall)
                }
                if (studentCount > sample.size) Text("+ ${studentCount - sample.size} more", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { AttractTextButton(onClick = onConfirm, enabled = !working) { Text(if (working) "Importing…" else "Import") } },
        dismissButton = { AttractTextButton(onClick = onDismiss, enabled = !working) { Text("Cancel") } },
    )
}


