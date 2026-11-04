package com.attract.attendance.domain.face

/**
 * Single source of truth for all presentation-attack-detection and liveness thresholds (doc-20, doc-22, doc-23).
 */
data class LivenessPolicy(
    val id: String = "liveness_v6_doc23",
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
    // WP7 3D / flash / blink (Doc 23: measured 5° hypot yaw delta, 0.030 LSQ residual threshold)
    val parallaxMinYawDeltaDeg: Float = 5f,
    val parallaxLiveResidual: Float = 0.030f,   // normalised by inter-ocular distance
    val meshWindowMs: Long = 3000L,             // mesh/PAD history older than this is discarded
    val latchTtlMs: Long = 20_000L,             // Doc 23 WP-3: latch lives for 20s per track
    val faceDropoutGraceMs: Long = 800L,        // Doc 23 WP-2: face dropout grace period (ms)
    val pixelVetoWindow: Int = 3,               // Doc 21 WP-D 2-of-3 temporal confirmation
    val pixelVetoMinHits: Int = 2,
    val flashMinCorrelation: Float = 0.55f,
    val flashMinShadingRatio: Float = 1.06f,
    val blinkClosedScore: Float = 0.55f,        // MediaPipe eyeBlink blendshape
    val blinkOpenScore: Float = 0.30f,
    val maxChallengeMs: Long = 6000L,           // Doc 23 WP-7: 6s challenge timeout
    val challengeTurnDeg: Float = 7f,           // Doc 23 WP-7: 7° challenge turn
    val maxMediumMsBeforeAssist: Long = 12_000L, // Doc 23 WP-8: 12s escape hatch to teacher assist
)
