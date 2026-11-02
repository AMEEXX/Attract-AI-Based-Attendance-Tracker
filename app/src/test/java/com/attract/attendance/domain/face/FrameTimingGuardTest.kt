package com.attract.attendance.domain.face

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameTimingGuardTest {

    @Test
    fun normalCadence_passesTimingGuard() {
        val guard = FrameTimingGuard()
        var ts = 1_000_000_000L

        // Simulate 30 fps with realistic jitter (33.3ms ± 1ms)
        val jitters = listOf(33L, 34L, 32L, 35L, 33L, 31L, 34L, 33L, 32L, 34L, 33L)
        for (j in jitters) {
            ts += j * 1_000_000L
            val ok = guard.onFrame(ts)
            assertTrue(ok)
        }
        assertFalse(guard.injectionSuspected)
    }

    @Test
    fun nonIncreasingTimestamps_triggersInjectionSuspected() {
        val guard = FrameTimingGuard(violationThreshold = 3)
        var ts = 1_000_000_000L
        guard.onFrame(ts)

        // Repeat same timestamp 3 times
        guard.onFrame(ts)
        guard.onFrame(ts)
        guard.onFrame(ts)

        assertTrue(guard.injectionSuspected)
    }

    @Test
    fun syntheticZeroJitter_triggersInjectionSuspected() {
        val guard = FrameTimingGuard(historySize = 5)
        var ts = 1_000_000_000L
        guard.onFrame(ts)

        // Exactly 33_333_333 ns every single frame with 0 jitter
        for (i in 0 until 10) {
            ts += 33_333_333L
            guard.onFrame(ts)
        }

        assertTrue(guard.injectionSuspected)
    }
}
