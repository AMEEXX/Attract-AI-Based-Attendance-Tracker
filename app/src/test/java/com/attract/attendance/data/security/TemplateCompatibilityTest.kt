package com.attract.attendance.domain.face

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TemplateCompatibilityTest â€” biometric template migration/compatibility (LLD-10
 * migration amendment). Covers the production-phone failure class:
 * "Embedding dimensions must match (A: 192, B: 32)".
 *
 * Invariant under test: stale/malformed templates NEVER reach cosine similarity and
 * are never converted (truncated/padded) into a compatible representation.
 */
class TemplateCompatibilityTest {

    private fun vec(dim: Int, seed: Float = 1f): FloatArray {
        val v = FloatArray(dim)
        for (i in v.indices) v[i] = ((i % 7) + seed).toFloat()
        var sum = 0f
        for (x in v) sum += x * x
        val norm = kotlin.math.sqrt(sum)
        for (i in v.indices) v[i] /= norm
        return v
    }

    private fun template(studentId: Long, dim: Int) = StudentTemplatePair(
        studentId = studentId,
        templateId = studentId * 10,
        embedding = vec(dim),
    )

    private val currentDim = TemplateCompatibility.CURRENT_EMBEDDING_DIM

    // ------------------------------------------------------------------
    // 1. Current query + current template -> normal matching.
    // ------------------------------------------------------------------
    @Test
    fun `current query against current template produces normal match`() {
        val query = vec(currentDim)
        val templates = listOf(template(1L, currentDim))
        val partitioned = TemplateCompatibility.partition(templates)

        assertEquals(1, partitioned.usable.size)
        assertTrue(partitioned.usable.all { it.embedding.size == TemplateCompatibility.CURRENT_EMBEDDING_DIM })

        val outcome = RecognitionDecisionEngine().evaluate(query, partitioned.usable)
        assertTrue(outcome is RecognitionOutcome.Match)
        assertEquals(1L, (outcome as RecognitionOutcome.Match).studentId)
    }

    // ------------------------------------------------------------------
    // 2. Current query + stale (32-D, 192-D) templates -> safe rejection / re-enrollment required.
    // ------------------------------------------------------------------
    @Test
    fun `stale 32D template is filtered out before similarity and yields unknown`() {
        val query = vec(currentDim)
        val stale32 = template(2L, 32)
        val stale192 = template(3L, 192)
        assertEquals(TemplateCompatibility.VectorClass.STALE_DIMENSION, TemplateCompatibility.classify(stale32.embedding))
        assertEquals(TemplateCompatibility.VectorClass.STALE_DIMENSION, TemplateCompatibility.classify(stale192.embedding))

        val partitioned = TemplateCompatibility.partition(listOf(stale32, stale192))
        assertTrue(partitioned.stale.size == 2 && partitioned.usable.isEmpty())

        // Decision engine receives ONLY the usable set -> no crash, fail-closed
        // (NoTemplatesAvailable when nothing usable remains).
        val outcome = RecognitionDecisionEngine().evaluate(query, partitioned.usable)
        assertTrue(outcome is RecognitionOutcome.Unknown || outcome is RecognitionOutcome.NoTemplatesAvailable)

        // Even if a caller forgets to partition, the decision engine's own guard skips it.
        val defensive = RecognitionDecisionEngine().evaluate(query, listOf(stale32, stale192))
        assertEquals(RecognitionOutcome.Unknown, defensive)
    }

    // ------------------------------------------------------------------
    // 3. Malformed vector (NaN/Infinity) -> safe rejection.
    // ------------------------------------------------------------------
    @Test
    fun `malformed vectors are classified malformed and rejected safely`() {
        val nanVector = vec(currentDim).also { it[5] = Float.NaN }
        val infVector = vec(currentDim).also { it[9] = Float.POSITIVE_INFINITY }

        assertEquals(TemplateCompatibility.VectorClass.MALFORMED, TemplateCompatibility.classify(nanVector))
        assertFalse(TemplateCompatibility.isUsableVector(nanVector))
        assertFalse(TemplateCompatibility.isUsableVector(infVector))

        val outcome = RecognitionDecisionEngine().evaluate(nanVector, listOf(template(3L, currentDim)))
        assertEquals(RecognitionOutcome.Unknown, outcome)

        val enrollmentCheck = TemplateCompatibility.validateEnrollment(listOf(nanVector))
        assertTrue(enrollmentCheck is TemplateCompatibility.EnrollmentValidation.Rejected)
    }

    // ------------------------------------------------------------------
    // 4. Mixed current and stale gallery -> incompatible ignored, current still matches.
    // ------------------------------------------------------------------
    @Test
    fun `mixed dimension gallery ignores stale templates and matches with current ones`() {
        val queryA = vec(currentDim)
        val gallery = listOf(
            template(1L, currentDim),   // enrolled under current model
            template(2L, 192),          // legacy mobilefacenet model
            template(3L, 32),           // legacy prototype model
        )
        val partitioned = TemplateCompatibility.partition(gallery)
        assertEquals(listOf(1L), partitioned.usable.map { it.studentId })
        assertEquals(setOf(2L, 3L), partitioned.stale.map { it.studentId }.toSet())

        val outcome = RecognitionDecisionEngine().evaluate(queryA, partitioned.usable)
        assertTrue(outcome is RecognitionOutcome.Match)
        assertEquals(1L, (outcome as RecognitionOutcome.Match).studentId)

        // Defensive path: even the unpartitioned gallery must not throw and must not
        // accept the stale-only students.
        val defensive = RecognitionDecisionEngine().evaluate(queryA, gallery)
        assertTrue(defensive is RecognitionOutcome.Match)
        assertEquals(1L, (defensive as RecognitionOutcome.Match).studentId)
    }

