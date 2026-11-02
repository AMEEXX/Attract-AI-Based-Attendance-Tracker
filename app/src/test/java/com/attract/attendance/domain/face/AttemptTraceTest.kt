package com.attract.attendance.domain.face

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AttemptTraceTest {

    @Test
    fun attemptTrace_toString_containsTelemetryWithoutEmbeddingsOrFloatArrays() {
        val trace = AttemptTrace(
            faceLuma = 105f,
            enhancementApplied = true,
            yawDeg = 5f,
            pitchDeg = -10f,
            rollDeg = 2f,
            padScore = 0.92f,
            depthScore = 0.75f,
            blinkSeen = true,
            flashScore = 0.88f,
            risk = "LOW",
            top1 = "Student-A",
            top2 = "Student-B",
            framesUsed = 3,
            elapsedMs = 450L,
            outcome = "MATCH"
        )

        val str = trace.toString()
        assertTrue(str.contains("luma=105.0"))
        assertTrue(str.contains("risk=LOW"))
        assertTrue(str.contains("outcome=MATCH"))
        // Strict privacy check: No float arrays, no raw embeddings
        assertFalse(str.contains("[F@"))
        assertFalse(str.contains("embedding"))
    }
}
