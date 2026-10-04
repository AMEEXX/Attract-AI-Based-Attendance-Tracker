package com.attract.attendance.domain.face

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PoseEstimatorTest {

    private val config = FaceQualityConfig.calibrationDefaults()

    @Test
    fun testFrontalYawNearZero() {
        // Eyes symmetrical, nose centered between eyes
        val leftEyeX = 100f
        val leftEyeY = 100f
        val rightEyeX = 200f
        val rightEyeY = 100f
        val noseX = 150f
        val noseY = 155f

        val yaw = YoloFaceDetector.calculateYaw(leftEyeX, leftEyeY, rightEyeX, rightEyeY, noseX, noseY)
        assertEquals(0.0f, yaw, 0.01f)

        val pitch = YoloFaceDetector.calculatePitch(leftEyeX, leftEyeY, rightEyeX, rightEyeY, noseX, noseY)
        assertEquals(0.0f, pitch, 0.01f)
    }

    @Test
    fun testLeftTurnProducesCalibratedNegativeYaw() {
        // In selfie camera frame, turning left moves nose to the right in the image (noseX > eyeMidX)
        val leftEyeX = 100f
        val leftEyeY = 100f
        val rightEyeX = 200f
        val rightEyeY = 100f
        val noseX = 175f // shifted right by 25px out of 100px inter-eye distance (0.25 fraction)
        val noseY = 155f

        val yaw = YoloFaceDetector.calculateYaw(leftEyeX, leftEyeY, rightEyeX, rightEyeY, noseX, noseY)
        // K_YAW = -60.8f * (175 - 150) / 100 = -60.8 * 0.25 = -15.2f
        assertEquals(-15.2f, yaw, 0.1f)

        // Negative yaw between -10° and -40° is accepted for LEFT pose
        val signals = FaceQualitySignals(
            faceCount = 1,
            yawDegrees = yaw,
            pitchDegrees = 0f,
            rollDegrees = 0f,
            leftEyeOpenProbability = 0.9f,
            rightEyeOpenProbability = 0.9f,
            faceRatio = 0.25f,
            centerX = 0.5f,
            centerY = 0.5f,
            blurVariance = 100f,
            brightness = 120f,
        )

        val eval = FaceQualityEngine.evaluate(signals, config, ExpectedPose.LEFT)
        assertTrue("Expected LEFT pose to be accepted for yaw=-15.2°", eval is QualityResult.Accepted)
    }

    @Test
    fun testRightTurnProducesCalibratedPositiveYaw() {
        // In selfie camera frame, turning right moves nose to the left in the image (noseX < eyeMidX)
        val leftEyeX = 100f
        val leftEyeY = 100f
        val rightEyeX = 200f
        val rightEyeY = 100f
        val noseX = 120f // shifted left by 30px out of 100px inter-eye distance (-0.30 fraction)
        val noseY = 155f

        val yaw = YoloFaceDetector.calculateYaw(leftEyeX, leftEyeY, rightEyeX, rightEyeY, noseX, noseY)
        // K_YAW = -60.8f * (120 - 150) / 100 = -60.8 * -0.30 = +18.24f
        assertEquals(18.24f, yaw, 0.1f)

        val signals = FaceQualitySignals(
            faceCount = 1,
            yawDegrees = yaw,
            pitchDegrees = 0f,
            rollDegrees = 0f,
            leftEyeOpenProbability = 0.9f,
            rightEyeOpenProbability = 0.9f,
            faceRatio = 0.25f,
            centerX = 0.5f,
            centerY = 0.5f,
            blurVariance = 100f,
            brightness = 120f,
        )

        val eval = FaceQualityEngine.evaluate(signals, config, ExpectedPose.RIGHT)
        assertTrue("Expected RIGHT pose to be accepted for yaw=18.24°", eval is QualityResult.Accepted)
    }

    @Test
    fun testRelativeGatingWithAnchorStraightYaw() {
        // Anchor straight yaw is +5° (student started slightly tilted right)
        val anchorYaw = 5f

        // Frame yaw is -8°: delta = -8 - 5 = -13° (valid natural turn in [-40°, -10°])
        val validLeftSignals = FaceQualitySignals(
            faceCount = 1,
            yawDegrees = -8f,
            pitchDegrees = 0f,
            rollDegrees = 0f,
            leftEyeOpenProbability = 0.9f,
            rightEyeOpenProbability = 0.9f,
            faceRatio = 0.25f,
            centerX = 0.5f,
            centerY = 0.5f,
            blurVariance = 100f,
            brightness = 120f,
        )

        // Without anchor: -8° would be REJECTED because -8° > -10° (profileMinYawDegrees)
        val evalWithoutAnchor = FaceQualityEngine.evaluate(validLeftSignals, config, ExpectedPose.LEFT, anchorYaw = null)
        assertTrue(evalWithoutAnchor is QualityResult.Rejected)
        assertEquals(QualityReason.POSE_NOT_LEFT, (evalWithoutAnchor as QualityResult.Rejected).reason)

        // With anchor = +5°: delta = -13°, ACCEPTED!
        val evalWithAnchor = FaceQualityEngine.evaluate(validLeftSignals, config, ExpectedPose.LEFT, anchorYaw = anchorYaw)
        assertTrue("Expected delta of -13° to be accepted with anchor", evalWithAnchor is QualityResult.Accepted)
    }

    @Test
    fun testPitchEstimator() {
        val leftEyeX = 100f
        val leftEyeY = 100f
        val rightEyeX = 200f
        val rightEyeY = 100f

        // Looking up: nose is closer to eye line (noseY = 130 instead of 155)
        // noseV = (130 - 100) / 100 = 0.30
        // pitch = 45 * (0.30 - 0.55) = 45 * -0.25 = -11.25°
        val pitchUp = YoloFaceDetector.calculatePitch(leftEyeX, leftEyeY, rightEyeX, rightEyeY, 150f, 130f)
        assertEquals(-11.25f, pitchUp, 0.1f)

        // Looking down: nose is further down (noseY = 180)
        // noseV = (180 - 100) / 100 = 0.80
        // pitch = 45 * (0.80 - 0.55) = 45 * 0.25 = +11.25°
        val pitchDown = YoloFaceDetector.calculatePitch(leftEyeX, leftEyeY, rightEyeX, rightEyeY, 150f, 180f)
        assertEquals(11.25f, pitchDown, 0.1f)
    }
}
