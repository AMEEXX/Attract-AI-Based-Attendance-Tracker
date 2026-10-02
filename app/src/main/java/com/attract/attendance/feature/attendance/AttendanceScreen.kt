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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.attract.attendance.core.model.ClassSummary
import com.attract.attendance.core.model.EnrollmentStatus
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
    ERROR
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
    isStandaloneMode: Boolean = false,
    targetStudentForStandalone: StudentSummary? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
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
    var statusMessage by remember { mutableStateOf("📷 Step 1/3 (STRAIGHT): Position face straight in ample lighting & tap CLICK") }

    // 2c. CANONICAL SESSION START (LLD-06 amendment, 2026-08-26): the attendance
    // session is created/resolved HERE — the DB row is the single source of truth.
    // Without this, every recognition/fallback persistence correctly refused with
    // NoActiveSession (the phone deadlock-loop bug).
    LaunchedEffect(Unit) {
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

    var showPinDialog by remember { mutableStateOf(false) }
    var teacherPinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }
    var isAuthenticating by remember { mutableStateOf(false) }
    var showZeroConfirmDialog by remember { mutableStateOf(false) }
    var showEnrollBottomSheet by remember { mutableStateOf(false) }
    var selectedStudentForEnroll by remember(targetStudentForStandalone) { mutableStateOf<StudentSummary?>(targetStudentForStandalone) }

    var latestFrameSignals by remember { mutableStateOf<FaceQualitySignals?>(null) }
    var latestFrameBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var lastQualitySignals by remember { mutableStateOf<FaceQualitySignals?>(null) }
    var capturedPoseBitmaps by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var isValidatingFrame by remember { mutableStateOf(false) }

    // Step-aware capture machine (LLD-09): 0 = STRAIGHT, 1 = LEFT profile, 2 = RIGHT profile.
    var captureStep by remember { mutableIntStateOf(0) }
    var capturedYawDegrees by remember { mutableStateOf<List<Float>>(emptyList()) }
    var capturedQualityScores by remember { mutableStateOf<List<Float>>(emptyList()) }

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

    fun stepPrompt(step: Int): String {
        if (isStandaloneMode) return when (step) {
            0 -> "📷 Step 1/3 (STRAIGHT): Position face straight in ample lighting & tap CLICK"
            1 -> "👈 Step 2/3 (LEFT PROFILE): Turn your head LEFT until your profile shows & tap CLICK"
            else -> "👉 Step 3/3 (RIGHT PROFILE): Turn your head RIGHT until your profile shows & tap CLICK"
        }
        return when (step) {
            0 -> "📷 Step 1: Look straight at the camera in ample lighting & tap CLICK"
            1 -> "🙂 Almost there — one more natural look at the camera & tap CLICK"
            else -> "🙂 Final check — one more natural capture & tap CLICK"
        }
    }

    fun resetToReady(resetAttempts: Boolean = false) {
        Log.i("ATTRACT_ATTENDANCE_PIPELINE", "[READY] sessionId=$activeSessionId classId=$classId presentCount=${presentIds.size}")
        if (resetAttempts) {
            recognitionAttemptCount = 0
        }
        state = SessionScreenState.READY
        collectedFrames = 0
        statusMessage = stepPrompt(0)
        selectedStudentForEnroll = null
        lastQualitySignals = null
        capturedPoseBitmaps = emptyList()
        isValidatingFrame = false
        captureStep = 0
        capturedYawDegrees = emptyList()
        capturedQualityScores = emptyList()
        adaptiveEngine = null
        lastFrameQualityScore = 0f
        lastFrameLive = false
        inlineEnrollmentTarget = null
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
        // Fallback roster = every student in the class not yet marked PRESENT this session
        // (enrolled or not — an unrecognized ENROLLED student must also be selectable).
        Log.i("ATTRACT_ATTENDANCE_FALLBACK", "Fallback roster offered: classId=$classId candidates=${students.size - presentIds.size}")
        val selectable = students.filter { it.id !in presentIds }
        if (selectable.isEmpty()) {
            state = SessionScreenState.READY
            statusMessage = "All students are already marked present."
        } else {
            recognitionAttemptCount = 0
            state = SessionScreenState.UNKNOWN_STUDENT
            statusMessage = "Face not recognized. Select the student below."
            showEnrollBottomSheet = true
        }
    }

    /** Starts the 3-pose inline enrollment capture within the attendance screen. */
    fun startInlineEnrollment(selected: StudentSummary) {
        inlineEnrollmentTarget = selected
        captureStep = 0
        collectedFrames = 0
        capturedPoseBitmaps = emptyList()
        capturedYawDegrees = emptyList()
        capturedQualityScores = emptyList()
        lastQualitySignals = null
        adaptiveEngine = null
        isValidatingFrame = false
        state = SessionScreenState.CAPTURING
        statusMessage = "📋 Enrolling ${selected.name} — Step 1/3 (STRAIGHT): Look at camera & tap CLICK"
        Log.i("ATTRACT_ATTENDANCE_FALLBACK", "Inline enrollment started for studentId=${selected.id} name=${selected.name}")
    }

    /** Marks an already-enrolled student as present via MANUAL fallback. */
    fun markFallbackAttendance(selected: StudentSummary) {
        state = SessionScreenState.PROCESSING
        statusMessage = "Marking ${selected.name} present..."
        scope.launch {
            Log.i("ATTRACT_ATTENDANCE_FALLBACK", "[ATTENDANCE_WRITE_STARTED] sessionId=$activeSessionId studentId=${selected.id} source=MANUAL")
            when (val result = repository.markFallbackPresent(classId, selected.id)) {
                is com.attract.attendance.data.repository.AttractRepository.FallbackMarkResult.Marked -> {
                    Log.i("ATTRACT_ATTENDANCE_PIPELINE", "[ATTENDANCE_WRITE_SUCCESS] sessionId=$activeSessionId studentId=${selected.id} recordId=${result.recordId}")
                    presentIds = presentIds + selected.id
                    lastRecognizedStudent = selected
                    state = SessionScreenState.MATCH_SUCCESS
                    statusMessage = "PRESENT: ${result.studentName}"
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

    /**
     * Teacher selects a student from the UNKNOWN roster (LLD-06).
     *
     * Routes based on enrollment status:
     *  - NOT_ENROLLED → start INLINE enrollment: capture 3 poses right here in the
     *    attendance screen, enroll, then auto-mark PRESENT.
     *  - ENROLLED → mark PRESENT directly via MANUAL fallback.
     */
    fun onRosterStudentSelected(selected: StudentSummary) {
        Log.i("ATTRACT_ATTENDANCE_PIPELINE",
            "[ROSTER_SELECTION] sessionId=$activeSessionId classId=$classId studentId=${selected.id} " +
                "studentName=${selected.name} enrollmentStatus=${selected.enrollmentStatus} uiState=$state")
        selectedStudentForEnroll = selected
        showEnrollBottomSheet = false

        if (selected.enrollmentStatus == EnrollmentStatus.NOT_ENROLLED) {
            // Inline enrollment: capture 3 poses right here, then enroll + mark present
            startInlineEnrollment(selected)
        } else {
            // Already enrolled but just not recognized → mark present via MANUAL fallback
            markFallbackAttendance(selected)
        }
    }

    fun markSelectedStudentPresent(selected: StudentSummary) = onRosterStudentSelected(selected)

    /** Completes inline enrollment: extracts embeddings from captured frames, enrolls, marks PRESENT. */
    fun completeInlineEnrollment() {
        val target = inlineEnrollmentTarget ?: return
        Log.i("ATTRACT_ATTENDANCE_FALLBACK", "[INLINE_ENROLLMENT] completing for studentId=${target.id} name=${target.name} frames=${capturedPoseBitmaps.size}")

        if (capturedPoseBitmaps.size < 3) {
            statusMessage = "Need all 3 poses. Retake missing ones."
            state = SessionScreenState.CAPTURING
            isValidatingFrame = false
            return
        }

        state = SessionScreenState.PROCESSING
        statusMessage = "Saving ${target.name}'s face data..."

        scope.launch {
            runCatching {
                val realEmbeddings = withContext(Dispatchers.IO) {
                    capturedPoseBitmaps.mapNotNull { bitmap ->
                        try {
                            com.attract.attendance.domain.face.EmbeddingEngine.extractEmbedding(context, bitmap)
                        } catch (_: Exception) { null }
                    }
                }
                if (realEmbeddings.isEmpty()) {
                    state = SessionScreenState.ERROR
                    statusMessage = "Face capture failed. Please retake photos."
                    delay(2000); resetToReady(); return@runCatching
                }

                val byteEmbeddings = realEmbeddings.map { emb ->
                    with(com.attract.attendance.domain.face.TemplateMatcher) { emb.toByteArray() }
                }
                Log.i("ATTRACT_ATTENDANCE_FALLBACK", "[INLINE_ENROLLMENT] calling onEnrollStudent for ${target.id}, ${byteEmbeddings.size} embeddings")

                onEnrollStudent(target.id, byteEmbeddings, capturedQualityScores) {
                    scope.launch {
                        Log.i("ATTRACT_ATTENDANCE_FALLBACK", "[INLINE_ENROLLMENT] enrollment succeeded, now marking PRESENT")
                        // After enrollment succeeds → mark PRESENT immediately
                        when (val result = repository.markFallbackPresent(classId, target.id)) {
                            is com.attract.attendance.data.repository.AttractRepository.FallbackMarkResult.Marked -> {
                                Log.i("ATTRACT_ATTENDANCE_PIPELINE", "[ATTENDANCE_WRITE_SUCCESS] sessionId=$activeSessionId studentId=${target.id} recordId=${result.recordId} source=MANUAL(enrolled)")
                                presentIds = presentIds + target.id
                                lastRecognizedStudent = target
                                state = SessionScreenState.MATCH_SUCCESS
                                statusMessage = "ENROLLED & PRESENT: ${target.name}"
                                delay(2500)
                                resetToReady(resetAttempts = true)
                            }
                            is com.attract.attendance.data.repository.AttractRepository.FallbackMarkResult.AlreadyPresent -> {
                                state = SessionScreenState.ALREADY_PRESENT
                                statusMessage = "Already Checked In: ${target.name}"
                                delay(2000)
                                resetToReady(resetAttempts = true)
                            }
                            else -> {
                                // Enrollment succeeded but attendance marking failed — still a win
                                state = SessionScreenState.MATCH_SUCCESS
                                statusMessage = "ENROLLED: ${target.name}"
                                delay(2500)
                                resetToReady(resetAttempts = true)
                            }
                        }
                    }
                }
            }.onFailure { error ->
                Log.e(TAG, "Inline enrollment error", error)
                isValidatingFrame = false
                state = SessionScreenState.ERROR
                statusMessage = "Enrollment failed. Please try again."
                delay(2500)
                resetToReady()
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
            statusMessage = "Already Checked In: ${matchedStudent.name}"
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
                    statusMessage = "PRESENT: ${matchedStudent.name}"
                }
                is com.attract.attendance.data.repository.AttractRepository.FallbackMarkResult.AlreadyPresent -> {
                    state = SessionScreenState.ALREADY_PRESENT
                    statusMessage = "Already Checked In: ${matchedStudent.name}"
                }
                else -> {
                    Log.e("ATTRACT_ATTENDANCE_FALLBACK", "AI attendance persist failed: $result")
                    state = SessionScreenState.ERROR
                    statusMessage = "Could not record attendance. Please try again."
                }
            }
            delay(2000)
            isValidatingFrame = false // terminal outcome must release the capture lock
            resetToReady(resetAttempts = true)
        }
    }

    fun onAdaptiveMatch(studentId: Long, confidence: Float) {
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
        if (recognitionAttemptCount < 1) {
            recognitionAttemptCount += 1
            state = SessionScreenState.READY
            statusMessage = "Couldn't verify clearly, please try again (Attempt 1/2)."
            scope.launch { delay(2000); resetToReady(resetAttempts = false) }
        } else {
            recognitionAttemptCount = 0
            handleNoEnrolledStudents()
        }
    }

    fun captureClick() {
        if (state == SessionScreenState.PROCESSING || state == SessionScreenState.MATCH_SUCCESS || isValidatingFrame) return
        Log.i("ATTRACT_ATTENDANCE_PIPELINE", "[CAPTURE_STARTED] sessionId=$activeSessionId classId=$classId uiState=$state")

        val signals = latestFrameSignals
        val frameBitmap = latestFrameBitmap

        if (signals == null || signals.faceCount == 0) {
            statusMessage = "⚠️ No face detected. Position your face in the frame."
            return
        }

        if (signals.faceCount > 1) {
            statusMessage = "⚠️ Multiple faces detected. Only one person at a time."
            return
        }

        isValidatingFrame = true

        // Pose gating: STANDALONE ENROLLMENT keeps the original strict 3-step windows
        // (STRAIGHT/LEFT/RIGHT per LLD-09). Live attendance uses adaptive gating (LLD-16):
        // frame 1 expects STRAIGHT; supporting frames accept any natural pose and reject
        // only EXTREMES, using existing production thresholds.
        val qualityEval: QualityResult = if (isStandaloneMode || inlineEnrollmentTarget != null) {
            // Strict pose gating for enrollment captures (standalone AND inline)
            val expectedPose = when (captureStep) {
                0 -> com.attract.attendance.domain.face.ExpectedPose.STRAIGHT
                1 -> com.attract.attendance.domain.face.ExpectedPose.LEFT
                else -> com.attract.attendance.domain.face.ExpectedPose.RIGHT
            }
            FaceQualityEngine.evaluate(signals, FaceQualityConfig.calibrationDefaults(), expectedPose)
        } else if (captureStep == 0) {
            FaceQualityEngine.evaluate(signals, FaceQualityConfig.calibrationDefaults())
        } else {
            val supportPoseOk =
                com.attract.attendance.domain.face.AdaptiveVerificationEngine.isAcceptableSupportPose(
                    signals, FaceQualityConfig.calibrationDefaults(),
                )
            if (!supportPoseOk) {
                statusMessage = "⚠️ Keep your head roughly facing the camera  —  extreme angles can't be used."
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
            statusMessage = when (qualityEval.reason) {
                com.attract.attendance.domain.face.QualityReason.DARK -> "⚠️ Lighting too dark. Move to better lighting."
                com.attract.attendance.domain.face.QualityReason.OVEREXPOSED -> "⚠️ Too bright/glare. Adjust lighting."
                com.attract.attendance.domain.face.QualityReason.BLUR -> "⚠️ Image blurry. Hold steady and try again."
                com.attract.attendance.domain.face.QualityReason.TOO_SMALL -> "⚠️ Move closer to the camera."
                com.attract.attendance.domain.face.QualityReason.OFF_CENTER -> "⚠️ Center your face inside the frame."
                com.attract.attendance.domain.face.QualityReason.POSE_NOT_STRAIGHT -> "⚠️ Please look straight at the camera for this step."
                com.attract.attendance.domain.face.QualityReason.POSE_NOT_LEFT -> "ðŸ‘ˆ Turn your head LEFT until your profile shows, then tap CLICK."
                com.attract.attendance.domain.face.QualityReason.POSE_NOT_RIGHT -> "ðŸ‘‰ Turn your head RIGHT until your profile shows, then tap CLICK."
                com.attract.attendance.domain.face.QualityReason.POSE -> "⚠️ Keep your head level  —  do not tilt up or down."
                com.attract.attendance.domain.face.QualityReason.EYES_UNCLEAR -> "⚠️ Please keep your eyes open."
                else -> "⚠️ Quality check failed. Please reposition."
            }
            isValidatingFrame = false
            return
        }

        // Duplicate-frame guard (LLD-09): standalone enrollment requires distinct poses;
        // live attendance handles replays inside the adaptive engine instead.
        if (isStandaloneMode && !FaceQualityEngine.isDistinctFromCaptured(signals.yawDegrees, capturedYawDegrees)) {
            statusMessage = "⚠️ Turn your head more  —  this angle matches a previous capture."
            isValidatingFrame = false
            return
        }

        // Presentation-attack signals (LLD-12) computed once per capture on the face crop.
        val patSignals = PresentationAttackAnalyzer.analyze(frameBitmap)

        val livenessEval = LivenessEngine.check(signals, lastQualitySignals, patSignals)
        if (livenessEval is LivenessResult.Rejected) {
            statusMessage = "⚠️ ${livenessEval.message}"
            isValidatingFrame = false
            return
        }

        lastQualitySignals = signals
        lastFrameLive = livenessEval is LivenessResult.Passed
        lastFrameQualityScore = (qualityEval as QualityResult.Accepted).score
        capturedYawDegrees = capturedYawDegrees + signals.yawDegrees
        capturedQualityScores = capturedQualityScores + qualityEval.score
        if (frameBitmap != null) {
            capturedPoseBitmaps = capturedPoseBitmaps + frameBitmap
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
                    1 -> { captureStep = 1; state = SessionScreenState.CAPTURING; statusMessage = stepPrompt(1) }
                    2 -> { captureStep = 2; state = SessionScreenState.CAPTURING; statusMessage = stepPrompt(2) }
                    else -> {
                        state = SessionScreenState.FRAMES_COLLECTED
                        statusMessage = "✅ Straight + Left + Right photos captured & validated! Tap SUBMIT"
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
                val poseName = when (nextFrame) { 1 -> "LEFT" else -> "RIGHT" }
                statusMessage = "📋 Enrolling ${target.name} — Step ${nextFrame + 1}/3 ($poseName): Tap CLICK"
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
        statusMessage = "Analyzing face..."
        scope.launch {
            runCatching {
                val signalsNow = lastQualitySignals
                val liveNow = lastFrameLive
                val scoreNow = lastFrameQualityScore
                val cropBitmap = frameBitmap
                if (signalsNow == null || cropBitmap == null) {
                    statusMessage = "⚠️ Frame capture failed. Please retake."
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
                        statusMessage = step.reason + " Tap CLICK"
                        isValidatingFrame = false
                    }
                    is com.attract.attendance.domain.face.AdaptiveVerificationEngine.Step.Final -> {
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
                val engine = com.attract.attendance.domain.face.RecognitionDecisionEngine(
                    acceptThreshold = 0.25f,
                    ambiguousMargin = 0.05f
                )

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
                IconButton(onClick = onBack) {
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
                    Text(
                        text = "ðŸ“Œ Screen Pinned (Attendance Mode)",
                        color = Color(0xFF81C995),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                } else {
                    // Pinning in progress or not yet confirmed
                    Text(
                        text = "⏳ Starting session...",
                        color = Color.White.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                IconButton(onClick = { showPinDialog = true }) {
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
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF111111))
                    .border(
                        width = 2.dp,
                        color = when (state) {
                            SessionScreenState.MATCH_SUCCESS -> Color(0xFF81C995)
                            SessionScreenState.CAPTURING -> Color(0xFFFDD835)
                            else -> Color(0xFF333333)
                        },
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (hasCameraPermission) {
                    CameraPreview(
                        modifier = Modifier.fillMaxSize(),
                        onFrameAnalyzed = { signals, frameBitmap ->
                            latestFrameSignals = signals
                            latestFrameBitmap = frameBitmap
                        }
                    )
                } else {
                    Text("Camera permission required", color = Color.White, style = MaterialTheme.typography.bodySmall)
                }

                if (state == SessionScreenState.PROCESSING) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color.White)
                    }
                } else if (state == SessionScreenState.MATCH_SUCCESS) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E8E3E)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Verified",
                            tint = Color.White,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Glass Status Pill
            Box(
                modifier = Modifier
                    .clip(com.attract.attendance.ui.theme.PillShape)
                    .background(Color.White.copy(alpha = 0.08f))
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = statusMessage,
                    color = when (state) {
                        SessionScreenState.MATCH_SUCCESS -> com.attract.attendance.ui.theme.CameraScreenColors.glowEmerald
                        SessionScreenState.ALREADY_PRESENT -> com.attract.attendance.ui.theme.CameraScreenColors.glowAmber
                        else -> com.attract.attendance.ui.theme.CameraScreenColors.onBackground
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
            }

            if (state == SessionScreenState.MATCH_SUCCESS && lastRecognizedStudent != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "${lastRecognizedStudent?.rollNumber}",
                    color = Color.Gray,
                    style = com.attract.attendance.ui.theme.NumericBody
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Progress Indicators & Retake Option for Frame Collection
            if (state == SessionScreenState.CAPTURING || state == SessionScreenState.FRAMES_COLLECTED) {
                com.attract.attendance.ui.components.biometric.PoseProgress(completedCount = collectedFrames)
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

            // Hero Glow Action Button (120dp Halo with 72dp Core)
            if (state == SessionScreenState.READY || state == SessionScreenState.CAPTURING || state == SessionScreenState.FRAMES_COLLECTED || state == SessionScreenState.UNKNOWN_STUDENT) {
                val glowState = when (state) {
                    SessionScreenState.FRAMES_COLLECTED -> com.attract.attendance.ui.components.biometric.GlowButtonState.SUBMIT
                    SessionScreenState.MATCH_SUCCESS -> com.attract.attendance.ui.components.biometric.GlowButtonState.SUCCESS
                    SessionScreenState.ALREADY_PRESENT -> com.attract.attendance.ui.components.biometric.GlowButtonState.WARNING
                    SessionScreenState.UNKNOWN_STUDENT -> com.attract.attendance.ui.components.biometric.GlowButtonState.ERROR
                    else -> com.attract.attendance.ui.components.biometric.GlowButtonState.READY
                }

                // Issue 3: Reopening Roster Sheet when selectedStudentForEnroll == null
                if (state == SessionScreenState.UNKNOWN_STUDENT && selectedStudentForEnroll == null) {
                    TextButton(
                        onClick = { showEnrollBottomSheet = true },
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text(if (isStandaloneMode) "ðŸ“‹ Select ID to Enroll" else "ðŸ“‹ Select Student to Mark Present", color = Color(0xFF8AB4F8), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                    }
                } else {
                    com.attract.attendance.ui.components.biometric.GlowCaptureButton(
                        buttonState = glowState,
                        onClick = {
                            if (state == SessionScreenState.PROCESSING || isValidatingFrame) return@GlowCaptureButton
                            if (state == SessionScreenState.FRAMES_COLLECTED) {
                                submitRecognition()
                            } else if (state == SessionScreenState.UNKNOWN_STUDENT) {
                                if (isStandaloneMode) {
                                    // Standalone mode keeps the dedicated enrollment flow.
                                    if (selectedStudentForEnroll != null) {
                                        val targetStudent = selectedStudentForEnroll!!
                                        state = SessionScreenState.PROCESSING
                                        statusMessage = "Enrolling ${targetStudent.name}..."

                                        scope.launch {
                                            val realEmbeddings = withContext(Dispatchers.IO) {
                                                capturedPoseBitmaps.map { bitmap ->
                                                    com.attract.attendance.domain.face.EmbeddingEngine.extractEmbedding(context, bitmap)
                                                }
                                            }

                                            if (realEmbeddings.isEmpty()) {
                                                state = SessionScreenState.ERROR
                                                statusMessage = "Face capture failed. Please retake photos."
                                                delay(2000)
                                                resetToReady()
                                                return@launch
                                            }

                                            // Duplicate face check across all enrolled class templates
                                            val combinedQuery = com.attract.attendance.domain.face.EmbeddingEngine.combineEmbeddings(realEmbeddings)
                                            val activeTemplates = repository.getActiveTemplatesForClass(classId)
                                            val checkOutcome = com.attract.attendance.domain.face.RecognitionDecisionEngine(
                                                acceptThreshold = 0.25f,
                                                ambiguousMargin = 0.05f
                                            ).evaluate(combinedQuery, activeTemplates)

                                            if (checkOutcome is com.attract.attendance.domain.face.RecognitionOutcome.Match && checkOutcome.studentId != targetStudent.id) {
                                                state = SessionScreenState.ERROR
                                                statusMessage = "⚠️ Face already enrolled under another student."
                                                delay(2500)
                                                resetToReady()
                                                return@launch
                                            }

                                            val byteEmbeddings = realEmbeddings.map { emb ->
                                                with(com.attract.attendance.domain.face.TemplateMatcher) { emb.toByteArray() }
                                            }

                                            onEnrollStudent(targetStudent.id, byteEmbeddings, capturedQualityScores) {
                                                state = SessionScreenState.MATCH_SUCCESS
                                                statusMessage = "ENROLLED SUCCESSFULLY: ${targetStudent.name}"
                                                scope.launch {
                                                    delay(1500)
                                                    onBack()
                                                }
                                            }
                                        }
                                    } else {
                                        showEnrollBottomSheet = true
                                    }
                                } else {
                                    // LLD-06 fallback: open roster, or mark the already-selected
                                    // student present immediately (idempotent via AlreadyPresent).
                                    Log.i("ATTRACT_ATTENDANCE_FALLBACK", "Fallback button tapped: selected=${selectedStudentForEnroll?.id}")
                                    val selected = selectedStudentForEnroll
                                    if (selected == null) {
                                        showEnrollBottomSheet = true
                                    } else {
                                        markSelectedStudentPresent(selected)
                                    }
                                }
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
            Box(
                modifier = Modifier
                    .clip(com.attract.attendance.ui.theme.PillShape)
                    .background(Color.White.copy(alpha = 0.05f))
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "Present: ${presentIds.size} / ${students.size}",
                    color = Color.White.copy(alpha = 0.6f),
                    style = com.attract.attendance.ui.theme.NumericBody,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Un-enrolled Roster Selection dialog (Bottom Sheet emulation)
        if (showEnrollBottomSheet) {
            val selectable = students.filter { it.id !in presentIds }
            AlertDialog(
                onDismissRequest = { showEnrollBottomSheet = false },
                title = { Text(if (isStandaloneMode) "Select your Name & ID to Enroll" else "Select Student to Mark Present", fontWeight = FontWeight.Bold) },
                text = {
                    Column(modifier = Modifier.height(280.dp)) {
                        Text(if (isStandaloneMode) "Only un-enrolled students are listed:" else "Students not yet marked present:", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(selectable, key = { it.id }) { student ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (selectedStudentForEnroll?.id == student.id) Color(0xFF333333) else Color.Transparent)
                                        .clickable {
                                            if (isStandaloneMode) {
                                                // Standalone enrollment flow keeps its dedicated behavior.
                                                selectedStudentForEnroll = student
                                                showEnrollBottomSheet = false
                                            } else {
                                                // LLD-06: selecting a student routes to inline
                                                // enrollment (if NOT_ENROLLED) or MANUAL fallback.
                                                onRosterStudentSelected(student)
                                            }
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(student.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                        Text(student.rollNumber, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showEnrollBottomSheet = false }) {
                        Text("Cancel")
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
                                    // Per LLD-13 end order: finalize attendance  ->  clear cache  ->  stopLockTask()
                                    // Stop lock task BEFORE calling onEndSession so the app unpins properly.
                                    try {
                                        lockTaskController?.stop()
                                        isScreenPinned = false
                                        Log.d(TAG, "Screen pinning stopped on teacher auth success")
                                    } catch (e: Exception) {
                                        Log.w(TAG, "stopLockTask failed: ${e.message}")
                                        // Continue  —  don't block session end if unpin fails
                                    }
                                    if (presentIds.isEmpty()) {
                                        showZeroConfirmDialog = true
                                    } else {
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
                        IconButton(onClick = { showZeroConfirmDialog = false }) {
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
                                onEndSession(emptySet())
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Save (0 Present) & Exit")
                        }
                        OutlinedButton(
                            onClick = {
                                showZeroConfirmDialog = false
                                onDiscardSession()
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








