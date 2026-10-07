package com.attract.attendance.feature.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.attract.attendance.core.model.AppError
import com.attract.attendance.core.model.AttendanceStatus
import com.attract.attendance.core.model.ClassSummary
import com.attract.attendance.core.model.CommandResult
import com.attract.attendance.core.model.SessionSummary
import com.attract.attendance.core.model.StudentSummary
import com.attract.attendance.core.model.TeacherProfile
import com.attract.attendance.core.model.RosterStudent
import com.attract.attendance.data.importexport.AttendanceExporter
import com.attract.attendance.data.importexport.BackupExporter
import com.attract.attendance.data.importexport.CsvRosterImporter
import com.attract.attendance.data.importexport.RosterParseResult
import com.attract.attendance.data.repository.AttractRepository
import com.attract.attendance.data.repository.CreateClassCommand
import com.attract.attendance.data.repository.CreateStudentCommand
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

import com.attract.attendance.ui.theme.AppThemeMode

sealed interface AppScreen {
    data object Loading : AppScreen
    data object ThemeSelection : AppScreen
    data object Onboarding : AppScreen
    data object Dashboard : AppScreen
    data object CreateClass : AppScreen
    data class ClassWorkspace(val classId: Long, val initialTab: Int = 0) : AppScreen
    data class StudentDetail(val classId: Long, val studentId: Long) : AppScreen
    data class StandaloneEnrollment(val classId: Long, val studentId: Long) : AppScreen
    data class ManualAttendance(val classId: Long, val sessionDate: String) : AppScreen
    data class FaceAttendance(val classId: Long, val sessionDate: String) : AppScreen
    data class SessionHistory(val classId: Long, val sessionId: Long, val originTab: Int = 2) : AppScreen
    data object Settings : AppScreen
}

data class ClassWorkspace(
    val summary: ClassSummary,
    val students: List<StudentSummary>,
    val sessions: List<SessionSummary>,
)

data class SessionHistory(
    val session: SessionSummary,
    val rows: List<Pair<StudentSummary, AttendanceStatus?>>,
)

data class PendingRosterImport(
    val classId: Long,
    val entries: List<RosterStudent>,
)

data class NoEnrolledStudentsDialogState(
    val classId: Long,
    val sessionDate: String,
    val totalStudents: Int,
    val enrolledCount: Int,
)

data class AttractUiState(
    val screen: AppScreen = AppScreen.Loading,
    val themeMode: AppThemeMode = AppThemeMode.LIGHT,
    val hasChosenTheme: Boolean = false,
    val teacher: TeacherProfile? = null,
    val classes: List<ClassSummary> = emptyList(),
    val workspace: ClassWorkspace? = null,
    val sessionHistory: SessionHistory? = null,
    val pendingRosterImport: PendingRosterImport? = null,
    val noEnrolledStudentsWarning: NoEnrolledStudentsDialogState? = null,
    val driveAccountEmail: String? = null,
    val driveSyncStatus: com.attract.attendance.data.drive.DriveSyncStatus = com.attract.attendance.data.drive.DriveSyncStatus.Idle,
    val driveLastSyncMillis: Long = 0L,
    val isWorking: Boolean = false,
    val message: String? = null,
)

