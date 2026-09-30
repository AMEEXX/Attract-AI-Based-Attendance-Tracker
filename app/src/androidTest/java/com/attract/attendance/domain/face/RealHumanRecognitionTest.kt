package com.attract.attendance.domain.face

import android.content.Context
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.InputStream

/**
 * RealHumanRecognitionTest
 *
 * [REAL_IMAGE_TEST] & [REAL_IMPOSTER_TEST]
 * Evaluates the real production biometric pipeline against real human face photographs:
 *  - Enrollment on image A (e.g. straight_01.jpg)
 *  - Verification on DIFFERENT images B/C (e.g. straight_02.jpg, left_01.jpg, right_01.jpg)
 *  - Imposter cross-identity evaluation (querying identity B against enrolled identity A)
 *
 * Calculates empirical biometric metrics:
 *  - True Accepts (TA)
 *  - False Rejects (FR)
 *  - False Accepts (FA)
 *  - Unknowns
 *  - Ambiguous
 *  - False Accept Rate (FAR) & False Reject Rate (FRR)
 *
 * If real-human images are missing, cleanly SKIPS with clear instructions.
 */
@RunWith(AndroidJUnit4::class)
class RealHumanRecognitionTest {

    private lateinit var context: Context
    private val decisionEngine = RecognitionDecisionEngine(acceptThreshold = 0.45f, ambiguousMargin = 0.10f)

