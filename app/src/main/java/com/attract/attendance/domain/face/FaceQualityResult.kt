package com.attract.attendance.domain.face

enum class PoseBucket {
    FRONTAL,
    LEFT,
    RIGHT,
    UP,
    DOWN,
}

/** Capture-step expectation for step-aware pose gating (LLD-09). */
enum class ExpectedPose { STRAIGHT, LEFT, RIGHT }

sealed interface QualityResult {
    data class Accepted(
        val score: Float,
        val poseBucket: PoseBucket,
        val configVersion: Int,
    ) : QualityResult

    data class Rejected(
        val reason: QualityReason,
    ) : QualityResult
}

enum class QualityReason {
    NO_FACE,
    MULTIPLE_FACES,
    TOO_SMALL,
    OFF_CENTER,
    POSE,
    POSE_NOT_STRAIGHT,
    POSE_NOT_LEFT,
    POSE_NOT_RIGHT,
    BLUR,
    DARK,
    OVEREXPOSED,
    EYES_UNCLEAR,
}
