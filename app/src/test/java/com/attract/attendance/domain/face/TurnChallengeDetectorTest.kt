package com.attract.attendance.domain.face

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class TurnChallengeDetectorTest {

    private val sampleEmbedding = FloatArray(192) { 0.1f }

    @Test
    fun turnLeft_completedWhenYawDecreasesByThreshold() {
        val challenge = TurnChallengeState(
            direction = TurnDirection.LEFT,
            initialYawDegrees = 0f,
            matchedEmbedding = sampleEmbedding,
            startedAtMillis = 1000L,
            timeoutMillis = 3000L,
            minRelativeYawDegrees = 10f,
        )

        // Still straight
        val inProgress = challenge.evaluate(-5f, sampleEmbedding, nowMillis = 1500L)
        assertTrue(inProgress is TurnChallengeResult.InProgress)

        // Turn completed: yaw is -12° relative to 0°
        val completed = challenge.evaluate(-12f, sampleEmbedding, nowMillis = 2000L)
        assertTrue(completed is TurnChallengeResult.Completed)
        assertEquals(-12f, (completed as TurnChallengeResult.Completed).achievedDeltaYaw, 0.01f)
    }

    @Test
    fun turnRight_completedWhenYawIncreasesByThreshold() {
        val challenge = TurnChallengeState(
            direction = TurnDirection.RIGHT,
            initialYawDegrees = -5f,
            matchedEmbedding = sampleEmbedding,
            startedAtMillis = 1000L,
            timeoutMillis = 3000L,
            minRelativeYawDegrees = 10f,
        )

        // Delta is +4 (yaw = -1°), not enough
        val inProgress = challenge.evaluate(-1f, sampleEmbedding, nowMillis = 1500L)
        assertTrue(inProgress is TurnChallengeResult.InProgress)

        // Delta is +11 (yaw = +6°), completes
        val completed = challenge.evaluate(6f, sampleEmbedding, nowMillis = 2000L)
        assertTrue(completed is TurnChallengeResult.Completed)
    }

    @Test
    fun challenge_expiresAfterTimeout() {
        val challenge = TurnChallengeState(
            direction = TurnDirection.RIGHT,
            initialYawDegrees = 0f,
            matchedEmbedding = sampleEmbedding,
            startedAtMillis = 1000L,
            timeoutMillis = 3000L,
        )

        val expired = challenge.evaluate(15f, sampleEmbedding, nowMillis = 4001L)
        assertTrue(expired is TurnChallengeResult.Expired)
    }

    @Test
    fun challenge_failsContinuityWhenFaceSwapped() {
        val challenge = TurnChallengeState(
            direction = TurnDirection.LEFT,
            initialYawDegrees = 0f,
            matchedEmbedding = sampleEmbedding,
            startedAtMillis = 1000L,
            timeoutMillis = 3000L,
            minContinuitySimilarity = 0.50f,
        )

        // Completely orthogonal/different embedding
        val differentEmbedding = FloatArray(192) { if (it % 2 == 0) -1f else 1f }
        val failed = challenge.evaluate(-15f, differentEmbedding, nowMillis = 1500L)
        assertTrue(failed is TurnChallengeResult.FailedContinuity)
    }

    @Test
    fun turnChallengeFactory_producesRandomValidChallenges() {
        val challenge = TurnChallengeFactory.createChallenge(
            initialYawDegrees = 2f,
            matchedEmbedding = sampleEmbedding,
            nowMillis = 5000L,
            random = Random(42)
        )
        assertEquals(2f, challenge.initialYawDegrees, 0.01f)
        assertEquals(5000L, challenge.startedAtMillis)
    }
}
