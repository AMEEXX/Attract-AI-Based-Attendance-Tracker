package com.attract.attendance.domain.face

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry

import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileWriter
import kotlin.math.abs
import kotlin.random.Random

/**
 * AdaptiveStrategyBenchmarkTest â€” [ADAPTIVE_BENCH] (LLD-16, TEST-ONLY).
 *
 * Compares three verification strategies on IDENTICAL deterministic inputs (100-identity
 * LFW subset, seed 20260825):
 *
 *   STRATEGY_A_ONE_FRAME        â€” maxFrames=1  (today's security, zero extra friction)
 *   STRATEGY_B_ADAPTIVE_1_TO_2  â€” maxFrames=2
 *   STRATEGY_C_ADAPTIVE_1_TO_3  â€” maxFrames=3  (full adaptive budget)
 *
 * All strategies share production thresholds UNCHANGED (accept=0.45, margin=0.10,
 * calibrationDefaults quality/liveness). Evidence fusion = conservativeBestFrame
 * (production default).
 *
 * Also measures enrollment strength: template from 1 vs 3 frontal observations.
 */
@RunWith(AndroidJUnit4::class)
class AdaptiveStrategyBenchmarkTest {

    private val config = FaceQualityConfig.calibrationDefaults()
    private val decisionEngine = RecognitionDecisionEngine()
    private val fusion = EvidenceFusion.conservativeBestFrame()

    private data class BenchImage(
        val identity: String,
        val file: String,
        val observation: FrameObservation,
        val poseBin: String,
    )

