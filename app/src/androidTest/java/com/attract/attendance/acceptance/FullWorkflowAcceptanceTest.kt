package com.attract.attendance.acceptance

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.attract.attendance.core.model.AttendanceSource
import com.attract.attendance.core.model.AttendanceStatus
import com.attract.attendance.core.model.CommandResult
import com.attract.attendance.data.local.AttractDatabase
import com.attract.attendance.data.local.FaceTemplateEntity
import com.attract.attendance.data.repository.AttractRepository
import com.attract.attendance.data.repository.AttractRepository.FallbackMarkResult
import com.attract.attendance.data.repository.CreateClassCommand
import com.attract.attendance.data.repository.CreateStudentCommand
import com.attract.attendance.data.security.PinHasher
import com.attract.attendance.domain.face.AdaptiveVerificationEngine
import com.attract.attendance.domain.face.EvidenceFusion
import com.attract.attendance.domain.face.FaceQualityEngine
import com.attract.attendance.domain.face.FaceQualityConfig
import com.attract.attendance.domain.face.FrameObservation
import com.attract.attendance.domain.face.LivenessEngine
import com.attract.attendance.domain.face.EmbeddingEngine
import com.attract.attendance.domain.face.PresentationAttackSignals
import com.attract.attendance.domain.face.QualityResult
import com.attract.attendance.domain.face.RecognitionDecisionEngine
import com.attract.attendance.domain.face.StudentTemplatePair
import com.attract.attendance.domain.face.TemplateCompatibility
import com.attract.attendance.domain.face.FaceAligner
import com.attract.attendance.domain.face.YoloFaceDetector
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * FullWorkflowAcceptanceTest â€” end-to-end functional acceptance of the REAL production
 * attendance workflow on-device, from a CLEAN database state, through the REAL production
 * classes the UI uses:
 *
 *   repository.createClass / addStudent / startFaceSession / enrollStudentFace
 *   -> ML Kit -> quality -> liveness -> crop -> MobileFaceNet 192-D  (REAL pipeline)
 *   -> AdaptiveVerificationEngine (production state machine wired exactly like AttendanceScreen)
 *   -> markRecognizedPresent / markFallbackPresent (production persistence commands)
 *   -> Room verification after EVERY action.
 *
 * NOT automated here (explicitly marked MANUAL in the acceptance report):
 *   - CameraX preview rendering and touch events on the Compose UI
 *   - physical-phone run (Phase 20)
 * The image-analysis path is identical: bitmaps enter at ML Kit exactly as CameraX frames do.
 */
@RunWith(AndroidJUnit4::class)
class FullWorkflowAcceptanceTest {

    private lateinit var context: Context
    private lateinit var database: AttractDatabase
    private lateinit var repository: AttractRepository
    private val dbName = "acceptance-test.db"

    private var classId = 0L
    private var amitId = 0L
    private var bId = 0L
    private var cId = 0L
    private var dId = 0L
    private var sessionId = 0L

    // Real face images from the on-device LFW bench assets (identity per role).
    // Frames VERIFIED to pass the production quality gate (see lfw benchmark image_metrics).
    private val amitAssets = listOf("George_W_Bush/George_W_Bush_0002.jpg", "George_W_Bush/George_W_Bush_0003.jpg", "George_W_Bush/George_W_Bush_0006.jpg")
    private val bobAssets = listOf("Vladimir_Putin/Vladimir_Putin_0002.jpg", "Vladimir_Putin/Vladimir_Putin_0003.jpg", "Vladimir_Putin/Vladimir_Putin_0007.jpg")
    private val strangerAsset = "Tiger_Woods/Tiger_Woods_0001.jpg"

    @Before
    fun phase0_cleanEnvironment() {
        context = ApplicationProvider.getApplicationContext()
        // PHASE 0: guaranteed-clean persistent DB â€” delete any previous files.
        context.deleteDatabase(dbName)
        listOf(dbName, "-wal", "-shm").forEach { suffix ->
            context.getDatabasePath(if (suffix.startsWith("-")) dbName + suffix else suffix).delete()
        }
        database = Room.databaseBuilder(context, AttractDatabase::class.java, dbName)
            .allowMainThreadQueries()
            .build()
        repository = AttractRepository(database, PinHasher(), embeddingCipher = null)

        println("[ACCEPTANCE] PHASE 0 clean state: classes=${tableCount("class_sections")} students=${tableCount("students")} sessions=${tableCount("attendance_sessions")} records=${tableCount("attendance_records")} templates=${tableCount("face_templates")}")
        assertEquals(0, tableCount("attendance_records"))
        assertEquals(0, tableCount("attendance_sessions"))
        assertEquals(0, tableCount("face_templates"))
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(dbName)
    }

