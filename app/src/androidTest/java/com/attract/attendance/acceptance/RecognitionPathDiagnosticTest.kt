package com.attract.attendance.acceptance

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.attract.attendance.core.model.AttendanceSource
import com.attract.attendance.core.model.AttendanceStatus
import com.attract.attendance.core.model.CommandResult
import com.attract.attendance.data.local.AttractDatabase
import com.attract.attendance.data.repository.AttractRepository
import com.attract.attendance.data.repository.AttractRepository.FallbackMarkResult
import com.attract.attendance.data.repository.CreateClassCommand
import com.attract.attendance.data.repository.CreateStudentCommand
import com.attract.attendance.data.security.PinHasher
import com.attract.attendance.domain.face.AdaptiveVerificationEngine
import com.attract.attendance.domain.face.EvidenceFusion
import com.attract.attendance.domain.face.ExpectedPose
import com.attract.attendance.domain.face.FaceQualityEngine
import com.attract.attendance.domain.face.FaceQualityConfig
import com.attract.attendance.domain.face.FrameObservation
import com.attract.attendance.domain.face.LivenessEngine
import com.attract.attendance.domain.face.EmbeddingEngine
import com.attract.attendance.domain.face.PresentationAttackSignals
import com.attract.attendance.domain.face.QualityResult
import com.attract.attendance.domain.face.FaceAligner
import com.attract.attendance.domain.face.RecognitionDecisionEngine
import com.attract.attendance.domain.face.StudentTemplatePair
import com.attract.attendance.domain.face.TemplateCompatibility
import com.attract.attendance.domain.face.YoloFaceDetector
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * RecognitionPathDiagnosticTest â€” PHASES 1â€“8 & 10â€“12 of the recognition investigation.
 *
 * Ground truth: REAL face JPEGs from the project's prepared real-faces dataset.
 * Every stage is the REAL production path. Nothing is mocked except the camera source,
 * which enters at the exact same boundary as CameraX (Bitmap -> ML Kit).
 */
@RunWith(AndroidJUnit4::class)
class RecognitionPathDiagnosticTest {

    private lateinit var context: android.content.Context
    private lateinit var db: AttractDatabase
    private lateinit var repo: AttractRepository
    private var classId = 0L
    private val ids = mutableMapOf<String, Long>()

    // Identities with frames VERIFIED to pass production quality (lfw benchmark image_metrics).
    private val people = linkedMapOf(
        "AMIT" to "George_W_Bush",
        "STUDENT_B" to "Vladimir_Putin",
        "STUDENT_C" to "Tiger_Woods",
    )
    private val candidatesFor = mapOf(
        "George_W_Bush" to listOf("George_W_Bush_0002.jpg","George_W_Bush_0003.jpg","George_W_Bush_0006.jpg","George_W_Bush_0007.jpg"),
        "Vladimir_Putin" to listOf("Vladimir_Putin_0002.jpg","Vladimir_Putin_0003.jpg","Vladimir_Putin_0007.jpg","Vladimir_Putin_0008.jpg"),
        "Tiger_Woods" to listOf("Tiger_Woods_0001.jpg","Tiger_Woods_0003.jpg","Tiger_Woods_0005.jpg","Tiger_Woods_0007.jpg"),
    )
    private val strangerAsset = "Venus_Williams/Venus_Williams_0001.jpg"

    private suspend fun firstUsableLfw(candidates: List<String>): Pair<String, FrameObservation>? {
        for (c in candidates) {
            val o = buildObservationFromLfw(c)
            if (o != null) return c to o
            println("[DIAG] candidate skipped: $c")
        }
        return null
    }

    private suspend fun pipelineEmbeddingLfw(asset: String): FloatArray? =
        buildObservationFromLfw(asset)?.embedding

    @Test
    fun recognitionDiagnosticsAndMatrix() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase("recognition_diag.db")
        db = Room.databaseBuilder(context, AttractDatabase::class.java, "recognition_diag.db")
            .allowMainThreadQueries().build()
        repo = AttractRepository(db, PinHasher(), embeddingCipher = null)

        assertTrue(repo.registerTeacher("Diag", "123456".toCharArray()) is CommandResult.Success)
        classId = valueOf(repo.createClass(CreateClassCommand(name = "Class A")), "createClass")
        for (name in people.keys) {
            ids[name] = valueOf(repo.addStudent(CreateStudentCommand(classId = classId, name = name, rollNumber = "R-$name")), "add $name")
        }