    @Test
    fun runAdaptiveStrategyBenchmark() {
        val context = InstrumentationRegistry.getInstrumentation().context
        // Model inference MUST use the TARGET application context (mobilefacenet.tflite
        // ships in the app APK assets, not the test APK).
        val appContext = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        assertTrue("TFLite model unavailable", EmbeddingEngine.isAvailable(appContext))
        val assetManager = context.assets
        val benchDir = "test-data/lfw-bench"
        val manifestLines = try {
            assetManager.open("$benchDir/bench_images.csv").bufferedReader().readLines()
        } catch (_: Exception) {
            emptyList()
        }
        val manifest = manifestLines.drop(1).filter { it.isNotBlank() }.map { it.split(",").toTypedArray() }
        assertTrue("bench assets missing", manifest.isNotEmpty())

        // ---------------- PHASE 1: pipeline over all images (same as LfwThreeFrameBenchmarkTest) ----------------
        val images = mutableListOf<BenchImage>()
        manifest.forEachIndexed { idx, row ->
            val identity = row[0]
            val fileName = row[1].substringAfterLast('/')
            val bitmap = try {
                android.graphics.BitmapFactory.decodeStream(assetManager.open("$benchDir/$identity/$fileName"))
            } catch (_: Exception) { null } ?: return@forEachIndexed
            try {
                val faces = YoloFaceDetector.detect(appContext, bitmap)
                if (faces.size != 1) return@forEachIndexed
                val f = faces[0]
                val box = f.boundingBox
                val yaw = f.estimatedYaw
                val pitch = f.estimatedPitch
                val signals = FaceQualitySignals(
                    faceCount = 1,
                    yawDegrees = yaw,
                    pitchDegrees = pitch,
                    rollDegrees = f.estimatedRoll,
                    leftEyeOpenProbability = 1f,
                    rightEyeOpenProbability = 1f,
                    faceRatio = f.faceRatio,
                    centerX = f.centerX,
                    centerY = f.centerY,
                    blurVariance = laplacianVariance(bitmap, box),
                    brightness = brightness(bitmap, box),
                )
                val poseBin = when {
                    yaw <= -config.profileMinYawDegrees && yaw >= -config.profileMaxYawDegrees &&
                        abs(pitch) <= config.maxPoseDegrees -> "LEFT"
                    yaw >= config.profileMinYawDegrees && yaw <= config.profileMaxYawDegrees &&
                        abs(pitch) <= config.maxPoseDegrees -> "RIGHT"
                    abs(yaw) <= config.straightMaxYawDegrees && abs(pitch) <= config.maxPoseDegrees -> "STRAIGHT"
                    else -> "OTHER"
                }
                val expectedPose = when (poseBin) {
                    "LEFT" -> ExpectedPose.LEFT
                    "RIGHT" -> ExpectedPose.RIGHT
                    else -> ExpectedPose.STRAIGHT
                }
                val quality = FaceQualityEngine.evaluate(signals, config, expectedPose)
                val liveness = LivenessEngine.check(signals, null, PresentationAttackSignals.Unavailable)
                var embedding: FloatArray? = null
                if (quality is QualityResult.Accepted && liveness is LivenessResult.Passed) {
                    embedding = try {
                        val alignedFace = FaceAligner.align(bitmap, f.landmarks)
                        EmbeddingEngine.extractEmbedding(appContext, alignedFace)
                    } catch (_: Exception) { null }
                }
                images.add(BenchImage(identity, fileName, FrameObservation(signals, quality, liveness, embedding), poseBin))
            } finally {
                bitmap.recycle()
            }
            if ((idx + 1) % 200 == 0) println("[ADAPTIVE_BENCH] phase1 $idx/${manifest.size}")
        }

        val rng = Random(20260825L)
        val byIdentity = images.groupBy { it.identity }

        // ---------------- Galleries: enrollment with 1 vs 3 frontal observations ----------------
        data class Gallery(val nEnroll: Int, val pairs: List<StudentTemplatePair>, val ids: Map<String, Long>)
        fun buildGallery(nEnroll: Int): Gallery {
            val ids = mutableMapOf<String, Long>()
            val pairs = mutableListOf<StudentTemplatePair>()
            var sid = 1L
            for ((identity, recs) in byIdentity) {
                val fronts = recs.filter { it.observation.embedding != null && it.poseBin == "STRAIGHT" }
                    .sortedBy { it.file }.take(nEnroll)
                val pool = (fronts.ifEmpty { recs.filter { it.observation.embedding != null }.take(nEnroll) })
                    .map { it.observation.embedding!! }
                if (pool.isEmpty()) continue
                val id = sid++
                ids[identity] = id
                pairs.add(StudentTemplatePair(id, id * 1000, EmbeddingEngine.combineEmbeddings(pool)))
            }
            return Gallery(nEnroll, pairs, ids)
        }
        val gallery1 = buildGallery(1)
        val gallery3 = buildGallery(3)
        println("[ADAPTIVE_BENCH] enrolled: n1=${gallery1.pairs.size} n3=${gallery3.pairs.size}")

        // ---------------- Strategy runner ----------------
        data class Metrics(
            var transactions: Int = 0,
            var trueAccepts: Int = 0, var falseRejects: Int = 0, var falseAccepts: Int = 0,
            var unknownEnd: Int = 0, var ambiguousEnd: Int = 0, var errors: Int = 0,
            var acceptedAt1: Int = 0, var needed2: Int = 0, var needed3: Int = 0,
            var framesTotal: Int = 0,
        ) {
            fun far() = if (transactions > 0) falseAccepts.toFloat() / transactions else 0f
            fun frr() = if (transactions > 0) falseRejects.toFloat() / transactions else 0f
            fun avgFrames() = if (transactions > 0) framesTotal.toFloat() / transactions else 0f
        }

        fun runGenuine(gallery: Gallery, maxFrames: Int, strat: EvidenceFusionStrategy = fusion): Metrics {
            val m = Metrics()
            for ((identity, recs) in byIdentity) {
                val trueId = gallery.ids[identity] ?: continue
                // EXCLUDE the frames used for enrollment â€” verification must use unseen captures.
                val enrollFiles = recs.filter { it.poseBin == "STRAIGHT" }.sortedBy { it.file }
                    .take(gallery.nEnroll).map { it.file }.toSet()
                val queryFrames = recs
                    .filter { it.observation.isBiometricallyUsable && it.file !in enrollFiles }
                    .sortedWith(compareBy({ it.poseBin != "STRAIGHT" }, { it.file }))
                if (queryFrames.isEmpty()) continue
                val obs = queryFrames.take(maxFrames).map { it.observation }
                m.transactions++
                m.framesTotal += obs.size
                val eng = AdaptiveVerificationEngine(gallery.pairs, decisionEngine, strat, config, maxFrames)
                var last: AdaptiveVerificationEngine.Step = eng.submit(obs[0])
                for (i in 1 until obs.size) {
                    if (last is AdaptiveVerificationEngine.Step.Final) break
                    last = eng.submit(obs[i])
                }
                if (last !is AdaptiveVerificationEngine.Step.Final) last = eng.finalizeNow()
                when (val o = (last as AdaptiveVerificationEngine.Step.Final).outcome) {
                    is AdaptiveVerificationEngine.Outcome.Match -> {
                        if (o.studentId == trueId) {
                            m.trueAccepts++
                            when (o.framesUsed) { 1 -> m.acceptedAt1++; 2 -> m.needed2++; else -> m.needed3++ }
                        } else { m.falseRejects++; m.falseAccepts++ }
                    }
                    AdaptiveVerificationEngine.Outcome.Ambiguous -> { m.falseRejects++; m.ambiguousEnd++ }
                    AdaptiveVerificationEngine.Outcome.Unknown -> { m.falseRejects++; m.unknownEnd++ }
                    is AdaptiveVerificationEngine.Outcome.Error -> m.errors++
                }
            }
            return m
        }

        fun runImpostor(gallery: Gallery, maxFrames: Int, strat: EvidenceFusionStrategy = fusion): Metrics {
            val m = Metrics()
            val impostorRows = try {
                assetManager.open("$benchDir/bench_impostor_pairs.csv").bufferedReader().readLines()
                    .drop(1).filter { it.isNotBlank() }
            } catch (_: Exception) { emptyList() }
            // One impostor TRANSACTION = one attacker identity attempting to be claimed as
            // one enrolled student, using up to maxFrames of THAT attacker's observations.
            val pairsSeen = mutableSetOf<Pair<String, String>>()
            for (row in impostorRows.map { it.split(",") }) {
                val attackerId = row[1]
                val claimedId = row[3]
                if (!gallery.ids.containsKey(claimedId)) continue
                if (!byIdentity.containsKey(attackerId)) continue
                val key = attackerId to claimedId
                if (!pairsSeen.add(key)) continue
                val targetSid = gallery.ids[claimedId]!!
                val tmpl = gallery.pairs.filter { it.studentId == targetSid }
                val attackerObs = byIdentity[attackerId]!!
                    .filter { it.observation.isBiometricallyUsable }
                    .sortedWith(compareBy({ it.poseBin != "STRAIGHT" }, { it.file }))
                    .take(maxFrames)
                    .map { it.observation }
                if (attackerObs.isEmpty()) continue
                m.transactions++
                m.framesTotal += attackerObs.size
                val eng = AdaptiveVerificationEngine(tmpl, decisionEngine, strat, config, maxFrames)
                var last: AdaptiveVerificationEngine.Step = eng.submit(attackerObs[0])
                for (k in 1 until attackerObs.size) {
                    if (last is AdaptiveVerificationEngine.Step.Final) break
                    last = eng.submit(attackerObs[k])
                }
                if (last !is AdaptiveVerificationEngine.Step.Final) last = eng.finalizeNow()
                when (val o = (last as AdaptiveVerificationEngine.Step.Final).outcome) {
                    is AdaptiveVerificationEngine.Outcome.Match -> m.falseAccepts++
                    AdaptiveVerificationEngine.Outcome.Ambiguous -> m.ambiguousEnd++
                    AdaptiveVerificationEngine.Outcome.Unknown -> m.unknownEnd++
                    is AdaptiveVerificationEngine.Outcome.Error -> m.errors++
                }
            }
            return m
        }

        // ---------------- Execute matrix ----------------
        data class Row(val name: String, val enroll: Int, val g: Metrics, val i: Metrics)
        val rows = mutableListOf<Row>()
        rows.add(Row("STRATEGY_A_ONE_FRAME", 3, runGenuine(gallery3, 1), runImpostor(gallery3, 1)))
        rows.add(Row("STRATEGY_B_ADAPTIVE_1_TO_2", 3, runGenuine(gallery3, 2), runImpostor(gallery3, 2)))
        rows.add(Row("STRATEGY_C_ADAPTIVE_1_TO_3", 3, runGenuine(gallery3, 3), runImpostor(gallery3, 3)))
        // Enrollment strength probe under full adaptive budget:
        rows.add(Row("STRATEGY_C_ENROLL_1_OBS", 1, runGenuine(gallery1, 3), runImpostor(gallery1, 3)))
        // Consistency-gated rescue: accept on frames>=2 only when confident frames AGREE.
        val gated = EvidenceFusion.allFrameConsistency()
        rows.add(Row("B_CONSISTENCY_GATED", 3, runGenuine(gallery3, 2, gated), runImpostor(gallery3, 2, gated)))
        rows.add(Row("C_CONSISTENCY_GATED", 3, runGenuine(gallery3, 3, gated), runImpostor(gallery3, 3, gated)))
        // Full fusion-strategy sweep under the maximum adaptive budget:
        for (s in EvidenceFusion.allStrategies()) {
            if (s.name == "PROD_CONSERVATIVE") continue // already covered by STRATEGY_C row
            rows.add(Row("FUSION_" + s.name, 3, runGenuine(gallery3, 3, s), runImpostor(gallery3, 3, s)))
        }

        // ---------------- Report ----------------
        val report = StringBuilder()
        report.appendLine("=" .repeat(78))
        report.appendLine("[ADAPTIVE_BENCH] STRATEGY COMPARISON (thresholds UNCHANGED: 0.45 / margin 0.10)")
        report.appendLine("=" .repeat(78))
        report.appendLine("%-28s %-6s %-7s %-7s %-8s %-8s %-9s %-9s %-6s".format(
            "Strategy", "Enroll", "FAR%", "FRR%", "Acc@1", "Need2", "Need3", "AvgFr", "TA"))
        for (r in rows) {
            report.appendLine("%-28s %-6d %-7.2f %-7.2f %-8.1f %-8.1f %-9.1f %-9.2f %-6d".format(
                r.name, r.enroll,
                r.i.far() * 100, r.g.frr() * 100,
                pct(r.g.acceptedAt1, r.g.transactions), pct(r.g.needed2, r.g.transactions),
                pct(r.g.needed3, r.g.transactions), r.g.avgFrames(), r.g.trueAccepts))
            report.appendLine("    details: TA=${r.g.trueAccepts} FR=${r.g.falseRejects} FA(impostor)=${r.i.falseAccepts}/${r.i.transactions} " +
                "AmbEnd=${r.g.ambiguousEnd} UnkEnd=${r.g.unknownEnd} Errors=${r.g.errors + r.i.errors}")
        }
        println(report.toString())

        // CSV export
        val resultsDir = InstrumentationRegistry.getInstrumentation().targetContext
            .getExternalFilesDir(null)?.resolve("lfw_bench_results")!!
        resultsDir.mkdirs()
        FileWriter(File(resultsDir, "adaptive_strategy_results.csv")).use { w ->
            w.write("strategy,enrollObs,transactions,far,frr,trueAccepts,falseRejects,falseAccepts,unknownEnd,ambiguousEnd,errors,acceptedAt1,needed2,needed3,avgFrames\n")
            for (r in rows) {
                w.write("${r.name},${r.enroll},${r.g.transactions},${r.i.far()},${r.g.frr()},${r.g.trueAccepts},${r.g.falseRejects},${r.i.falseAccepts},${r.g.unknownEnd},${r.g.ambiguousEnd},${r.g.errors},${r.g.acceptedAt1},${r.g.needed2},${r.g.needed3},${r.g.avgFrames()}\n")
            }
        }
        println("[ADAPTIVE_BENCH] CSV written.")
    }

