package com.attract.attendance.domain.face.liveness

import com.attract.attendance.domain.face.FaceMeshResult

/**
 * Passive natural eye blink detector over track blendshapes (doc-20 WP7.2).
 */
object BlinkDetector {

    /**
     * Detects if the sequence contains at least one natural blink:
     * Open (<= openThreshold) -> Closed (>= closedThreshold for 1..8 frames) -> Open (<= openThreshold).
     */
    fun hasBlink(
        history: List<FaceMeshResult>,
        closedScore: Float = 0.55f,
        openScore: Float = 0.30f,
    ): Boolean {
        if (history.size < 3) return false

        var wasOpenBefore = false
        var closedCount = 0

        for (frame in history) {
            val maxBlink = maxOf(frame.eyeBlinkLeft, frame.eyeBlinkRight)
            if (maxBlink <= openScore) {
                if (closedCount in 1..8 && wasOpenBefore) {
                    return true // Complete blink cycle observed!
                }
                wasOpenBefore = true
                closedCount = 0
            } else if (maxBlink >= closedScore) {
                if (wasOpenBefore) {
                    closedCount++
                }
            } else {
                // In intermediate transition
                if (closedCount > 8) {
                    wasOpenBefore = false
                    closedCount = 0
                }
            }
        }

        return false
    }
}