    // ------------------------------------------------------------------
    // 5. Enrollment validation: only the CURRENT embedding dimension may be stored.
    // ------------------------------------------------------------------
    @Test
    fun `enrollment validation accepts only current-format observations`() {
        val ok = TemplateCompatibility.validateEnrollment(listOf(vec(currentDim), vec(currentDim, seed = 2f)))
        assertTrue(ok is TemplateCompatibility.EnrollmentValidation.Ok)
        assertEquals(currentDim, (ok as TemplateCompatibility.EnrollmentValidation.Ok).dim)

        val stale = TemplateCompatibility.validateEnrollment(listOf(vec(currentDim), vec(192)))
        assertTrue(stale is TemplateCompatibility.EnrollmentValidation.Rejected)

        val empty = TemplateCompatibility.validateEnrollment(emptyList())
        assertTrue(empty is TemplateCompatibility.EnrollmentValidation.Rejected)

        val nullLike = TemplateCompatibility.validateEnrollment(listOf(FloatArray(0)))
        assertTrue(nullLike is TemplateCompatibility.EnrollmentValidation.Rejected)
    }

    // ------------------------------------------------------------------
    // 6. Re-enrollment semantics: re-enrolling REPLACES stale templates with the
    // current format (validated at repository level; here we verify the contract:
    // new observations classify CURRENT and carry the new model id).
    // ------------------------------------------------------------------
    @Test
    fun `re-enrolled observations classify as current under new model profile`() {
        val profile = TemplateCompatibility.currentProfile()
        assertEquals(TemplateCompatibility.CURRENT_MODEL_ID, profile.modelId)
        assertEquals(currentDim, profile.embeddingDim)

        val freshEnrollment = vec(currentDim)
        assertEquals(TemplateCompatibility.VectorClass.CURRENT, TemplateCompatibility.classify(freshEnrollment))
        assertTrue(TemplateCompatibility.isUsableVector(freshEnrollment))
    }

    // ------------------------------------------------------------------
    // 7. NO silent conversion: a 32-D vector must not be truncated/padded into current dimension
    // anywhere in the compatibility layer.
    // ------------------------------------------------------------------
    @Test
    fun `no truncation or padding conversion exists for stale vectors`() {
        val stale32 = vec(32)
        // After classification the vector object is unchanged — same size, same values.
        assertEquals(TemplateCompatibility.VectorClass.STALE_DIMENSION, TemplateCompatibility.classify(stale32))
        assertEquals(32, stale32.size)
        // Partition returns the SAME instance; nothing produced a current-dimension derivative.
        val p = TemplateCompatibility.partition(listOf(StudentTemplatePair(2L, 20L, stale32)))
        assert(p.stale.single().embedding === stale32)
    }

    // ------------------------------------------------------------------
    // 8. Adaptive engine end-to-end: stale gallery cannot crash verification and
    // cannot accept an identity (regression for the production phone failure).
    // ------------------------------------------------------------------
    @Test
    fun `adaptive engine with stale-dimension templates fails closed without crashing`() {
        val queryObservation = FrameObservation(
            signals = FaceQualitySignals(
                faceCount = 1, yawDegrees = 0f, pitchDegrees = 0f, rollDegrees = 0f,
                leftEyeOpenProbability = 0.95f, rightEyeOpenProbability = 0.95f,
                faceRatio = 0.25f, centerX = 0.5f, centerY = 0.5f,
                blurVariance = 500f, brightness = 120f,
            ),
            quality = FaceQualityEngine.evaluate(
                FaceQualitySignals(
                    faceCount = 1, yawDegrees = 0f, pitchDegrees = 0f, rollDegrees = 0f,
                    leftEyeOpenProbability = 0.95f, rightEyeOpenProbability = 0.95f,
                    faceRatio = 0.25f, centerX = 0.5f, centerY = 0.5f,
                    blurVariance = 500f, brightness = 120f,
                ),
                FaceQualityConfig.calibrationDefaults(),
            ),
            liveness = LivenessResult.Passed,
            embedding = vec(currentDim),
        )

        val staleGallery = listOf(template(9L, 192), template(10L, 32))
        val engine = AdaptiveVerificationEngine(
            templates = staleGallery,
            maxFrames = 3,
        )
        // Must NOT throw IllegalArgumentException (the original production failure).
        val step = engine.submit(queryObservation)
        when (val final = step as? AdaptiveVerificationEngine.Step.Final) {
            null -> {
                // NeedMoreFrames would also be acceptable, but with budget 3 and one
                // observation the engine asks for more frames — still safe.
                assertTrue(step is AdaptiveVerificationEngine.Step.NeedMoreFrames)
            }
            else -> {
                val o = final.outcome
                assertTrue(
                    "Stale gallery must never produce a Match",
                    o !is AdaptiveVerificationEngine.Outcome.Match,
                )
            }
        }
    }
}

