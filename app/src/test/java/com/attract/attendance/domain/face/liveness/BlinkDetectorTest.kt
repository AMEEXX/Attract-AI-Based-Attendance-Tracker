package com.attract.attendance.domain.face.liveness

import com.attract.attendance.domain.face.FaceMeshResult
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for passive natural eye blink detection (doc-20 WP7.2).
 */
class BlinkDetectorTest {

    private fun frameWithBlink(blinkLeft: Float, blinkRight: Float = blinkLeft): FaceMeshResult {
        return FaceMeshResult(
            landmarks = emptyList(),
            yawDeg = 0f,
            pitchDeg = 0f,
            rollDeg = 0f,
            eyeBlinkLeft = blinkLeft,
            eyeBlinkRight = blinkRight,
            jawOpen = 0f,
            mouthSmile = 0f,
            faceCount = 1
        )
    }

    @Test
    fun insufficientFrames_returnsFalse() {
        val history = listOf(
            frameWithBlink(0.1f),
            frameWithBlink(0.8f)
        )
        assertFalse(BlinkDetector.hasBlink(history))
    }

    @Test
    fun fullBlinkCycle_openToClosedToOpen_returnsTrue() {
        // Open (0.10) -> Closed (0.75, 0.80) -> Open (0.15)
        val history = listOf(
            frameWithBlink(0.10f),
            frameWithBlink(0.75f),
            frameWithBlink(0.80f),
            frameWithBlink(0.15f)
        )
        assertTrue(BlinkDetector.hasBlink(history))
    }

    @Test
    fun alwaysOpen_returnsFalse() {
        val history = listOf(
            frameWithBlink(0.10f),
            frameWithBlink(0.15f),
            frameWithBlink(0.12f),
            frameWithBlink(0.20f)
        )
        assertFalse(BlinkDetector.hasBlink(history))
    }

    @Test
    fun alwaysClosed_noPrecedingOpen_returnsFalse() {
        val history = listOf(
            frameWithBlink(0.80f),
            frameWithBlink(0.85f),
            frameWithBlink(0.78f)
        )
        assertFalse(BlinkDetector.hasBlink(history))
    }

    @Test
    fun closedForTooManyFrames_sleepingOrPhoto_returnsFalse() {
        // Closed for > 8 frames
        val list = mutableListOf<FaceMeshResult>()
        list.add(frameWithBlink(0.10f)) // Open
        repeat(10) {
            list.add(frameWithBlink(0.80f)) // Closed for 10 frames
        }
        list.add(frameWithBlink(0.10f)) // Open

        assertFalse(BlinkDetector.hasBlink(list))
    }
}
