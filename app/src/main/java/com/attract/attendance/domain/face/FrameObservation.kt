package com.attract.attendance.domain.face

/**
 * One captured observation inside an adaptive verification transaction (LLD-16).
 *
 * An observation bundles everything the adaptive engine needs from a single camera
 * capture: the raw quality signals, the production quality/liveness verdicts, and the
 * biometric embedding when the frame survived both gates.
 *
 * Frames that fail quality/liveness are carried as [embedding] == null so the engine
 * can reason about them (retry prompts, contamination tracking) without ever letting
 * them contribute biometric evidence.
 */
data class FrameObservation(
    val signals: FaceQualitySignals,
    val quality: QualityResult,
    val liveness: LivenessResult,
    val embedding: FloatArray?,
    /** Set when this capture is a near-exact repetition of the previous frame (static replay). */
    val isStaticReplay: Boolean = false,
) {
    /** Embedding dimensionality produced by the active model profile. */
    val expectedEmbeddingSize: Int get() = EmbeddingEngine.EMBEDDING_SIZE

    val hasValidEmbedding: Boolean
        get() = embedding != null && embedding.size == expectedEmbeddingSize && embedding.all { it.isFinite() }

    val passedQuality: Boolean get() = quality is QualityResult.Accepted

    val passedLiveness: Boolean get() = liveness is LivenessResult.Passed

    /** A frame contributes biometric evidence only when every gate passed independently. */
    val isBiometricallyUsable: Boolean get() = passedQuality && passedLiveness && hasValidEmbedding

    /**
     * Static-replay detector (test matrix #8): two captures whose signals are identical to
     * four decimals AND whose yaw did not move are treated as ONE observation, never as
     * independent evidence. Saved-image replays always land here.
     */
    fun looksLikeReplayOf(previous: FrameObservation): Boolean {
        val a = signals
        val b = previous.signals
        val eyesIdentical =
            ((a.leftEyeOpenProbability ?: 1f) - (b.leftEyeOpenProbability ?: 1f)) < REPLAY_EPSILON &&
                ((a.rightEyeOpenProbability ?: 1f) - (b.rightEyeOpenProbability ?: 1f)) < REPLAY_EPSILON &&
                ((b.leftEyeOpenProbability ?: 1f) - (a.leftEyeOpenProbability ?: 1f)) < REPLAY_EPSILON &&
                ((b.rightEyeOpenProbability ?: 1f) - (a.rightEyeOpenProbability ?: 1f)) < REPLAY_EPSILON
        val yawStatic = kotlin.math.abs(a.yawDegrees - b.yawDegrees) < YAW_EPSILON
        return eyesIdentical && yawStatic
    }

    companion object {
        const val REPLAY_EPSILON = 0.0001f
        const val YAW_EPSILON = 0.001f
    }
}
