package com.attract.attendance.domain.face

/**
 * Single source of truth for all presentation-attack-detection and liveness thresholds (doc-20).
 */
data class LivenessPolicy(
    val id: String = "liveness_v4",
    // Legacy pixel gates — now only veto STRONG signatures, measured on the WIDE crop (WP1.3)
    val maxHighFrequencyEnergy: Float = 0.28f,
    val maxSpecularHighlightRatio: Float = 0.25f,
    val printFlatTextureStdDev: Float = 4.0f,
    val printMaxBlurVariance: Float = 200f,
    // WP6 PAD CNN
    val padLiveAccept: Float = 0.80f,     // >= : LOW risk contribution
    val padSpoofReject: Float = 0.25f,    // <  : HIGH risk (hard reject)
    // WP7 3D / flash / blink
    val parallaxMinYawDeltaDeg: Float = 4f,
    val parallaxLiveResidual: Float = 0.020f,   // normalised by inter-ocular distance
    val flashMinCorrelation: Float = 0.55f,
    val flashMinShadingRatio: Float = 1.06f,
    val blinkClosedScore: Float = 0.55f,        // MediaPipe eyeBlink blendshape
    val blinkOpenScore: Float = 0.30f,
    val maxChallengeMs: Long = 4000L,
)
