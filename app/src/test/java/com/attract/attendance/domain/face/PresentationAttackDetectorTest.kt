package com.attract.attendance.domain.face

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PresentationAttackDetectorTest {

    private fun signals(
        highFrequencyEnergy: Float,
        specularHighlightRatio: Float,
        centerTextureStdDev: Float,
        noiseSigma: Float = 0f,
    ) = PresentationAttackSignals(
        highFrequencyEnergy = highFrequencyEnergy,
        specularHighlightRatio = specularHighlightRatio,
        centerTextureStdDev = centerTextureStdDev,
        noiseSigma = noiseSigma,
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
        val result = PresentationAttackDetector.analyze(signals(0.35f, 0.01f, 20f), blurVariance = 500f)
        assertTrue(result is LivenessResult.Rejected)
        assertEquals(LivenessReason.SCREEN_REPLAY_SUSPECTED, (result as LivenessResult.Rejected).reason)
    }

    @Test
    fun screenReplay_withHighNoiseSigma_compensatedHfePassesGenuineDimFace() {
        // High raw HFE (0.25 > 0.22 threshold), but noiseSigma = 5.0f from ISP/ISO in dim room
        // hfeCompensated = max(0, 0.25*255 - 3.6*5.0)/255 = (63.75 - 18.0)/255 = 45.75/255 ≈ 0.179 < 0.22
        val sig = signals(highFrequencyEnergy = 0.25f, specularHighlightRatio = 0.01f, centerTextureStdDev = 20f, noiseSigma = 5.0f)
        assertEquals(LivenessResult.Passed, PresentationAttackDetector.analyze(sig, blurVariance = 500f))
    }

    @Test
    fun screenGlare_specularHighlights_rejectedAsScreenReplay() {
        // 0.15 passes with recalibrated 0.25 threshold
        assertEquals(LivenessResult.Passed, PresentationAttackDetector.analyze(signals(0.05f, 0.15f, 20f), blurVariance = 500f))

        // 0.30 exceeds 0.25 threshold and is rejected
        val result = PresentationAttackDetector.analyze(signals(0.05f, 0.30f, 20f), blurVariance = 500f)
        assertTrue(result is LivenessResult.Rejected)
        assertEquals(LivenessReason.SCREEN_REPLAY_SUSPECTED, (result as LivenessResult.Rejected).reason)
    }

    @Test
    fun printedPhoto_flatTexture_rejectedAsPrintPhoto() {
        // Flat central texture (2.0 < 3.0 threshold) rejects as PRINT without requiring a blur condition (Doc 21 WP-D)
        val result = PresentationAttackDetector.analyze(signals(0.05f, 0.01f, 2.0f), blurVariance = 500f)
        assertTrue(result is LivenessResult.Rejected)
        assertEquals(LivenessReason.PRINT_PHOTO_SUSPECTED, (result as LivenessResult.Rejected).reason)
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
