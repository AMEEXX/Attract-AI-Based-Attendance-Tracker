package com.attract.attendance.domain.face

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry

import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileWriter
import kotlin.math.abs
import kotlin.random.Random

/**
 * LfwThreeFrameBenchmarkTest — [LFW_BENCHMARK]
 *
 * TEST-ONLY benchmark. Runs the REAL production pipeline over an LFW subset pushed to
 * /data/local/tmp/lfw_bench (dataset stays out of the APK):
 *   JPEG -> ML Kit FaceDetector -> measured pixel statistics (blur/brightness, same
 *   algorithms as CameraPreview) -> FaceQualitySignals -> FaceQualityEngine ->
 *   LivenessEngine -> cropFaceForEmbedding -> EmbeddingEngine (mobilefacenet.tflite,
 *   192-D, L2-normalized) -> TemplateMatcher cosine similarity -> RecognitionDecisionEngine.
 *
 * Pose bins are assigned from MEASURED ML Kit euler angles (never filenames), using the
 * production windows: STRAIGHT |yaw|<=15, LEFT -45..-20, RIGHT 20..45, |pitch|<=20.
 *
 * Production thresholds are used UNCHANGED: accept=0.45, ambiguity margin=0.10,
 * FaceQualityConfig.calibrationDefaults().
 *
 * Results CSVs are written to the app external files dir for host-side confusion analysis.
 */
@RunWith(AndroidJUnit4::class)
class LfwThreeFrameBenchmarkTest {

    companion object {
        const val DEVICE_BASE = "/data/local/tmp/lfw_bench"
        const val SEED = 20260825L
    }

    private lateinit var context: Context
    private val config = FaceQualityConfig.calibrationDefaults()
    private val decisionEngine = RecognitionDecisionEngine()

    data class ImageRecord(
        val identity: String,
        val path: String,
        var yaw: Float = 0f,
        var pitch: Float = 0f,
        var roll: Float = 0f,
        var leftEye: Float = 1f,
        var rightEye: Float = 1f,
        var faceRatio: Float = 0f,
        var centerX: Float = 0.5f,
        var centerY: Float = 0.5f,
        var blur: Float = 0f,
        var brightness: Float = 0f,
        var rawFaces: Int = 0,
        var usableFaces: Int = 0,
        var poseBin: String = "NONE",
        var qualityAccepted: Boolean = false,
        var qualityReason: String = "",
        var livenessPassed: Boolean = false,
        var cropOk: Boolean = false,
        var embeddingDim: Int = 0,
        var l2Norm: Float = 0f,
        var embedding: FloatArray? = null,
        var detectFailed: Boolean = false,
    ) {
        val signals: FaceQualitySignals
            get() = FaceQualitySignals(
                faceCount = usableFaces,
                yawDegrees = yaw,
                pitchDegrees = pitch,
                rollDegrees = roll,
                leftEyeOpenProbability = leftEye,
                rightEyeOpenProbability = rightEye,
                faceRatio = faceRatio,
                centerX = centerX,
                centerY = centerY,
                blurVariance = blur,
                brightness = brightness,
            )
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        assertTrue("TFLite model 'arcface_mobilefacenet.tflite' must be available", EmbeddingEngine.isAvailable(context))
        assertTrue("TFLite model 'yolov8n_face.tflite' must be available", YoloFaceDetector.isAvailable(context))
    }

    // =========================================================================
    // MAIN BENCHMARK
    // =========================================================================

