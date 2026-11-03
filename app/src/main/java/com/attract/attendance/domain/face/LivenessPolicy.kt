package com.attract.attendance.domain.face

/**
 * Single source of truth for all presentation-attack-detection and liveness thresholds (doc-20).
 */
data class LivenessPolicy(
    val id: String = "liveness_v5_doc22",
    // Legacy pixel gates — only veto STRONG signatures. Doc 22 WP-A: measured on the INNER 1.0x face crop
    // (PadCrop scale 1.0 -> 112x112), NEVER on the 2.7x wide crop (background white walls/windows => false SCREEN_GLARE).
    val maxHighFrequencyEnergy: Float = 0.22f,
    val maxSpecularHighlightRatio: Float = 0.25f,
    val printFlatTextureStdDev: Float = 3.0f,
    val printMaxBlurVariance: Float = 200f,
    // WP6 PAD CNN
    // Doc 22 WP-B: MiniFASNetV2 (silentface.tflite) on the 2.7x crop, raw 0..255 BGR, median of last frames.
    // exp10: genuine backlit 2–9 % below 0.10, laptop replay 84 % below 0.10 => PAD below this ESCALATES to an
    // active challenge; it is NOT a hard reject on its own (would re-introduce backlight false rejects).
    val padLiveAccept: Float = 0.80f,     // >= : strong live evidence (informational / AttemptTrace)
    val padSpoofReject: Float = 0.10f,    // <  : spoof-suspect => challenge required
    // WP7 3D / flash / blink
    // Doc 22 WP-E (exp8): yaw >= 8° and residual >= 0.035 => real 97 % pass, planar 0 % pass.
    val parallaxMinYawDeltaDeg: Float = 8f,
    val parallaxLiveResidual: Float = 0.035f,   // normalised by inter-ocular distance
    val meshWindowMs: Long = 3000L,             // mesh/PAD history older than this is discarded
    val pixelVetoWindow: Int = 3,               // Doc 21 WP-D 2-of-3 temporal confirmation
    val pixelVetoMinHits: Int = 2,
    val flashMinCorrelation: Float = 0.55f,
    val flashMinShadingRatio: Float = 1.06f,
    val blinkClosedScore: Float = 0.55f,        // MediaPipe eyeBlink blendshape
    val blinkOpenScore: Float = 0.30f,
    val maxChallengeMs: Long = 4000L,
)
