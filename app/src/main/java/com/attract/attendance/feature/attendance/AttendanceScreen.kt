package com.attract.attendance.feature.attendance

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.Log
import com.attract.attendance.BuildConfig
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.attract.attendance.ui.components.AttractIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.attract.attendance.domain.session.TeacherAuthAction
import com.attract.attendance.domain.session.TeacherAuthorizationGrant
import kotlinx.coroutines.Job
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import com.attract.attendance.ui.components.feedbackClickable
import com.attract.attendance.core.model.ClassSummary
import com.attract.attendance.core.model.EnrollmentStatus
import com.attract.attendance.feature.attendance.AttendanceStrings
import com.attract.attendance.ui.components.biometric.FaceFrameOverlay
import com.attract.attendance.ui.components.biometric.FrameState
import com.attract.attendance.ui.components.biometric.GuidanceCard
import com.attract.attendance.ui.components.biometric.PoseStepper
import com.attract.attendance.ui.components.biometric.RosterSelectionBottomSheet
import com.attract.attendance.ui.components.biometric.VerificationMeter
import com.attract.attendance.ui.theme.BiometricError
import com.attract.attendance.ui.theme.BiometricIndigo
import com.attract.attendance.ui.theme.BiometricSuccess
import com.attract.attendance.ui.theme.BiometricSurface
import com.attract.attendance.ui.theme.BiometricWarning
import com.attract.attendance.core.model.StudentSummary
import com.attract.attendance.data.repository.AttractRepository
import com.attract.attendance.domain.face.FaceQualityConfig
import com.attract.attendance.domain.face.FaceQualityEngine
import com.attract.attendance.domain.face.FaceQualitySignals
import com.attract.attendance.domain.face.LivenessEngine
import com.attract.attendance.domain.face.LivenessResult
import com.attract.attendance.domain.face.QualityResult
import com.attract.attendance.lockdown.platform.LockTaskControllerImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "AttendanceScreen"

enum class SessionScreenState {
    READY,
    CAPTURING,
    FRAMES_COLLECTED,
    PROCESSING,
    MATCH_SUCCESS,
    UNKNOWN_STUDENT,
    ALREADY_PRESENT,
    TEACHER_ASSIST,
    ERROR,
    AUTO_ENDED
}

