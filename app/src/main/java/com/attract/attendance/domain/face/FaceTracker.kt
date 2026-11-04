package com.attract.attendance.domain.face

import kotlin.math.sqrt

/**
 * Pure Kotlin IoU-based face tracking engine for cross-frame continuity (doc-20 WP4.3, doc-23 WP-2).
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
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

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

    fun centerDistance(other: FaceBox): Float {
        val dx = centerX - other.centerX
        val dy = centerY - other.centerY
        return sqrt(dx * dx + dy * dy)
    }
}

class FaceTracker(
    private val maxGapNanos: Long = DEFAULT_MAX_GAP_NANOS,
    private val minIouThreshold: Float = DEFAULT_MIN_IOU_THRESHOLD,
) {
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
            if (elapsedNanos <= maxGapNanos) {
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

                val isMatch = bestBox != null && (
                    bestIou >= minIouThreshold ||
                    currentTrack.lastBox.centerDistance(bestBox) < 0.5f * currentTrack.lastBox.width
                )

                if (isMatch) {
                    val updated = currentTrack.copy(
                        lastBox = bestBox!!,
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

    companion object {
        const val DEFAULT_MAX_GAP_NANOS = 1_000_000_000L // 1000 ms adaptive threshold (Doc 23 WP-2.1)
        const val DEFAULT_MIN_IOU_THRESHOLD = 0.20f
    }
}