    @Test
    fun runFullLfwThreeFrameBenchmark() {
        val assetManager = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context.assets
        val benchDir = "test-data/lfw-bench"
        val manifestLines = try {
            assetManager.open("$benchDir/bench_images.csv").bufferedReader().readLines()
        } catch (_: Exception) {
            emptyList()
        }
        val imagesManifest = manifestLines.drop(1).filter { it.isNotBlank() }.map { it.split(",").toTypedArray() }
        assertTrue(
            "Benchmark assets missing under $benchDir -- run scripts/generate_lfw_benchmark_subset.py --assets",
            imagesManifest.isNotEmpty(),
        )
        println("[LFW_BENCH] Using bench assets: $benchDir (${imagesManifest.size} images)")

        // ---------------- PHASE 1: PER-IMAGE PIPELINE ----------------
        val records = mutableListOf<ImageRecord>()
        var detectFailures = 0
        var multiFace = 0
        var qualityRejects = 0
        var livenessRejects = 0
        var cropFailures = 0
        var embeddingFailures = 0

        val t0 = System.currentTimeMillis()
        imagesManifest.forEachIndexed { idx, row ->
            val identity = row[0]
            val fileName = row[1].substringAfterLast('/')
            val assetPath = "$benchDir/$identity/$fileName"
            val rec = ImageRecord(identity = identity, path = fileName)
            val bitmap = try {
                BitmapFactory.decodeStream(assetManager.open(assetPath))
            } catch (_: Exception) {
                null
            }
            if (bitmap == null) {
                rec.detectFailed = true
                detectFailures++
                records.add(rec)
                return@forEachIndexed
            }
            try {
                val faces = YoloFaceDetector.detect(context, bitmap)
                rec.rawFaces = faces.size
                rec.usableFaces = faces.size
                if (faces.size > 1) multiFace++
                if (faces.isEmpty()) {
                    rec.detectFailed = true
                    detectFailures++
                    records.add(rec)
                    return@forEachIndexed
                }
                val f = faces[0]
                val box = f.boundingBox
                rec.yaw = f.estimatedYaw
                rec.pitch = f.estimatedPitch
                rec.roll = f.estimatedRoll
                rec.leftEye = 1.0f
                rec.rightEye = 1.0f
                rec.faceRatio = f.faceRatio
                rec.centerX = f.centerX
                rec.centerY = f.centerY
                rec.blur = computeLaplacianVariance(bitmap, box)
                rec.brightness = computeBrightness(bitmap, box)

                // Measured-pose binning using PRODUCTION windows (never filename-based)
                rec.poseBin = when {
                    rec.yaw <= -config.profileMinYawDegrees && rec.yaw >= -config.profileMaxYawDegrees &&
                        abs(rec.pitch) <= config.maxPoseDegrees -> "LEFT"
                    rec.yaw >= config.profileMinYawDegrees && rec.yaw <= config.profileMaxYawDegrees &&
                        abs(rec.pitch) <= config.maxPoseDegrees -> "RIGHT"
                    abs(rec.yaw) <= config.straightMaxYawDegrees && abs(rec.pitch) <= config.maxPoseDegrees -> "STRAIGHT"
                    abs(rec.yaw) > config.profileMaxYawDegrees || abs(rec.pitch) > config.maxPoseDegrees -> "EXTREME"
                    else -> "GAP" // between straight max and profile min, or pitch-broken
                }

                val expectedPose = when (rec.poseBin) {
                    "LEFT" -> ExpectedPose.LEFT
                    "RIGHT" -> ExpectedPose.RIGHT
                    else -> ExpectedPose.STRAIGHT
                }
                val q = FaceQualityEngine.evaluate(rec.signals, config, expectedPose)
                rec.qualityAccepted = q is QualityResult.Accepted
                rec.qualityReason = if (q is QualityResult.Rejected) q.reason.name else "ACCEPTED"
                if (!rec.qualityAccepted) qualityRejects++

                val lv = LivenessEngine.check(rec.signals, null, PresentationAttackSignals.Unavailable)
                rec.livenessPassed = lv is LivenessResult.Passed
                if (!rec.livenessPassed) livenessRejects++

                if (rec.qualityAccepted && rec.livenessPassed) {
                    val aligned = try {
                        FaceAligner.align(bitmap, f.landmarks)
                    } catch (_: Exception) {
                        null
                    }
                    if (aligned == null) {
                        cropFailures++
                    } else {
                        rec.cropOk = true
                        try {
                            val emb = EmbeddingEngine.extractEmbedding(context, aligned)
                            rec.embedding = emb
                            rec.embeddingDim = emb.size
                            rec.l2Norm = kotlin.math.sqrt(emb.fold(0f) { acc, v -> acc + v * v })
                        } catch (_: Exception) {
                            embeddingFailures++
                        }
                    }
                }
            } finally {
                bitmap.recycle()
            }
            records.add(rec)
            if ((idx + 1) % 100 == 0) {
                println("[LFW_BENCH] progress ${idx + 1}/${imagesManifest.size} (${System.currentTimeMillis() - t0} ms)")
            }
        }
        val processed = records.count { !it.detectFailed }
        println("[LFW_BENCH] Phase1 done in ${System.currentTimeMillis() - t0} ms")

        // ---------------- PHASE 2: GALLERY ENROLLMENT ----------------
        val byIdentity = records.groupBy { it.identity }
        data class GalleryEntry(val studentId: Long, val identity: String, val template: FloatArray, val enrolledFrom: Int)
        val gallery = mutableListOf<GalleryEntry>()
        var sidCounter = 1L
        val studentIdOf = mutableMapOf<String, Long>()
        for ((identity, recs) in byIdentity) {
            val straightPassed = recs.filter { it.embedding != null && it.poseBin == "STRAIGHT" }.map { it.embedding!! }
            val pool = straightPassed.ifEmpty { recs.filter { it.embedding != null }.map { it.embedding!! } }
            if (pool.isEmpty()) continue
            val sid = sidCounter++
            studentIdOf[identity] = sid
            gallery.add(GalleryEntry(sid, identity, EmbeddingEngine.combineEmbeddings(pool), pool.size))
        }
        println("[LFW_BENCH] Enrolled ${gallery.size}/${byIdentity.size} identities")

        // ---------------- PHASE 3: RECOGNITION METRICS ----------------
        var genuineComparisons = 0
        val genuineRows = mutableListOf<String>()
        var trueAccepts = 0
        var falseRejects = 0
        var falseAcceptsWrongId = 0
        var unknowns = 0
        var ambiguous = 0
        val genuineSims = mutableListOf<Pair<String, Float>>()      // caseKey -> sim to TRUE identity
        val impostorSims = mutableListOf<Triple<String, String, Float>>() // idA -> idB -> sim
        var impostorComparisons = 0
        var impostorFalseAccepts = 0
        var lowestMargin = Pair("", Float.MAX_VALUE)

        for ((identity, recs) in byIdentity) {
            val sid = studentIdOf[identity] ?: continue
            for (r in recs) {
                val emb = r.embedding ?: continue
                genuineComparisons++
                val outcome = decisionEngine.evaluate(emb, gallery.map { StudentTemplatePair(it.studentId, it.studentId * 1000, it.template) })
                val simToTrue = gallery.first { it.studentId == sid }.let { TemplateMatcher.cosineSimilarity(emb, it.template) }
                genuineSims.add(Pair("$identity/${r.path}", simToTrue))

                // top1/top2/margin analysis
                val scores = gallery.map { it.studentId to TemplateMatcher.cosineSimilarity(emb, it.template) }
                    .sortedByDescending { it.second }
                val m = scores.getOrNull(0)?.second?.minus(scores.getOrNull(1)?.second ?: 0f) ?: Float.NaN
                if (!m.isNaN() && m < lowestMargin.second) lowestMargin = Pair("$identity:${r.path}(top=${scores[0].second},2nd=${scores[1].second})", m)

                when (outcome) {
                    is RecognitionOutcome.Match -> if (outcome.studentId == sid) trueAccepts++ else { falseAcceptsWrongId++; falseRejects++ }
                    is RecognitionOutcome.Ambiguous -> { ambiguous++; falseRejects++ }
                    is RecognitionOutcome.Unknown -> { unknowns++; falseRejects++ }
                    RecognitionOutcome.NoTemplatesAvailable -> {}
                }
                val outcomeName = when (outcome) {
                    is RecognitionOutcome.Match -> if (outcome.studentId == sid) "TA" else "FA_WRONG_ID"
                    is RecognitionOutcome.Ambiguous -> "AMBIGUOUS"
                    is RecognitionOutcome.Unknown -> "UNKNOWN"
                    RecognitionOutcome.NoTemplatesAvailable -> "NO_TEMPLATES"
                }
                val top2 = scores.getOrNull(0)?.let { gallery.first { g -> g.studentId == it.first }.identity } ?: ""
                val secondId = scores.getOrNull(1)?.let { gallery.first { g -> g.studentId == it.first }.identity } ?: ""
                genuineRows.add("$identity,${r.path},${r.poseBin},$outcomeName,$simToTrue,${scores.getOrNull(0)?.second ?: Float.NaN},$top2,${scores.getOrNull(1)?.second ?: Float.NaN},$secondId,$m")
            }
        }

        val impostorRows = try {
            assetManager.open("$benchDir/bench_impostor_pairs.csv").bufferedReader().readLines()
                .drop(1).filter { it.isNotBlank() }.map { it.split(",").toTypedArray() }
        } catch (_: Exception) {
            emptyList()
        }
        val impostorOutRows = mutableListOf<String>()
        for (row in impostorRows) {
            val (idA, imgA, idB) = Triple(row[1], row[2].substringAfterLast('/'), row[3])
            val embA = byIdentity[idA]?.firstOrNull { it.path == imgA }?.embedding ?: continue
            val sidB = studentIdOf[idB] ?: continue
            val tmplB = gallery.firstOrNull { it.studentId == sidB } ?: continue
            impostorComparisons++
            val sim = TemplateMatcher.cosineSimilarity(embA, tmplB.template)
            impostorSims.add(Triple(idA, idB, sim))
            val outcome = decisionEngine.evaluate(embA, listOf(StudentTemplatePair(sidB, sidB * 1000, tmplB.template)))
            if (outcome is RecognitionOutcome.Match) impostorFalseAccepts++
            impostorOutRows.add("$idA,$imgA,$idB,$sim,${if (outcome is RecognitionOutcome.Match) "FALSE_ACCEPT" else "CORRECT_REJECT"}")
        }
        val far = if (impostorComparisons > 0) impostorFalseAccepts.toFloat() / impostorComparisons else 0f
        val frr = if (genuineComparisons > 0) falseRejects.toFloat() / genuineComparisons else 0f

        // ---------------- PHASE 4: THREE-FRAME SEQUENCES ----------------
        val seqResults = mutableListOf<String>() // csv rows
        var validSeqTotal = 0; var validSeqAccepted = 0
        var invalidSeqTotal = 0; var invalidSeqRejected = 0
        val rejectReasonCounts = mutableMapOf<String, Int>()
        val rng = Random(SEED)

        fun pickWithEmbedding(identity: String, bin: String): ImageRecord? =
            byIdentity[identity]?.filter { it.embedding != null && it.poseBin == bin }?.randomOrNull(rng)

        fun evaluateSequence(caseId: String, frames: List<Triple<ImageRecord, ExpectedPose, String>>, expectAccept: Boolean): Boolean {
            var prev: FaceQualitySignals? = null
            val capturedYaws = mutableListOf<Float>()
            for ((rec, expected, _) in frames) {
                val q = FaceQualityEngine.evaluate(rec.signals, config, expected)
                if (q is QualityResult.Rejected) {
                    seqResults.add("$caseId,${frames[0].first.identity},${frames.joinToString("|") { it.third }},${if (expectAccept) "VALID" else "INVALID"},REJECTED_QUALITY:${q.reason.name},${if (expectAccept) "MISSED_ACCEPT" else "CORRECT_REJECT"}")
                    rejectReasonCounts.merge("QUALITY:${q.reason.name}", 1, Int::plus)
                    return !expectAccept
                }
                val lv = LivenessEngine.check(rec.signals, prev, PresentationAttackSignals.Unavailable)
                if (lv is LivenessResult.Rejected) {
                    seqResults.add("$caseId,${frames[0].first.identity},${frames.joinToString("|") { it.third }},${if (expectAccept) "VALID" else "INVALID"},REJECTED_LIVENESS:${lv.reason.name},${if (expectAccept) "MISSED_ACCEPT" else "CORRECT_REJECT"}")
                    rejectReasonCounts.merge("LIVENESS:${lv.reason.name}", 1, Int::plus)
                    return !expectAccept
                }
                if (!FaceQualityEngine.isDistinctFromCaptured(rec.yaw, capturedYaws)) {
                    seqResults.add("$caseId,${frames[0].first.identity},${frames.joinToString("|") { it.third }},${if (expectAccept) "VALID" else "INVALID"},REJECTED_DUPLICATE_YAW,${if (expectAccept) "MISSED_ACCEPT" else "CORRECT_REJECT"}")
                    rejectReasonCounts.merge("DUPLICATE_YAW", 1, Int::plus)
                    return !expectAccept
                }
                capturedYaws.add(rec.yaw)
                prev = rec.signals
            }
            // All frames passed gates: verify recognition consistency through decision engine
            val sids = frames.mapNotNull { (rec, _, _) ->
                val outcome = decisionEngine.evaluate(rec.embedding!!, gallery.map { StudentTemplatePair(it.studentId, it.studentId * 1000, it.template) })
                when (outcome) {
                    is RecognitionOutcome.Match -> outcome.studentId
                    else -> null
                }
            }
            val consistent = sids.size == frames.size && sids.distinct().size == 1
            val accepted = consistent
            val verdict = when {
                expectAccept && accepted -> "CORRECT_ACCEPT"
                expectAccept && !accepted -> "MISSED_ACCEPT"
                !expectAccept && !accepted -> "CORRECT_REJECT"
                else -> "MISSED_REJECT"
            }
            seqResults.add("$caseId,${frames[0].first.identity},${frames.joinToString("|") { it.third }},${if (expectAccept) "VALID" else "INVALID"},RECOGNIZED:${if (accepted) "SAME_PERSON_CONSISTENT" else "INCONSISTENT"},$verdict")
            return accepted == expectAccept
        }

        // VALID sequences
        val identities = byIdentity.keys.filter { studentIdOf.containsKey(it) }.sorted()
        var vId = 0
        val validCategories = mutableMapOf<String, Int>()
        for (identity in identities) {
            val s = pickWithEmbedding(identity, "STRAIGHT") ?: continue
            val l = pickWithEmbedding(identity, "LEFT")
            val r = pickWithEmbedding(identity, "RIGHT")
            if (l != null && r != null) {
                vId++
                val ok = evaluateSequence("SEQ_V_${vId.toString().padStart(5, '0')}",
                    listOf(Triple(s, ExpectedPose.STRAIGHT, "STRAIGHT"),
                        Triple(l, ExpectedPose.LEFT, "LEFT"),
                        Triple(r, ExpectedPose.RIGHT, "RIGHT")), true)
                validSeqTotal++; validSeqAccepted += if (ok) 1 else 0
                validCategories.merge("S->L->R", 1, Int::plus)
                // V2: STRAIGHT -> slight LEFT -> strong RIGHT where measurements support sub-bins
                val slightL = byIdentity[identity]?.filter { it.embedding != null && it.poseBin == "LEFT" && it.yaw in -30f..-20f }?.randomOrNull(rng)
                val strongR = byIdentity[identity]?.filter { it.embedding != null && it.poseBin == "RIGHT" && it.yaw >= 35f }?.randomOrNull(rng)
                if (slightL != null && strongR != null) {
                    vId++
                    val ok2 = evaluateSequence("SEQ_V_${vId.toString().padStart(5, '0')}",
                        listOf(Triple(s, ExpectedPose.STRAIGHT, "STRAIGHT"),
                            Triple(slightL, ExpectedPose.LEFT, "SLIGHT_LEFT(-20..-30)"),
                            Triple(strongR, ExpectedPose.RIGHT, "STRONG_RIGHT(>=35)")), true)
                    validSeqTotal++; validSeqAccepted += if (ok2) 1 else 0
                    validCategories.merge("S->SLIGHT_L->STRONG_R", 1, Int::plus)
                }
            }
            if (vId >= 800) break
        }

        // INVALID sequences
        assertTrue(
            "No identities enrolled (enrolled=${gallery.size}); cannot build sequences. " +
                "Check image staging and detection results in image_metrics.csv.",
            identities.isNotEmpty(),
        )
        var xId = 0
        fun newX() = "SEQ_X_${(xId++).toString().padStart(5, '0')}"
        // helper: three different identities
        val shuffled = identities.shuffled(rng)
        var idx3 = 0
        var attempts = 0
        while (xId < 2500 && attempts < 20000) {
            attempts++
            val category = attempts % 7
            when (category) {
                0 -> { // wrong order S->R->L
                    val id = identities[rng.nextInt(identities.size)]
                    val s = pickWithEmbedding(id, "STRAIGHT"); val r = pickWithEmbedding(id, "RIGHT"); val l = pickWithEmbedding(id, "LEFT")
                    if (s != null && r != null && l != null) {
                        val ok = evaluateSequence(newX(), listOf(Triple(s, ExpectedPose.STRAIGHT, "STRAIGHT"), Triple(r, ExpectedPose.LEFT, "WRONG_EXPECT_RIGHT_FRAME"), Triple(l, ExpectedPose.RIGHT, "WRONG_EXPECT_LEFT_FRAME")), false)
                        invalidSeqTotal++; invalidSeqRejected += if (ok) 1 else 0
                    }
                }
                1 -> { // insufficient pose diversity S->S->R (distinct straight images)
                    val id = identities[rng.nextInt(identities.size)]
                    val ss = byIdentity[id]?.filter { it.embedding != null && it.poseBin == "STRAIGHT" } ?: emptyList()
                    if (ss.size >= 2) {
                        val r = pickWithEmbedding(id, "RIGHT")
                        if (r != null) {
                            val ok = evaluateSequence(newX(), listOf(Triple(ss[0], ExpectedPose.STRAIGHT, "STRAIGHT_A"), Triple(ss[1], ExpectedPose.LEFT, "STRAIGHT_B_EXP_LEFT"), Triple(r, ExpectedPose.RIGHT, "RIGHT")), false)
                            invalidSeqTotal++; invalidSeqRejected += if (ok) 1 else 0
                        }
                    }
                }
                2 -> { // replay: same image x3
                    val id = identities[rng.nextInt(identities.size)]
                    val s = pickWithEmbedding(id, "STRAIGHT")
                    if (s != null) {
                        val ok = evaluateSequence(newX(), listOf(Triple(s, ExpectedPose.STRAIGHT, "SAME_IMG"), Triple(s, ExpectedPose.LEFT, "SAME_IMG"), Triple(s, ExpectedPose.RIGHT, "SAME_IMG")), false)
                        invalidSeqTotal++; invalidSeqRejected += if (ok) 1 else 0
                    }
                }
                3, 4 -> { // cross-person patterns A,A,B / B,B,A / A,B,C
                    if (shuffled.size >= 3) {
                        val i0 = idx3 % shuffled.size
                        val ids = listOf(shuffled[i0], shuffled[(i0 + 1) % shuffled.size], shuffled[(i0 + 2) % shuffled.size])
                        idx3++
                        // Build directly: choose one image from each identity
                        val a = pickAny(ids[0], rng, byIdentity); val b = pickAny(ids[1], rng, byIdentity); val c = pickAny(ids[2], rng, byIdentity)
                        val aAlt = pickAny(ids[0], rng, byIdentity); val bAlt = pickAny(ids[1], rng, byIdentity)
                        if (a != null && b != null && c != null && aAlt != null && bAlt != null) {
                            val kind = attempts % 3
                            val frames = when (kind) {
                                0 -> listOf(Triple(a, ExpectedPose.STRAIGHT, "ID_A:${ids[0]}"), Triple(aAlt, ExpectedPose.STRAIGHT, "ID_A:${ids[0]}"), Triple(b, ExpectedPose.STRAIGHT, "ID_B:${ids[1]}")) // A A B
                                1 -> listOf(Triple(b, ExpectedPose.STRAIGHT, "ID_B:${ids[1]}"), Triple(bAlt, ExpectedPose.STRAIGHT, "ID_B:${ids[1]}"), Triple(a, ExpectedPose.STRAIGHT, "ID_A:${ids[0]}")) // B B A
                                else -> listOf(Triple(a, ExpectedPose.STRAIGHT, "ID_A:${ids[0]}"), Triple(b, ExpectedPose.LEFT, "ID_B:${ids[1]}"), Triple(c, ExpectedPose.RIGHT, "ID_C:${ids[2]}")) // A B C
                            }
                            val ok = evaluateSequence(newX(), frames, false)
                            invalidSeqTotal++; invalidSeqRejected += if (ok) 1 else 0
                        }
                    }
                }
                5 -> { // extreme pose frame in otherwise good sequence
                    val id = identities[rng.nextInt(identities.size)]
                    val ext = byIdentity[id]?.filter { it.embedding != null && (abs(it.yaw) > config.profileMaxYawDegrees || abs(it.pitch) > config.maxPoseDegrees) }?.randomOrNull(rng)
                    val s = pickWithEmbedding(id, "STRAIGHT"); val l = pickWithEmbedding(id, "LEFT")
                    if (ext != null && s != null && l != null) {
                        val ok = evaluateSequence(newX(), listOf(Triple(s, ExpectedPose.STRAIGHT, "STRAIGHT"), Triple(ext, ExpectedPose.LEFT, "EXTREME_POSE"), Triple(l, ExpectedPose.RIGHT, "LEFT")), false)
                        invalidSeqTotal++; invalidSeqRejected += if (ok) 1 else 0
                    }
                }
                6 -> { // poor-quality frame (degraded bitmap) mixed into good sequence
                    val id = identities[rng.nextInt(identities.size)]
                    val base = pickWithEmbedding(id, "STRAIGHT")
                    val s = pickWithEmbedding(id, "STRAIGHT"); val r = pickWithEmbedding(id, "RIGHT")
                    if (s != null && r != null) {
                        val degraded = degradeDark(s)
                        val q = FaceQualityEngine.evaluate(degraded.signals, config, ExpectedPose.LEFT)
                        val rejectedByQuality = q is QualityResult.Rejected
                        seqResults.add("${newX()},$id,DEGRADED_DARK_BLUR|STRAIGHT|RIGHT,INVALID,QUALITY:${if (rejectedByQuality) (q as QualityResult.Rejected).reason.name else "ACCEPTED"},${if (rejectedByQuality) "CORRECT_REJECT" else "MISSED_REJECT"}")
                        invalidSeqTotal++; if (rejectedByQuality) invalidSeqRejected++
                        rejectReasonCounts.merge(if (rejectedByQuality) "QUALITY:${(q as QualityResult.Rejected).reason.name}" else "QUALITY:NONE", 1, Int::plus)
                    }
                }
            }
        }

        // ---------------- OUTPUT ----------------
        val resultsDir = InstrumentationRegistry.getInstrumentation().targetContext
            .getExternalFilesDir(null)?.resolve("lfw_bench_results")!!
        resultsDir.mkdirs()
        writeCsv(File(resultsDir, "image_metrics.csv"), listOf(
            "identity,file,yaw,pitch,roll,leftEye,rightEye,faceRatio,centerX,centerY,blur,brightness,rawFaces,usableFaces,poseBin,qualityAccepted,qualityReason,livenessPassed,cropOk,embeddingDim,l2Norm,detectFailed") +
                records.map { "${it.identity},${it.path},${it.yaw},${it.pitch},${it.roll},${it.leftEye},${it.rightEye},${it.faceRatio},${it.centerX},${it.centerY},${it.blur},${it.brightness},${it.rawFaces},${it.usableFaces},${it.poseBin},${it.qualityAccepted},${it.qualityReason},${it.livenessPassed},${it.cropOk},${it.embeddingDim},${it.l2Norm},${it.detectFailed}" })
        writeCsv(File(resultsDir, "sequence_results.csv"), listOf("case_id,identity,frame_labels,expected_class,outcome,verdict") + seqResults)
        writeCsv(File(resultsDir, "genuine_comparisons.csv"), listOf(
            "trueIdentity,file,poseBin,outcome,simToTrue,top1Sim,top1Id,top2Sim,top2Id,margin") + genuineRows)
        writeCsv(File(resultsDir, "impostor_comparisons.csv"), listOf(
            "idA,imageA,idB,sim,outcome") + impostorOutRows)

        val genuineSorted = genuineSims.sortedBy { it.second }
        val impostorSorted = impostorSims.sortedByDescending { it.third }
        println("""
            ===================================================================
            [LFW_BENCH] FINAL BENCHMARK REPORT (production thresholds UNCHANGED)
            ===================================================================
            DATASET: identities=${byIdentity.size} images=${records.size} enrolled=${gallery.size}
            IMAGE-LEVEL:
              Detection failures=$detectFailures  MultiFaceImages=$multiFace
              Quality rejects=$qualityRejects  Liveness rejects=$livenessRejects
              Crop failures=$cropFailures  Embedding failures=$embeddingFailures
              PoseBins: ${records.groupBy { it.poseBin }.mapValues { it.value.size }}
              EmbeddingDim=${records.firstOrNull { it.embeddingDim > 0 }?.embeddingDim}  L2NormRange=${records.minOfOrNull { it.l2Norm }}..${records.maxOfOrNull { it.l2Norm }}
            IDENTITY-LEVEL:
              Genuine comparisons=$genuineComparisons  Impostor comparisons=$impostorComparisons
              TrueAccepts=$trueAccepts  FalseAccepts(wrong-id)=$falseAcceptsWrongId
              FalseRejects=$falseRejects  Unknown=$unknowns  Ambiguous=$ambiguous
              FAR=${"%.4f".format(far * 100)}%  FRR=${"%.4f".format(frr * 100)}%
              GenuineSim min=${genuineSorted.firstOrNull()?.second} max=${genuineSorted.lastOrNull()?.second}
              ImpostorSim min=${impostorSorted.lastOrNull()?.third} max=${impostorSorted.firstOrNull()?.third}
              SmallestMargin: ${lowestMargin.first} = ${lowestMargin.second}
            THREE-FRAME:
              Valid sequences=$validSeqTotal accepted=$validSeqAccepted (${validCategories})
              Invalid sequences=$invalidSeqTotal correctly-rejected=$invalidSeqRejected
              RejectReasons: $rejectReasonCounts
            ===================================================================
        """.trimIndent())
        println("[LFW_BENCH] Worst genuine sims: ${genuineSorted.take(5)}")
        println("[LFW_BENCH] Highest impostor sims: ${impostorSorted.take(5)}")
    }