    data class BiometricEvaluationMetrics(
        var totalEnrollments: Int = 0,
        var totalVerifications: Int = 0,
        var totalImposterQueries: Int = 0,
        var trueAccepts: Int = 0,
        var falseRejects: Int = 0,
        var falseAccepts: Int = 0,
        var unknowns: Int = 0,
        var ambiguous: Int = 0,
        var qualityRejects: Int = 0,
        var processingFailures: Int = 0
    ) {
        val falseRejectRate: Float
            get() = if (totalVerifications > 0) falseRejects.toFloat() / totalVerifications else 0f
        val falseAcceptRate: Float
            get() = if (totalImposterQueries > 0) falseAccepts.toFloat() / totalImposterQueries else 0f
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun realHumanBiometricRecognitionAndImposterEvaluation() {
        val assetManager = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context.assets
        val realPersonDirs = try {
            assetManager.list("test-data/real-faces") ?: emptyArray()
        } catch (_: Exception) {
            emptyArray()
        }

        val validPersonDirs = realPersonDirs.filter { !it.startsWith(".") && it != "README.md" }

        // Rule 2: Clean skip if real human test dataset is missing
        if (validPersonDirs.isEmpty()) {
            println("""
                ===================================================================
                [REAL_IMAGE_TEST] SKIPPED: Real-human test dataset missing.
                To run real-person recognition accuracy testing:
                 1. Add consenting test subject photos to: test-data/real-faces/person_XX/
                 2. Structure:
                    test-data/real-faces/person_01/straight_01.jpg
                    test-data/real-faces/person_01/left_01.jpg
                    test-data/real-faces/person_01/right_01.jpg
                 3. Run: python scripts/prepare_face_test_data.py
                ===================================================================
            """.trimIndent())
            return
        }

        assertTrue("TFLite model asset 'mobilefacenet.tflite' must be available", EmbeddingEngine.isAvailable(context))

        val metrics = BiometricEvaluationMetrics()
        val enrolledTemplates = mutableListOf<StudentTemplatePair>()
        val personIdMap = mutableMapOf<String, Long>()
        var personCounter = 1L

        println("[REAL_IMAGE_TEST] Starting biometric evaluation on ${validPersonDirs.size} identities...")

        // =========================================================================
        // 1. ENROLLMENT STAGE — Enroll using primary straight images
        // =========================================================================
        for (personDir in validPersonDirs) {
            val personPath = "test-data/real-faces/$personDir"
            val files = assetManager.list(personPath) ?: continue
            val imageFiles = files.filter { it.endsWith(".png") || it.endsWith(".jpg") || it.endsWith(".jpeg") }
            if (imageFiles.isEmpty()) continue

            // Pick primary enrollment image (straight_01 or first available)
            val enrollFile = imageFiles.firstOrNull { it.contains("straight") } ?: imageFiles.first()
            val stream: InputStream = assetManager.open("$personPath/$enrollFile")
            val bitmap = BitmapFactory.decodeStream(stream)

            if (bitmap == null) {
                metrics.processingFailures++
                continue
            }

            val embedding = EmbeddingEngine.extractEmbedding(context, bitmap)
            val sId = personCounter++
            personIdMap[personDir] = sId
            val templateId = sId * 1000 + 1

            enrolledTemplates.add(StudentTemplatePair(studentId = sId, templateId = templateId, embedding = embedding))
            metrics.totalEnrollments++
        }

        assertTrue("At least 1 identity must be enrolled", enrolledTemplates.isNotEmpty())

        // =========================================================================
        // 2. VERIFICATION STAGE — Test recognition on DIFFERENT images of same identity
        // =========================================================================
        for (personDir in validPersonDirs) {
            val sId = personIdMap[personDir] ?: continue
            val personPath = "test-data/real-faces/$personDir"
            val files = assetManager.list(personPath) ?: continue
            val imageFiles = files.filter { it.endsWith(".png") || it.endsWith(".jpg") || it.endsWith(".jpeg") }

            // Filter out primary enrollment image so verification tests DIFFERENT images
            val enrollFile = imageFiles.firstOrNull { it.contains("straight") } ?: imageFiles.first()
            val verificationFiles = imageFiles.filter { it != enrollFile }

            for (verFile in verificationFiles) {
                metrics.totalVerifications++
                val stream: InputStream = assetManager.open("$personPath/$verFile")
                val bitmap = BitmapFactory.decodeStream(stream)

                if (bitmap == null) {
                    metrics.processingFailures++
                    continue
                }

                val queryEmbedding = EmbeddingEngine.extractEmbedding(context, bitmap)
                val outcome = decisionEngine.evaluate(queryEmbedding, enrolledTemplates)

                when (outcome) {
                    is RecognitionOutcome.Match -> {
                        if (outcome.studentId == sId) {
                            metrics.trueAccepts++
                        } else {
                            // Incorrect identity matched
                            metrics.falseAccepts++
                            metrics.falseRejects++
                        }
                    }
                    is RecognitionOutcome.Ambiguous -> {
                        metrics.ambiguous++
                        metrics.falseRejects++
                    }
                    is RecognitionOutcome.Unknown -> {
                        metrics.unknowns++
                        metrics.falseRejects++
                    }
                    RecognitionOutcome.NoTemplatesAvailable -> {
                        metrics.processingFailures++
                    }
                }
            }
        }

        // =========================================================================
        // 3. IMPOSTER EVALUATION STAGE — Query identity B against enrolled identity A
        // =========================================================================
        if (enrolledTemplates.size >= 2) {
            for (personA in validPersonDirs) {
                val sIdA = personIdMap[personA] ?: continue
                val templateA = enrolledTemplates.filter { it.studentId == sIdA }

                for (personB in validPersonDirs) {
                    if (personA == personB) continue // skip genuine pair
                    val personPathB = "test-data/real-faces/$personB"
                    val filesB = assetManager.list(personPathB) ?: continue
                    val imageFilesB = filesB.filter { it.endsWith(".png") || it.endsWith(".jpg") || it.endsWith(".jpeg") }

                    for (verFileB in imageFilesB) {
                        metrics.totalImposterQueries++
                        val stream: InputStream = assetManager.open("$personPathB/$verFileB")
                        val bitmap = BitmapFactory.decodeStream(stream) ?: continue
                        val queryEmbeddingB = EmbeddingEngine.extractEmbedding(context, bitmap)

                        val imposterOutcome = decisionEngine.evaluate(queryEmbeddingB, templateA)

                        if (imposterOutcome is RecognitionOutcome.Match) {
                            metrics.falseAccepts++
                        }
                    }
                }
            }
        }

        // =========================================================================
        // 4. PRINT BIOMETRIC ACCURACY REPORT
        // =========================================================================
        println("""
            ===================================================================
            [REAL_IMAGE_TEST] EMPIRICAL BIOMETRIC ACCURACY REPORT
            ===================================================================
            Enrolled Identities:       ${metrics.totalEnrollments}
            Total Genuine Queries:     ${metrics.totalVerifications}
            Total Imposter Queries:    ${metrics.totalImposterQueries}
            -------------------------------------------------------------------
            True Accepts (TA):         ${metrics.trueAccepts}
            False Rejects (FR):        ${metrics.falseRejects}
            False Accepts (FA):        ${metrics.falseAccepts}
            Unknowns:                  ${metrics.unknowns}
            Ambiguous:                 ${metrics.ambiguous}
            Processing Failures:       ${metrics.processingFailures}
            -------------------------------------------------------------------
            False Reject Rate (FRR):   ${String.format("%.2f%%", metrics.falseRejectRate * 100)}
            False Accept Rate (FAR):   ${String.format("%.2f%%", metrics.falseAcceptRate * 100)}
            ===================================================================
        """.trimIndent())
    }
}
