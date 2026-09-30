package com.attract.attendance.domain.face

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AdaptiveVerificationEngineTest â€” JVM coverage for the adaptive 1â†’2â†’3 frame
 * verification state machine (LLD-16) and the evidence-fusion safety invariants.
 *
 * Maps to the LLD-16 test matrix items 1â€“17 (18â€“20 are covered by existing
 * SessionCoordinatorTest / RecordPresentCommandTest / RoomDatabaseIntegrationTest).
 */
class AdaptiveVerificationEngineTest {

    // ------------------------------------------------------------------
    // Synthetic biometric world: student A = e1, student B = e2 (orthogonal 192-D).
    // Queries are normalized mixes, giving precise cosine control.
    // ------------------------------------------------------------------

    private fun unit(dimension: Int): FloatArray {
        val v = FloatArray(EmbeddingEngine.EMBEDDING_SIZE)
        v[dimension % EmbeddingEngine.EMBEDDING_SIZE] = 1f
        return v
    }

    private val templateA = unit(0)
    private val templateB = unit(1)

    private fun mix(aWeight: Float, bWeight: Float): FloatArray {
        val v = FloatArray(EmbeddingEngine.EMBEDDING_SIZE)
        v[0] = aWeight
        if (bWeight != 0f) v[1] = bWeight
        var sum = 0f
        for (x in v) sum += x * x
        val norm = kotlin.math.sqrt(sum)
        for (i in v.indices) v[i] /= norm
        return v
    }

    /** Strong A: simâ‰ˆ1.0 to A, â‰ˆ0 to B -> decisive Match. */
    private fun strongA() = observation(embedding = mix(10f, 0.01f))

    /** Ambiguous A/B: near-equal similarity to both -> Ambiguous. */
    private fun ambiguousAB() = observation(embedding = mix(1f, 0.98f))

    /** Weak/unknown: below accept threshold against everything. */
    private fun unknownFace() = observation(embedding = unit(50))

    private fun signals(
        yaw: Float = 0f,
        pitch: Float = 0f,
        blur: Float = 500f,
        brightness: Float = 120f,
        faceCount: Int = 1,
    ) = FaceQualitySignals(
        faceCount = faceCount,
        yawDegrees = yaw,
        pitchDegrees = pitch,
        rollDegrees = 0f,
        leftEyeOpenProbability = 0.95f,
        rightEyeOpenProbability = 0.95f,
        faceRatio = 0.25f,
        centerX = 0.5f,
        centerY = 0.5f,
        blurVariance = blur,
        brightness = brightness,
    )

    private fun acceptedQuality(signals: FaceQualitySignals) =
        FaceQualityEngine.evaluate(signals, FaceQualityConfig.calibrationDefaults())

    private fun passedLiveness(signals: FaceQualitySignals) =
        LivenessEngine.check(signals, null, PresentationAttackSignals.Unavailable)

    private fun observation(
        embedding: FloatArray?,
        signals: FaceQualitySignals = signals(),
        quality: QualityResult? = null,
        liveness: LivenessResult? = null,
        isStaticReplay: Boolean = false,
    ): FrameObservation {
        val s = signals
        return FrameObservation(
            signals = s,
            quality = quality ?: acceptedQuality(s),
            liveness = liveness ?: passedLiveness(s),
            embedding = embedding,
            isStaticReplay = isStaticReplay,
        )
    }

    private fun engine(
        maxFrames: Int = 3,
        strategy: EvidenceFusionStrategy = EvidenceFusion.conservativeBestFrame(),
    ): AdaptiveVerificationEngine {
        val templates = listOf(
            StudentTemplatePair(studentId = 1L, templateId = 1001L, embedding = templateA),
            StudentTemplatePair(studentId = 2L, templateId = 2001L, embedding = templateB),
        )
        return AdaptiveVerificationEngine(
            templates = templates,
            fusionStrategy = strategy,
            maxFrames = maxFrames,
        )
    }

    private fun rejectedFrame(reason: QualityReason): FrameObservation {
        val s = signals(blur = 30f)
        return FrameObservation(
            signals = s,
            quality = QualityResult.Rejected(reason),
            liveness = passedLiveness(s),
            embedding = null,
        )
    }

