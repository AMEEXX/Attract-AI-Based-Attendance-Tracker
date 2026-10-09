package com.attract.attendance.domain.face

import kotlin.math.abs
import kotlin.random.Random

/**
 * Challenge direction for check-in relative turn challenge (WP-G / PR-03).
 */
enum class TurnDirection {
    LEFT,
    RIGHT;

    val displayPrompt: String
        get() = when (this) {
            LEFT -> "Please turn your face slightly LEFT"
            RIGHT -> "Please turn your face slightly RIGHT"
        }
}

/**
 * State machine representing an active check-in relative turn challenge (doc 18 WP-G).
 * Requires ≥ 10° relative yaw turn within 3 seconds while maintaining identity continuity ≥ 0.30.
 */
data class TurnChallengeState(
    val direction: TurnDirection,
    val initialYawDegrees: Float,
    val matchedEmbedding: FloatArray,
    val startedAtMillis: Long,
    val timeoutMillis: Long = 3000L,
    val minRelativeYawDegrees: Float = 10f,
    val minContinuitySimilarity: Float = 0.30f,
) {
    fun isExpired(nowMillis: Long): Boolean = (nowMillis - startedAtMillis) > timeoutMillis

    fun evaluate(
        currentYawDegrees: Float,
        currentEmbedding: FloatArray,
        nowMillis: Long
    ): TurnChallengeResult {
        if (isExpired(nowMillis)) {
            return TurnChallengeResult.Expired
        }

        // Verify face identity continuity against the matched frame to block swapping photos
        val similarity = cosineSimilarity(matchedEmbedding, currentEmbedding)
        if (similarity < minContinuitySimilarity) {
            return TurnChallengeResult.FailedContinuity(similarity)
        }

        val deltaYaw = currentYawDegrees - initialYawDegrees
        val turnMet = when (direction) {
            TurnDirection.LEFT -> deltaYaw <= -minRelativeYawDegrees
            TurnDirection.RIGHT -> deltaYaw >= minRelativeYawDegrees
        }

        return if (turnMet) {
            TurnChallengeResult.Completed(deltaYaw)
        } else {
            TurnChallengeResult.InProgress(deltaYaw)
        }
    }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        var dot = 0f
        var normA = 0f
        var normB = 0f
        val len = minOf(a.size, b.size)
        for (i in 0 until len) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denom = kotlin.math.sqrt(normA) * kotlin.math.sqrt(normB)
        return if (denom > 0f) dot / denom else 0f
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as TurnChallengeState
        if (direction != other.direction) return false
        if (initialYawDegrees != other.initialYawDegrees) return false
        if (!matchedEmbedding.contentEquals(other.matchedEmbedding)) return false
        if (startedAtMillis != other.startedAtMillis) return false
        return true
    }

    override fun hashCode(): Int {
        var result = direction.hashCode()
        result = 31 * result + initialYawDegrees.hashCode()
        result = 31 * result + matchedEmbedding.contentHashCode()
        result = 31 * result + startedAtMillis.hashCode()
        return result
    }
}

sealed interface TurnChallengeResult {
    data class Completed(val achievedDeltaYaw: Float) : TurnChallengeResult
    data class InProgress(val currentDeltaYaw: Float) : TurnChallengeResult
    data class FailedContinuity(val similarity: Float) : TurnChallengeResult
    data object Expired : TurnChallengeResult
}

object TurnChallengeFactory {
    fun createChallenge(
        initialYawDegrees: Float,
        matchedEmbedding: FloatArray,
        nowMillis: Long = System.currentTimeMillis(),
        random: Random = Random.Default
    ): TurnChallengeState {
        val direction = if (random.nextBoolean()) TurnDirection.LEFT else TurnDirection.RIGHT
        return TurnChallengeState(
            direction = direction,
            initialYawDegrees = initialYawDegrees,
            matchedEmbedding = matchedEmbedding,
            startedAtMillis = nowMillis
        )
    }
}
