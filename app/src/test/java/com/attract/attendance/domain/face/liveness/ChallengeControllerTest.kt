package com.attract.attendance.domain.face.liveness

import com.attract.attendance.domain.face.FaceMeshResult
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for randomized micro-challenge state machine (doc-20 WP7.4).
 */
class ChallengeControllerTest {

    private fun frame(
        yawDeg: Float = 0f,
        blinkLeft: Float = 0f,
        blinkRight: Float = 0f,
        smile: Float = 0f,
    ): FaceMeshResult {
        return FaceMeshResult(
            landmarks = emptyList(),
            yawDeg = yawDeg,
            pitchDeg = 0f,
            rollDeg = 0f,
            eyeBlinkLeft = blinkLeft,
            eyeBlinkRight = blinkRight,
            jawOpen = 0f,
            mouthSmile = smile,
            faceCount = 1
        )
    }

    @Test
    fun timeout_transitionsToTimedOut() {
        val ctrl = ChallengeController(
            challenge = LivenessChallengeType.BLINK,
            startTimestampMs = 1000L,
            maxChallengeMs = 4000L
        )

        val status = ctrl.update(frame(), nowMs = 6000L)
        assertEquals(ChallengeStatus.TIMED_OUT, status)
    }

    @Test
    fun turnLeftChallenge_whenYawReachesThreshold_passes() {
        val ctrl = ChallengeController(
            challenge = LivenessChallengeType.TURN_SLIGHT_LEFT,
            startTimestampMs = 1000L
        )

        // Initial reference frame (yaw = 0)
        assertEquals(ChallengeStatus.IN_PROGRESS, ctrl.update(frame(yawDeg = 0f), 1100L))
        // Slight turn not enough (yaw = -5)
        assertEquals(ChallengeStatus.IN_PROGRESS, ctrl.update(frame(yawDeg = -5f), 1200L))
        // Reached 10 degrees left turn (yaw = -12) -> PASSED
        assertEquals(ChallengeStatus.PASSED, ctrl.update(frame(yawDeg = -12f), 1300L))
    }

    @Test
    fun turnRightChallenge_whenYawReachesThreshold_passes() {
        val ctrl = ChallengeController(
            challenge = LivenessChallengeType.TURN_SLIGHT_RIGHT,
            startTimestampMs = 1000L
        )

        // Initial reference frame (yaw = 0)
        assertEquals(ChallengeStatus.IN_PROGRESS, ctrl.update(frame(yawDeg = 0f), 1100L))
        // Slight turn not enough (yaw = 4)
        assertEquals(ChallengeStatus.IN_PROGRESS, ctrl.update(frame(yawDeg = 4f), 1200L))
        // Reached 10 degrees right turn (yaw = 11) -> PASSED
        assertEquals(ChallengeStatus.PASSED, ctrl.update(frame(yawDeg = 11f), 1300L))
    }

    @Test
    fun smileChallenge_transitionsFromNeutralToSmile_passes() {
        val ctrl = ChallengeController(
            challenge = LivenessChallengeType.SMILE,
            startTimestampMs = 1000L
        )

        // Neutral initial face (smile = 0.05)
        assertEquals(ChallengeStatus.IN_PROGRESS, ctrl.update(frame(smile = 0.05f), 1100L))
        // Moderate smile (smile = 0.35)
        assertEquals(ChallengeStatus.IN_PROGRESS, ctrl.update(frame(smile = 0.35f), 1200L))
        // Verified smile >= 0.50 -> PASSED
        assertEquals(ChallengeStatus.PASSED, ctrl.update(frame(smile = 0.60f), 1300L))
    }

    @Test
    fun blinkChallenge_completedBlinkCycle_passes() {
        val ctrl = ChallengeController(
            challenge = LivenessChallengeType.BLINK,
            startTimestampMs = 1000L
        )

        // Open
        assertEquals(ChallengeStatus.IN_PROGRESS, ctrl.update(frame(blinkLeft = 0.10f), 1100L))
        // Closed
        assertEquals(ChallengeStatus.IN_PROGRESS, ctrl.update(frame(blinkLeft = 0.80f), 1200L))
        // Open -> Complete cycle observed -> PASSED
        assertEquals(ChallengeStatus.PASSED, ctrl.update(frame(blinkLeft = 0.15f), 1300L))
    }

    @Test
    fun armAt_setsArmedTimestamp() {
        val ctrl = ChallengeController(
            challenge = LivenessChallengeType.BLINK,
            startTimestampMs = 1000L,
            maxChallengeMs = 6000L
        )
        ctrl.armAt(2000L)
        // At 7000ms (5000ms after arming), still within 6000ms limit
        assertEquals(ChallengeStatus.IN_PROGRESS, ctrl.update(frame(), nowMs = 7000L))
        // At 8100ms (6100ms after arming), timed out
        assertEquals(ChallengeStatus.TIMED_OUT, ctrl.update(frame(), nowMs = 8100L))
    }
}
