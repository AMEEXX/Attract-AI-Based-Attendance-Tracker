package com.attract.attendance.domain.face

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LivenessEngineTest {

    private fun signals(
        faceCount: Int = 1,
        yawDegrees: Float = 0f,
        leftEyeOpenProbability: Float = 0.9f,
        rightEyeOpenProbability: Float = 0.9f,
        blurVariance: Float = 50f,
        brightness: Float = 100f
    ) = FaceQualitySignals(
        faceCount = faceCount,
        yawDegrees = yawDegrees,
        pitchDegrees = 0f,
        rollDegrees = 0f,
        leftEyeOpenProbability = leftEyeOpenProbability,
        rightEyeOpenProbability = rightEyeOpenProbability,
        faceRatio = 0.2f,
        centerX = 0.5f,
        centerY = 0.5f,
        blurVariance = blurVariance,
        brightness = brightness
    )

    @Test
    fun closedEyes_rejected() {
        val result = LivenessEngine.check(signals(leftEyeOpenProbability = 0.1f))
        assertTrue(result is LivenessResult.Rejected)
        assertEquals(LivenessReason.EYES_CLOSED_OR_STATIC, (result as LivenessResult.Rejected).reason)
    }

    @Test
    fun staticLowQualityBlur_rejected() {
        val result = LivenessEngine.check(signals(blurVariance = 10f))
        assertTrue(result is LivenessResult.Rejected)
        assertEquals(LivenessReason.PASSIVE_SPOOF_SUSPECTED, (result as LivenessResult.Rejected).reason)
    }

    @Test
    fun lowLight_rejected() {
        val result = LivenessEngine.check(signals(brightness = 30f))
        assertTrue(result is LivenessResult.Rejected)
        assertEquals(LivenessReason.PASSIVE_SPOOF_SUSPECTED, (result as LivenessResult.Rejected).reason)
    }

    @Test
    fun staticCrossFramePresentationAttack_rejected() {
        val current = signals(leftEyeOpenProbability = 0.9500f, rightEyeOpenProbability = 0.9500f, yawDegrees = 2.000f)
        val previous = signals(leftEyeOpenProbability = 0.9500f, rightEyeOpenProbability = 0.9500f, yawDegrees = 2.000f)

        val result = LivenessEngine.check(current, previous)
        assertTrue(result is LivenessResult.Rejected)
        assertEquals(LivenessReason.INSUFFICIENT_VARIANCE, (result as LivenessResult.Rejected).reason)
    }

    @Test
    fun naturalMovementCrossFrame_passed() {
        val current = signals(leftEyeOpenProbability = 0.95f, rightEyeOpenProbability = 0.95f, yawDegrees = 12.0f)
        val previous = signals(leftEyeOpenProbability = 0.85f, rightEyeOpenProbability = 0.88f, yawDegrees = 0.0f)

        val result = LivenessEngine.check(current, previous)
        assertTrue(result is LivenessResult.Passed)
    }
}
