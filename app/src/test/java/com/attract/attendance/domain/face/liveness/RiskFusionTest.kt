package com.attract.attendance.domain.face.liveness

import com.attract.attendance.domain.face.LivenessPolicy
import com.attract.attendance.domain.face.LivenessReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Doc 22 RiskFusion contract (exp7 / exp8 / exp10):
 *  - HIGH   = conclusive attack signature only.
 *  - LOW    = requires CONCLUSIVE 3D depth (or a passed challenge) — never passive evidence alone.
 *  - MEDIUM = not yet proven live (ACTIVE_CHECK_REQUIRED). Inconclusive depth is ALWAYS MEDIUM, never LOW.
 *  - PAD CNN below padSpoofReject (0.10) ESCALATES, it never hard-rejects on its own.
 */
class RiskFusionTest {

    private val policy = LivenessPolicy()

    private val liveDepth = 0.80f   // residual well above 0.035 -> depthScoreFor >= 0.50
    private val flatDepth = 0.10f   // conclusive plane -> < DEPTH_FLAT (0.20)

    // ---------------------------------------------------------------- policy constants are the locked values
    @Test
    fun policy_isDoc22() {
        assertEquals("liveness_v5_doc22", policy.id)
        assertEquals(0.10f, policy.padSpoofReject, 0f)
        assertEquals(8f, policy.parallaxMinYawDeltaDeg, 0f)
        assertEquals(0.035f, policy.parallaxLiveResidual, 0f)
        assertEquals(3, policy.pixelVetoWindow)
        assertEquals(2, policy.pixelVetoMinHits)
        assertEquals(0.22f, policy.maxHighFrequencyEnergy, 0f)
        assertEquals(0.25f, policy.maxSpecularHighlightRatio, 0f)
        assertEquals(3.0f, policy.printFlatTextureStdDev, 0f)
    }

    // ---------------------------------------------------------------- HIGH (conclusive attack)
    @Test
    fun injectionSuspected_returnsHigh() {
        val e = LivenessEvidence(injectionSuspected = true, depthScore = liveDepth, depthInconclusive = false)
        assertEquals(LivenessRisk.HIGH, RiskFusion.decide(e, policy))
    }

    @Test
    fun confirmedPixelVeto_returnsHigh_evenWithLiveDepth() {
        val e = LivenessEvidence(
            legacyPixelVeto = LivenessReason.SCREEN_REPLAY_SUSPECTED,
            depthScore = liveDepth, depthInconclusive = false, padScore = 0.95f,
        )
        assertEquals(LivenessRisk.HIGH, RiskFusion.decide(e, policy))
    }

    @Test
    fun challengeFailed_returnsHigh() {
        assertEquals(LivenessRisk.HIGH, RiskFusion.decide(LivenessEvidence(challengePassed = false), policy))
    }

    @Test
    fun flashBelowFail_returnsHigh() {
        assertEquals(LivenessRisk.HIGH, RiskFusion.decide(LivenessEvidence(flashScore = 0.25f), policy))
    }

    @Test
    fun conclusiveFlatDepth_returnsHigh_evenWithHighPad() {
        val e = LivenessEvidence(depthScore = flatDepth, depthInconclusive = false, padScore = 0.99f)
        assertEquals(LivenessRisk.HIGH, RiskFusion.decide(e, policy))
    }

    // ---------------------------------------------------------------- PAD CNN escalates, never hard-rejects
    @Test
    fun lowPadAlone_isMedium_notHigh() {
        // exp10: genuine window-backlit faces have 8.7 % of 3-frame medians below 0.10.
        val e = LivenessEvidence(padScore = 0.02f, depthInconclusive = true)
        assertEquals(LivenessRisk.MEDIUM, RiskFusion.decide(e, policy))
    }

    @Test
    fun lowPadWithLiveDepth_isMedium_challengeRequired() {
        val e = LivenessEvidence(padScore = 0.05f, depthScore = liveDepth, depthInconclusive = false)
        assertEquals(LivenessRisk.MEDIUM, RiskFusion.decide(e, policy))
    }

    @Test
    fun lowPadWithLiveDepthAndPassedChallenge_isLow() {
        val e = LivenessEvidence(padScore = 0.05f, depthScore = liveDepth, depthInconclusive = false, challengePassed = true)
        assertEquals(LivenessRisk.LOW, RiskFusion.decide(e, policy))
    }

