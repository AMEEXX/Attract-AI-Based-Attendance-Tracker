package com.attract.attendance.domain.face

/**
 * Pure Kotlin IoU-based face tracking engine for cross-frame continuity (doc-20 WP4.3).
 * Produces a stable trackId while a single student remains in front of the lens.
 */
data class FaceBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = maxOf(0f, right - left)
    val height: Float get() = maxOf(0f, bottom - top)
    val area: Float get() = width * height

    fun iou(other: FaceBox): Float {
        val interLeft = maxOf(left, other.left)
        val interTop = maxOf(top, other.top)
        val interRight = minOf(right, other.right)
        val interBottom = minOf(bottom, other.bottom)
        val interW = maxOf(0f, interRight - interLeft)
        val interH = maxOf(0f, interBottom - interTop)
        val interArea = interW * interH
        val unionArea = area + other.area - interArea
        return if (unionArea > 0f) interArea / unionArea else 0f
    }
}

class FaceTracker {
    data class Track(
        val id: Int,
        val lastBox: FaceBox,
        val lastSeenNanos: Long,
        val hits: Int,
    )

    private var activeTrack: Track? = null
    private var nextTrackId = 1

    fun update(boxes: List<FaceBox>, nowNanos: Long): Track? {
        if (boxes.isEmpty()) return null

        val currentTrack = activeTrack
        if (currentTrack != null) {
            val elapsedNanos = nowNanos - currentTrack.lastSeenNanos
            if (elapsedNanos <= 400_000_000L) { // 400 ms gap threshold
                // Find detection with highest IoU
                var bestIou = 0f
                var bestBox: FaceBox? = null
                for (b in boxes) {
                    val iou = currentTrack.lastBox.iou(b)
                    if (iou > bestIou) {
                        bestIou = iou
                        bestBox = b
                    }
                }

                if (bestBox != null && bestIou >= 0.30f) {
                    val updated = currentTrack.copy(
                        lastBox = bestBox,
                        lastSeenNanos = nowNanos,
                        hits = currentTrack.hits + 1
                    )
                    activeTrack = updated
                    return updated
                }
            }
        }

        // Create new track
        val primaryBox = boxes[0]
        val newTrack = Track(
            id = nextTrackId++,
            lastBox = primaryBox,
            lastSeenNanos = nowNanos,
            hits = 1
        )
        activeTrack = newTrack
        return newTrack
    }

    fun reset() {
        activeTrack = null
    }
}
