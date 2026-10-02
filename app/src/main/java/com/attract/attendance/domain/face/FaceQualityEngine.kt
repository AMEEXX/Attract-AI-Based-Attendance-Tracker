package com.attract.attendance.domain.face

import kotlin.math.abs
import kotlin.math.max

/**
 * Pure, platform-independent quality evaluator.
 *
 * Runs deterministic checks on [FaceQualitySignals] against a versioned
 * [FaceQualityConfig]. Rejects bad frames with a single, human-actionable reason.
 */
object FaceQualityEngine {

    const val MIN_CAPTURE_YAW_SEPARATION_DEGREES = 15f

    fun evaluate(
        signals: FaceQualitySignals,
        config: FaceQualityConfig,
        expectedPose: ExpectedPose = ExpectedPose.STRAIGHT,
    ): QualityResult {
        if (!signals.isFinite) return QualityResult.Rejected(QualityReason.NO_FACE)
        if (signals.faceCount == 0) return QualityResult.Rejected(QualityReason.NO_FACE)
        if (signals.faceCount > 1) return QualityResult.Rejected(QualityReason.MULTIPLE_FACES)
        if (signals.faceRatio < config.minFaceRatio) return QualityResult.Rejected(QualityReason.TOO_SMALL)
        if (!isInFrame(signals, config)) return QualityResult.Rejected(QualityReason.OFF_CENTER)
        if (abs(signals.pitchDegrees) > config.maxPoseDegrees) {
            return QualityResult.Rejected(QualityReason.POSE)
        }
        if (!yawInWindow(signals.yawDegrees, config, expectedPose)) {
            val reason = when (expectedPose) {
                ExpectedPose.STRAIGHT -> QualityReason.POSE_NOT_STRAIGHT
                ExpectedPose.LEFT -> QualityReason.POSE_NOT_LEFT
                ExpectedPose.RIGHT -> QualityReason.POSE_NOT_RIGHT
            }
            return QualityResult.Rejected(reason)
        }
        if (signals.blurVariance < config.minBlurVariance) return QualityResult.Rejected(QualityReason.BLUR)
        if (signals.brightness < config.minBrightness) return QualityResult.Rejected(QualityReason.DARK)
        if (signals.brightness > config.maxBrightness) return QualityResult.Rejected(QualityReason.OVEREXPOSED)
        if (config.requireEyes && !eyesClear(signals, config.minEyeOpenProbability)) {
            return QualityResult.Rejected(QualityReason.EYES_UNCLEAR)
        }

        return QualityResult.Accepted(
            score = score(signals, config, expectedPose),
            poseBucket = poseBucket(signals, config.frontalThresholdDegrees),
            configVersion = config.version,
        )
    }

    /**
     * Duplicate-frame guard (LLD-09): an accepted frame must differ in yaw from every
     * previously accepted capture by at least [MIN_CAPTURE_YAW_SEPARATION_DEGREES].
     */
    fun isDistinctFromCaptured(yawDegrees: Float, capturedYaws: List<Float>): Boolean =
        capturedYaws.all { abs(yawDegrees - it) >= MIN_CAPTURE_YAW_SEPARATION_DEGREES }

    private fun yawInWindow(yaw: Float, config: FaceQualityConfig, expected: ExpectedPose): Boolean = when (expected) {
        ExpectedPose.STRAIGHT -> abs(yaw) <= config.straightMaxYawDegrees
        ExpectedPose.LEFT -> yaw in -config.profileMaxYawDegrees..-config.profileMinYawDegrees
        ExpectedPose.RIGHT -> yaw in config.profileMinYawDegrees..config.profileMaxYawDegrees
    }

    private fun isInFrame(signals: FaceQualitySignals, config: FaceQualityConfig): Boolean {
        val limit = config.maxOffCenterFraction
        return signals.centerX in limit..(1f - limit) && signals.centerY in limit..(1f - limit)
    }

    private fun eyesClear(signals: FaceQualitySignals, minProbability: Float): Boolean {
        val left = signals.leftEyeOpenProbability ?: return false
        val right = signals.rightEyeOpenProbability ?: return false
        return left >= minProbability && right >= minProbability
    }

    fun score(
        signals: FaceQualitySignals,
        config: FaceQualityConfig,
        expected: ExpectedPose = ExpectedPose.STRAIGHT,
    ): Float {
        val targetYaw = when (expected) {
            ExpectedPose.STRAIGHT -> 0f
            ExpectedPose.LEFT -> -(config.profileMinYawDegrees + config.profileMaxYawDegrees) / 2f
            ExpectedPose.RIGHT -> (config.profileMinYawDegrees + config.profileMaxYawDegrees) / 2f
        }
        val yawDeviation = abs(signals.yawDegrees - targetYaw)
        val poseScore = (1f - max(yawDeviation, abs(signals.pitchDegrees)) / config.maxPoseDegrees)
            .coerceIn(0f, 1f)
        val sharpnessScore = (signals.blurVariance / config.targetBlurVariance).coerceIn(0f, 1f)
        val lightingScore = (1f - abs(signals.brightness - config.idealBrightness) / config.maxBrightnessDeviation)
            .coerceIn(0f, 1f)
        val framingScore = (1f - max(abs(signals.centerX - 0.5f), abs(signals.centerY - 0.5f)) * 2f)
            .coerceIn(0f, 1f)
        val eyeScore = averageEyeOpenProbability(signals) ?: 1f

        val weights = config.scoreWeights
        return poseScore * weights.pose +
            sharpnessScore * weights.sharpness +
            lightingScore * weights.lighting +
            framingScore * weights.framing +
            eyeScore * weights.eyes
    }

    private fun averageEyeOpenProbability(signals: FaceQualitySignals): Float? {
        val left = signals.leftEyeOpenProbability ?: return null
        val right = signals.rightEyeOpenProbability ?: return null
        return (left + right) / 2f
    }

    private fun poseBucket(signals: FaceQualitySignals, frontalThresholdDegrees: Float): PoseBucket {
        val yaw = signals.yawDegrees
        val pitch = signals.pitchDegrees
        return when {
            yaw < -frontalThresholdDegrees -> PoseBucket.LEFT
            yaw > frontalThresholdDegrees -> PoseBucket.RIGHT
            pitch < -frontalThresholdDegrees -> PoseBucket.DOWN
            pitch > frontalThresholdDegrees -> PoseBucket.UP
            else -> PoseBucket.FRONTAL
        }
    }
}