    private fun pct(v: Int, total: Int) = if (total > 0) v * 100f / total else 0f

    // Same algorithms as CameraPreview (bounded sampling over face box).
    private fun getLuminance(b: android.graphics.Bitmap, x: Int, y: Int): Int {
        val p = b.getPixel(x.coerceIn(0, b.width - 1), y.coerceIn(0, b.height - 1))
        val r = (p shr 16) and 0xFF; val g = (p shr 8) and 0xFF; val bl = p and 0xFF
        return (0.299 * r + 0.587 * g + 0.114 * bl).toInt()
    }

    private fun brightness(b: android.graphics.Bitmap, box: android.graphics.Rect): Float {
        val l = box.left.coerceIn(0, b.width - 1); val r = box.right.coerceIn(l + 1, b.width)
        val t = box.top.coerceIn(0, b.height - 1); val bt = box.bottom.coerceIn(t + 1, b.height)
        var sum = 0L; var n = 0
        val step = ((bt - t) / 40).coerceAtLeast(1)
        var y = t
        while (y < bt) { var x = l; while (x < r) { sum += getLuminance(b, x, y); n++; x += step }; y += step }
        return if (n > 0) sum.toFloat() / n else 0f
    }

    private fun laplacianVariance(b: android.graphics.Bitmap, box: android.graphics.Rect): Float {
        val l = (box.left + 1).coerceIn(1, b.width - 2); val r = (box.right - 1).coerceIn(l + 1, b.width - 1)
        val t = (box.top + 1).coerceIn(1, b.height - 2); val bt = (box.bottom - 1).coerceIn(t + 1, b.height - 1)
        val step = ((bt - t) / 30).coerceAtLeast(1)
        val responses = mutableListOf<Float>()
        var y = t
        while (y < bt) {
            var x = l
            while (x < r) {
                val c = getLuminance(b, x, y)
                responses.add((getLuminance(b, x, y - 1) + getLuminance(b, x, y + 1) +
                    getLuminance(b, x - 1, y) + getLuminance(b, x + 1, y) - 4 * c).toFloat())
                x += step
            }
            y += step
        }
        if (responses.isEmpty()) return 0f
        val mean = responses.average().toFloat()
        return responses.fold(0.0) { acc, v -> acc + (v - mean) * (v - mean) }.toFloat() / responses.size
    }
}