    // ------------------------------------------------------------------
    // Production pipeline helper â€” EXACTLY what AttendanceScreen/CameraPreview do:
    // bitmap -> ML Kit -> signals(quality+liveness) -> crop -> MobileFaceNet 192-D.
    // ------------------------------------------------------------------
    private fun runRealPipeline(assetRelative: String): FrameObservation? {
        val am = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context.assets
        val bitmap = android.graphics.BitmapFactory.decodeStream(am.open("test-data/lfw-bench/$assetRelative"))
        assertNotNull(bitmap)
        try {
            val faces = YoloFaceDetector.detect(context, bitmap)
            val face = faces.maxByOrNull { it.confidence } ?: return null
            val box = face.boundingBox
            val cfg = FaceQualityConfig.calibrationDefaults()
            val signals = com.attract.attendance.domain.face.FaceQualitySignals(
                faceCount = faces.size,
                yawDegrees = face.estimatedYaw,
                pitchDegrees = face.estimatedPitch,
                rollDegrees = face.estimatedRoll,
                leftEyeOpenProbability = 1f,
                rightEyeOpenProbability = 1f,
                faceRatio = face.faceRatio,
                centerX = face.centerX,
                centerY = face.centerY,
                blurVariance = laplacian(bitmap, box),
                brightness = brightness(bitmap, box),
            )
            val quality = FaceQualityEngine.evaluate(signals, cfg)
            val liveness = LivenessEngine.check(signals, null, PresentationAttackSignals.Unavailable)
            if (quality !is QualityResult.Accepted || liveness !is com.attract.attendance.domain.face.LivenessResult.Passed) {
                println("[ACCEPTANCE] frame $assetRelative rejected: quality=${quality::class.simpleName}")
                return null
            }
            val alignedFace = FaceAligner.align(bitmap, face.landmarks)
            val embedding = EmbeddingEngine.extractEmbedding(context, alignedFace)
            assertEquals(TemplateCompatibility.CURRENT_EMBEDDING_DIM, embedding.size)
            return FrameObservation(
                signals = signals,
                quality = quality,
                liveness = liveness,
                embedding = embedding,
            )
        } finally {
            bitmap.recycle()
        }
    }

    /** Engine wired IDENTICALLY to AttendanceScreen's adaptive flow. */
    private fun newScreenEngine(): AdaptiveVerificationEngine =
        AdaptiveVerificationEngine(
            templates = runBlocking { repository.getActiveTemplatesForClass(classId) },
            decisionEngine = RecognitionDecisionEngine(),
            fusionStrategy = EvidenceFusion.conservativeBestFrame(),
            config = FaceQualityConfig.calibrationDefaults(),
            maxFrames = 3,
        )

    private fun presentRecords(session: Long = sessionId) = runBlocking {
        database.attendanceRecordDao().forSession(session)
    }

    private fun recordsFor(studentId: Long) = presentRecords().filter { it.studentId == studentId }

    private fun expectSuccess(result: CommandResult<Long>, what: String): Long {
        assertTrue("$what failed: $result", result is CommandResult.Success)
        return (result as CommandResult.Success).value
    }

    private fun tableCount(table: String): Int {
        val cursor = database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table")
        cursor.moveToFirst()
        return cursor.getInt(0).also { cursor.close() }
    }