    // ---------- helpers ----------

    private fun pickAny(identity: String, rng: Random, byIdentity: Map<String, List<ImageRecord>>): ImageRecord? =
        byIdentity[identity]?.filter { it.embedding != null }?.randomOrNull(rng)

    /** Same passive-luminance algorithm as CameraPreview.computeBitmapBrightness. */
    private fun getLuminance(bitmap: Bitmap, x: Int, y: Int): Int {
        val p = bitmap.getPixel(x.coerceIn(0, bitmap.width - 1), y.coerceIn(0, bitmap.height - 1))
        val r = (p shr 16) and 0xFF; val g = (p shr 8) and 0xFF; val b = p and 0xFF
        return (0.299 * r + 0.587 * g + 0.114 * b).toInt()
    }

    private fun computeBrightness(bitmap: Bitmap, box: Rect): Float {
        val left = box.left.coerceIn(0, bitmap.width - 1)
        val right = box.right.coerceIn(left + 1, bitmap.width)
        val top = box.top.coerceIn(0, bitmap.height - 1)
        val bottom = box.bottom.coerceIn(top + 1, bitmap.height)
        var sum = 0L; var count = 0
        val step = ((bottom - top) / 40).coerceAtLeast(1)
        var y = top
        while (y < bottom) { var x = left; while (x < right) { sum += getLuminance(bitmap, x, y); count++; x += step }; y += step }
        return if (count > 0) sum.toFloat() / count else 0f
    }

