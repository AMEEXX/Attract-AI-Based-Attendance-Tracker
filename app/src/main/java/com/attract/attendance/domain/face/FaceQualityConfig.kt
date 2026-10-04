package com.attract.attendance.domain.face

import kotlin.math.abs

data class FaceQualityConfig(
    val version: Int,
    val minFaceRatio: Float,
    val maxPoseDegrees: Float,
    val maxOffCenterFraction: Float,
    val minBlurVariance: Float,
    val targetBlurVariance: Float,
    val minBrightness: Float,
    val maxBrightness: Float,
    val idealBrightness: Float,
    val maxBrightnessDeviation: Float,
    val minEyeOpenProbability: Float,
    val requireEyes: Boolean,
    val frontalThresholdDegrees: Float,
    val straightMaxYawDegrees: Float = 12f,
    val profileMinYawDegrees: Float = 10f,
    val profileMaxYawDegrees: Float = 40f,
    val scoreWeights: QualityScoreWeights,
) {
    init {
        require(version >= 1) { "Config version must be positive." }
        require(minFaceRatio > 0f) { "minFaceRatio must be positive." }
        require(maxPoseDegrees > 0f) { "maxPoseDegrees must be positive." }
        require(maxOffCenterFraction in 0f..0.5f) { "maxOffCenterFraction must be between 0 and 0.5." }
        require(targetBlurVariance > minBlurVariance) { "targetBlurVariance must exceed minBlurVariance." }
        require(minBrightness in 0f..255f) { "minBrightness must be in 0..255." }
        require(maxBrightness in 0f..255f) { "maxBrightness must be in 0..255." }
        require(minBrightness < idealBrightness && idealBrightness < maxBrightness) {
            "idealBrightness must lie strictly between minBrightness and maxBrightness."
        }
        require(maxBrightnessDeviation > 0f) { "maxBrightnessDeviation must be positive." }
        require(minEyeOpenProbability in 0f..1f) { "minEyeOpenProbability must be in 0..1." }
        require(frontalThresholdDegrees > 0f) { "frontalThresholdDegrees must be positive." }
        require(straightMaxYawDegrees > 0f) { "straightMaxYawDegrees must be positive." }
        require(profileMinYawDegrees > 0f) { "profileMinYawDegrees must be positive." }
        require(profileMaxYawDegrees > profileMinYawDegrees) { "profileMaxYawDegrees must exceed profileMinYawDegrees." }
        require(abs(scoreWeights.total - 1f) <= 0.01f) { "Score weights must sum to 1." }
        require(
            scoreWeights.pose >= 0f &&
                scoreWeights.sharpness >= 0f &&
                scoreWeights.lighting >= 0f &&
                scoreWeights.framing >= 0f &&
                scoreWeights.eyes >= 0f,
        ) { "Score weights must be non-negative." }
    }

    companion object {
        fun calibrationDefaults() = FaceQualityConfig(
            version = 1,
            minFaceRatio = 0.10f,
            maxPoseDegrees = 15f,
            maxOffCenterFraction = 0.25f,
            minBlurVariance = 60f,
            targetBlurVariance = 500f,
            minBrightness = 40f,
            maxBrightness = 220f,
            idealBrightness = 128f,
            maxBrightnessDeviation = 90f,
            minEyeOpenProbability = 0.6f,
            requireEyes = false,
            frontalThresholdDegrees = 10f,
            straightMaxYawDegrees = 12f,
            profileMinYawDegrees = 10f,
            profileMaxYawDegrees = 40f,
            scoreWeights = QualityScoreWeights(
                pose = 0.3f,
                sharpness = 0.3f,
                lighting = 0.2f,
                framing = 0.1f,
                eyes = 0.1f,
            ),
        )
    }
}

data class QualityScoreWeights(
    val pose: Float,
    val sharpness: Float,
    val lighting: Float,
    val framing: Float,
    val eyes: Float,
) {
    val total: Float get() = pose + sharpness + lighting + framing + eyes
}
