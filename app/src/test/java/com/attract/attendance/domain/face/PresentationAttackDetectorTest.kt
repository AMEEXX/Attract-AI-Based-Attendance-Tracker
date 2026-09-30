package com.attract.attendance.domain.face

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PresentationAttackDetectorTest {

    private fun signals(
        highFrequencyEnergy: Float,
        specularHighlightRatio: Float,
        centerTextureStdDev: Float,
    ) = PresentationAttackSignals(
        highFrequencyEnergy = highFrequencyEnergy,
        specularHighlightRatio = specularHighlightRatio,
        centerTextureStdDev = centerTextureStdDev,
    )

    @Test
    fun nullOrUnavailableSignals_passClosedNeverBlocksGenuineUser() {
        assertEquals(LivenessResult.Passed, PresentationAttackDetector.analyze(null, blurVariance = 500f))
        assertEquals(LivenessResult.Passed, PresentationAttackDetector.analyze(PresentationAttackSignals.Unavailable, blurVariance = 500f))
    }

    @Test
    fun nonFiniteSignals_safePassNoCrash() {
        assertEquals(
            LivenessResult.Passed,
            PresentationAttackDetector.analyze(signals(Float.NaN, 0f, 20f), blurVariance = 500f),
        )
    }

    @Test
    fun screenReplay_highFrequencyMoireEnergy_rejected() {
        val result = PresentationAttackDetector.analyze(signals(0.30f, 0.01f, 20f), blurVariance = 500f)
        assertTrue(result is LivenessResult.Rejected)
        assertEquals(LivenessReason.SCREEN_REPLAY_SUSPECTED, (result as LivenessResult.Rejected).reason)
    }

    @Test
    fun screenGlare_specularHighlights_rejectedAsScreenReplay() {
        val result = PresentationAttackDetector.analyze(signals(0.05f, 0.20f, 20f), blurVariance = 500f)
        assertTrue(result is LivenessResult.Rejected)
        assertEquals(LivenessReason.SCREEN_REPLAY_SUSPECTED, (result as LivenessResult.Rejected).reason)
    }

    @Test
    fun printedPhoto_flatSharpTexture_rejectedAsPrintPhoto() {
        // Sharp frame overall but abnormally flat central texture — print signature.
        val result = PresentationAttackDetector.analyze(signals(0.05f, 0.01f, 3f), blurVariance = 150f)
        assertTrue(result is LivenessResult.Rejected)
        assertEquals(LivenessReason.PRINT_PHOTO_SUSPECTED, (result as LivenessResult.Rejected).reason)
    }

    @Test
    fun flatTextureButBlurryFrame_notPrintRejected_blurGateOwnsThatCase() {
        // A blurry flat crop is handled by the BLUR quality gate, not the print gate.
        assertEquals(LivenessResult.Passed, PresentationAttackDetector.analyze(signals(0.05f, 0.01f, 3f), blurVariance = 500f))
    }

    @Test
    fun cleanLiveLikeSignals_passAllGates() {
        assertEquals(
            LivenessResult.Passed,
            PresentationAttackDetector.analyze(signals(0.05f, 0.02f, 18f), blurVariance = 500f),
        )
    }

    @Test
    fun livenessCheck_integratesPresentationAttackGates() {
        val baseSignals = FaceQualitySignals(
            faceCount = 1,
            yawDegrees = -30f,
            pitchDegrees = 0f,
            rollDegrees = 0f,
            leftEyeOpenProbability = 0.9f,
            rightEyeOpenProbability = 0.9f,
            faceRatio = 0.25f,
            centerX = 0.5f,
            centerY = 0.5f,
            blurVariance = 1000f,
            brightness = 128f,
        )

        // Live-like: passes.
        assertEquals(
            LivenessResult.Passed,
            LivenessEngine.check(baseSignals, previousSignals = null, presentationAttackSignals = signals(0.05f, 0.02f, 18f)),
        )

        // Spoofed screen presentation: rejected with student-safe guidance.
        val rejected = LivenessEngine.check(baseSignals, previousSignals = null, presentationAttackSignals = signals(0.30f, 0.01f, 18f))
        assertTrue(rejected is LivenessResult.Rejected)
        assertEquals(LivenessReason.SCREEN_REPLAY_SUSPECTED, (rejected as LivenessResult.Rejected).reason)
    }
}