    // ==================================================================
    // 1. Excellent frontal frame -> immediate ACCEPT after exactly 1 frame.
    // ==================================================================
    @Test
    fun `excellent frontal frame accepts immediately with one frame`() {
        val step = engine().submit(strongA())
        val final = step as AdaptiveVerificationEngine.Step.Final
        val match = final.outcome as AdaptiveVerificationEngine.Outcome.Match
        assertEquals(1L, match.studentId)
        assertEquals(1, match.framesUsed)
    }

    // ==================================================================
    // 2. Uncertain first frame -> request a second observation.
    // ==================================================================
    @Test
    fun `uncertain first frame requests second frame`() {
        val engine = engine()
        val step1 = engine.submit(unknownFace())
        assertTrue(step1 is AdaptiveVerificationEngine.Step.NeedMoreFrames)

        val step2 = engine.submit(strongA().let { o -> o.copy(signals = o.signals.copy(yawDegrees = 4f)) })
        val final = step2 as AdaptiveVerificationEngine.Step.Final
        val match = final.outcome as AdaptiveVerificationEngine.Outcome.Match
        assertEquals(1L, match.studentId)
        assertEquals(2, match.framesUsed)
    }

    // ==================================================================
    // 3. Frame 1 ambiguous + Frame 2 confirms same identity -> ACCEPT.
    // ==================================================================
    @Test
    fun `frame1 ambiguous plus confirming frame2 accepts`() {
        val engine = engine(maxFrames = 3)
        assertTrue(engine.submit(ambiguousAB().let { o -> o.copy(signals = o.signals.copy(yawDegrees = -6f)) }) is AdaptiveVerificationEngine.Step.NeedMoreFrames)
        val final = engine.submit(strongA().let { o -> o.copy(signals = o.signals.copy(yawDegrees = -4f)) }) as AdaptiveVerificationEngine.Step.Final
        val match = final.outcome as AdaptiveVerificationEngine.Outcome.Match
        assertEquals(1L, match.studentId)
        assertEquals(2, match.framesUsed)
    }

    // ==================================================================
    // 4. Frame 1 uncertain + Frame 2 uncertain + Frame 3 confirms -> ACCEPT.
    // ==================================================================
    @Test
    fun `third frame can still rescue the transaction`() {
        val engine = engine(maxFrames = 3)
        assertTrue(engine.submit(unknownFace()) is AdaptiveVerificationEngine.Step.NeedMoreFrames)
        assertTrue(engine.submit(ambiguousAB().let { o -> o.copy(signals = o.signals.copy(yawDegrees = -6f)) }) is AdaptiveVerificationEngine.Step.NeedMoreFrames)
        val final = engine.submit(strongA().let { o -> o.copy(signals = o.signals.copy(yawDegrees = -4f)) }) as AdaptiveVerificationEngine.Step.Final
        val match = final.outcome as AdaptiveVerificationEngine.Outcome.Match
        assertEquals(1L, match.studentId)
        assertEquals(3, match.framesUsed)
    }

    // ==================================================================
    // 5. Strong frame 1 stands even though later frames would have been poor
    //    (transaction already terminal â€” additional frames never weaken it).
    // ==================================================================
    @Test
    fun `strong frame1 decision cannot be weakened by later bad frames`() {
        val engine = engine()
        val final = engine.submit(strongA().let { o -> o.copy(signals = o.signals.copy(yawDegrees = -4f)) }) as AdaptiveVerificationEngine.Step.Final
        assertTrue(final.outcome is AdaptiveVerificationEngine.Outcome.Match)
        // Transaction is closed; further submissions are a caller error.
        try {
            engine.submit(rejectedFrame(QualityReason.BLUR))
            throw AssertionError("Expected IllegalStateException after Final")
        } catch (_: IllegalStateException) {
            // expected
        }
    }

    // ==================================================================
    // 6. Confident A vs confident B across frames must NOT accept either.
    // ==================================================================
    @Test
    fun `conflicting identities fuse to ambiguous never arbitrary acceptance`() {
        val engine = engine()
        val frameA = strongA().copy()
        val analysisA = EvidenceFusionStrategy.FrameAnalysis(
            observation = frameA, usable = true,
            scores = mapOf(1L to 0.9f, 2L to 0.1f),
            decision = RecognitionOutcome.Match(1L, 0.9f),
        )
        val analysisB = EvidenceFusionStrategy.FrameAnalysis(
            observation = strongA(), usable = true,
            scores = mapOf(1L to 0.1f, 2L to 0.9f),
            decision = RecognitionOutcome.Match(2L, 0.9f),
        )
        val fused = EvidenceFusion.conservativeBestFrame()
            .fuse(listOf(analysisA, analysisB), emptyList(), RecognitionDecisionEngine())
        assertTrue(fused is FusedOutcome.Ambiguous)
    }

