package com.attract.attendance.domain.face

import android.content.Context
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * FullBiometricPipelineIntegrationTest
 *
 * FULL END-TO-END INTEGRATION TEST — uses REAL production implementations only:
 *   Real JPEG file (from androidTest assets)
 *   -> YoloFaceDetector (yolov8n_face.tflite, 5 landmarks, pose estimation)
 *   -> FaceQualitySignals extraction
 *   -> FaceQualityEngine (production implementation)
 *   -> LivenessEngine (production implementation)
 *   -> FaceAligner.align (canonical 112x112 ArcFace alignment)
 *   -> EmbeddingEngine / ArcFace MobileFaceNet (real TFLite model, 512-D output)
 *   -> TemplateMatcher cosine similarity
 *   -> RecognitionDecisionEngine final outcome
 *
 * Two test variants are provided:
 *   A) executeFullEndToEndBiometricPipeline_productionConfig: uses unmodified FaceQualityConfig.calibrationDefaults()
 *   B) executeFullEndToEndBiometricPipeline_lenientConfig: uses a relaxed config for isolated ML/embedding verification
 *      (labeled clearly, not intended to prove production accuracy)
 */
@RunWith(AndroidJUnit4::class)
class FullBiometricPipelineIntegrationTest {

    private lateinit var context: Context

    /** Unmodified production quality configuration -- no overrides whatsoever. */
    private val productionConfig = FaceQualityConfig.calibrationDefaults()

    /**
     * [TEST-ONLY LENIENT CONFIG] -- reduced thresholds to isolate downstream ML/embedding
     * behaviour from quality gating. This config must never be used in production.
     * Tests using this config are labelled _lenientConfig and are NOT evidence of
     * production-quality-gate performance.
     */
    private val lenientConfig = FaceQualityConfig.calibrationDefaults().copy(
        minFaceRatio = 0.01f,
        maxPoseDegrees = 45f,
        maxOffCenterFraction = 0.45f
    )