@Composable
fun AttendanceScreen(
    classId: Long,
    students: List<StudentSummary>,
    repository: AttractRepository,
    onEnrollStudent: (Long, List<ByteArray>, List<Float>, () -> Unit) -> Unit,
    onEndSession: (Set<Long>) -> Unit,
    onDiscardSession: () -> Unit = {},
    onBack: () -> Unit,
    onSessionAutoEnded: (Int) -> Unit = { onBack() },
    isStandaloneMode: Boolean = false,
    targetStudentForStandalone: StudentSummary? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val fragmentActivity = context as? FragmentActivity
    val biometricManager = remember { BiometricManager.from(context) }
    val canUseBiometric = remember(biometricManager, fragmentActivity) {
        fragmentActivity != null &&
        biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    }
    val scope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    // --- Screen Pinning (LLD-13 / SDD Â§7) ---
    // Lock Task starts AUTOMATICALLY when the attendance screen appears  —  no teacher action needed.
    // The SDD flow is: Session Created  ->  startLockTask()  ->  Pinned Attendance World
    // Per LLD-13: "LLD-06 creates a recoverable ACTIVE session  ->  root swaps to attendance graph
    //  ->  startLockTask() is invoked  ->  lock-task state is observed/timeout checked  ->  camera enabled"
    var isScreenPinned by remember { mutableStateOf(false) }
    var screenPinningFailed by remember { mutableStateOf(false) }
    val lockTaskController = remember(activity) {
        if (activity != null && !isStandaloneMode) LockTaskControllerImpl(activity) else null
    }

    // --- Model availability check ---
    var isModelReady by remember { mutableStateOf(true) }

    // Canonical ACTIVE attendance session id — resolved from the DATABASE at screen
    // entry (ensureFaceSession). Display/diagnostics only; persistence always resolves
    // the session inside the repository transaction (DB = single source of truth).
    var activeSessionId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(Unit) {
        Log.i("ATTRACT_ATTENDANCE_PIPELINE", "[ATTENDANCE_SCREEN_ENTERED] classId=$classId")
        // 1. Request camera permission if needed
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }

        // 2. Pre-warm TFLite model  —  surfaces errors before any student interacts
        withContext(Dispatchers.IO) {
            val modelOk = com.attract.attendance.domain.face.EmbeddingEngine.isAvailable(context)
            if (!modelOk) {
                Log.e(TAG, "TFLite model failed to load  —  face verification unavailable")
            }
            isModelReady = modelOk
        }

        // 2b. Biometric template migration sweep (LLD-10 amendment): deactivate templates
        // stored by an incompatible earlier model (e.g. 32-D prototype) so they can never
        // reach TemplateMatcher; affected students fall back to the re-enrollment flow.
        withContext(Dispatchers.IO) {
            val report = repository.retireIncompatibleTemplates(classId)
            if (report.studentsNeedingReEnrollment.isNotEmpty()) {
                Log.w(
                    TAG,
                    "Template migration: ${report.deactivatedTemplates} stale templates retired; " +
                        "${report.studentsNeedingReEnrollment.size} student(s) need re-enrollment",
                )
            }
        }


        // 3. Auto-start screen pinning (non-standalone mode only)
        // Per SDD Â§7: Teacher navigation must be inaccessible during an ACTIVE session.
        if (!isStandaloneMode && lockTaskController != null) {
            try {
                val result = lockTaskController.start()
                when (result) {
                    is com.attract.attendance.lockdown.domain.LockTaskResult.Started -> {
                        isScreenPinned = true
                        Log.d(TAG, "Screen pinning started successfully")
                    }
                    is com.attract.attendance.lockdown.domain.LockTaskResult.AlreadyLocked -> {
                        isScreenPinned = true
                        Log.d(TAG, "Screen already pinned")
                    }
                    is com.attract.attendance.lockdown.domain.LockTaskResult.Error -> {
                        // Per LLD-13: unsupported pinning config must surface, not silently fail.
                        // startLockTask() throws SecurityException on non-whitelisted non-Device-Owner apps.
                        isScreenPinned = false
                        screenPinningFailed = true
                        Log.w(TAG, "Screen pinning failed: ${result.message}")
                    }
                }
            } catch (e: SecurityException) {
                // Device not configured for lock task (not Device Owner, not whitelisted).
                // This is expected on consumer Android 11 devices without DPC setup.
                isScreenPinned = false
                screenPinningFailed = true
                Log.w(TAG, "Screen pinning SecurityException  —  device not whitelisted: ${e.message}")
            }
        }
    }

    var state by remember { mutableStateOf(SessionScreenState.READY) }
    var presentIds by remember { mutableStateOf(setOf<Long>()) }
    var collectedFrames by remember { mutableIntStateOf(0) }
    var lastRecognizedStudent by remember { mutableStateOf<StudentSummary?>(null) }
    var statusMessage by remember {
        mutableStateOf(
            if (isStandaloneMode) AttendanceStrings.ENROLL_STEP1_TITLE
            else AttendanceStrings.ATTENDANCE_READY_TITLE
        )
    }
    var statusSubtitle by remember {
        mutableStateOf<String?>(
            if (isStandaloneMode) AttendanceStrings.ENROLL_STEP1_SUBTITLE
            else AttendanceStrings.ATTENDANCE_READY_SUBTITLE
        )
    }

    // 2c. CANONICAL SESSION START (LLD-06 amendment, 2026-08-26): the attendance
    // session is created/resolved HERE — the DB row is the single source of truth.
    // In standalone enrollment mode, we do NOT create an attendance session (fixes R08).
    LaunchedEffect(Unit) {
        if (isStandaloneMode) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            when (val ensured = repository.ensureFaceSession(classId)) {
                is com.attract.attendance.core.model.CommandResult.Success -> {
                    activeSessionId = ensured.value
                    Log.i("ATTRACT_ATTENDANCE_PIPELINE", "[START_SESSION] sessionId=${ensured.value} classId=$classId")
                }
                else -> {
                    Log.e("ATTRACT_ATTENDANCE_PIPELINE", "Session start FAILED: $ensured")
                    state = SessionScreenState.ERROR
                    statusMessage = "Could not start the attendance session. End any other active session and reopen."
                }
            }
        }
    }

    // Sync presentIds from Room database on activeSessionId resolution (resolves R09 & WP11)
    LaunchedEffect(activeSessionId) {
        val sId = activeSessionId ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            val existing = repository.attendanceRecordsForSession(sId)
                .filter { it.status == com.attract.attendance.core.model.AttendanceStatus.PRESENT }
                .map { it.studentId }
                .toSet()
            presentIds = existing
            Log.i("ATTRACT_ATTENDANCE_PIPELINE", "[RECOVERED_SESSION_ATTENDANCE] presentCount=${existing.size}")
        }
    }

    var isAutoEnding by remember { mutableStateOf(false) }

    val currentPresentIds by rememberUpdatedState(presentIds)
    val currentActiveSessionId by rememberUpdatedState(activeSessionId)
    val currentClassId by rememberUpdatedState(classId)
    val currentIsStandaloneMode by rememberUpdatedState(isStandaloneMode)
    val currentOnSessionAutoEnded by rememberUpdatedState(onSessionAutoEnded)

    val handleAutoInterrupt: () -> Unit = remember {
        {
            if (!currentIsStandaloneMode && !isAutoEnding && currentActiveSessionId != null && state != SessionScreenState.AUTO_ENDED) {
                isAutoEnding = true
                scope.launch {
                    Log.w(TAG, "Session interrupted (device locked / screen unpinned / screen off). Auto-saving session...")
                    val result = withContext(Dispatchers.IO) {
                        repository.saveFaceAttendance(currentClassId, currentPresentIds)
                    }
                    if (result is com.attract.attendance.core.model.CommandResult.Success) {
                        currentOnSessionAutoEnded(currentPresentIds.size)
                    } else {
                        state = SessionScreenState.AUTO_ENDED
                        statusMessage = "Attendance Paused & Saved"
                        statusSubtitle = "Screen unpinned or device was locked. You can continue from the calendar within 24 hours."
                    }
                }
            }
        }
    }

    DisposableEffect(context) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: android.content.Context?, intent: android.content.Intent?) {
                if (intent?.action == android.content.Intent.ACTION_SCREEN_OFF) {
                    handleAutoInterrupt()
                }
            }
        }
        val filter = android.content.IntentFilter(android.content.Intent.ACTION_SCREEN_OFF)
        try {
            androidx.core.content.ContextCompat.registerReceiver(
                context,
                receiver,
                filter,
                androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register screen-off broadcast receiver", e)
        }
        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (ignored: Exception) {}
        }
    }

    LaunchedEffect(isScreenPinned, activeSessionId, isAutoEnding) {
        if (isScreenPinned && activeSessionId != null && !isAutoEnding) {
            // Grace period: allow Android 3 seconds to settle into lock task mode
            delay(3000L)
            while (true) {
                delay(1000L)
                if (state == SessionScreenState.AUTO_ENDED || isAutoEnding) break
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                val currentMode = am?.lockTaskModeState ?: ActivityManager.LOCK_TASK_MODE_NONE
                if (currentMode == ActivityManager.LOCK_TASK_MODE_NONE) {
                    Log.w(TAG, "Screen pinning lost (mode is NONE). Triggering auto-save.")
                    handleAutoInterrupt()
                    break
                }
            }
        }
    }

    var showPinDialog by remember { mutableStateOf(false) }
    var teacherPinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }
    var isAuthenticating by remember { mutableStateOf(false) }
    var showZeroConfirmDialog by remember { mutableStateOf(false) }
    var showEnrollBottomSheet by remember { mutableStateOf(false) }
    var selectedStudentForEnroll by remember(targetStudentForStandalone) { mutableStateOf<StudentSummary?>(targetStudentForStandalone) }

    // R04/R05: attempt tokens, feedback job tracking, and active teacher authorization grant
    var currentInteractionToken by remember { mutableLongStateOf(0L) }
    var feedbackJob by remember { mutableStateOf<Job?>(null) }
    var activeTeacherGrant by remember { mutableStateOf<TeacherAuthorizationGrant?>(null) }

    // D-007 & R01/R02: Student self-service identity confirmation & teacher assist states
    var showStudentConfirmDialog by remember { mutableStateOf(false) }
    var pendingEnrollmentStudent by remember { mutableStateOf<StudentSummary?>(null) }
    var showTeacherAssistDialog by remember { mutableStateOf(false) }
    var showTeacherAssistPinDialog by remember { mutableStateOf(false) }
    var teacherAssistPinInput by remember { mutableStateOf("") }
    var teacherAssistPinError by remember { mutableStateOf<String?>(null) }
    var isAuthenticatingAssist by remember { mutableStateOf(false) }

    val launchExitAuth: () -> Unit = {
        if (!canUseBiometric || fragmentActivity == null) {
            showPinDialog = true
        } else {
            val executor = ContextCompat.getMainExecutor(context)
            var failureCount = 0
            var prompt: BiometricPrompt? = null
            prompt = BiometricPrompt(
                fragmentActivity,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        scope.launch {
                            if (presentIds.isEmpty()) {
                                showZeroConfirmDialog = true
                            } else {
                                try {
                                    lockTaskController?.stop()
                                    isScreenPinned = false
                                    Log.d(TAG, "Screen pinning stopped on session exit")
                                } catch (e: Exception) {
                                    Log.w(TAG, "stopLockTask failed: ${e.message}")
                                }
                                onEndSession(presentIds)
                            }
                        }
                    }

                    override fun onAuthenticationFailed() {
                        failureCount++
                        if (failureCount >= 3) {
                            prompt?.cancelAuthentication()
                            showPinDialog = true
                            pinError = "Too many failed fingerprint attempts. Please enter your PIN."
                        }
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        when (errorCode) {
                            BiometricPrompt.ERROR_LOCKOUT,
                            BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> {
                                showPinDialog = true
                                pinError = "Biometric locked. Please enter your PIN."
                            }
                            BiometricPrompt.ERROR_NEGATIVE_BUTTON -> {
                                showPinDialog = true
                            }
                        }
                    }
                }
            )

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Teacher Verification")
                .setSubtitle("Scan fingerprint to end session & unpin")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .setNegativeButtonText("Use PIN Instead")
                .build()

            prompt.authenticate(promptInfo)
        }
    }

    val launchTeacherAssistAuth: () -> Unit = {
        if (!canUseBiometric || fragmentActivity == null) {
            showTeacherAssistPinDialog = true
        } else {
            val executor = ContextCompat.getMainExecutor(context)
            var failureCount = 0
            var prompt: BiometricPrompt? = null
            prompt = BiometricPrompt(
                fragmentActivity,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        showTeacherAssistDialog = true
                    }

                    override fun onAuthenticationFailed() {
                        failureCount++
                        if (failureCount >= 3) {
                            prompt?.cancelAuthentication()
                            showTeacherAssistPinDialog = true
                            teacherAssistPinError = "Too many failed fingerprint attempts. Please enter your PIN."
                        }
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        when (errorCode) {
                            BiometricPrompt.ERROR_LOCKOUT,
                            BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> {
                                showTeacherAssistPinDialog = true
                                teacherAssistPinError = "Biometric locked. Please enter your PIN."
                            }
                            BiometricPrompt.ERROR_NEGATIVE_BUTTON -> {
                                showTeacherAssistPinDialog = true
                            }
                        }
                    }
                }
            )

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Teacher Authorization")
                .setSubtitle("Scan fingerprint for assisted check-in")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .setNegativeButtonText("Use PIN Instead")
                .build()

            prompt.authenticate(promptInfo)
        }
    }

    var latestFrameBundle by remember { mutableStateOf<com.attract.attendance.domain.face.FrameBundle?>(null) }
    var latestFrameSignals by remember { mutableStateOf<FaceQualitySignals?>(null) }
    var latestFrameBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var lastQualitySignals by remember { mutableStateOf<FaceQualitySignals?>(null) }
    var capturedPoseBitmaps by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var isValidatingFrame by remember { mutableStateOf(false) }

    // Step-aware capture machine (WP-B / WP-D): 0 = STRAIGHT, 1 = LEFT profile, 2 = RIGHT profile.
    var captureStep by remember { mutableIntStateOf(0) }
    var capturedYawDegrees by remember { mutableStateOf<List<Float>>(emptyList()) }
    var capturedQualityScores by remember { mutableStateOf<List<Float>>(emptyList()) }
    var enrollmentSlotEmbeddings by remember { mutableStateOf<List<FloatArray>>(emptyList()) }
    var anchorStraightYaw by remember { mutableStateOf<Float?>(null) }
    var autoCaptureJob by remember { mutableStateOf<Job?>(null) }
    var lastPoseHintTimeMs by remember { mutableLongStateOf(0L) }

    var recognitionAttemptCount by remember { mutableIntStateOf(0) }

    // ---- Adaptive 1 -> 2 -> 3 verification (LLD-16): one excellent frontal frame can accept
    // immediately; extra frames are requested ONLY when evidence is insufficient.
    var adaptiveEngine by remember { mutableStateOf<com.attract.attendance.domain.face.AdaptiveVerificationEngine?>(null) }
    var lastFrameQualityScore by remember { mutableStateOf(0f) }
    var lastFrameLive by remember { mutableStateOf(false) }

    // ---- Inline enrollment (LLD-06): when a NOT_ENROLLED student is selected from the
    // UNKNOWN roster, the attendance screen collects 3 enrollment poses right here,
    // enrolls them, and marks the student PRESENT — all without leaving the session.
    var inlineEnrollmentTarget by remember { mutableStateOf<StudentSummary?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            feedbackJob?.cancel()
            autoCaptureJob?.cancel()
            adaptiveEngine = null
            activeTeacherGrant = null
        }
    }

    fun stepPrompt(step: Int): String {
        if (isStandaloneMode) return when (step) {
            0 -> AttendanceStrings.ENROLL_STEP1_TITLE
            1 -> AttendanceStrings.ENROLL_STEP2_TITLE
            else -> AttendanceStrings.ENROLL_STEP3_TITLE
        }
        return when (step) {
            0 -> AttendanceStrings.ATTENDANCE_READY_TITLE
            1 -> AttendanceStrings.NEED_MORE_FRAMES_TITLE
            else -> AttendanceStrings.NEED_MORE_FRAMES_TITLE
        }
    }

    fun resetToReady(resetAttempts: Boolean = false, clearEnrollmentTarget: Boolean = true) {
        feedbackJob?.cancel()
        feedbackJob = null
        autoCaptureJob?.cancel()
        autoCaptureJob = null
        adaptiveEngine = null
        Log.i("ATTRACT_ATTENDANCE_PIPELINE", "[READY] sessionId=$activeSessionId classId=$classId presentCount=${presentIds.size}")
        if (resetAttempts) {
            recognitionAttemptCount = 0
            currentInteractionToken++
        }
        state = SessionScreenState.READY
        collectedFrames = 0
        statusMessage = stepPrompt(0)
        statusSubtitle = if (isStandaloneMode) AttendanceStrings.ENROLL_STEP1_SUBTITLE else AttendanceStrings.ATTENDANCE_READY_SUBTITLE
        selectedStudentForEnroll = null
        lastQualitySignals = null
        capturedPoseBitmaps = emptyList()
        isValidatingFrame = false
        captureStep = 0
        capturedYawDegrees = emptyList()
        capturedQualityScores = emptyList()
        enrollmentSlotEmbeddings = emptyList()
        anchorStraightYaw = null
        lastPoseHintTimeMs = 0L
        lastFrameQualityScore = 0f
        lastFrameLive = false
        if (clearEnrollmentTarget) {
            inlineEnrollmentTarget = null
            activeTeacherGrant = null
        }
    }


    /**
     * Generic message for release builds; on debug builds appends the real cause so
     * field failures can be diagnosed without adb (LLD-16 diagnostics amendment).
     */
    fun unavailableMessage(error: Throwable? = null): String {
        val base = "Face verification temporarily unavailable."
        if (!BuildConfig.DEBUG) return base
        val detail = error?.let { "${it::class.simpleName}: ${it.message}" }
            ?: com.attract.attendance.domain.face.EmbeddingEngine.lastInitError ?: ""
        return if (detail.isBlank()) base else "$base [$detail]"
    }

    fun handleNoEnrolledStudents() {
        feedbackJob?.cancel()
        adaptiveEngine = null
        recognitionAttemptCount = 0
        state = SessionScreenState.UNKNOWN_STUDENT
        statusMessage = AttendanceStrings.UNKNOWN_FACE_TITLE
        statusSubtitle = AttendanceStrings.UNKNOWN_FACE_SUBTITLE
        showEnrollBottomSheet = true
    }

    /** Starts the 3-pose self-service enrollment capture (D-007, WP-D). */
    fun startInlineEnrollment(selected: StudentSummary, grant: TeacherAuthorizationGrant? = null) {
        inlineEnrollmentTarget = selected
        activeTeacherGrant = grant
        captureStep = 0
        collectedFrames = 0
        capturedPoseBitmaps = emptyList()
        capturedYawDegrees = emptyList()
        capturedQualityScores = emptyList()
        enrollmentSlotEmbeddings = emptyList()
        anchorStraightYaw = null
        lastQualitySignals = null
        adaptiveEngine = null
        isValidatingFrame = false
        state = SessionScreenState.CAPTURING
        statusMessage = "Enrolling ${selected.name}"
        statusSubtitle = AttendanceStrings.ENROLL_STEP1_SUBTITLE
        Log.i("ATTRACT_ATTENDANCE_FALLBACK", "Inline enrollment started for studentId=${selected.id} name=${selected.name}")
    }

    /** Marks an already-enrolled student as present via authenticated TEACHER_ASSISTED fallback (fixes R02). */
    fun markTeacherAssistedPresent(selected: StudentSummary, grant: TeacherAuthorizationGrant) {
        state = SessionScreenState.PROCESSING
        statusMessage = "Marking ${selected.name} present (Teacher Assisted)..."
        scope.launch {
            Log.i("ATTRACT_ATTENDANCE_FALLBACK", "[TEACHER_ASSISTED_WRITE_STARTED] sessionId=$activeSessionId studentId=${selected.id}")
            when (val result = repository.markTeacherAssistedPresent(classId, selected.id, grant)) {
                is com.attract.attendance.data.repository.AttractRepository.FallbackMarkResult.Marked -> {
                    Log.i("ATTRACT_ATTENDANCE_PIPELINE", "[ATTENDANCE_WRITE_SUCCESS] sessionId=$activeSessionId studentId=${selected.id} recordId=${result.recordId}")
                    presentIds = presentIds + selected.id
                    lastRecognizedStudent = selected
                    state = SessionScreenState.MATCH_SUCCESS
                    statusMessage = "PRESENT: ${result.studentName} (Assisted)"
                    delay(2000)
                    resetToReady(resetAttempts = true)
                }
                is com.attract.attendance.data.repository.AttractRepository.FallbackMarkResult.AlreadyPresent -> {
                    Log.i("ATTRACT_ATTENDANCE_FALLBACK", "[ALREADY_PRESENT] student=${result.studentName}")
                    state = SessionScreenState.ALREADY_PRESENT
                    statusMessage = "Already Checked In: ${result.studentName}"
                    delay(2000)
                    resetToReady(resetAttempts = true)
                }
                com.attract.attendance.data.repository.AttractRepository.FallbackMarkResult.NoActiveSession -> {
                    Log.w("ATTRACT_ATTENDANCE_FALLBACK", "[NO_SESSION] rejected")
                    state = SessionScreenState.ERROR
                    statusMessage = "No active attendance session. Please start a session first."
                    delay(2500)
                    resetToReady()
                }
                com.attract.attendance.data.repository.AttractRepository.FallbackMarkResult.StudentNotFound,
                com.attract.attendance.data.repository.AttractRepository.FallbackMarkResult.StudentNotEligible -> {
                    Log.w("ATTRACT_ATTENDANCE_FALLBACK", "[INELIGIBLE] student=${selected.id}")
                    state = SessionScreenState.ERROR
                    statusMessage = "${selected.name} is not eligible for attendance in this session."
                    delay(2500)
                    resetToReady()
                }
                is com.attract.attendance.data.repository.AttractRepository.FallbackMarkResult.Failed -> {
                    Log.e("ATTRACT_ATTENDANCE_FALLBACK", "[FAILED] ${result.cause}")
                    state = SessionScreenState.ERROR
                    statusMessage = if (BuildConfig.DEBUG) "Could not mark attendance [${result.cause}]"
                    else "Could not mark attendance. Please try again."
                    delay(2500)
                    resetToReady()
                }
            }
        }
    }

    fun completeStandaloneEnrollment() {
        val target = targetStudentForStandalone ?: selectedStudentForEnroll ?: return
        Log.i("ATTRACT_ATTENDANCE_STANDALONE", "[STANDALONE_ENROLLMENT] completing for studentId=${target.id} name=${target.name}")

        state = SessionScreenState.PROCESSING
        statusMessage = "Saving ${target.name}'s face profile..."

        scope.launch {
            val extractedSamples = mutableListOf<com.attract.attendance.domain.face.EnrollmentSample>()
            for (i in 0 until 3) {
                val emb = enrollmentSlotEmbeddings.getOrNull(i) ?: run {
                    val bitmap = capturedPoseBitmaps.getOrNull(i)
                    if (bitmap != null) withContext(Dispatchers.IO) {
                        com.attract.attendance.domain.face.EmbeddingEngine.extractEmbedding(context, bitmap)
                    } else null
                } ?: run {
                    statusMessage = "Missing capture for step ${i + 1}."
                    state = SessionScreenState.ERROR
                    isValidatingFrame = false
                    delay(2000)
                    resetToReady(clearEnrollmentTarget = false)
                    return@launch
                }
                extractedSamples.add(
                    com.attract.attendance.domain.face.EnrollmentSample(
                        slotIndex = i,
                        embedding = emb,
                        qualityScore = capturedQualityScores.getOrElse(i) { 1.0f },
                        yawDegrees = capturedYawDegrees.getOrElse(i) { 0f },
                        timestampNanos = System.nanoTime() + i
                    )
                )
            }

            val validation = com.attract.attendance.domain.face.EnrollmentBatchValidator.validate(
                studentId = target.id,
                classId = classId,
                samples = extractedSamples,
            )
            val batch = when (validation) {
                is com.attract.attendance.domain.face.EnrollmentBatchValidator.ValidationResult.Valid -> validation.batch
                is com.attract.attendance.domain.face.EnrollmentBatchValidator.ValidationResult.Invalid -> {
                    statusMessage = "⚠️ ${validation.reason}"
                    state = SessionScreenState.ERROR
                    isValidatingFrame = false
                    delay(2500)
                    resetToReady(clearEnrollmentTarget = false)
                    return@launch
                }
            }

            val enrollResult = withContext(Dispatchers.IO) {
                repository.standaloneEnrollStudentFace(classId, target.id, batch)
            }
            when (enrollResult) {
                is com.attract.attendance.domain.face.EnrollmentResult.Committed -> {
                    state = SessionScreenState.MATCH_SUCCESS
                    statusMessage = "✓ ENROLLED SUCCESSFULLY: ${target.name}"
                    delay(1500)
                    onBack()
                }
                is com.attract.attendance.domain.face.EnrollmentResult.DuplicateSuspected -> {
                    val dupStudent = students.find { it.id == enrollResult.existingStudentId }
                    val name = dupStudent?.name ?: "ID ${enrollResult.existingStudentId}"
                    statusMessage = "⚠️ Duplicate face detected matching $name. Registration blocked."
                    state = SessionScreenState.ERROR
                    isValidatingFrame = false
                    delay(3000)
                    resetToReady()
                }
                is com.attract.attendance.domain.face.EnrollmentResult.Failed -> {
                    statusMessage = "⚠️ Enrollment failed: ${enrollResult.reason}"
                    state = SessionScreenState.ERROR
                    isValidatingFrame = false
                    delay(2500)
                    resetToReady()
                }
                else -> {
                    statusMessage = "⚠️ Enrollment could not be completed."
                    state = SessionScreenState.ERROR
                    isValidatingFrame = false
                    delay(2000)
                    resetToReady()
                }
            }
        }
    }

    /** Completes inline enrollment: validates batch, checks duplicates, self-enrolls, marks PRESENT (D-007, WP-D). */
    fun completeInlineEnrollment() {
        val target = inlineEnrollmentTarget ?: return
        Log.i("ATTRACT_ATTENDANCE_FALLBACK", "[INLINE_ENROLLMENT] completing for studentId=${target.id} name=${target.name} frames=${capturedPoseBitmaps.size}")

        if (capturedPoseBitmaps.size < 3 && enrollmentSlotEmbeddings.size < 3) {
            statusMessage = "Need all 3 poses. Retake missing ones."
            state = SessionScreenState.CAPTURING
            isValidatingFrame = false
            return
        }

        state = SessionScreenState.PROCESSING
        statusMessage = "Saving ${target.name}'s face data..."

        scope.launch {
            val extractedSamples = mutableListOf<com.attract.attendance.domain.face.EnrollmentSample>()
            for (i in 0 until 3) {
                val emb = enrollmentSlotEmbeddings.getOrNull(i) ?: run {
                    val bitmap = capturedPoseBitmaps.getOrNull(i)
                    if (bitmap != null) withContext(Dispatchers.IO) {
                        com.attract.attendance.domain.face.EmbeddingEngine.extractEmbedding(context, bitmap)
                    } else null
                } ?: run {
                    statusMessage = "Missing capture for step ${i + 1}."
                    state = SessionScreenState.ERROR
                    isValidatingFrame = false
                    delay(2000)
                    resetToReady(clearEnrollmentTarget = false)
                    return@launch
                }
                extractedSamples.add(
                    com.attract.attendance.domain.face.EnrollmentSample(
                        slotIndex = i,
                        embedding = emb,
                        qualityScore = capturedQualityScores.getOrElse(i) { 1.0f },
                        yawDegrees = capturedYawDegrees.getOrElse(i) { 0f },
                        timestampNanos = System.nanoTime() + i
                    )
                )
            }

            val validation = com.attract.attendance.domain.face.EnrollmentBatchValidator.validate(
                studentId = target.id,
                classId = classId,
                samples = extractedSamples,
            )
            val batch = when (validation) {
                is com.attract.attendance.domain.face.EnrollmentBatchValidator.ValidationResult.Valid -> validation.batch
                is com.attract.attendance.domain.face.EnrollmentBatchValidator.ValidationResult.Invalid -> {
                    statusMessage = "⚠️ ${validation.reason}"
                    state = SessionScreenState.ERROR
                    isValidatingFrame = false
                    delay(2500)
                    resetToReady(clearEnrollmentTarget = false)
                    return@launch
                }
            }

            val isReEnroll = target.enrollmentStatus == EnrollmentStatus.REENROLL_REQUIRED
            val enrollResult = withContext(Dispatchers.IO) {
                if (isReEnroll) {
                    repository.reEnrollStudentFace(classId, target.id, batch, activeTeacherGrant)
                } else {
                    repository.selfEnrollAndCheckIn(
                        sessionId = activeSessionId,
                        classId = classId,
                        studentId = target.id,
                        batch = batch
                    )
                }
            }

            when (enrollResult) {
                is com.attract.attendance.domain.face.EnrollmentResult.Committed -> {
                    Log.i("ATTRACT_ATTENDANCE_FALLBACK", "[INLINE_ENROLLMENT] enrollment succeeded for ${target.id}")
                    if (enrollResult.attendanceRecordId != null) {
                        presentIds = presentIds + target.id
                    }
                    lastRecognizedStudent = target
                    state = SessionScreenState.MATCH_SUCCESS
                    statusMessage = if (isReEnroll) "PROFILE REPAIRED: ${target.name}" else "✓ ENROLLED & PRESENT: ${target.name}"
                    delay(2500)
                    resetToReady(resetAttempts = true)
                }
                is com.attract.attendance.domain.face.EnrollmentResult.DuplicateSuspected -> {
                    val dupStudent = students.find { it.id == enrollResult.existingStudentId }
                    val name = dupStudent?.name ?: "ID ${enrollResult.existingStudentId}"
                    statusMessage = "⚠️ Duplicate face detected matching enrolled student $name. Registration blocked."
                    state = SessionScreenState.ERROR
                    isValidatingFrame = false
                    delay(3000)
                    resetToReady()
                }
                is com.attract.attendance.domain.face.EnrollmentResult.ApprovalExpired -> {
                    statusMessage = "⚠️ Authorization expired: ${enrollResult.reason}"
                    state = SessionScreenState.ERROR
                    isValidatingFrame = false
                    delay(2500)
                    resetToReady()
                }
                is com.attract.attendance.domain.face.EnrollmentResult.AlreadyEnrolled -> {
                    statusMessage = "⚠️ ${target.name} is already enrolled."
                    state = SessionScreenState.ERROR
                    isValidatingFrame = false
                    delay(2000)
                    resetToReady()
                }
                is com.attract.attendance.domain.face.EnrollmentResult.Ineligible -> {
                    statusMessage = "⚠️ Ineligible: ${enrollResult.reason}"
                    state = SessionScreenState.ERROR
                    isValidatingFrame = false
                    delay(2500)
                    resetToReady()
                }
                is com.attract.attendance.domain.face.EnrollmentResult.Failed -> {
                    statusMessage = "⚠️ Enrollment failed: ${enrollResult.reason}"
                    state = SessionScreenState.ERROR
                    isValidatingFrame = false
                    delay(2500)
                    resetToReady()
                }
                else -> {
                    statusMessage = "⚠️ Enrollment could not be completed."
                    state = SessionScreenState.ERROR
                    isValidatingFrame = false
                    delay(2000)
                    resetToReady()
                }
            }
        }
    }

    fun processAutoCaptureFrame(bundle: com.attract.attendance.domain.face.FrameBundle) {
        val isEnrollment = inlineEnrollmentTarget != null || (isStandaloneMode && (targetStudentForStandalone != null || selectedStudentForEnroll != null))
        if (!isEnrollment) return
        if (state != SessionScreenState.CAPTURING) return
        if (isValidatingFrame) return
        if (autoCaptureJob?.isActive == true) return

        val signals = bundle.qualitySignals ?: return
        val cropBitmap = bundle.alignedCrop ?: return
        if (signals.faceCount != 1) return

        val config = FaceQualityConfig.calibrationDefaults()
        val currentSlot = captureStep
        val expectedPose = when (currentSlot) {
            0 -> com.attract.attendance.domain.face.ExpectedPose.STRAIGHT
            1 -> com.attract.attendance.domain.face.ExpectedPose.LEFT
            else -> com.attract.attendance.domain.face.ExpectedPose.RIGHT
        }

        val qualityEval = FaceQualityEngine.evaluate(signals, config, expectedPose, anchorStraightYaw)
        if (qualityEval is QualityResult.Rejected) {
            val now = System.currentTimeMillis()
            if (now - lastPoseHintTimeMs > 1000L) {
                lastPoseHintTimeMs = now
                val targetName = inlineEnrollmentTarget?.name ?: selectedStudentForEnroll?.name ?: targetStudentForStandalone?.name ?: "Student"
                val (msgTitle, msgSubtitle) = when (qualityEval.reason) {
                    com.attract.attendance.domain.face.QualityReason.DARK -> AttendanceStrings.QUALITY_DARK_TITLE to AttendanceStrings.QUALITY_DARK_SUBTITLE
                    com.attract.attendance.domain.face.QualityReason.OVEREXPOSED -> AttendanceStrings.QUALITY_OVEREXPOSED_TITLE to AttendanceStrings.QUALITY_OVEREXPOSED_SUBTITLE
                    com.attract.attendance.domain.face.QualityReason.BLUR -> AttendanceStrings.QUALITY_BLUR_TITLE to AttendanceStrings.QUALITY_BLUR_SUBTITLE
                    com.attract.attendance.domain.face.QualityReason.TOO_SMALL -> AttendanceStrings.QUALITY_TOO_SMALL_TITLE to AttendanceStrings.QUALITY_TOO_SMALL_SUBTITLE
                    com.attract.attendance.domain.face.QualityReason.OFF_CENTER -> AttendanceStrings.QUALITY_OFF_CENTER_TITLE to AttendanceStrings.QUALITY_OFF_CENTER_SUBTITLE
                    com.attract.attendance.domain.face.QualityReason.POSE_NOT_STRAIGHT -> AttendanceStrings.ENROLL_STEP1_TITLE to AttendanceStrings.ENROLL_STEP1_SUBTITLE
                    com.attract.attendance.domain.face.QualityReason.POSE_NOT_LEFT -> AttendanceStrings.ENROLL_STEP2_TITLE to AttendanceStrings.ENROLL_STEP2_SUBTITLE
                    com.attract.attendance.domain.face.QualityReason.POSE_NOT_RIGHT -> AttendanceStrings.ENROLL_STEP3_TITLE to AttendanceStrings.ENROLL_STEP3_SUBTITLE
                    com.attract.attendance.domain.face.QualityReason.POSE -> AttendanceStrings.QUALITY_POSE_TITLE to AttendanceStrings.QUALITY_POSE_SUBTITLE
                    else -> "Hold steady in good lighting" to null
                }
                statusMessage = msgTitle
                statusSubtitle = msgSubtitle
            }
            return
        }

        isValidatingFrame = true
        autoCaptureJob = scope.launch {
            try {
                val embedding = withContext(Dispatchers.IO) {
                    com.attract.attendance.domain.face.EmbeddingEngine.extractEmbedding(context, cropBitmap)
                }

                if (currentSlot > 0 && enrollmentSlotEmbeddings.isNotEmpty()) {
                    val straightEmbedding = enrollmentSlotEmbeddings[0]
                    val sim = com.attract.attendance.domain.face.IdentityScorer.cosineSimilarity(straightEmbedding, embedding)
                    if (sim < com.attract.attendance.domain.face.BiometricModelProfile.CURRENT.continuityThreshold) {
                        statusMessage = "Person swap suspected"
                        statusSubtitle = "Hold steady and retake pose"
                        isValidatingFrame = false
                        return@launch
                    }
                }

                capturedPoseBitmaps = capturedPoseBitmaps + cropBitmap
                capturedYawDegrees = capturedYawDegrees + signals.yawDegrees
                capturedQualityScores = capturedQualityScores + (qualityEval as QualityResult.Accepted).score
                enrollmentSlotEmbeddings = enrollmentSlotEmbeddings + embedding

                if (currentSlot == 0) {
                    anchorStraightYaw = signals.yawDegrees
                    captureStep = 1
                    collectedFrames = 1
                    statusMessage = AttendanceStrings.ENROLL_STEP2_TITLE
                    statusSubtitle = AttendanceStrings.ENROLL_STEP2_SUBTITLE
                    delay(500)
                    isValidatingFrame = false
                } else if (currentSlot == 1) {
                    captureStep = 2
                    collectedFrames = 2
                    statusMessage = AttendanceStrings.ENROLL_STEP3_TITLE
                    statusSubtitle = AttendanceStrings.ENROLL_STEP3_SUBTITLE
                    delay(500)
                    isValidatingFrame = false
                } else {
                    captureStep = 3
                    collectedFrames = 3
                    state = SessionScreenState.PROCESSING
                    statusMessage = AttendanceStrings.ENROLL_SAVING_TITLE
                    statusSubtitle = AttendanceStrings.ENROLL_SAVING_SUBTITLE
                    if (inlineEnrollmentTarget != null) {
                        completeInlineEnrollment()
                    } else if (isStandaloneMode) {
                        completeStandaloneEnrollment()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Auto-capture failed", e)
                isValidatingFrame = false
            }
        }
    }

    fun handleMatchedStudent(matchedId: Long, confidence: Float? = null) {
        recognitionAttemptCount = 0
        val matchedStudent = students.firstOrNull { it.id == matchedId }
        if (matchedStudent == null) {
            handleNoEnrolledStudents()
            return
        }
        if (matchedStudent.id in presentIds) {
            isValidatingFrame = false
            state = SessionScreenState.ALREADY_PRESENT
            statusMessage = "${AttendanceStrings.ALREADY_PRESENT_TITLE}: ${matchedStudent.name}"
            statusSubtitle = matchedStudent.rollNumber
            scope.launch { delay(2000); resetToReady(resetAttempts = true) }
            return
        }
        // LLD-06 PersistingPresent: the AI-recognition mark is written to Room
        // IMMEDIATELY through the production command — same uniqueness guarantees as
        // the manual fallback; end-of-session save only reconciles the remainder.
        scope.launch {
            Log.i("ATTRACT_ATTENDANCE_PIPELINE", "[ATTENDANCE_WRITE_STARTED] sessionId=$activeSessionId classId=$classId studentId=$matchedId source=AI_RECOGNITION")
            when (val result = repository.markRecognizedPresent(classId, matchedId, confidence)) {
                is com.attract.attendance.data.repository.AttractRepository.FallbackMarkResult.Marked -> {
                    Log.i(
                        "ATTRACT_ATTENDANCE_FALLBACK",
                        "AI attendance persisted: studentId=$matchedId recordId=${result.recordId}",
                    )
                    Log.i("ATTRACT_ATTENDANCE_PIPELINE", "[ATTENDANCE_WRITE_SUCCESS] sessionId=$activeSessionId studentId=$matchedId recordId=${result.recordId}")
                    presentIds = presentIds + matchedStudent.id
                    lastRecognizedStudent = matchedStudent
                    state = SessionScreenState.MATCH_SUCCESS
                    statusMessage = "${AttendanceStrings.MATCH_TITLE}: ${matchedStudent.name}"
                    statusSubtitle = matchedStudent.rollNumber
                }
                is com.attract.attendance.data.repository.AttractRepository.FallbackMarkResult.AlreadyPresent -> {
                    state = SessionScreenState.ALREADY_PRESENT
                    statusMessage = "${AttendanceStrings.ALREADY_PRESENT_TITLE}: ${matchedStudent.name}"
                    statusSubtitle = matchedStudent.rollNumber
                }
                else -> {
                    Log.e("ATTRACT_ATTENDANCE_FALLBACK", "AI attendance persist failed: $result")
                    state = SessionScreenState.ERROR
                    statusMessage = "Could not record attendance"
                    statusSubtitle = "Please try again"
                }
            }
            delay(2000)
            isValidatingFrame = false // terminal outcome must release the capture lock
            resetToReady(resetAttempts = true)
        }
    }

    fun onAdaptiveMatch(studentId: Long, confidence: Float) {
        adaptiveEngine = null // R05: terminal engine must be cleared immediately
        feedbackJob?.cancel()
        Log.i(
            "ATTRACT_ATTENDANCE_PIPELINE",
            "RECOGNITION match: sessionId=$activeSessionId classId=$classId studentId=$studentId confidence=$confidence uiState=$state",
        )
        isValidatingFrame = false // terminal outcome must release the capture lock
        handleMatchedStudent(studentId, confidence)
    }

    fun onAdaptiveNonMatch() {
        Log.i(
            "ATTRACT_ATTENDANCE_FALLBACK",
            "[UNKNOWN] sessionId=$activeSessionId attempt=${recognitionAttemptCount + 1} presentCount=${presentIds.size}",
        )
        isValidatingFrame = false // terminal outcome must release the capture lock
        adaptiveEngine = null // R05: terminal engine must be cleared immediately before enabling capture
        feedbackJob?.cancel()
        val attemptToken = ++currentInteractionToken

        val hasUnenrolled = students.any { it.enrollmentStatus == EnrollmentStatus.NOT_ENROLLED && it.id !in presentIds }
        if (hasUnenrolled || recognitionAttemptCount >= 1) {
            recognitionAttemptCount = 0
            handleNoEnrolledStudents()
        } else {
            recognitionAttemptCount += 1
            state = SessionScreenState.READY
            statusMessage = "Couldn't verify clearly, please try again"
            statusSubtitle = "Attempt 1 of 2 · Hold still and look straight"
            feedbackJob = scope.launch {
                delay(2000)
                if (currentInteractionToken == attemptToken) {
                    resetToReady(resetAttempts = false)
                }
            }
        }
    }

    fun captureClick() {
        if (state == SessionScreenState.PROCESSING || state == SessionScreenState.MATCH_SUCCESS || isValidatingFrame) return
        feedbackJob?.cancel()
        feedbackJob = null
        Log.i("ATTRACT_ATTENDANCE_PIPELINE", "[CAPTURE_STARTED] sessionId=$activeSessionId classId=$classId uiState=$state")

        val bundle = latestFrameBundle
        if (bundle?.error != null) {
            statusMessage = "⚠️ Camera/detector error: ${bundle.error}"
            return
        }

        val signals = bundle?.qualitySignals ?: latestFrameSignals
        val frameBitmap = bundle?.alignedCrop ?: latestFrameBitmap

        if (signals == null || signals.faceCount == 0) {
            statusMessage = AttendanceStrings.NO_FACE_TITLE
            statusSubtitle = AttendanceStrings.NO_FACE_SUBTITLE
            return
        }

        if (signals.faceCount > 1) {
            statusMessage = AttendanceStrings.MULTIPLE_FACES_TITLE
            statusSubtitle = AttendanceStrings.MULTIPLE_FACES_SUBTITLE
            return
        }

        isValidatingFrame = true

        // Pose gating: STANDALONE ENROLLMENT keeps the calibrated 3-step windows
        // (STRAIGHT/LEFT/RIGHT per LLD-09 with anchor straight yaw). Live attendance uses adaptive gating (LLD-16):
        // frame 1 expects STRAIGHT; supporting frames accept any natural pose and reject
        // only EXTREMES, using existing production thresholds.
        val qualityEval: QualityResult = if (isStandaloneMode || inlineEnrollmentTarget != null) {
            // Strict pose gating for enrollment captures (standalone AND inline)
            val expectedPose = when (captureStep) {
                0 -> com.attract.attendance.domain.face.ExpectedPose.STRAIGHT
                1 -> com.attract.attendance.domain.face.ExpectedPose.LEFT
                else -> com.attract.attendance.domain.face.ExpectedPose.RIGHT
            }
            FaceQualityEngine.evaluate(signals, FaceQualityConfig.calibrationDefaults(), expectedPose, anchorStraightYaw)
        } else if (captureStep == 0) {
            FaceQualityEngine.evaluate(signals, FaceQualityConfig.calibrationDefaults())
        } else {
            val supportPoseOk =
                com.attract.attendance.domain.face.AdaptiveVerificationEngine.isAcceptableSupportPose(
                    signals, FaceQualityConfig.calibrationDefaults(),
                )
            if (!supportPoseOk) {
                statusMessage = AttendanceStrings.QUALITY_POSE_TITLE
                statusSubtitle = "Keep your head roughly facing the camera"
                isValidatingFrame = false
                return
            }
            // Bin by MEASURED yaw so genuine profiles validate against their own window.
            val cfg = FaceQualityConfig.calibrationDefaults()
            val measured = when {
                signals.yawDegrees <= -cfg.profileMinYawDegrees -> com.attract.attendance.domain.face.ExpectedPose.LEFT
                signals.yawDegrees >= cfg.profileMinYawDegrees -> com.attract.attendance.domain.face.ExpectedPose.RIGHT
                else -> com.attract.attendance.domain.face.ExpectedPose.STRAIGHT
            }
            FaceQualityEngine.evaluate(signals, cfg, measured)
        }
        if (qualityEval is QualityResult.Rejected) {
            val (qTitle, qSub) = when (qualityEval.reason) {
                com.attract.attendance.domain.face.QualityReason.DARK -> AttendanceStrings.QUALITY_DARK_TITLE to AttendanceStrings.QUALITY_DARK_SUBTITLE
                com.attract.attendance.domain.face.QualityReason.OVEREXPOSED -> AttendanceStrings.QUALITY_OVEREXPOSED_TITLE to AttendanceStrings.QUALITY_OVEREXPOSED_SUBTITLE
                com.attract.attendance.domain.face.QualityReason.BLUR -> AttendanceStrings.QUALITY_BLUR_TITLE to AttendanceStrings.QUALITY_BLUR_SUBTITLE
                com.attract.attendance.domain.face.QualityReason.TOO_SMALL -> AttendanceStrings.QUALITY_TOO_SMALL_TITLE to AttendanceStrings.QUALITY_TOO_SMALL_SUBTITLE
                com.attract.attendance.domain.face.QualityReason.OFF_CENTER -> AttendanceStrings.QUALITY_OFF_CENTER_TITLE to AttendanceStrings.QUALITY_OFF_CENTER_SUBTITLE
                com.attract.attendance.domain.face.QualityReason.POSE_NOT_STRAIGHT,
                com.attract.attendance.domain.face.QualityReason.POSE_NOT_LEFT,
                com.attract.attendance.domain.face.QualityReason.POSE_NOT_RIGHT,
                com.attract.attendance.domain.face.QualityReason.POSE -> AttendanceStrings.QUALITY_POSE_TITLE to AttendanceStrings.QUALITY_POSE_SUBTITLE
                else -> "Reposition face in frame" to "Hold steady and try again"
            }
            statusMessage = qTitle
            statusSubtitle = qSub
            isValidatingFrame = false
            return
        }

        // Presentation-attack signals (LLD-12) computed once per capture on the face crop.
        val patSignals = PresentationAttackAnalyzer.analyze(frameBitmap)

        val livenessEval = LivenessEngine.check(signals, lastQualitySignals, patSignals)
        if (livenessEval is LivenessResult.Rejected) {
            statusMessage = "Liveness check failed"
            statusSubtitle = livenessEval.message
            isValidatingFrame = false
            return
        }

        lastQualitySignals = signals
        lastFrameLive = livenessEval is LivenessResult.Passed
        lastFrameQualityScore = (qualityEval as QualityResult.Accepted).score

        val isEnrollmentMode = isStandaloneMode || inlineEnrollmentTarget != null
        if (isEnrollmentMode) {
            capturedYawDegrees = capturedYawDegrees + signals.yawDegrees
            capturedQualityScores = capturedQualityScores + qualityEval.score
            if (frameBitmap != null) {
                capturedPoseBitmaps = capturedPoseBitmaps + frameBitmap
            }
            if (captureStep == 0) {
                anchorStraightYaw = signals.yawDegrees
            }
        }

        // ---- STANDALONE ENROLLMENT (Face Biometrics Setup): collect 3 poses, then the
        // existing SUBMIT path enrolls. The adaptive recognition engine must NOT run here —
        // there is no gallery yet by definition. (Regression fix 2026-08-26: adaptive
        // submission previously hijacked this flow into UNKNOWN/roster.)
        if (isStandaloneMode) {
            if (collectedFrames < 3) {
                val nextFrame = collectedFrames + 1
                collectedFrames = nextFrame
                when (nextFrame) {
                    1 -> { captureStep = 1; state = SessionScreenState.CAPTURING; statusMessage = stepPrompt(1); statusSubtitle = AttendanceStrings.ENROLL_STEP2_SUBTITLE }
                    2 -> { captureStep = 2; state = SessionScreenState.CAPTURING; statusMessage = stepPrompt(2); statusSubtitle = AttendanceStrings.ENROLL_STEP3_SUBTITLE }
                    else -> {
                        state = SessionScreenState.FRAMES_COLLECTED
                        statusMessage = AttendanceStrings.ENROLL_ALL_CAPTURED_TITLE
                        statusSubtitle = AttendanceStrings.ENROLL_ALL_CAPTURED_SUBTITLE
                    }
                }
            }
            isValidatingFrame = false
            return
        }

        // ---- INLINE ENROLLMENT (LLD-06): teacher selected a NOT_ENROLLED student from the
        // UNKNOWN roster → collect 3 enrollment poses right here, then enroll + mark present.
        if (inlineEnrollmentTarget != null) {
            val target = inlineEnrollmentTarget!!
            if (collectedFrames < 2) {
                val nextFrame = collectedFrames + 1
                collectedFrames = nextFrame
                captureStep = nextFrame
                state = SessionScreenState.CAPTURING
                val (stepTitle, stepSub) = when (nextFrame) {
                    1 -> AttendanceStrings.ENROLL_STEP2_TITLE to AttendanceStrings.ENROLL_STEP2_SUBTITLE
                    else -> AttendanceStrings.ENROLL_STEP3_TITLE to AttendanceStrings.ENROLL_STEP3_SUBTITLE
                }
                statusMessage = stepTitle
                statusSubtitle = stepSub
                isValidatingFrame = false
            } else {
                // All 3 frames captured → enroll + mark present
                Log.i("ATTRACT_ATTENDANCE_FALLBACK", "[INLINE_ENROLLMENT] all 3 frames captured for ${target.name}")
                completeInlineEnrollment()
            }
            return
        }

        // ---- Adaptive submission (LLD-16): embed this frame, submit to the engine, and
        // either accept immediately, request another natural capture, or finalize safely.
        state = SessionScreenState.PROCESSING
        statusMessage = AttendanceStrings.PROCESSING_TITLE
        statusSubtitle = AttendanceStrings.PROCESSING_SUBTITLE
        scope.launch {
            runCatching {
                val signalsNow = lastQualitySignals
                val liveNow = lastFrameLive
                val scoreNow = lastFrameQualityScore
                val cropBitmap = frameBitmap
                if (signalsNow == null || cropBitmap == null) {
                    statusMessage = AttendanceStrings.QUALITY_NO_FACE_TITLE
                    statusSubtitle = AttendanceStrings.QUALITY_NO_FACE_SUBTITLE
                    state = SessionScreenState.CAPTURING
                    isValidatingFrame = false
                    return@launch
                }

                val embedding = withContext(Dispatchers.IO) {
                    com.attract.attendance.domain.face.EmbeddingEngine.extractEmbedding(context, cropBitmap)
                }
                val activeTemplates = repository.getActiveTemplatesForClass(classId)
                if (activeTemplates.isEmpty()) {
                    handleNoEnrolledStudents()
                    return@launch
                }
                val eng = adaptiveEngine ?: com.attract.attendance.domain.face.AdaptiveVerificationEngine(
                    templates = activeTemplates,
                ).also { adaptiveEngine = it }

                val observation = com.attract.attendance.domain.face.FrameObservation(
                    signals = signalsNow,
                    quality = QualityResult.Accepted(scoreNow, com.attract.attendance.domain.face.PoseBucket.FRONTAL, 1),
                    liveness = if (liveNow) LivenessResult.Passed
                    else LivenessResult.Rejected(com.attract.attendance.domain.face.LivenessReason.INSUFFICIENT_VARIANCE, "static"),
                    embedding = embedding,
                )

                when (val step = eng.submit(observation)) {
                    is com.attract.attendance.domain.face.AdaptiveVerificationEngine.Step.NeedMoreFrames -> {
                        collectedFrames = step.framesSubmitted
                        captureStep = minOf(step.framesSubmitted, 2)
                        state = SessionScreenState.CAPTURING
                        statusMessage = AttendanceStrings.NEED_MORE_FRAMES_TITLE
                        statusSubtitle = AttendanceStrings.NEED_MORE_FRAMES_SUBTITLE
                        isValidatingFrame = false
                    }
                    is com.attract.attendance.domain.face.AdaptiveVerificationEngine.Step.Final -> {
                        adaptiveEngine = null // R05: clear terminal engine immediately
                        when (val outcome = step.outcome) {
                            is com.attract.attendance.domain.face.AdaptiveVerificationEngine.Outcome.Match -> {
                                onAdaptiveMatch(outcome.studentId, outcome.confidence)
                            }
                            com.attract.attendance.domain.face.AdaptiveVerificationEngine.Outcome.Ambiguous,
                            com.attract.attendance.domain.face.AdaptiveVerificationEngine.Outcome.Unknown -> {
                                onAdaptiveNonMatch()
                            }
                            is com.attract.attendance.domain.face.AdaptiveVerificationEngine.Outcome.Error -> {
                                isValidatingFrame = false // terminal outcome must release the capture lock
                                state = SessionScreenState.ERROR
                                statusMessage = unavailableMessage()
                                delay(2000)
                                resetToReady()
                            }
                        }
                    }
                }
            }.onFailure { error ->
                Log.e(TAG, "Adaptive verification error: ${error::class.simpleName}  —  ${error.message}", error)
                isValidatingFrame = false // never leave the capture button dead-locked
                state = SessionScreenState.ERROR
                statusMessage = unavailableMessage(error)
                delay(2000)
                resetToReady()
            }
        }
    }

    fun submitRecognition() {
        if (state != SessionScreenState.FRAMES_COLLECTED) return
        state = SessionScreenState.PROCESSING
        statusMessage = "Analyzing multi-angle face embeddings..."

        scope.launch {
            runCatching {
                val currentSignals = lastQualitySignals
                if (currentSignals != null) {
                    val livenessReCheck = LivenessEngine.check(currentSignals)
                    if (livenessReCheck is LivenessResult.Rejected) {
                        state = SessionScreenState.CAPTURING
                        collectedFrames = 2
                        captureStep = 2
                        statusMessage = "Retake needed  —  ${livenessReCheck.message}"
                        return@launch
                    }
                }

                // capturedPoseBitmaps now contains FACE-CROPPED bitmaps from CameraPreview.
                // The crop happens in CameraPreview.cropFaceFromBitmap() so TFLite receives
                // a proper face region, not the full camera frame.
                val queryEmbeddings = withContext(Dispatchers.IO) {
                    capturedPoseBitmaps.map { bitmap ->
                        com.attract.attendance.domain.face.EmbeddingEngine.extractEmbedding(context, bitmap)
                    }
                }

                if (queryEmbeddings.isEmpty()) {
                    state = SessionScreenState.ERROR
                    statusMessage = "Face capture failed. Please retake photos."
                    delay(2000)
                    resetToReady()
                    return@launch
                }

                val targetFloats = com.attract.attendance.domain.face.EmbeddingEngine.combineEmbeddings(queryEmbeddings)
                val activeTemplates = repository.getActiveTemplatesForClass(classId)
                val engine = com.attract.attendance.domain.face.RecognitionDecisionEngine()

                val outcome = engine.evaluate(targetFloats, activeTemplates)

                when (outcome) {
                    is com.attract.attendance.domain.face.RecognitionOutcome.Match -> {
                        recognitionAttemptCount = 0
                        val matchedId = outcome.studentId
                        val matchedStudent = students.firstOrNull { it.id == matchedId }
                        if (matchedStudent != null) {
                            if (matchedStudent.id in presentIds) {
                                state = SessionScreenState.ALREADY_PRESENT
                                statusMessage = "Already Checked In: ${matchedStudent.name}"
                                delay(2000)
                                resetToReady(resetAttempts = true)
                            } else {
                                presentIds = presentIds + matchedStudent.id
                                lastRecognizedStudent = matchedStudent
                                state = SessionScreenState.MATCH_SUCCESS
                                statusMessage = "PRESENT: ${matchedStudent.name}"
                                delay(2000)
                                resetToReady(resetAttempts = true)
                            }
                        } else {
                            val selectable = students.filter { it.id !in presentIds }
                            if (selectable.isEmpty()) {
                                state = SessionScreenState.READY
                                statusMessage = "All students are already marked present."
                            } else {
                                state = SessionScreenState.UNKNOWN_STUDENT
                                statusMessage = "Face not recognized. Select the student below."
                                showEnrollBottomSheet = true
                            }
                        }
                    }
                    else -> {
                        if (recognitionAttemptCount < 1) {
                            recognitionAttemptCount += 1
                            state = SessionScreenState.READY
                            statusMessage = "Couldn't verify clearly, please try again (Attempt 1/2)."
                            delay(2000)
                            resetToReady(resetAttempts = false)
                        } else {
                            recognitionAttemptCount = 0
                            val selectable = students.filter { it.id !in presentIds }
                            if (selectable.isEmpty()) {
                                state = SessionScreenState.READY
                                statusMessage = "All students are already marked present."
                            } else {
                                state = SessionScreenState.UNKNOWN_STUDENT
                                statusMessage = "Face not recognized. Select the student below."
                                showEnrollBottomSheet = true
                            }
                        }
                    }
                }
            }.onFailure { error ->
                // Per LLD-11: TFLite interpreter fault  ->  Unavailable state, never PRESENT.
                // Log the real cause for debugging; show safe message to student.
                Log.e(TAG, "Face verification error: ${error::class.simpleName}  —  ${error.message}", error)
                state = SessionScreenState.ERROR
                statusMessage = unavailableMessage(error)
                delay(2000)
                resetToReady()
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp)
    ) {
        // Top Bar  —  Screen Pinning Indicator & Exit Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopEnd),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isStandaloneMode) {
                AttractIconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Text(
                    text = "Face Biometrics Setup",
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            } else {
                // Accurate screen pinning state  —  only show "pinned" if actually pinned.
                // Per LLD-13: unsupported pinning config must surface, not be silently ignored.
                if (screenPinningFailed) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFFFDD835),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Screen lock unavailable",
                            color = Color(0xFFFDD835),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                } else if (isScreenPinned) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = BiometricSuccess,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Screen Pinned",
                            color = BiometricSuccess,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                } else {
                    // Pinning in progress or not yet confirmed
                    Text(
                        text = AttendanceStrings.SESSION_STARTING,
                        color = Color.White.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                AttractIconButton(onClick = { launchExitAuth() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                        contentDescription = "End Session & Unpin Screen",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        // Center Content Area
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Square Selfie Camera Preview Box (280dp x 280dp)
            Box(
                modifier = Modifier
                    .size(280.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF111111))
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(24.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (hasCameraPermission) {
                    CameraPreview(
                        modifier = Modifier.fillMaxSize(),
                        onFrameBundleAnalyzed = { bundle ->
                            latestFrameBundle = bundle
                            processAutoCaptureFrame(bundle)
                        },
                        onFrameAnalyzed = { signals, frameBitmap ->
                            latestFrameSignals = signals
                            latestFrameBitmap = frameBitmap
                        }
                    )
                } else {
                    Text("Camera permission required", color = Color.White, style = MaterialTheme.typography.bodySmall)
                }

                // Face frame brackets overlay (indigo idle, amber quality issue, emerald success)
                val bracketState = when (state) {
                    SessionScreenState.MATCH_SUCCESS -> FrameState.SUCCESS
                    SessionScreenState.ALREADY_PRESENT -> FrameState.WARNING
                    SessionScreenState.UNKNOWN_STUDENT, SessionScreenState.ERROR -> FrameState.ERROR
                    else -> {
                        val hasQualityIssue = latestFrameBundle?.qualitySignals?.let { signals ->
                            signals.faceCount != 1 ||
                                FaceQualityEngine.evaluate(signals, FaceQualityConfig.calibrationDefaults()) is QualityResult.Rejected
                        } ?: false
                        if (hasQualityIssue && (state == SessionScreenState.CAPTURING || state == SessionScreenState.PROCESSING)) {
                            FrameState.WARNING
                        } else {
                            FrameState.NEUTRAL
                        }
                    }
                }
                FaceFrameOverlay(
                    frameState = bracketState,
                    modifier = Modifier.fillMaxSize().padding(10.dp)
                )

                if (state == SessionScreenState.PROCESSING) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = BiometricIndigo)
                    }
                } else if (state == SessionScreenState.MATCH_SUCCESS) {
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .clip(CircleShape)
                            .background(BiometricSuccess),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Verified",
                            tint = Color(0xFF06301F),
                            modifier = Modifier.size(52.dp)
                        )
                    }
                } else if (state == SessionScreenState.ALREADY_PRESENT) {
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .clip(CircleShape)
                            .background(BiometricWarning),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Already Checked In",
                            tint = Color(0xFF3D2600),
                            modifier = Modifier.size(52.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Guidance Card (Title + Subtitle, state colors & icons)
            GuidanceCard(
                state = state,
                title = statusMessage,
                subtitle = statusSubtitle,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            )

            if (state == SessionScreenState.MATCH_SUCCESS && lastRecognizedStudent != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "${lastRecognizedStudent?.name} • ${lastRecognizedStudent?.rollNumber}",
                    color = Color.White.copy(alpha = 0.7f),
                    style = com.attract.attendance.ui.theme.NumericBody
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Progress Indicators: PoseStepper for enrollment only, VerificationMeter for adaptive checks
            val isEnrollment = isStandaloneMode || inlineEnrollmentTarget != null
            if (isEnrollment) {
                if (state == SessionScreenState.CAPTURING || state == SessionScreenState.FRAMES_COLLECTED) {
                    PoseStepper(
                        completedCount = collectedFrames,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    if (collectedFrames > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(
                            onClick = { resetToReady() },
                            modifier = Modifier.height(44.dp)
                        ) {
                            Text("↺ Retake Poses", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            } else {
                if (state == SessionScreenState.CAPTURING && collectedFrames > 0) {
                    VerificationMeter(
                        completedChecks = collectedFrames,
                        totalBudget = 2,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            if (state == SessionScreenState.AUTO_ENDED) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f), RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Attendance Paused & Saved",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Screen was unpinned or device entered lock screen. All marks are saved safely in the database. You can continue taking attendance from the Calendar within 24 hours.",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = launchExitAuth,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Exit to Workspace", fontWeight = FontWeight.SemiBold)
                    }
                }
            } else if (state == SessionScreenState.READY || state == SessionScreenState.CAPTURING || state == SessionScreenState.FRAMES_COLLECTED || state == SessionScreenState.UNKNOWN_STUDENT) {
                val glowState = when (state) {
                    SessionScreenState.FRAMES_COLLECTED -> com.attract.attendance.ui.components.biometric.GlowButtonState.SUBMIT
                    SessionScreenState.MATCH_SUCCESS -> com.attract.attendance.ui.components.biometric.GlowButtonState.SUCCESS
                    SessionScreenState.ALREADY_PRESENT -> com.attract.attendance.ui.components.biometric.GlowButtonState.WARNING
                    SessionScreenState.UNKNOWN_STUDENT -> com.attract.attendance.ui.components.biometric.GlowButtonState.ERROR
                    else -> com.attract.attendance.ui.components.biometric.GlowButtonState.READY
                }

                if (state == SessionScreenState.UNKNOWN_STUDENT && selectedStudentForEnroll == null) {
                    Button(
                        onClick = { showEnrollBottomSheet = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BiometricIndigo,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text(
                            if (isStandaloneMode) "Select Student to Enroll" else "Select Name / Ask Teacher",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                } else {
                    com.attract.attendance.ui.components.biometric.GlowCaptureButton(
                        buttonState = glowState,
                        onClick = {
                            if (state == SessionScreenState.PROCESSING || isValidatingFrame) return@GlowCaptureButton
                            // Guard against starting capture before session is initialized in attendance mode (prevents session id 0 error)
                            if (!isStandaloneMode && activeSessionId == null) {
                                statusMessage = AttendanceStrings.SESSION_STARTING
                                statusSubtitle = ""
                                return@GlowCaptureButton
                            }
                            if (state == SessionScreenState.FRAMES_COLLECTED) {
                                if (isStandaloneMode) {
                                    completeStandaloneEnrollment()
                                } else {
                                    submitRecognition()
                                }
                            } else if (state == SessionScreenState.UNKNOWN_STUDENT) {
                                showEnrollBottomSheet = true
                            } else {
                                captureClick()
                            }
                        }
                    )
                }
            }
        }

        // Bottom Present Counter
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (selectedStudentForEnroll != null) {
                Text(
                    text = "Selected: ${selectedStudentForEnroll!!.name} (${selectedStudentForEnroll!!.rollNumber})",
                    color = Color(0xFF8AB4F8),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            val totalStudents = students.size
            val presentCount = presentIds.size
            val animatedCount by animateIntAsState(
                targetValue = presentCount,
                label = "presentCountAnim"
            )
            val progressFraction = if (totalStudents > 0) presentCount.toFloat() / totalStudents.toFloat() else 0f
            val animatedProgress by animateFloatAsState(
                targetValue = progressFraction,
                label = "presentProgressAnim"
            )

            Box(
                modifier = Modifier
                    .clip(com.attract.attendance.ui.theme.PillShape)
                    .background(Color.White.copy(alpha = 0.05f))
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Present: $animatedCount / $totalStudents",
                        color = Color.White.copy(alpha = 0.85f),
                        style = com.attract.attendance.ui.theme.NumericBody,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .width(100.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = BiometricSuccess,
                        trackColor = Color.White.copy(alpha = 0.12f)
                    )
                }
            }
        }

        // Roster Selection dialog: Material3 ModalBottomSheet
        if (showEnrollBottomSheet) {
            RosterSelectionBottomSheet(
                onDismissRequest = { showEnrollBottomSheet = false },
                isStandaloneMode = isStandaloneMode,
                students = students,
                presentIds = presentIds,
                selectedStudent = selectedStudentForEnroll,
                onSelectStudent = { student ->
                    if (isStandaloneMode) {
                        selectedStudentForEnroll = student
                        showEnrollBottomSheet = false
                    } else {
                        // D-007: Student self-service identity confirmation
                        pendingEnrollmentStudent = student
                        showEnrollBottomSheet = false
                        showStudentConfirmDialog = true
                    }
                },
                onTeacherAssistRequested = {
                    showEnrollBottomSheet = false
                    launchTeacherAssistAuth()
                }
            )
        }

        // D-007 Student Confirmation Dialog for First-Time Self-Service Enrollment
        if (showStudentConfirmDialog && pendingEnrollmentStudent != null) {
            val student = pendingEnrollmentStudent!!

            AlertDialog(
                onDismissRequest = {
                    showStudentConfirmDialog = false
                    pendingEnrollmentStudent = null
                },
                containerColor = BiometricSurface,
                titleContentColor = Color.White,
                textContentColor = Color.White.copy(alpha = 0.85f),
                title = { Text("Confirm Your Name", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Are you ${student.name} (${student.rollNumber})?\n\nThis face will be linked to your attendance profile for this class.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showStudentConfirmDialog = false
                            val target = pendingEnrollmentStudent!!
                            pendingEnrollmentStudent = null
                            startInlineEnrollment(target, grant = null)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BiometricIndigo,
                            contentColor = Color.White
                        )
                    ) {
                        Text("Yes, that's me")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showStudentConfirmDialog = false
                            pendingEnrollmentStudent = null
                            showEnrollBottomSheet = true
                        }
                    ) {
                        Text("Back", color = Color.White.copy(alpha = 0.7f))
                    }
                }
            )
        }

        // Teacher Assist PIN Dialog
        if (showTeacherAssistPinDialog) {
            AlertDialog(
                onDismissRequest = {
                    if (!isAuthenticatingAssist) {
                        showTeacherAssistPinDialog = false
                        teacherAssistPinInput = ""
                        teacherAssistPinError = null
                    }
                },
                title = { Text("Teacher Authorization", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Enter Teacher PIN to unlock assisted check-in & profile repair:")
                        OutlinedTextField(
                            value = teacherAssistPinInput,
                            onValueChange = {
                                teacherAssistPinInput = it.filter(Char::isDigit).take(12)
                                teacherAssistPinError = null
                            },
                            label = { Text("Teacher PIN") },
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine = true,
                            isError = teacherAssistPinError != null,
                            supportingText = { teacherAssistPinError?.let { Text(it, color = MaterialTheme.colorScheme.error) } },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (teacherAssistPinInput.isBlank()) {
                                teacherAssistPinError = "PIN is required."
                                return@Button
                            }
                            isAuthenticatingAssist = true
                            scope.launch {
                                val success = repository.authenticate(teacherAssistPinInput.toCharArray())
                                isAuthenticatingAssist = false
                                if (success) {
                                    showTeacherAssistPinDialog = false
                                    teacherAssistPinInput = ""
                                    showTeacherAssistDialog = true
                                } else {
                                    teacherAssistPinError = "Incorrect PIN. Try again."
                                }
                            }
                        },
                        enabled = !isAuthenticatingAssist
                    ) {
                        Text(if (isAuthenticatingAssist) "Verifying..." else "Authorize")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showTeacherAssistPinDialog = false
                            teacherAssistPinInput = ""
                            teacherAssistPinError = null
                        },
                        enabled = !isAuthenticatingAssist
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Teacher Assist Student Selection Dialog
        if (showTeacherAssistDialog) {
            val enrolledNotPresent = students.filter {
                (it.enrollmentStatus == EnrollmentStatus.ENROLLED || it.enrollmentStatus == EnrollmentStatus.REENROLL_REQUIRED) &&
                    it.id !in presentIds
            }

            AlertDialog(
                onDismissRequest = { showTeacherAssistDialog = false },
                title = { Text("Teacher Assisted Check-in", fontWeight = FontWeight.Bold) },
                text = {
                    Column(modifier = Modifier.fillMaxWidth().height(300.dp)) {
                        Text("Select student to mark present or repair profile:", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        Spacer(modifier = Modifier.height(8.dp))
                        if (enrolledNotPresent.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No pending enrolled students.", color = Color.Gray)
                            }
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(enrolledNotPresent, key = { it.id }) { student ->
                                    val isReenroll = student.enrollmentStatus == EnrollmentStatus.REENROLL_REQUIRED
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color.White.copy(alpha = 0.05f))
                                            .feedbackClickable {
                                                val grant = TeacherAuthorizationGrant(
                                                    sessionId = activeSessionId ?: 0L,
                                                    classId = classId,
                                                    studentId = student.id,
                                                    action = if (isReenroll) TeacherAuthAction.RE_ENROLLMENT
                                                             else TeacherAuthAction.TEACHER_ASSISTED_CHECKIN,
                                                    interactionId = currentInteractionToken,
                                                    expiresAtMillis = System.currentTimeMillis() + 120_000L,
                                                )
                                                showTeacherAssistDialog = false
                                                if (isReenroll) {
                                                    startInlineEnrollment(student, grant)
                                                } else {
                                                    markTeacherAssistedPresent(student, grant)
                                                }
                                            }
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(student.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                            Text(
                                                if (isReenroll) "${student.rollNumber} • [Re-Enroll Required]" else student.rollNumber,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (isReenroll) MaterialTheme.colorScheme.error else Color.Gray
                                            )
                                        }
                                        Text(
                                            if (isReenroll) "Repair" else "Mark Present",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showTeacherAssistDialog = false }) {
                        Text("Close")
                    }
                }
            )
        }

        // Teacher Exit PIN Verification Dialog
        if (showPinDialog) {
            AlertDialog(
                onDismissRequest = {
                    if (!isAuthenticating) {
                        showPinDialog = false
                        teacherPinInput = ""
                        pinError = null
                    }
                },
                title = { Text("Teacher PIN Required", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Enter your 4 — 12 digit teacher PIN to exit screen pinning and end the session.")
                        OutlinedTextField(
                            value = teacherPinInput,
                            onValueChange = {
                                teacherPinInput = it.filter(Char::isDigit).take(12)
                                pinError = null
                            },
                            label = { Text("Teacher PIN") },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine = true,
                            isError = pinError != null,
                            supportingText = {
                                pinError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (teacherPinInput.isBlank()) {
                                pinError = "PIN is required."
                                return@Button
                            }
                            isAuthenticating = true
                            scope.launch {
                                val success = repository.authenticate(teacherPinInput.toCharArray())
                                isAuthenticating = false
                                if (success) {
                                    showPinDialog = false
                                    teacherPinInput = ""
                                    if (presentIds.isEmpty()) {
                                        showZeroConfirmDialog = true
                                    } else {
                                        try {
                                            lockTaskController?.stop()
                                            isScreenPinned = false
                                            Log.d(TAG, "Screen pinning stopped on session exit")
                                        } catch (e: Exception) {
                                            Log.w(TAG, "stopLockTask failed: ${e.message}")
                                        }
                                        onEndSession(presentIds)
                                    }
                                } else {
                                    pinError = "Incorrect PIN. Try again."
                                }
                            }
                        },
                        enabled = !isAuthenticating
                    ) {
                        Text(if (isAuthenticating) "Verifying..." else "End Session & Unpin")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showPinDialog = false
                            teacherPinInput = ""
                            pinError = null
                        },
                        enabled = !isAuthenticating
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Issue 1: 3-Way Zero-Student Exit Confirmation Dialog with back arrow in title
        if (showZeroConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showZeroConfirmDialog = false },
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AttractIconButton(onClick = { showZeroConfirmDialog = false }) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Cancel"
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("No Students Present", fontWeight = FontWeight.Bold)
                    }
                },
                text = { Text("You haven't recorded any attendance yet. What would you like to do?") },
                confirmButton = {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = {
                                showZeroConfirmDialog = false
                                scope.launch {
                                    try {
                                        lockTaskController?.stop()
                                        isScreenPinned = false
                                    } catch (_: Exception) {}
                                    onEndSession(emptySet())
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Save (0 Present) & Exit")
                        }
                        OutlinedButton(
                            onClick = {
                                showZeroConfirmDialog = false
                                scope.launch {
                                    try {
                                        lockTaskController?.stop()
                                        isScreenPinned = false
                                    } catch (_: Exception) {}
                                    onDiscardSession()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Discard & Exit")
                        }
                    }
                },
                dismissButton = null
            )
        }
    }
}