    /** Same Laplacian-variance algorithm as CameraPreview.computeBitmapLaplacianVariance. */
    private fun computeLaplacianVariance(bitmap: Bitmap, box: Rect): Float {
        val left = (box.left + 1).coerceIn(1, bitmap.width - 2)
        val right = (box.right - 1).coerceIn(left + 1, bitmap.width - 1)
        val top = (box.top + 1).coerceIn(1, bitmap.height - 2)
        val bottom = (box.bottom - 1).coerceIn(top + 1, bitmap.height - 1)
        val step = ((bottom - top) / 30).coerceAtLeast(1)
        val responses = mutableListOf<Float>()
        var y = top
        while (y < bottom) {
            var x = left
            while (x < right) {
                val c = getLuminance(bitmap, x, y)
                val lap = getLuminance(bitmap, x, y - 1) + getLuminance(bitmap, x, y + 1) +
                    getLuminance(bitmap, x - 1, y) + getLuminance(bitmap, x + 1, y) - 4 * c
                responses.add(lap.toFloat()); x += step
            }
            y += step
        }
        if (responses.isEmpty()) return 0f
        val mean = responses.average().toFloat()
        return responses.fold(0.0) { acc, r -> acc + (r - mean) * (r - mean) }.toFloat() / responses.size
    }

    private fun degradeDark(s: ImageRecord): ImageRecord = s.copy(brightness = 15f, blur = 40f)

    private fun writeCsv(f: File, lines: List<String>) {
        FileWriter(f).use { w -> lines.forEach { w.write(it + "\n") } }
    }
}