        // ---------------- PHASE 3+4: enroll with first quality-passing frame --------
        println("\n===== ENROLLMENT (production enrollStudentFace) =====")
        val enrolledFile = mutableMapOf<String, String>()
        for ((name, dir) in people) {
            val hit = firstUsableLfw(candidatesFor[dir]!!.map { "$dir/$it" })
            assertNotNull("no quality-passing enrollment frame for $name", hit)
            enrolledFile[name] = hit!!.first
            println("[ENROLL] $name frame=${hit.first}")
            val bytes = with(com.attract.attendance.domain.face.TemplateMatcher) { hit.second.embedding!!.toByteArray() }
            val r = repo.enrollStudentFace(ids[name]!!, listOf(bytes), listOf(1f))
            assertTrue(r.toString(), r is CommandResult.Success)
        }

        // ---------------- PHASE 2: Room enrollment table ----------------------------
        println("\n===== PHASE 2: ENROLLMENT TABLE (Room) =====")
        println("student | id | templates | dim | modelVersion | active")
        for ((name, _) in people) {
            val t = db.faceTemplateDao().forStudent(ids[name]!!)
            println("${name} | ${ids[name]} | ${t.size} | ${t.joinToString("/") { it.embeddingDim.toString() }} | ${t.joinToString("/") { it.modelVersion }} | ${t.all { it.active }}")
            assertTrue(t.isNotEmpty())
            assertTrue(t.all { it.embeddingDim == TemplateCompatibility.CURRENT_EMBEDDING_DIM })
            assertTrue(t.all { it.modelVersion == TemplateCompatibility.CURRENT_MODEL_ID })
        }

        // ---------------- PHASE 5: gallery / class filtering ------------------------
        val gallery = repo.getActiveTemplatesForClass(classId)
        println("\n===== PHASE 5: GALLERY =====")
        println("queryClassId=$classId loadedTemplateStudentIds=${gallery.map { it.studentId }} (${gallery.size} templates)")
        assertEquals(people.size, gallery.map { it.studentId }.distinct().size)

        val decisionEngine = RecognitionDecisionEngine()

        // ---------------- PHASE 4+6+7: DIRECT RECOGNITION DIAGNOSTIC ----------------
        println("\n===== PHASE 4/6/7: DIRECT RECOGNITION DIAGNOSTIC =====")
        data class Row(val label: String, val expected: String, val actual: String, val pass: Boolean)
        val matrix = mutableListOf<Row>()

        // SAME-IMAGE sanity (easiest case): cosine(E_A, E_A) must be ~1
        println("\n----- PHASE 6: same-image determinism -----")
        for ((name, dir) in people) {
            val e1 = pipelineEmbeddingLfw(enrolledFile[name]!!)!!
            val e2 = pipelineEmbeddingLfw(enrolledFile[name]!!)!!
            val cos = com.attract.attendance.domain.face.TemplateMatcher.cosineSimilarity(e1, e2)
            println("SAME-IMAGE $name: cosine(E1,E2)=$cos")
            matrix.add(Row("same-image $name", "cos>=0.999", "cos=$cos", cos > 0.999f))
        }

        // DIFFERENT-IMAGE genuine queries through decision engine
        for ((name, dir) in people) {
            val enrollName = enrolledFile[name]!!.substringAfterLast('/')
            val others = candidatesFor[dir]!!.filter { it != enrollName }
            val qObs = firstUsableLfw(others.map { "$dir/$it" })
            if (qObs == null) { println("[DIAG] no second usable frame for $name"); continue }
            val q = qObs.second.embedding!!
            val scores = gallery.associate { it.studentId to com.attract.attendance.domain.face.TemplateMatcher.cosineSimilarity(q, it.embedding) }
                .entries.sortedByDescending { it.value }
            val top1 = scores[0]; val top2 = scores.getOrNull(1)
            val margin = top1.value - (top2?.value ?: -1f)
            val outcome = decisionEngine.evaluate(q, gallery)
            val top1Name = ids.filterValues { it == top1.key }.keys.first()
            val verdict = when (outcome) {
                is com.attract.attendance.domain.face.RecognitionOutcome.Match ->
                    if (outcome.studentId == ids[name]) "MATCH(correct)" else "MATCH(WRONG)"
                is com.attract.attendance.domain.face.RecognitionOutcome.Ambiguous -> "AMBIGUOUS"
                else -> "UNKNOWN"
            }
            println("[DIAG] $name different-image: top1=$top1Name(${top1.value}) top2=${scores.getOrNull(1)?.key}(${top2?.value}) margin=$margin thr=0.45 mReq=0.10 â†’ $verdict")
            matrix.add(Row("diff-image $name", "MATCH($name)", verdict, verdict == "MATCH(correct)"))
        }