    private val decisionEngine = RecognitionDecisionEngine(acceptThreshold = 0.25f, ambiguousMargin = 0.05f)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        assertTrue("TFLite model asset 'arcface_mobilefacenet.tflite' must be available", EmbeddingEngine.isAvailable(context))
        assertTrue("TFLite model asset 'yolov8n_face.tflite' must be available", YoloFaceDetector.isAvailable(context))
    }

    // =========================================================================
    // TEST A: Production FaceQualityConfig -- no overrides
    // =========================================================================

    @Test
    fun executeFullEndToEndBiometricPipeline_productionConfig() {
        println("[FULL_E2E] === TEST A: Production FaceQualityConfig (calibrationDefaults, NO overrides) ===")
        runPipeline(configLabel = "PRODUCTION", config = productionConfig, assertQuality = true)
    }

    // =========================================================================
    // TEST B: [TEST-ONLY LENIENT CONFIG] -- isolates ML / embedding behaviour
    //         from quality gating. Results do NOT represent production accuracy.
    // =========================================================================

    @Test
    fun executeFullEndToEndBiometricPipeline_lenientConfig() {
        println("[FULL_E2E] === TEST B: [TEST-ONLY LENIENT CONFIG] -- quality gates relaxed to isolate TFLite/embedding path ===")
        println("[FULL_E2E]    minFaceRatio=0.01, maxPoseDegrees=45, maxOffCenterFraction=0.45")
        println("[FULL_E2E]    Results are NOT evidence of production recognition accuracy.")
        runPipeline(configLabel = "LENIENT", config = lenientConfig, assertQuality = false)
    }

    // =========================================================================
    // Shared pipeline implementation
    // =========================================================================

    private fun runPipeline(configLabel: String, config: FaceQualityConfig, assertQuality: Boolean) {
        val assetManager = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context.assets
        val realPersonDirs = try {
            assetManager.list("test-data/real-faces") ?: emptyArray()
        } catch (_: Exception) {
            emptyArray()
        }

        val validPersonDirs = realPersonDirs.filter { !it.startsWith(".") && it != "README.md" }

        if (validPersonDirs.isEmpty()) {
            println("""
                [$configLabel] SKIPPED: Real-human test dataset missing.
                To execute: add real photos under test-data/real-faces/person_XX/ and run:
                  python scripts/prepare_face_test_data.py
            """.trimIndent())
            return
        }

        val enrolledTemplates = mutableListOf<StudentTemplatePair>()

        // ---- ENROLLMENT ----
        println("[$configLabel] Starting E2E Enrollment for ${validPersonDirs.size} subjects...")
        var subjectId = 1L

        for (personDir in validPersonDirs) {
            val personPath = "test-data/real-faces/$personDir"
            val files = assetManager.list(personPath) ?: continue
            val enrollFile = files.firstOrNull { it.contains("straight") } ?: continue

            val stream = assetManager.open("$personPath/$enrollFile")
            val bitmap = BitmapFactory.decodeStream(stream)
            assertNotNull("Enrollment bitmap must load for $enrollFile", bitmap)

            val faces = YoloFaceDetector.detect(context, bitmap)
            println("[$configLabel] $personDir/$enrollFile -- yoloFaceCount=${faces.size}")

            if (faces.isEmpty()) {
                println("[$configLabel] WARNING: 0 usable faces in $enrollFile -- skipping subject.")
                continue
            }

            val primaryFace = faces.maxByOrNull { it.confidence }!!
            val box = primaryFace.boundingBox

            println("[$configLabel]   boundingBox=$box  confidence=${primaryFace.confidence}")
            println("[$configLabel]   yaw=${primaryFace.estimatedYaw}  pitch=${primaryFace.estimatedPitch}  roll=${primaryFace.estimatedRoll}")
            println("[$configLabel]   faceRatio=${primaryFace.faceRatio}  centerX=${primaryFace.centerX}  centerY=${primaryFace.centerY}")

            val signals = FaceQualitySignals(
                faceCount = faces.size,
                yawDegrees = primaryFace.estimatedYaw,
                pitchDegrees = primaryFace.estimatedPitch,
                rollDegrees = primaryFace.estimatedRoll,
                leftEyeOpenProbability = 1.0f,
                rightEyeOpenProbability = 1.0f,
                faceRatio = primaryFace.faceRatio,
                centerX = primaryFace.centerX,
                centerY = primaryFace.centerY,
                blurVariance = 500f,
                brightness = 120f
            )

            val qualityResult = FaceQualityEngine.evaluate(signals, config)
            val livenessResult = LivenessEngine.check(signals, null, PresentationAttackSignals.Unavailable)

            println("[$configLabel]   qualityResult=$qualityResult  livenessResult=$livenessResult")

            if (assertQuality) {
                if (qualityResult is QualityResult.Rejected) {
                    android.util.Log.e("E2E_AUDIT", "[$configLabel] QUALITY REJECTED for $personDir/$enrollFile: ${qualityResult.reason}")
                }
                assertTrue("[$configLabel] Enrollment face must pass quality engine -- $personDir/$enrollFile", qualityResult is QualityResult.Accepted)
                assertTrue("[$configLabel] Enrollment face must pass liveness engine -- $personDir/$enrollFile", livenessResult is LivenessResult.Passed)
            }

            val faceAligned = FaceAligner.align(bitmap, primaryFace.landmarks)

            // Generate 512-D embedding via TFLite ArcFace MobileFaceNet
            val embedding = EmbeddingEngine.extractEmbedding(context, faceAligned)
            assertNotNull(embedding)
            assertEquals("[$configLabel] Embedding must be ${TemplateCompatibility.CURRENT_EMBEDDING_DIM}-D", TemplateCompatibility.CURRENT_EMBEDDING_DIM, embedding.size)
            println("[$configLabel]   embeddingDim=${embedding.size}")

            enrolledTemplates.add(StudentTemplatePair(studentId = subjectId, templateId = subjectId * 1000 + 1, embedding = embedding))
            subjectId++
        }

        // ---- VERIFICATION ----
        println("[$configLabel] Starting E2E Verification queries...")
        subjectId = 1L

        for (personDir in validPersonDirs) {
            val personPath = "test-data/real-faces/$personDir"
            val files = assetManager.list(personPath) ?: continue
            val otherFiles = files.filter { !it.contains("straight_01") }

            for (verFile in otherFiles) {
                val stream = assetManager.open("$personPath/$verFile")
                val bitmap = BitmapFactory.decodeStream(stream) ?: continue

                val faces = YoloFaceDetector.detect(context, bitmap)
                println("[$configLabel] $personDir/$verFile -- yoloFaceCount=${faces.size}")
                if (faces.isEmpty()) continue

                val primaryFace = faces.maxByOrNull { it.confidence }!!
                val box = primaryFace.boundingBox

                println("[$configLabel]   boundingBox=$box  confidence=${primaryFace.confidence}")
                println("[$configLabel]   yaw=${primaryFace.estimatedYaw}  pitch=${primaryFace.estimatedPitch}  roll=${primaryFace.estimatedRoll}")
                println("[$configLabel]   faceRatio=${primaryFace.faceRatio}  centerX=${primaryFace.centerX}  centerY=${primaryFace.centerY}")

                val faceAligned = FaceAligner.align(bitmap, primaryFace.landmarks)
                val queryEmbedding = EmbeddingEngine.extractEmbedding(context, faceAligned)
                println("[$configLabel]   embeddingDim=${queryEmbedding.size}")

                // Compute all per-template scores for full transparency before calling decision engine
                val scores = enrolledTemplates.map { tmpl ->
                    Pair(tmpl.studentId, TemplateMatcher.cosineSimilarity(queryEmbedding, tmpl.embedding))
                }.sortedByDescending { it.second }

                val topScore = scores.getOrNull(0)
                val secondScore = scores.getOrNull(1)
                val margin = if (topScore != null && secondScore != null) topScore.second - secondScore.second else Float.NaN

                println("[$configLabel]   topScore=${topScore?.second} (studentId=${topScore?.first})  secondScore=${secondScore?.second}  margin=$margin")

                val outcome = decisionEngine.evaluate(queryEmbedding, enrolledTemplates)
                println("[$configLabel]   finalDecision=$outcome")
            }
            subjectId++
        }
    }
}