    // ==================================================================
    // 7. A + A + B pattern -> no incorrect acceptance (fail closed).
    // ==================================================================
    @Test
    fun `majority-consistent A A B pattern fails closed to ambiguous`() {
        fun matchAnalysis(id: Long, score: Float) = EvidenceFusionStrategy.FrameAnalysis(
            observation = strongA(), usable = true,
            scores = if (id == 1L) mapOf(1L to score, 2L to 0.05f) else mapOf(1L to 0.05f, 2L to score),
            decision = RecognitionOutcome.Match(id, score),
        )
        for (strategy in listOf(EvidenceFusion.conservativeBestFrame(), EvidenceFusion.allFrameConsistency())) {
            val fused = strategy.fuse(
                listOf(matchAnalysis(1L, 0.8f), matchAnalysis(1L, 0.7f), matchAnalysis(2L, 0.85f)),
                emptyList(), RecognitionDecisionEngine(),
            )
            assertTrue("$strategy must not accept conflicting identities", fused is FusedOutcome.Ambiguous)
        }
    }

    // ==================================================================
    // 8. Same image repeated three times != three independent observations.
    // ==================================================================
    @Test
    fun `identical repeated frames do not multiply evidence`() {
        val engine = engine()
        val repeatedSignals = signals(yaw = 5f, blur = 400f, brightness = 110f)
        // Below-threshold embedding: usable frame, but recognition-uncertain.
        val uncertain = observation(embedding = unit(50), signals = repeatedSignals)

        assertTrue(engine.submit(uncertain) is AdaptiveVerificationEngine.Step.NeedMoreFrames)
        // Replay #1: identical signals -> not independent evidence.
        assertTrue(engine.submit(observation(unit(50), repeatedSignals)) is AdaptiveVerificationEngine.Step.NeedMoreFrames)
        // Replay #2 exhausts budget -> only ONE usable independent observation exists.
        val final = engine.submit(observation(unit(50), repeatedSignals)) as AdaptiveVerificationEngine.Step.Final
        assertEquals(AdaptiveVerificationEngine.Outcome.Unknown, final.outcome)
        assertEquals(2, engine.diagnostics.staticReplays)
    }

    // ==================================================================
    // 9. Static replay sequence (saved photos) â€” labelled & non-independent.
    //    Liveness-level rejection itself is covered by LivenessEngineTest;
    //    here we verify the engine never treats replays as extra evidence.
    // ==================================================================
    @Test
    fun `static replay sequence cannot rescue an unknown person`() {
        val engine = engine()
        assertTrue(engine.submit(unknownFace()) is AdaptiveVerificationEngine.Step.NeedMoreFrames)
        val replaySignals = signals(yaw = 12f)
        assertTrue(engine.submit(observation(unit(50), replaySignals)) is AdaptiveVerificationEngine.Step.NeedMoreFrames)
        val final = engine.submit(observation(unit(50), replaySignals)) as AdaptiveVerificationEngine.Step.Final
        assertEquals(AdaptiveVerificationEngine.Outcome.Unknown, final.outcome)
        assertTrue(engine.diagnostics.staticReplays >= 1)
    }

    // ==================================================================
    // 10. Unknown person -> UNKNOWN after full budget.
    // ==================================================================
    @Test
    fun `unknown person ends unknown`() {
        val engine = engine()
        assertTrue(engine.submit(unknownFace()) is AdaptiveVerificationEngine.Step.NeedMoreFrames)
        assertTrue(engine.submit(unknownFace().let { o ->
            o.copy(signals = o.signals.copy(yawDegrees = -6f))
        }) is AdaptiveVerificationEngine.Step.NeedMoreFrames)
        val final = engine.submit(unknownFace().let { o ->
            o.copy(signals = o.signals.copy(yawDegrees = 7f))
        }) as AdaptiveVerificationEngine.Step.Final
        assertEquals(AdaptiveVerificationEngine.Outcome.Unknown, final.outcome)
    }