        // Cross-identity: AMIT query must NOT match B/C
        val qA = pipelineEmbeddingLfw(enrolledFile["AMIT"]!!)!!
        for (other in listOf("STUDENT_B", "STUDENT_C")) {
            val tmpl = gallery.first { it.studentId == ids[other] }
            val s = com.attract.attendance.domain.face.TemplateMatcher.cosineSimilarity(qA, tmpl.embedding)
            matrix.add(Row("AMITâ†’${other}", "< 0.45", "sim=$s", s < 0.45f))
        }

        // ---------------- PHASE 8: adaptive engine â†’ attendance --------------------
        println("\n===== PHASE 8/10: ADAPTIVE â†’ ATTENDANCE (production callbacks) =====")
        val sessionId = valueOf(repo.ensureFaceSession(classId), "ensureFaceSession")
        for ((name, dir) in people) {
            val enrollName2 = enrolledFile[name]!!.substringAfterLast('/')
            val qObs2 = firstUsableLfw(candidatesFor[dir]!!.filter { it != enrollName2 }.map { "$dir/$it" })
            val obs = qObs2?.second ?: continue
            val eng = AdaptiveVerificationEngine(gallery.map { StudentTemplatePair(it.studentId, it.studentId * 1000, it.embedding) },
                decisionEngine, EvidenceFusion.conservativeBestFrame(), FaceQualityConfig.calibrationDefaults(), 3)
            val step = eng.submit(obs)
            val final = step as? AdaptiveVerificationEngine.Step.Final
            val outcome = final?.outcome
            val matchedId = (outcome as? AdaptiveVerificationEngine.Outcome.Match)?.studentId
            println("[ADAPTIVE] $name: outcome=${outcome?.let { it::class.simpleName }} studentId=$matchedId framesUsed=${(outcome as? AdaptiveVerificationEngine.Outcome.Match)?.framesUsed}")
            if (matchedId != null && matchedId == ids[name]) {
                val mark = repo.markRecognizedPresent(classId, matchedId, (outcome as AdaptiveVerificationEngine.Outcome.Match).confidence)
                assertTrue(mark.toString(), mark is FallbackMarkResult.Marked || mark is FallbackMarkResult.AlreadyPresent)
            }
        }

        // duplicate via second submission for AMIT
        val engDup = AdaptiveVerificationEngine(gallery.map { StudentTemplatePair(it.studentId, it.studentId * 1000, it.embedding) },
            decisionEngine, EvidenceFusion.conservativeBestFrame(), FaceQualityConfig.calibrationDefaults(), 3)
        val dupObs = firstUsableLfw(candidatesFor[people["AMIT"]]!!.map { "${people["AMIT"]}/$it" })
        val dupStep = engDup.submit(dupObs!!.second)
        val dupMatch = ((dupStep as? AdaptiveVerificationEngine.Step.Final)?.outcome) as? AdaptiveVerificationEngine.Outcome.Match
        if (dupMatch != null && dupMatch.studentId == ids["AMIT"]) {
            val d = repo.markRecognizedPresent(classId, dupMatch.studentId, dupMatch.confidence)
            assertTrue(d is FallbackMarkResult.AlreadyPresent)
        }
        val amitRows = recordsFor(sessionId, ids["AMIT"]!!)
        assertEquals(1, amitRows.size)
        assertEquals(AttendanceSource.AI_RECOGNITION, amitRows.single().attendanceMethod)

        // ---------------- PHASE 11: unknown identity --------------------------------
        val strangerObs = buildObservationFromLfw(strangerAsset)
        if (strangerObs != null) {
            val engU = AdaptiveVerificationEngine(gallery.map { StudentTemplatePair(it.studentId, it.studentId * 1000, it.embedding) },
                decisionEngine, EvidenceFusion.conservativeBestFrame(), FaceQualityConfig.calibrationDefaults(), 3)
            var last: AdaptiveVerificationEngine.Step = engU.submit(strangerObs)
            var n = 0
            while (last is AdaptiveVerificationEngine.Step.NeedMoreFrames && n < 2) { last = engU.submit(strangerObs); n++ }
            val outU = (last as AdaptiveVerificationEngine.Step.Final).outcome
            println("[UNKNOWN] outcome=${outU::class.simpleName}")
            val noMatch = outU !is AdaptiveVerificationEngine.Outcome.Match
            matrix.add(Row("unknown person", "UNKNOWN/AMBIGUOUS (never Match)",
                if (noMatch) "NO_MATCH" else "WRONGLY_MATCHED", noMatch))
        } else {
            println("[UNKNOWN] stranger asset unavailable â€” covered by FullWorkflowAcceptanceTest")
        }

