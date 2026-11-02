package com.attract.attendance.domain.face.liveness

import com.attract.attendance.domain.face.FaceMeshResult
import java.security.SecureRandom

enum class LivenessChallengeType {
    BLINK,
    TURN_SLIGHT_LEFT,
    TURN_SLIGHT_RIGHT,
    SMILE,
}

enum class ChallengeStatus {
    IN_PROGRESS,
    PASSED,
    TIMED_OUT,
}

/**
 * Pure state machine governing randomized active micro-challenges when passive risk is MEDIUM (doc-20 WP7.4).
 */
class ChallengeController(
    val challenge: LivenessChallengeType = randomChallenge(),
    val startTimestampMs: Long = System.currentTimeMillis(),
    val maxChallengeMs: Long = 4000L,
) {
    private var initialYaw: Float? = null
    private var initialSmile: Float? = null
    private val meshHistory = mutableListOf<FaceMeshResult>()

    fun update(frame: FaceMeshResult, nowMs: Long): ChallengeStatus {
        if (nowMs - startTimestampMs > maxChallengeMs) {
            return ChallengeStatus.TIMED_OUT
        }

        meshHistory.add(frame)
        if (initialYaw == null) initialYaw = frame.yawDeg
        if (initialSmile == null) initialSmile = frame.mouthSmile

        return when (challenge) {
            LivenessChallengeType.BLINK -> {
                if (BlinkDetector.hasBlink(meshHistory)) ChallengeStatus.PASSED else ChallengeStatus.IN_PROGRESS
            }
            LivenessChallengeType.TURN_SLIGHT_LEFT -> {
                val yawDiff = (initialYaw ?: 0f) - frame.yawDeg // Left turns have negative yaw
                if (yawDiff >= 10f) ChallengeStatus.PASSED else ChallengeStatus.IN_PROGRESS
            }
            LivenessChallengeType.TURN_SLIGHT_RIGHT -> {
                val yawDiff = frame.yawDeg - (initialYaw ?: 0f)
                if (yawDiff >= 10f) ChallengeStatus.PASSED else ChallengeStatus.IN_PROGRESS
            }
            LivenessChallengeType.SMILE -> {
                if (frame.mouthSmile >= 0.50f && (initialSmile ?: 0f) < 0.25f) {
                    ChallengeStatus.PASSED
                } else {
                    ChallengeStatus.IN_PROGRESS
                }
            }
        }
    }

    companion object {
        private val rng = SecureRandom()
        fun randomChallenge(): LivenessChallengeType {
            val values = LivenessChallengeType.values()
            return values[rng.nextInt(values.size)]
        }
    }
}
