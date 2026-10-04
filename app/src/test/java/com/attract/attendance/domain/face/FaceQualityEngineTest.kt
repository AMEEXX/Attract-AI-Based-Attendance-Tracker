package com.attract.attendance.domain.face

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceQualityEngineTest {

    private val config = FaceQualityConfig.calibrationDefaults()

    private fun signals(
        faceCount: Int = 1,
        yawDegrees: Float = 0f,
        pitchDegrees: Float = 0f,
        rollDegrees: Float = 0f,
        leftEyeOpenProbability: Float? = 0.9f,
        rightEyeOpenProbability: Float? = 0.9f,
        faceRatio: Float = 0.25f,
        centerX: Float = 0.5f,
        centerY: Float = 0.5f,
        blurVariance: Float = 1000f,
        brightness: Float = 128f,
    ) = FaceQualitySignals(
        faceCount = faceCount,
        yawDegrees = yawDegrees,
        pitchDegrees = pitchDegrees,
        rollDegrees = rollDegrees,
        leftEyeOpenProbability = leftEyeOpenProbability,
        rightEyeOpenProbability = rightEyeOpenProbability,
        faceRatio = faceRatio,
        centerX = centerX,
        centerY = centerY,
        blurVariance = blurVariance,
        brightness = brightness,
    )

    private fun rejectedReason(result: QualityResult): QualityReason? =
        (result as? QualityResult.Rejected)?.reason

    @Test
    fun noFace_rejectedAsNoFace() {
        assertEquals(QualityReason.NO_FACE, rejectedReason(FaceQualityEngine.evaluate(signals(faceCount = 0), config)))
    }

    @Test
    fun multipleFaces_rejectedAsMultipleFaces() {
        assertEquals(QualityReason.MULTIPLE_FACES, rejectedReason(FaceQualityEngine.evaluate(signals(faceCount = 2), config)))
    }

    @Test
    fun smallFace_rejectedBeforeLivenessOrModel() {
        assertEquals(QualityReason.TOO_SMALL, rejectedReason(FaceQualityEngine.evaluate(signals(faceRatio = 0.05f), config)))
    }

    @Test
    fun offFrameFace_rejectedAsOffCenter() {
        assertEquals(QualityReason.OFF_CENTER, rejectedReason(FaceQualityEngine.evaluate(signals(centerX = 0.9f), config)))
    }

    @Test
    fun extremePose_rejectedAsPose() {
        // Legacy straight-only evaluation maps excessive yaw to the step-aware reason.
        assertEquals(QualityReason.POSE_NOT_STRAIGHT, rejectedReason(FaceQualityEngine.evaluate(signals(yawDegrees = 30f), config)))
        assertEquals(QualityReason.POSE, rejectedReason(FaceQualityEngine.evaluate(signals(pitchDegrees = -25f), config)))
    }

    @Test
    fun straightStep_frontalYawAccepted_extremeYawRejectedWithStepReason() {
        assertEquals(QualityReason.POSE_NOT_STRAIGHT, rejectedReason(FaceQualityEngine.evaluate(signals(yawDegrees = 30f), config, ExpectedPose.STRAIGHT)))
        assertEquals(QualityReason.POSE_NOT_STRAIGHT, rejectedReason(FaceQualityEngine.evaluate(signals(yawDegrees = -18f), config, ExpectedPose.STRAIGHT)))
        assertEquals(QualityReason.POSE_NOT_STRAIGHT, rejectedReason(FaceQualityEngine.evaluate(signals(yawDegrees = 14f), config, ExpectedPose.STRAIGHT)))
        assertTrue(FaceQualityEngine.evaluate(signals(yawDegrees = 10f), config, ExpectedPose.STRAIGHT) is QualityResult.Accepted)
    }

    @Test
    fun leftStep_onlyNegativeProfileYawAccepted() {
        // Frontal frame offered for the LEFT step is rejected and does not advance.
        assertEquals(QualityReason.POSE_NOT_LEFT, rejectedReason(FaceQualityEngine.evaluate(signals(), config, ExpectedPose.LEFT)))
        assertEquals(QualityReason.POSE_NOT_LEFT, rejectedReason(FaceQualityEngine.evaluate(signals(yawDegrees = 30f), config, ExpectedPose.LEFT)))
        // Genuine left profile accepted.
        assertTrue(FaceQualityEngine.evaluate(signals(yawDegrees = -30f), config, ExpectedPose.LEFT) is QualityResult.Accepted)
        // Window bounds honored: -10° is on the threshold (accepted), -5° is too shallow (rejected), -45° is too steep (rejected).
        assertTrue(FaceQualityEngine.evaluate(signals(yawDegrees = -10f), config, ExpectedPose.LEFT) is QualityResult.Accepted)
        assertEquals(QualityReason.POSE_NOT_LEFT, rejectedReason(FaceQualityEngine.evaluate(signals(yawDegrees = -5f), config, ExpectedPose.LEFT)))
        assertEquals(QualityReason.POSE_NOT_LEFT, rejectedReason(FaceQualityEngine.evaluate(signals(yawDegrees = -45f), config, ExpectedPose.LEFT)))
    }

    @Test
    fun rightStep_onlyPositiveProfileYawAccepted() {
        assertEquals(QualityReason.POSE_NOT_RIGHT, rejectedReason(FaceQualityEngine.evaluate(signals(), config, ExpectedPose.RIGHT)))
        assertEquals(QualityReason.POSE_NOT_RIGHT, rejectedReason(FaceQualityEngine.evaluate(signals(yawDegrees = -30f), config, ExpectedPose.RIGHT)))
        assertTrue(FaceQualityEngine.evaluate(signals(yawDegrees = 30f), config, ExpectedPose.RIGHT) is QualityResult.Accepted)
        assertEquals(QualityReason.POSE_NOT_RIGHT, rejectedReason(FaceQualityEngine.evaluate(signals(yawDegrees = 50f), config, ExpectedPose.RIGHT)))
    }

    @Test
    fun profileSteps_pitchStillGloballyBounded() {
        assertEquals(
            QualityReason.POSE,
            rejectedReason(FaceQualityEngine.evaluate(signals(yawDegrees = -30f, pitchDegrees = 25f), config, ExpectedPose.LEFT)),
        )
    }

    @Test
    fun yawDiversityGuard_rejectsNearIdenticalCaptures() {
        val captured = listOf(0f, -30f)
        assertTrue(FaceQualityEngine.isDistinctFromCaptured(30f, captured))
        assertTrue(!FaceQualityEngine.isDistinctFromCaptured(-5f, captured))
        assertTrue(!FaceQualityEngine.isDistinctFromCaptured(-35f, captured))
        assertTrue(FaceQualityEngine.isDistinctFromCaptured(0f, emptyList()))
    }

    @Test
    fun blurredFace_rejectedAsBlur() {
        assertEquals(QualityReason.BLUR, rejectedReason(FaceQualityEngine.evaluate(signals(blurVariance = 40f), config)))
    }

    @Test
    fun darkFace_rejectedAsDark() {
        assertEquals(QualityReason.DARK, rejectedReason(FaceQualityEngine.evaluate(signals(brightness = 20f), config)))
    }

    @Test
    fun overexposedFace_rejectedAsOverexposed() {
        assertEquals(QualityReason.OVEREXPOSED, rejectedReason(FaceQualityEngine.evaluate(signals(brightness = 240f), config)))
    }

    @Test
    fun eyesRequired_nullEyeProbabilities_rejectedAsEyesUnclear() {
        val strict = config.copy(requireEyes = true)
        assertEquals(
            QualityReason.EYES_UNCLEAR,
            rejectedReason(FaceQualityEngine.evaluate(signals(leftEyeOpenProbability = null, rightEyeOpenProbability = null), strict)),
        )
    }

    @Test
    fun eyesRequired_lowEyeProbability_rejectedAsEyesUnclear() {
        val strict = config.copy(requireEyes = true)
        assertEquals(
            QualityReason.EYES_UNCLEAR,
            rejectedReason(FaceQualityEngine.evaluate(signals(leftEyeOpenProbability = 0.3f, rightEyeOpenProbability = 0.9f), strict)),
        )
    }

    @Test
    fun eyesNotRequired_nullEyeProbabilities_safeConfiguredFallbackNoCrash() {
        val result = FaceQualityEngine.evaluate(signals(leftEyeOpenProbability = null, rightEyeOpenProbability = null), config)
        assertTrue(result is QualityResult.Accepted)
    }

    @Test
    fun malformedNonFiniteSignal_safeRejectNoCrash() {
        val result = FaceQualityEngine.evaluate(signals(blurVariance = Float.NaN), config)
        assertTrue(result is QualityResult.Rejected)
    }

    @Test
    fun validFrontalFace_acceptedWithUnitRangeScoreAndFrontalBucket() {
        val result = FaceQualityEngine.evaluate(signals(), config)
        assertTrue(result is QualityResult.Accepted)
        result as QualityResult.Accepted
        assertTrue(result.score in 0f..1f)
        assertEquals(PoseBucket.FRONTAL, result.poseBucket)
    }

    @Test
    fun poseBuckets_distinguishLeftRightUpDownFrontal() {
        val yaw = config.frontalThresholdDegrees + 2f
        val pitch = config.frontalThresholdDegrees + 2f
        assertEquals(PoseBucket.LEFT, (FaceQualityEngine.evaluate(signals(yawDegrees = -yaw), config) as QualityResult.Accepted).poseBucket)
        assertEquals(PoseBucket.RIGHT, (FaceQualityEngine.evaluate(signals(yawDegrees = yaw), config) as QualityResult.Accepted).poseBucket)
        assertEquals(PoseBucket.UP, (FaceQualityEngine.evaluate(signals(pitchDegrees = pitch), config) as QualityResult.Accepted).poseBucket)
        assertEquals(PoseBucket.DOWN, (FaceQualityEngine.evaluate(signals(pitchDegrees = -pitch), config) as QualityResult.Accepted).poseBucket)
        assertEquals(PoseBucket.FRONTAL, (FaceQualityEngine.evaluate(signals(), config) as QualityResult.Accepted).poseBucket)
    }

    @Test
    fun thresholdConfigVersionChange_acceptedCarriesCurrentVersion() {
        val v1 = FaceQualityEngine.evaluate(signals(), config)
        val v2 = FaceQualityEngine.evaluate(signals(), config.copy(version = 2, minBlurVariance = 100f))
        assertEquals(1, (v1 as QualityResult.Accepted).configVersion)
        assertEquals(2, (v2 as QualityResult.Accepted).configVersion)
    }
}