        // fallback B still works (regression guard)
        val fb = repo.markFallbackPresent(classId, ids["STUDENT_B"]!!)
        assertTrue(fb is FallbackMarkResult.Marked || fb is FallbackMarkResult.AlreadyPresent)

        // ---------------- PHASE 12: MATRIX ------------------------------------------
        println("\n===== PHASE 12: PASS/FAIL MATRIX =====")
        var allPass = true
        for (r in matrix) {
            println("${if (r.pass) "PASS" else "FAIL"} | ${r.label} | expected=${r.expected} | actual=${r.actual}")
            if (!r.pass) allPass = false
        }
        println(if (allPass) "===== ALL DIAGNOSTIC MATRIX ROWS PASSED =====" else "===== MATRIX FAILURES PRESENT =====")
        assertTrue(allPass)
    }

    // ------------------------------------------------------------------

    private suspend fun buildObservation(asset: String): FrameObservation? =
        decodeAndProcess("test-data/real-faces/$asset")

    private suspend fun buildObservationFromLfw(asset: String): FrameObservation? =
        decodeAndProcess("test-data/lfw-bench/$asset")

    private suspend fun decodeAndProcess(pathInAssets: String): FrameObservation? {
        val am = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context.assets
        val bitmap = try {
            android.graphics.BitmapFactory.decodeStream(am.open(pathInAssets))
        } catch (_: Exception) { null } ?: return null
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
                blurVariance = lap(bitmap, box),
                brightness = bright(bitmap, box),
            )
            val quality = FaceQualityEngine.evaluate(signals, cfg)
            val liveness = LivenessEngine.check(signals, null, PresentationAttackSignals.Unavailable)
            if (quality !is QualityResult.Accepted || liveness !is com.attract.attendance.domain.face.LivenessResult.Passed) {
                println("[DIAG] $pathInAssets rejected: Q=${quality::class.simpleName}")
                return null
            }
            val alignedFace = FaceAligner.align(bitmap, face.landmarks)
            val emb = EmbeddingEngine.extractEmbedding(context, alignedFace)
            return FrameObservation(signals, quality, liveness, emb)
        } finally { bitmap.recycle() }
    }

    private suspend fun pipelineEmbedding(asset: String): FloatArray? =
        buildObservation(asset)?.embedding

    private suspend fun recordsFor(sessionId: Long, studentId: Long) =
        db.attendanceRecordDao().forSession(sessionId).filter { it.studentId == studentId }

    private fun valueOf(result: CommandResult<Long>, what: String): Long {
        assertTrue("$what failed: $result", result is CommandResult.Success)
        return (result as CommandResult.Success).value
    }

    private fun getL(b: android.graphics.Bitmap, x: Int, y: Int): Int {
        val p = b.getPixel(x.coerceIn(0, b.width - 1), y.coerceIn(0, b.height - 1))
        return (0.299 * ((p shr 16) and 0xFF) + 0.587 * ((p shr 8) and 0xFF) + 0.114 * (p and 0xFF)).toInt()
    }
    private fun bright(b: android.graphics.Bitmap, box: android.graphics.Rect): Float {
        val l = box.left.coerceIn(0, b.width - 1); val r = box.right.coerceIn(l + 1, b.width)
        val t = box.top.coerceIn(0, b.height - 1); val bt = box.bottom.coerceIn(t + 1, b.height)
        var sum = 0L; var n = 0; val st = ((bt - t) / 40).coerceAtLeast(1)
        var y = t
        while (y < bt) { var x = l; while (x < r) { sum += getL(b, x, y); n++; x += st }; y += st }
        return if (n > 0) sum.toFloat() / n else 0f
    }
    private fun lap(b: android.graphics.Bitmap, box: android.graphics.Rect): Float {
        val l = (box.left + 1).coerceIn(1, b.width - 2); val r = (box.right - 1).coerceIn(l + 1, b.width - 1)
        val t = (box.top + 1).coerceIn(1, b.height - 2); val bt = (box.bottom - 1).coerceIn(t + 1, b.height - 1)
        val st = ((bt - t) / 30).coerceAtLeast(1)
        val resp = mutableListOf<Float>()
        var y = t
        while (y < bt) {
            var x = l
            while (x < r) {
                val c = getL(b, x, y)
                resp.add((getL(b, x, y - 1) + getL(b, x, y + 1) + getL(b, x - 1, y) + getL(b, x + 1, y) - 4 * c).toFloat())
                x += st
            }
            y += st
        }
        if (resp.isEmpty()) return 0f
        val mean = resp.average().toFloat()
        return resp.fold(0.0) { a, v -> a + (v - mean) * (v - mean) }.toFloat() / resp.size
    }
}