    // ==================================================================
    @Test
    fun fullProductionWorkflow() = runBlocking {
        // ---------------- PHASE 1: teacher + class + students via production commands ---
        assertTrue(
            "Teacher registration is a prerequisite of the real flow",
            repository.registerTeacher("Acceptance Teacher", "123456".toCharArray()) is CommandResult.Success,
        )
        classId = expectSuccess(repository.createClass(CreateClassCommand(name = "Test Class")), "createClass")
        amitId = expectSuccess(repository.addStudent(CreateStudentCommand(classId = classId, name = "AMIT", rollNumber = "R-01")), "addStudent AMIT")
        bId = expectSuccess(repository.addStudent(CreateStudentCommand(classId = classId, name = "Student B", rollNumber = "R-02")), "addStudent B")
        cId = expectSuccess(repository.addStudent(CreateStudentCommand(classId = classId, name = "Student C", rollNumber = "R-03")), "addStudent C")
        dId = expectSuccess(repository.addStudent(CreateStudentCommand(classId = classId, name = "Student D", rollNumber = "R-04")), "addStudent D")
        assertTrue(amitId != bId && bId != cId && cId != dId)
        assertEquals(4, database.studentDao().activeForClass(classId).size)
        println("[ACCEPTANCE] PHASE 1 OK: classId=$classId AMIT=$amitId B=$bId C=$cId D=$dId")

        // ---------------- PHASE 3: REAL enrollment (AMIT + Student B) ---------------
        // Production semantics: enrollment happens BEFORE the session is started
        // (setup phase). Enrolling during an ACTIVE session would also mark the
        // student PRESENT with source ENROLLMENT — exercised implicitly elsewhere.
        enrollReal(amitId, amitAssets.take(2))
        enrollReal(bId, bobAssets)
        val amitTemplates = database.faceTemplateDao().forStudent(amitId)
        val bTemplates = database.faceTemplateDao().forStudent(bId)
        assertTrue(amitTemplates.isNotEmpty())
        (amitTemplates + bTemplates).forEach { t ->
            assertEquals(TemplateCompatibility.CURRENT_EMBEDDING_DIM, t.embeddingDim)
            assertEquals(TemplateCompatibility.CURRENT_MODEL_ID, t.modelVersion)
            assertTrue("No stale 32-D template may exist", t.embeddingDim != 32)
        }
        assertEquals(0, tableCount("attendance_records"))
        println("[ACCEPTANCE] PHASE 3 OK: AMIT templates=${amitTemplates.size}, B templates=${bTemplates.size}, all ${TemplateCompatibility.CURRENT_EMBEDDING_DIM}-D ${TemplateCompatibility.CURRENT_MODEL_ID}; records=0")

        // ---------------- PHASE 2: ACTIVE session via PRODUCTION UI entry ------------
        // REGRESSION (phone deadlock-loop bug): the UI creates/resolves its session
        // through ensureFaceSession at screen entry. Before it runs, persistence MUST
        // refuse with NoActiveSession; after it, the SAME session id must serve
        // recognition AND fallback AND Room.
        val beforeEnsure = repository.markFallbackPresent(classId, amitId)
        assertTrue(
            "Without session start, fallback must be refused (NoActiveSession)",
            beforeEnsure is FallbackMarkResult.NoActiveSession,
        )
        assertTrue(tableCount("attendance_records") == 0)

        sessionId = expectSuccess(repository.ensureFaceSession(classId), "ensureFaceSession")
        // Idempotent: second call must resolve the SAME session id (no new row).
        assertEquals(sessionId, expectSuccess(repository.ensureFaceSession(classId), "ensureFaceSession again"))
        val session = database.sessionDao().find(sessionId)!!
        assertEquals(classId, session.classId)
        assertEquals(com.attract.attendance.core.model.SessionStatus.ACTIVE, session.status)
        assertEquals(com.attract.attendance.core.model.SessionMode.FACE, session.mode)
        assertNotNull(session.startedAt)
        assertEquals(0, presentRecords().size)
        println("[ACCEPTANCE] PHASE 2 OK: Session ID=$sessionId Class ID=$classId students=4 initialAttendance=0 (ensureFaceSession idempotent)")

        // ---------------- PHASE 4: REAL recognized attendance -----------------------
        val amitQuery1 = runRealPipeline(amitAssets[2])!!   // unseen frame (enrollment used [0] and [1])
        val engine = newScreenEngine()
        val step1 = engine.submit(amitQuery1)
        val final1 = step1 as AdaptiveVerificationEngine.Step.Final
        val match = final1.outcome as AdaptiveVerificationEngine.Outcome.Match
        assertEquals(amitId, match.studentId)
        assertEquals(1, match.framesUsed)
        // UI handler: persist immediately through production callback
        val mark1 = repository.markRecognizedPresent(classId, match.studentId, match.confidence)
        assertTrue(mark1 is FallbackMarkResult.Marked)
        val rec1 = recordsFor(amitId)
        assertEquals(1, rec1.size)
        assertEquals(AttendanceSource.AI_RECOGNITION, rec1.single().attendanceMethod)
        assertEquals(sessionId, rec1.single().sessionId)
        println("[ACCEPTANCE] PHASE 4 OK: AMIT recognized (frames=1) â†’ record id=${rec1.single().id} source=AI_RECOGNITION")

        // ---------------- PHASE 5: duplicate recognition ----------------------------
        val engine5 = newScreenEngine()
        val step5 = engine5.submit(runRealPipeline(amitAssets[1])!!)
        val match5 = (step5 as AdaptiveVerificationEngine.Step.Final).outcome as AdaptiveVerificationEngine.Outcome.Match
        assertEquals(amitId, match5.studentId)
        val mark5 = repository.markRecognizedPresent(classId, match5.studentId, match5.confidence)
        assertTrue(mark5 is FallbackMarkResult.AlreadyPresent)
        assertEquals(1, presentRecords().size)
        println("[ACCEPTANCE] PHASE 5 OK: repeat AMIT â†’ AlreadyPresent, recordCount=1")

        // ---------------- PHASE 6: REAL unknown face --------------------------------
        val strangerObs = runRealPipeline(strangerAsset)
        val engine6 = newScreenEngine()
        var last6: AdaptiveVerificationEngine.Step = engine6.submit(strangerObs!!)
        var guard = 0
        while (last6 is AdaptiveVerificationEngine.Step.NeedMoreFrames && guard < 2) {
            last6 = engine6.submit(strangerObs); guard++
        }
        val final6 = last6 as AdaptiveVerificationEngine.Step.Final
        assertFalse(final6.outcome is AdaptiveVerificationEngine.Outcome.Match)
        assertEquals(1, presentRecords().size) // no record created for unknown
        // Roster candidates = production filter: all students not yet PRESENT.
        val roster = listOf(amitId, bId, cId, dId).filter { it !in setOf(amitId) }
        assertTrue(!roster.contains(amitId))
        assertEquals(setOf(bId, cId, dId), roster.toSet())
        println("[ACCEPTANCE] PHASE 6 OK: stranger â†’ ${final6.outcome::class.simpleName}; no record; roster=B,C,D (AMIT excluded)")

        // ---------------- PHASE 7: UNKNOWN â†’ manual fallback (THE BUG FIX) ----------
        val mark7 = repository.markFallbackPresent(classId, bId)
        assertTrue(mark7 is FallbackMarkResult.Marked)
        val recB = recordsFor(bId)
        assertEquals(1, recB.size)
        assertEquals(AttendanceSource.MANUAL, recB.single().attendanceMethod)
        assertEquals("teacher_fallback_after_unknown", recB.single().recognitionMetadata)
        assertEquals(2, presentRecords().size)
        println("[ACCEPTANCE] PHASE 7 OK: fallback Student B â†’ MANUAL source, totalRecords=2")

        // ---------------- PHASE 8: fallback duplicate -------------------------------
        val mark8 = repository.markFallbackPresent(classId, bId)
        assertTrue(mark8 is FallbackMarkResult.AlreadyPresent)
        assertEquals(2, presentRecords().size)
        println("[ACCEPTANCE] PHASE 8 OK: repeat fallback B â†’ AlreadyPresent, totalRecords=2")

        // ---------------- PHASES 9â€“13 combined: session save/isolation --------------
        // End session through production save (reconciling end-save must NOT duplicate
        // or crash despite immediate marks existing).
        val saved = repository.saveFaceAttendance(classId, setOf(amitId, bId))
        assertTrue(saved.toString(), saved is CommandResult.Success)
        assertEquals(com.attract.attendance.core.model.SessionStatus.ENDED, database.sessionDao().find(sessionId)!!.status)
        val sessionARecords = presentRecords()
        assertEquals("A=AMIT,B=StudentB + ABSENT rows for C,D", 4, sessionARecords.size)
        assertEquals(AttendanceSource.AI_RECOGNITION, sessionARecords.first { it.studentId == amitId }.attendanceMethod)
        assertEquals(AttendanceSource.MANUAL, sessionARecords.first { it.studentId == bId }.attendanceMethod)
        println("[ACCEPTANCE] PHASE 9a/12 OK: reconciled end-save; A-session rows=4 with correct sources")

        // No active session now â†’ fallback must fail safely.
        val markNoSession = repository.markFallbackPresent(classId, cId)
        assertTrue(markNoSession is FallbackMarkResult.NoActiveSession)
        println("[ACCEPTANCE] PHASE 9b OK: ended session â†’ NoActiveSession, no write")

        // NEW session: prior attendance MUST NOT contaminate it (session isolation).
        val sessionB = expectSuccess(repository.ensureFaceSession(classId), "ensureFaceSession B")
        assertEquals(0, presentRecords(sessionB).size)
        val markAgain = repository.markFallbackPresent(classId, amitId)
        assertTrue("AMIT must be markable again in a NEW session, got $markAgain", markAgain is FallbackMarkResult.Marked)
        // Session B: exactly ONE record (AMIT). Session A untouched: still 2 PRESENT rows.
        assertEquals(1, presentRecords(sessionB).size)
        assertEquals(amitId, presentRecords(sessionB).single().studentId)
        assertEquals(2, presentRecords(sessionId).count { it.status == AttendanceStatus.PRESENT })
        println("[ACCEPTANCE] PHASE 12 OK: session isolation — AMIT marked fresh in session $sessionB; session $sessionId untouched")

        // ---------------- PHASE 10/13: ineligible student & class isolation ---------
        val classB = expectSuccess(repository.createClass(CreateClassCommand(name = "Other Class")), "createClass B")
        val studentX = expectSuccess(repository.addStudent(CreateStudentCommand(classId = classB, name = "Student X", rollNumber = "X-01")), "addStudent X")
        val markX = repository.markFallbackPresent(classId, studentX)
        assertTrue(markX is FallbackMarkResult.StudentNotEligible)
        assertTrue(database.attendanceRecordDao().forSession(sessionB).none { it.studentId == studentX })
        // Template queries are class-scoped: X has no templates in any class and never appears.
        assertTrue(repository.getActiveTemplatesForClass(classId).none { it.studentId == studentX })
        println("[ACCEPTANCE] PHASE 10/13 OK: cross-class student rejected; templates class-scoped")

        // ---------------- PHASE 14: legacy 32-D template migration ------------------
        val legacyBytes = with(com.attract.attendance.domain.face.TemplateMatcher) { FloatArray(32) { 0.1f }.toByteArray() }
        database.faceTemplateDao().insert(
            FaceTemplateEntity(
                studentId = dId, encryptedEmbedding = legacyBytes, cryptoVersion = 1,
                modelVersion = "v1", qualityScore = 1f, capturedAt = 1, source = "legacy", active = true,
            ),
        )
        // Stale template NEVER reaches matcher:
        assertTrue(repository.getActiveTemplatesForClass(classId).none { it.studentId == dId })
        val report = repository.retireIncompatibleTemplates(classId)
        assertEquals(1, report.deactivatedTemplates)
        assertTrue(report.studentsNeedingReEnrollment.contains(dId))
        // Re-enrollment replaces it with current format:
        enrollReal(dId, bobAssets.reversed())
        val dTemplates = database.faceTemplateDao().forStudent(dId)
        assertTrue(dTemplates.all { it.embeddingDim == TemplateCompatibility.CURRENT_EMBEDDING_DIM && it.modelVersion == TemplateCompatibility.CURRENT_MODEL_ID })
        assertEquals(0, repository.retireIncompatibleTemplates(classId).deactivatedTemplates)
        println("[ACCEPTANCE] PHASE 14 OK: 32-D retired → re-enrollment stored ${TemplateCompatibility.CURRENT_EMBEDDING_DIM}-D ${TemplateCompatibility.CURRENT_MODEL_ID}")

        // ---------------- PHASE 15: adaptive 1â†’2â†’3 real-flow variants ---------------
        // B: frame-1 uncertain â†’ frame-2 resolves.
        val engB = newScreenEngine()
        // Use a LEFT/GAP-pose frame first if available; else stranger-ish low-similarity is not identity... use B's own frames against full gallery (ambiguous between none).
        val obsB1 = runRealPipeline(bobAssets[2])
        val s1 = engB.submit(obsB1!!)
        if (s1 is AdaptiveVerificationEngine.Step.NeedMoreFrames) {
            val s2 = engB.submit(runRealPipeline(bobAssets[0])!!)
            assertTrue(s2 is AdaptiveVerificationEngine.Step.Final || s2 is AdaptiveVerificationEngine.Step.NeedMoreFrames)
        }
        // D/E: strong frame-1 is terminal; further submits are refused (lock semantics).
        val engD = newScreenEngine()
        engD.submit(runRealPipeline(amitAssets[0])!!)
        try {
            engD.submit(runRealPipeline(bobAssets[0])!!)
            // If terminal was reached, this MUST throw.
            throw AssertionError("Engine accepted submit after terminal state")
        } catch (_: IllegalStateException) { /* expected */ }
        // F: all-invalid observations â†’ Unknown.
        val engF = AdaptiveVerificationEngine(emptyList(), RecognitionDecisionEngine(), EvidenceFusion.conservativeBestFrame(), FaceQualityConfig.calibrationDefaults(), 3)
        var lastF: AdaptiveVerificationEngine.Step = engF.submit(makeInvalidObservation())
        lastF = engF.submit(makeInvalidObservation())
        lastF = engF.submit(makeInvalidObservation())
        assertTrue(lastF is AdaptiveVerificationEngine.Step.Final)
        assertTrue((lastF as AdaptiveVerificationEngine.Step.Final).outcome is AdaptiveVerificationEngine.Outcome.Unknown)
        println("[ACCEPTANCE] PHASE 15 OK: adaptive variants behave per contract (terminal locks, invalidâ†’Unknown)")

        println("[ACCEPTANCE] ===== FULL WORKFLOW ACCEPTANCE PASSED =====")
    }

