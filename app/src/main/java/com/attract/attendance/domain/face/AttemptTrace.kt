package com.attract.attendance.domain.face

/**
 * Per-attempt telemetry record for debug and performance analysis.
 * Explicitly designed to never contain embeddings, raw images, or PII.
 */
data class AttemptTrace(
    val faceLuma: Float = 0f,
    val enhancementApplied: Boolean = false,
    val yawDeg: Float = 0f,
    val pitchDeg: Float = 0f,
    val rollDeg: Float = 0f,
    val padScore: Float? = null,
    val depthScore: Float? = null,
    val blinkSeen: Boolean = false,
    val flashScore: Float? = null,
    val risk: String = "UNKNOWN",
    val top1: String? = null,
    val top2: String? = null,
    val framesUsed: Int = 1,
    val elapsedMs: Long = 0L,
    val outcome: String = "PENDING",
) {
    override fun toString(): String {
        return "AttemptTrace(luma=$faceLuma, enhanced=$enhancementApplied, " +
            "pose=[yaw=$yawDeg, pitch=$pitchDeg, roll=$rollDeg], " +
            "pad=$padScore, depth=$depthScore, blink=$blinkSeen, flash=$flashScore, " +
            "risk=$risk, top1=$top1, top2=$top2, frames=$framesUsed, " +
            "elapsedMs=$elapsedMs, outcome=$outcome)"
    }
}
