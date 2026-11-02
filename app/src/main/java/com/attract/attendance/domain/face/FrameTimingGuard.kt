package com.attract.attendance.domain.face

/**
 * Pure anti-injection frame-timing guard (Doc 20 WP11.3).
 *
 * Verifies that CameraX image timestamps come from a real physical camera sensor:
 * - Strictly increasing timestamps (no replay of identical timestamps)
 * - Inter-frame delta > minDeltaNs (e.g. >= 15ms, catching 0ms or unrealistic > 66 fps synthetic pumps)
 * - Inter-frame delta < maxDeltaNs (e.g. <= 2000ms before resetting cadence)
 * - Jitter detection: synthetic feeds often provide synthetic timestamps with perfectly zero jitter (std dev < 0.05ms)
 *
 * Repeated violations set [injectionSuspected] = true, triggering RiskFusion Rule 1 -> HIGH risk.
 */
class FrameTimingGuard(
    private val minDeltaNs: Long = 15_000_000L,        // ~66.6 fps max
    private val maxDeltaNs: Long = 2_000_000_000L,     // 2 sec max gap
    private val violationThreshold: Int = 3,
    private val historySize: Int = 10
) {
    private var lastTimestampNs: Long = -1L
    private var consecutiveViolations: Int = 0
    private val deltaHistory = ArrayDeque<Long>(historySize)

    var injectionSuspected: Boolean = false
        private set

    /**
     * Records an incoming camera frame timestamp in nanoseconds.
     * Returns true if the frame timing is plausible, false if suspicious.
     */
    fun onFrame(timestampNs: Long): Boolean {
        if (lastTimestampNs < 0) {
            lastTimestampNs = timestampNs
            return true
        }

        val delta = timestampNs - lastTimestampNs
        var isViolation = false

        if (delta <= 0) {
            // Strictly non-increasing timestamp
            isViolation = true
        } else if (delta < minDeltaNs) {
            // Unphysically fast frame rate
            isViolation = true
        }

        lastTimestampNs = timestampNs

        if (isViolation) {
            consecutiveViolations++
            if (consecutiveViolations >= violationThreshold) {
                injectionSuspected = true
            }
            return false
        } else {
            consecutiveViolations = 0
        }

        if (delta < maxDeltaNs) {
            if (deltaHistory.size >= historySize) {
                deltaHistory.removeFirst()
            }
            deltaHistory.addLast(delta)

            // Check for suspiciously zero jitter across a series of frames
            if (deltaHistory.size >= historySize) {
                val mean = deltaHistory.average()
                var variance = 0.0
                for (d in deltaHistory) {
                    val diff = d - mean
                    variance += diff * diff
                }
                variance /= deltaHistory.size
                val stdDevMs = kotlin.math.sqrt(variance) / 1_000_000.0

                // Physical phone camera sensors always have some timestamp jitter (>= 0.2ms std dev).
                // Synthetic software feeds often inject exact 33.3333ms deltas with < 0.01ms jitter.
                if (stdDevMs < 0.05) {
                    injectionSuspected = true
                    return false
                }
            }
        } else {
            // Large pause (e.g. app paused or backgrounded) -> clear history
            deltaHistory.clear()
        }

        return true
    }

    fun reset() {
        lastTimestampNs = -1L
        consecutiveViolations = 0
        deltaHistory.clear()
        injectionSuspected = false
    }
}