    @Test
    fun padExactlyAtThreshold_isNotSuspect() {
        val e = LivenessEvidence(padScore = 0.10f, depthScore = liveDepth, depthInconclusive = false)
        assertFalse(RiskFusion.padSuspect(e, policy))
        assertEquals(LivenessRisk.LOW, RiskFusion.decide(e, policy))
    }

    @Test
    fun padJustBelowThreshold_isSuspect() {
        assertTrue(RiskFusion.padSuspect(LivenessEvidence(padScore = 0.0999f), policy))
    }

    @Test
    fun missingPad_isNotSuspect_liveDepthIsLow() {
        // Model asset missing -> null PAD -> 3D depth alone decides.
        val e = LivenessEvidence(padScore = null, depthScore = liveDepth, depthInconclusive = false)
        assertEquals(LivenessRisk.LOW, RiskFusion.decide(e, policy))
    }

    // ---------------------------------------------------------------- LOW requires conclusive depth / challenge
    @Test
    fun liveDepthAndCleanPad_isLow() {
        val e = LivenessEvidence(padScore = 0.90f, depthScore = liveDepth, depthInconclusive = false)
        assertEquals(LivenessRisk.LOW, RiskFusion.decide(e, policy))
    }

    @Test
    fun liveDepthAtBoundary_isLow() {
        val atBoundary = ParallaxDepthTest.depthScoreFor(policy.parallaxLiveResidual, policy.parallaxLiveResidual)
        assertEquals(0.50f, atBoundary, 1e-6f)
        val e = LivenessEvidence(depthScore = atBoundary, depthInconclusive = false)
        assertEquals(LivenessRisk.LOW, RiskFusion.decide(e, policy))
    }

    @Test
    fun passedChallengeWithoutDepth_cleanPad_isLow() {
        val e = LivenessEvidence(challengePassed = true, padScore = 0.60f, depthInconclusive = true)
        assertEquals(LivenessRisk.LOW, RiskFusion.decide(e, policy))
    }

    @Test
    fun passedChallengeWithoutDepth_suspectPad_isMedium() {
        val e = LivenessEvidence(challengePassed = true, padScore = 0.05f, depthInconclusive = true)
        assertEquals(LivenessRisk.MEDIUM, RiskFusion.decide(e, policy))
    }

    // ---------------------------------------------------------------- Inconclusive depth is NEVER LOW
    @Test
    fun inconclusiveDepth_highPadAndBlink_isMedium_notLow() {
        // Passive evidence alone (high PAD + blink) must not pass: a replayed video blinks too.
        val e = LivenessEvidence(padScore = 0.99f, blinkSeen = true, depthInconclusive = true)
        assertEquals(LivenessRisk.MEDIUM, RiskFusion.decide(e, policy))
    }

    @Test
    fun inconclusiveDepth_ignoresStaleDepthScore() {
        // A depthScore value with depthInconclusive=true must be ignored (never treated as live).
        val e = LivenessEvidence(depthScore = 0.95f, depthInconclusive = true, padScore = 0.95f)
        assertFalse(e.depthLive)
        assertFalse(e.depthFlat)
        assertEquals(LivenessRisk.MEDIUM, RiskFusion.decide(e, policy))
    }

    @Test
    fun strongFlashWithInconclusiveDepth_isMedium() {
        val e = LivenessEvidence(flashScore = 0.90f, depthScore = 0.60f, depthInconclusive = true)
        assertEquals(LivenessRisk.MEDIUM, RiskFusion.decide(e, policy))
    }

    @Test
    fun ambiguousDepthBetweenFlatAndLive_isMedium() {
        val e = LivenessEvidence(depthScore = 0.35f, depthInconclusive = false, padScore = 0.90f)
        assertFalse(e.depthLive)
        assertFalse(e.depthFlat)
        assertEquals(LivenessRisk.MEDIUM, RiskFusion.decide(e, policy))
    }

    @Test
    fun emptyEvidence_isMedium() {
        assertEquals(LivenessRisk.MEDIUM, RiskFusion.decide(LivenessEvidence(), policy))
    }
}