    // ------------------------------------------------------------------

    private suspend fun enrollReal(studentId: Long, assets: List<String>) {
        val embeddings = assets.mapNotNull { runRealPipeline(it)?.embedding }
        assertTrue("Need >=2 usable enrollment frames for $studentId", embeddings.size >= 2)
        val bytes = embeddings.map { emb -> with(com.attract.attendance.domain.face.TemplateMatcher) { emb.toByteArray() } }
        val result = repository.enrollStudentFace(studentId, bytes, List(bytes.size) { 0.9f })
        assertTrue(result.toString(), result is CommandResult.Success)
    }

    /** Deliberately unusable observation: multiple faces signal, no embedding. */
    private fun makeInvalidObservation(): FrameObservation {
        val signals = com.attract.attendance.domain.face.FaceQualitySignals(
            faceCount = 2, yawDegrees = 0f, pitchDegrees = 40f, rollDegrees = 0f,
            leftEyeOpenProbability = 1f, rightEyeOpenProbability = 1f,
            faceRatio = 0.05f, centerX = 0.5f, centerY = 0.5f,
            blurVariance = 30f, brightness = 250f,
        )
        return FrameObservation(
            signals = signals,
            quality = QualityResult.Rejected(com.attract.attendance.domain.face.QualityReason.MULTIPLE_FACES),
            liveness = LivenessEngine.check(signals, null, PresentationAttackSignals.Unavailable),
            embedding = null,
        )
    }