class AttractViewModel(
    private val repository: AttractRepository,
    private val csvRosterImporter: CsvRosterImporter,
    private val attendanceExporter: AttendanceExporter,
    private val backupExporter: BackupExporter,
    private val themeRepository: com.attract.attendance.data.theme.ThemeRepository? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AttractUiState())
    val uiState: StateFlow<AttractUiState> = _uiState.asStateFlow()

    private var workspaceJob: Job? = null
    private var historyJob: Job? = null

    init {
        val startupExceptionHandler = kotlinx.coroutines.CoroutineExceptionHandler { _, throwable ->
            android.util.Log.e("AttractViewModel", "Startup error in ViewModel coroutine", throwable)
            _uiState.update { it.copy(message = "Startup note: ${throwable.localizedMessage ?: "Database synchronizing"}") }
        }

        themeRepository?.let { repo ->
            viewModelScope.launch(startupExceptionHandler) {
                repo.themeMode.collect { mode ->
                    _uiState.update { it.copy(themeMode = mode) }
                }
            }
            viewModelScope.launch(startupExceptionHandler) {
                repo.hasChosenTheme.collect { chosen ->
                    _uiState.update { it.copy(hasChosenTheme = chosen) }
                }
            }
        }

        viewModelScope.launch(startupExceptionHandler) {
            repository.observeTeacher().collect { teacher ->
                _uiState.update { state ->
                    val chosen = themeRepository?.hasChosenTheme?.value ?: state.hasChosenTheme
                    val nextScreen = when {
                        !chosen -> AppScreen.ThemeSelection
                        teacher == null -> AppScreen.Onboarding
                        state.screen == AppScreen.Loading || state.screen == AppScreen.ThemeSelection || state.screen == AppScreen.Onboarding -> AppScreen.Dashboard
                        else -> state.screen
                    }
                    state.copy(teacher = teacher, screen = nextScreen)
                }
            }
        }
        viewModelScope.launch(startupExceptionHandler) {
            repository.finalizeStaleSessions()
        }
        viewModelScope.launch(startupExceptionHandler) {
            repository.observeClasses().collect { classes ->
                _uiState.update { it.copy(classes = classes) }
            }
        }
    }

    fun createTeacher(displayName: String, pin: String) = runCommand(
        work = { repository.registerTeacher(displayName, pin.toCharArray()) },
        onSuccess = { showMessage("Welcome to Attract.") },
    )

    fun createClass(command: CreateClassCommand) = runCommand(
        work = { repository.createClass(command) },
        onSuccess = { classId ->
            showMessage("Class created.")
            openClass(classId)
        },
    )

    /**
     * Loads the class workspace for session recovery (LLD-07). Mirrors openClass's
     * workspace collection WITHOUT changing the current screen.
     */
    private fun loadWorkspace(classId: Long) {
        workspaceJob?.cancel()
        workspaceJob = viewModelScope.launch {
            val summary = _uiState.value.classes.firstOrNull { it.id == classId }
                ?: repository.getClassSummary(classId)
                ?: return@launch
            combine(repository.observeStudents(classId), repository.observeEndedSessions(classId)) { students, sessions ->
                ClassWorkspace(summary, students, sessions)
            }.collect { workspace ->
                _uiState.update { it.copy(workspace = workspace) }
            }
        }
    }

    fun openClass(classId: Long, initialTab: Int = 0) {
        historyJob?.cancel()
        workspaceJob?.cancel()
        viewModelScope.launch {
            val summary = _uiState.value.classes.firstOrNull { it.id == classId }
                ?: repository.getClassSummary(classId)
                ?: run {
                    showMessage("This class is unavailable.")
                    return@launch
                }
            _uiState.update { it.copy(screen = AppScreen.ClassWorkspace(classId, initialTab), sessionHistory = null) }
            workspaceJob = viewModelScope.launch {
                combine(repository.observeStudents(classId), repository.observeEndedSessions(classId)) { students, sessions ->
                    ClassWorkspace(summary, students, sessions)
                }.collect { workspace ->
                    _uiState.update { it.copy(workspace = workspace) }
                }
            }
        }
    }

    fun addStudent(command: CreateStudentCommand, onResult: (CommandResult<Long>) -> Unit = {}) {
        viewModelScope.launch {
            val result = repository.addStudent(command)
            if (result is CommandResult.Success) {
                showMessage("Student ${command.name} added.")
            }
            onResult(result)
        }
    }

    fun prepareRosterImport(classId: Long, uri: Uri) {
        if (_uiState.value.isWorking) return
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true) }
            when (val parsed = csvRosterImporter.parse(uri)) {
                is RosterParseResult.Success -> _uiState.update {
                    it.copy(pendingRosterImport = PendingRosterImport(classId, parsed.entries))
                }
                is RosterParseResult.Rejected -> showMessage(parsed.message)
            }
            _uiState.update { it.copy(isWorking = false) }
        }
    }

    fun confirmRosterImport() {
        val import = _uiState.value.pendingRosterImport ?: return
        runCommand(
            work = { repository.importRoster(import.classId, import.entries) },
            onSuccess = { count ->
                _uiState.update { it.copy(pendingRosterImport = null) }
                showMessage("Imported $count students.")
            },
        )
    }

    fun importRosterDirectly(classId: Long, entries: List<RosterStudent>) = runCommand(
        work = { repository.importRoster(classId, entries) },
        onSuccess = { count ->
            showMessage("Added $count students from attendance sheet.")
        },
    )

    fun cancelRosterImport() = _uiState.update { it.copy(pendingRosterImport = null) }

    fun openStandaloneEnrollment(classId: Long, studentId: Long) {
        _uiState.update { it.copy(screen = AppScreen.StandaloneEnrollment(classId, studentId)) }
    }

    fun archiveStudent(studentId: Long) = runCommand(
        work = { repository.archiveStudent(studentId, archived = true) },
        onSuccess = { showMessage("Student archived. Their history is preserved.") },
    )

    fun openManualAttendance(sessionDate: String) {
        val workspace = _uiState.value.workspace ?: return
        _uiState.update { it.copy(screen = AppScreen.ManualAttendance(workspace.summary.id, sessionDate)) }
    }

    fun saveManualAttendance(classId: Long, presentIds: Set<Long>, targetDate: String) = runCommand(
        work = { repository.saveManualAttendance(classId, presentIds, targetDate) },
        onSuccess = {
            _uiState.update { it.copy(screen = AppScreen.ClassWorkspace(classId)) }
            showMessage("Attendance saved.")
        },
    )

    fun openFaceAttendance(sessionDate: String) {
        val workspace = _uiState.value.workspace ?: return
        val totalStudents = workspace.students.size

        if (totalStudents == 0) {
            _uiState.update {
                it.copy(
                    noEnrolledStudentsWarning = NoEnrolledStudentsDialogState(
                        classId = workspace.summary.id,
                        sessionDate = sessionDate,
                        totalStudents = 0,
                        enrolledCount = 0
                    )
                )
            }
            return
        }
        _uiState.update { it.copy(screen = AppScreen.FaceAttendance(workspace.summary.id, sessionDate)) }
    }

    fun dismissNoEnrolledStudentsWarning() {
        _uiState.update { it.copy(noEnrolledStudentsWarning = null) }
    }

    fun openStudentsTabFromWarning(classId: Long) {
        _uiState.update {
            it.copy(
                screen = AppScreen.ClassWorkspace(classId, initialTab = 1),
                noEnrolledStudentsWarning = null
            )
        }
    }

    fun openManualAttendanceFromWarning(sessionDate: String) {
        val workspace = _uiState.value.workspace ?: return
        _uiState.update {
            it.copy(
                screen = AppScreen.ManualAttendance(workspace.summary.id, sessionDate),
                noEnrolledStudentsWarning = null
            )
        }
    }

    fun faceAttendanceRequested(sessionDate: String) {
        openFaceAttendance(sessionDate)
    }

    fun saveFaceAttendance(classId: Long, presentIds: Set<Long>, targetDate: String) = runCommand(
        work = { repository.saveFaceAttendance(classId, presentIds, targetDate) },
        onSuccess = {
            _uiState.update { it.copy(screen = AppScreen.ClassWorkspace(classId, initialTab = 0)) }
            showMessage("Face attendance session saved (${presentIds.size} present).")
        },
    )

    fun discardFaceAttendance(classId: Long) = runCommand(
        work = { repository.discardSession(classId) },
        onSuccess = {
            _uiState.update { it.copy(screen = AppScreen.ClassWorkspace(classId, initialTab = 0)) }
            showMessage("Attendance session discarded.")
        },
    )

    fun enrollStudentFace(studentId: Long, embeddings: List<ByteArray>, qualityScores: List<Float>, onComplete: () -> Unit) = runCommand(
        work = { repository.enrollStudentFace(studentId, embeddings, qualityScores) },
        onSuccess = {
            onComplete()
        }
    )

    fun exportClassReport(classId: Long, uri: Uri) {
        if (_uiState.value.isWorking) return
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true) }
            try {
                val className = _uiState.value.workspace?.summary?.name ?: "Attract"
                val reportRows = repository.classReportRows(classId)
                val sessionRows = repository.sessionExportRows(classId)
                val now = System.currentTimeMillis()
                attendanceExporter.exportClassReport(uri, className, now, reportRows, sessionRows)
                    .onSuccess { showMessage("Attendance report exported.") }
                    .onFailure { showMessage(it.message ?: "Export failed. Please try again.") }
            } catch (error: Throwable) {
                showMessage("Export failed. Please try again.")
            }
            _uiState.update { it.copy(isWorking = false) }
        }
    }

    fun exportBackup(uri: Uri) {
        if (_uiState.value.isWorking) return
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true) }
            try {
                val snapshot = repository.backupSnapshot()
                backupExporter.export(uri, snapshot)
                    .onSuccess { showMessage("Backup exported.") }
                    .onFailure { showMessage(it.message ?: "Backup failed. Please try again.") }
            } catch (error: Throwable) {
                showMessage("Backup failed. Please try again.")
            }
            _uiState.update { it.copy(isWorking = false) }
        }
    }

    fun importBackup(uri: Uri) {
        if (_uiState.value.isWorking) return
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true) }
            try {
                backupExporter.import(uri)
                    .onSuccess { snapshot ->
                        when (val result = repository.restoreBackup(snapshot)) {
                            is com.attract.attendance.core.model.CommandResult.Success -> {
                                _uiState.value.workspace?.summary?.id?.let { loadWorkspace(it) }
                                showMessage("Backup restored successfully.")
                            }
                            is com.attract.attendance.core.model.CommandResult.Failure -> {
                                showMessage(result.error.toUserMessage())
                            }
                        }
                    }
                    .onFailure {
                        showMessage(it.message ?: "Could not read backup file.")
                    }
            } catch (e: Throwable) {
                showMessage("Restore failed: ${e.message}")
            } finally {
                _uiState.update { it.copy(isWorking = false) }
            }
        }
    }

    fun openSessionHistory(session: SessionSummary, originTab: Int = 2) {
        val classId = session.classId
        historyJob?.cancel()
        _uiState.update { it.copy(screen = AppScreen.SessionHistory(classId, session.id, originTab)) }
        historyJob = viewModelScope.launch {
            repository.observeSessionStudents(classId, session.id).collect { rows ->
                _uiState.update { it.copy(sessionHistory = SessionHistory(session, rows)) }
            }
        }
    }

    fun correctAttendance(sessionId: Long, studentId: Long, newStatus: AttendanceStatus) = runCommand(
        work = { repository.correctAttendance(sessionId, studentId, newStatus) },
        onSuccess = { showMessage("Attendance corrected.") },
    )

    fun deleteSession(sessionId: Long, classId: Long, originTab: Int = 2) = runCommand(
        work = { repository.deleteEndedSession(sessionId) },
        onSuccess = {
            historyJob?.cancel()
            _uiState.update { it.copy(screen = AppScreen.ClassWorkspace(classId, initialTab = originTab), sessionHistory = null) }
            showMessage("Attendance session deleted.")
        },
    )

    fun setThemeMode(mode: AppThemeMode) {
        themeRepository?.setThemeMode(mode)
        _uiState.update { it.copy(themeMode = mode) }
    }

    fun completeThemeSelection(mode: AppThemeMode) {
        themeRepository?.setThemeMode(mode)
        viewModelScope.launch {
            val teacher = repository.observeTeacher()
            _uiState.update { state ->
                val nextScreen = if (state.teacher == null) AppScreen.Onboarding else AppScreen.Dashboard
                state.copy(themeMode = mode, hasChosenTheme = true, screen = nextScreen)
            }
        }
    }

    fun openSettings() = _uiState.update { it.copy(screen = AppScreen.Settings) }

    fun initDriveSync(context: Context) {
        val prefs = com.attract.attendance.data.drive.DriveBackupPreferences(context)
        val email = prefs.accountEmail
        _uiState.update {
            it.copy(
                driveAccountEmail = email,
                driveLastSyncMillis = prefs.lastSyncMillis,
                driveSyncStatus = if (prefs.lastSyncError != null) {
                    com.attract.attendance.data.drive.DriveSyncStatus.Error(prefs.lastSyncError ?: "")
                } else if (prefs.lastSyncMillis > 0) {
                    com.attract.attendance.data.drive.DriveSyncStatus.Success(prefs.lastSyncMillis)
                } else {
                    com.attract.attendance.data.drive.DriveSyncStatus.Idle
                }
            )
        }
        if (!email.isNullOrBlank() && prefs.isAutoSyncEnabled) {
            com.attract.attendance.data.drive.BackupScheduler.schedulePeriodicBackup(context)
        }
    }

    fun onDriveConnected(email: String, context: Context) {
        val prefs = com.attract.attendance.data.drive.DriveBackupPreferences(context)
        prefs.accountEmail = email
        _uiState.update {
            it.copy(
                driveAccountEmail = email,
                driveSyncStatus = com.attract.attendance.data.drive.DriveSyncStatus.Idle
            )
        }
        com.attract.attendance.data.drive.BackupScheduler.schedulePeriodicBackup(context)
        syncDriveNow(context)
    }

    fun onDriveSignInFailed(errorMessage: String) {
        _uiState.update {
            it.copy(driveSyncStatus = com.attract.attendance.data.drive.DriveSyncStatus.Error(errorMessage))
        }
        showMessage("Google Drive: $errorMessage")
    }

    fun onDriveDisconnected(context: Context) {
        val prefs = com.attract.attendance.data.drive.DriveBackupPreferences(context)
        prefs.clear()
        com.attract.attendance.data.drive.BackupScheduler.cancelPeriodicBackup(context)
        val authManager = com.attract.attendance.data.drive.DriveAuthManager(context)
        authManager.signOut {
            _uiState.update {
                it.copy(
                    driveAccountEmail = null,
                    driveSyncStatus = com.attract.attendance.data.drive.DriveSyncStatus.Idle,
                    driveLastSyncMillis = 0L
                )
            }
            showMessage("Disconnected from Google Drive.")
        }
    }

    fun syncDriveNow(context: Context) {
        val prefs = com.attract.attendance.data.drive.DriveBackupPreferences(context)
        val email = prefs.accountEmail
        if (email.isNullOrBlank()) {
            showMessage("Please connect to Google Drive first.")
            return
        }
        _uiState.update { it.copy(driveSyncStatus = com.attract.attendance.data.drive.DriveSyncStatus.Syncing) }
        viewModelScope.launch {
            try {
                val snapshot = repository.backupSnapshot()
                val json = backupExporter.toJson(snapshot)
                val result = com.attract.attendance.data.drive.DriveServiceHelper(context).uploadBackupJson(email, json)
                if (result.isSuccess) {
                    val timestamp = result.getOrThrow()
                    val prefs = com.attract.attendance.data.drive.DriveBackupPreferences(context)
                    prefs.lastSyncMillis = timestamp
                    prefs.lastSyncError = null
                    _uiState.update {
                        it.copy(
                            driveLastSyncMillis = timestamp,
                            driveSyncStatus = com.attract.attendance.data.drive.DriveSyncStatus.Success(timestamp)
                        )
                    }
                    showMessage("Google Drive backup completed successfully.")
                } else {
                    val errorMsg = formatDriveError(result.exceptionOrNull())
                    _uiState.update { it.copy(driveSyncStatus = com.attract.attendance.data.drive.DriveSyncStatus.Error(errorMsg)) }
                    showMessage("Backup error: $errorMsg")
                }
            } catch (t: Throwable) {
                val errorMsg = formatDriveError(t)
                _uiState.update { it.copy(driveSyncStatus = com.attract.attendance.data.drive.DriveSyncStatus.Error(errorMsg)) }
                showMessage("Backup error: $errorMsg")
            }
        }
    }

    private fun formatDriveError(error: Throwable?): String {
        val msg = error?.message.orEmpty()
        return if (error is java.net.UnknownHostException || msg.contains("unable to resolve host", ignoreCase = true) || msg.contains("no such host", ignoreCase = true)) {
            "Internet connection required: Unable to reach Google Drive servers. Please check your network connection and try again."
        } else {
            error?.localizedMessage ?: "Network or Drive error. Please try again."
        }
    }

    fun restoreDriveBackup(context: Context) {
        val prefs = com.attract.attendance.data.drive.DriveBackupPreferences(context)
        val email = prefs.accountEmail
        if (email.isNullOrBlank()) {
            showMessage("Please connect to Google Drive first.")
            return
        }
        if (_uiState.value.isWorking) return
        _uiState.update { it.copy(isWorking = true) }
        viewModelScope.launch {
            try {
                val helper = com.attract.attendance.data.drive.DriveServiceHelper(context)
                val downloadResult = helper.downloadBackupJson(email)
                if (downloadResult.isSuccess) {
                    val json = downloadResult.getOrThrow()
                    if (json.isNullOrBlank()) {
                        showMessage("No backup found on Google Drive.")
                    } else {
                        val snapshot = backupExporter.fromJson(json)
                        when (val restoreResult = repository.restoreBackup(snapshot)) {
                            is com.attract.attendance.core.model.CommandResult.Success -> {
                                _uiState.value.workspace?.summary?.id?.let { loadWorkspace(it) }
                                showMessage("Backup restored from Google Drive successfully.")
                            }
                            is com.attract.attendance.core.model.CommandResult.Failure -> {
                                showMessage(restoreResult.error.toUserMessage())
                            }
                        }
                    }
                } else {
                    val errorMsg = formatDriveError(downloadResult.exceptionOrNull())
                    showMessage("Drive download failed: $errorMsg")
                }
            } catch (t: Throwable) {
                showMessage("Restore failed: ${formatDriveError(t)}")
            } finally {
                _uiState.update { it.copy(isWorking = false) }
            }
        }
    }

    fun continueSession(session: SessionSummary) {
        viewModelScope.launch {
            when (val result = repository.resumeFaceSession(session.id)) {
                is com.attract.attendance.core.model.CommandResult.Success -> {
                    val activeSession = result.value
                    if (_uiState.value.workspace?.summary?.id != activeSession.classId) {
                        loadWorkspace(activeSession.classId)
                    }
                    _uiState.update {
                        it.copy(screen = AppScreen.FaceAttendance(activeSession.classId, activeSession.sessionDate))
                    }
                }
                is com.attract.attendance.core.model.CommandResult.Failure -> {
                    showMessage(result.error.toUserMessage())
                }
            }
        }
    }

    fun openCreateClass() = _uiState.update { it.copy(screen = AppScreen.CreateClass) }

    fun openStudentDetail(studentId: Long) {
        val workspace = _uiState.value.workspace ?: return
        _uiState.update { it.copy(screen = AppScreen.StudentDetail(workspace.summary.id, studentId)) }
    }

    fun observeStudentAttendanceStats(classId: Long, studentId: Long): Flow<Pair<Int, Int>> =
        repository.observeStudentAttendanceStats(classId, studentId)

    fun navigateBack() {
        when (val screen = _uiState.value.screen) {
            is AppScreen.CreateClass -> _uiState.update { it.copy(screen = AppScreen.Dashboard) }
            is AppScreen.Settings -> _uiState.update { it.copy(screen = AppScreen.Dashboard) }
            is AppScreen.StudentDetail -> _uiState.update { it.copy(screen = AppScreen.ClassWorkspace(screen.classId)) }
            is AppScreen.ClassWorkspace -> {
                workspaceJob?.cancel()
                _uiState.update { it.copy(screen = AppScreen.Dashboard) }
            }
            is AppScreen.ManualAttendance -> _uiState.update { it.copy(screen = AppScreen.ClassWorkspace(screen.classId)) }
            is AppScreen.FaceAttendance -> _uiState.update { it.copy(screen = AppScreen.ClassWorkspace(screen.classId)) }
            is AppScreen.StandaloneEnrollment -> _uiState.update { it.copy(screen = AppScreen.StudentDetail(screen.classId, screen.studentId)) }
            is AppScreen.SessionHistory -> _uiState.update { it.copy(screen = AppScreen.ClassWorkspace(screen.classId, initialTab = screen.originTab), sessionHistory = null) }
            else -> Unit
        }
    }

    fun clearMessage() = _uiState.update { it.copy(message = null) }

    fun resetBiometricData(pin: String, confirmationText: String, onComplete: (Boolean, String) -> Unit) {
        if (confirmationText.trim() != "RESET") {
            onComplete(false, "Confirmation text must be 'RESET'")
            return
        }
        viewModelScope.launch {
            val authenticated = repository.authenticate(pin.toCharArray())
            if (!authenticated) {
                onComplete(false, "Invalid teacher PIN")
                return@launch
            }
            when (val result = repository.resetBiometricData()) {
                is CommandResult.Success -> {
                    showMessage("All biometric data has been reset.")
                    onComplete(true, "All biometric data has been reset.")
                }
                is CommandResult.Failure -> {
                    val msg = result.error.toUserMessage()
                    showMessage(msg)
                    onComplete(false, msg)
                }
            }
        }
    }

    private fun <T> runCommand(work: suspend () -> CommandResult<T>, onSuccess: (T) -> Unit) {
        if (_uiState.value.isWorking) return
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true) }
            when (val result = work()) {
                is CommandResult.Success -> onSuccess(result.value)
                is CommandResult.Failure -> showMessage(result.error.toUserMessage())
            }
            _uiState.update { it.copy(isWorking = false) }
        }
    }

    fun showMessage(message: String) = _uiState.update { it.copy(message = message) }

    private fun AppError.toUserMessage(): String = when (this) {
        is AppError.Validation -> message
        AppError.DuplicateRollNumber -> "That roll number already exists in this class."
        AppError.AnotherSessionActive -> "End or recover the active attendance session first."
        AppError.ActiveSessionExists -> "This change is unavailable while attendance is active."
        AppError.SessionNotActive -> "This attendance session is no longer active."
        AppError.NotEligible -> "This student is not eligible for this session."
        AppError.WrongClass -> "Selected students must belong to this class."
        AppError.AlreadyPresent -> "This student is already marked present."
        AppError.AlreadyFinalized -> "This attendance session is already finalized."
        AppError.NotFound -> "This item is unavailable."
        is AppError.Storage -> "Your data could not be saved. Please try again."
    }
}