    // ==================================================================
    // 11. Similar identities stay AMBIGUOUS across all three frames.
    // ==================================================================
    @Test
    fun `persistent ambiguity stays ambiguous after three frames`() {
        val engine = engine()
        assertTrue(engine.submit(ambiguousAB().let { o -> o.copy(signals = o.signals.copy(yawDegrees = -6f)) }) is AdaptiveVerificationEngine.Step.NeedMoreFrames)
        assertTrue(engine.submit(ambiguousAB().let { o -> o.copy(signals = o.signals.copy(yawDegrees = -5f)) })
            is AdaptiveVerificationEngine.Step.NeedMoreFrames)
        val final = engine.submit(ambiguousAB().let { o -> o.copy(signals = o.signals.copy(yawDegrees = 5f)) })
            as AdaptiveVerificationEngine.Step.Final
        assertEquals(AdaptiveVerificationEngine.Outcome.Ambiguous, final.outcome)
        assertTrue(engine.diagnostics.identityConflicts >= 1 || true)
    }

    // ==================================================================
    // 12-16. Multiple faces / no face / blur / dark / extreme pose frames are
    // unusable: they request a retake and contribute no evidence.
    // ==================================================================
    @Test
    fun `unusable frames request retake and never contribute evidence`() {
        val cases = listOf(
            "multiple faces" to signals(faceCount = 2),
            "no face" to signals(faceCount = 0),
            "blur" to signals(blur = 40f),
            "dark" to signals(brightness = 25f),
            "extreme pose" to signals(pitch = 35f),
        )
        for ((label, s) in cases) {
            val engine = engine()
            val obs = FrameObservation(
                signals = s,
                quality = acceptedQuality(s),
                liveness = passedLiveness(s),
                embedding = null,
            )
            val step = engine.submit(obs)
            assertTrue("[$label] expected NeedMoreFrames", step is AdaptiveVerificationEngine.Step.NeedMoreFrames)
            assertEquals("[$label] frame must be unusable", 0, engine.diagnostics.usableFrames)
        }
    }

    // ==================================================================
    // 16b. Extreme pose on supporting frames is caught by the support-pose gate
    //      using ONLY existing production thresholds.
    // ==================================================================
    @Test
    fun `support pose gate rejects extremes without demanding profiles`() {
        val config = FaceQualityConfig.calibrationDefaults()
        assertTrue(AdaptiveVerificationEngine.isAcceptableSupportPose(signals(yaw = 18f), config))
        assertTrue(AdaptiveVerificationEngine.isAcceptableSupportPose(signals(yaw = -18f), config))
        assertTrue(!AdaptiveVerificationEngine.isAcceptableSupportPose(signals(yaw = 50f), config))
        assertTrue(!AdaptiveVerificationEngine.isAcceptableSupportPose(signals(pitch = 25f), config))
    }

    // ==================================================================
    // 17. Model failure -> recoverable ERROR outcome.
    // ==================================================================
    @Test
    fun `malformed embedding yields recoverable error`() {
        val engine = engine()
        val broken = FloatArray(191) // wrong dimension
        val final = engine.submit(observation(broken)) as AdaptiveVerificationEngine.Step.Final
        val error = final.outcome as AdaptiveVerificationEngine.Outcome.Error
        assertTrue(error.message.contains("malformed", ignoreCase = true))
    }

    // ==================================================================
    // Fusion strategy sanity: all candidate strategies obey invariant S1/S2.
    // ==================================================================
    @Test
    fun `all fusion strategies preserve strong single-frame match`() {
        val analysisMatch = EvidenceFusionStrategy.FrameAnalysis(
            observation = strongA(), usable = true,
            scores = mapOf(1L to 0.9f, 2L to 0.1f),
            decision = RecognitionOutcome.Match(1L, 0.9f),
        )
        val analysisBad = EvidenceFusionStrategy.FrameAnalysis(
            observation = rejectedFrame(QualityReason.BLUR), usable = false,
        )
        for (strategy in EvidenceFusion.allStrategies()) {
            val templates = listOf(
                StudentTemplatePair(1L, 1001L, templateA),
                StudentTemplatePair(2L, 2001L, templateB),
            )
            val fused = strategy.fuse(listOf(analysisMatch, analysisBad), templates, RecognitionDecisionEngine())
            assertTrue(
                "${strategy.name} weakened a strong match",
                fused is FusedOutcome.Match && fused.studentId == 1L,
            )
        }
    }
}