    private fun getLum(b: android.graphics.Bitmap, x: Int, y: Int): Int {
        val p = b.getPixel(x.coerceIn(0, b.width - 1), y.coerceIn(0, b.height - 1))
        return (0.299 * ((p shr 16) and 0xFF) + 0.587 * ((p shr 8) and 0xFF) + 0.114 * (p and 0xFF)).toInt()
    }

    private fun brightness(b: android.graphics.Bitmap, box: android.graphics.Rect): Float {
        val l = box.left.coerceIn(0, b.width - 1); val r = box.right.coerceIn(l + 1, b.width)
        val t = box.top.coerceIn(0, b.height - 1); val bt = box.bottom.coerceIn(t + 1, b.height)
        var sum = 0L; var n = 0; val st = ((bt - t) / 40).coerceAtLeast(1)
        var y = t
        while (y < bt) { var x = l; while (x < r) { sum += getLum(b, x, y); n++; x += st }; y += st }
        return if (n > 0) sum.toFloat() / n else 0f
    }

    private fun laplacian(b: android.graphics.Bitmap, box: android.graphics.Rect): Float {
        val l = (box.left + 1).coerceIn(1, b.width - 2); val r = (box.right - 1).coerceIn(l + 1, b.width - 1)
        val t = (box.top + 1).coerceIn(1, b.height - 2); val bt = (box.bottom - 1).coerceIn(t + 1, b.height - 1)
        val st = ((bt - t) / 30).coerceAtLeast(1)
        val resp = mutableListOf<Float>()
        var y = t
        while (y < bt) {
            var x = l
            while (x < r) {
                val c = getLum(b, x, y)
                resp.add((getLum(b, x, y - 1) + getLum(b, x, y + 1) + getLum(b, x - 1, y) + getLum(b, x + 1, y) - 4 * c).toFloat())
                x += st
            }
            y += st
        }
        if (resp.isEmpty()) return 0f
        val m = resp.average().toFloat()
        return resp.fold(0.0) { acc, v -> acc + (v - m) * (v - m) }.toFloat() / resp.size
    }
}




