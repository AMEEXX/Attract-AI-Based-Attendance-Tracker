package com.attract.attendance.domain.face

/**
 * Pure, platform-independent face-detection signals used by the quality engine.
 *
 * Pixel statistics (blur/brightness) are computed on a bounded down-sampled crop by the
 * Android adapter and passed here as plain values so the decision logic stays JVM-testable.
 */
data class FaceQualitySignals(
    val faceCount: Int,
    val yawDegrees: Float,
    val pitchDegrees: Float,
    val rollDegrees: Float,
    val leftEyeOpenProbability: Float?,
    val rightEyeOpenProbability: Float?,
    val faceRatio: Float,
    val centerX: Float,
    val centerY: Float,
    val blurVariance: Float,
    val brightness: Float,
    val landmarkMinConf: Float = 1.0f,
) {
    val isSingleFace: Boolean get() = faceCount == 1

    /** All values must be finite; a malformed crop is a safe reject, never a native crash. */
    val isFinite: Boolean
        get() = listOf(yawDegrees, pitchDegrees, rollDegrees, faceRatio, centerX, centerY, blurVariance, brightness, landmarkMinConf)
            .all { it.isFinite() }
}
