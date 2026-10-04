package com.attract.attendance.domain.face

/**
 * Pinned Biometric Model Profile (WP02 / R11).
 *
 * Encapsulates the complete contract of the biometric pipeline:
 *  - Detector & Recognizer asset names and SHA-256 hashes
 *  - Tensor dimensions, layout, dtype, and coordinate decoder
 *  - Color space, normalization parameters, and alignment specification
 *  - Quality, liveness, and matching thresholds
 */
data class BiometricModelProfile(
    val profileId: String = "arcface512_bgr_noseyaw_v5",
    val detectorAsset: String = "yolov8n_face.tflite",
    val detectorAssetSha256: String = "85a19457127249bb7f2a0875ff344b9dc6021a2a371e14c77d4c0e5f22f7ed54",
    val detectorInputWidth: Int = 640,
    val detectorInputHeight: Int = 640,
    val detectorInputChannels: Int = 3,
    val detectorInputLayout: String = "NCHW", // [1, 3, 640, 640]
    val detectorOutputShape: List<Int> = listOf(1, 20, 8400),
    val recognizerAsset: String = "arcface_mobilefacenet.tflite",
    val recognizerAssetSha256: String = "dfac9cfe6517a9c4c3969b6ff0c2a0ac112cdf67a287d8218b60636810f0b576",
    val recognizerInputWidth: Int = 112,
    val recognizerInputHeight: Int = 112,
    val recognizerInputChannels: Int = 3,
    val recognizerInputLayout: String = "NHWC", // [1, 112, 112, 3]
    val recognizerColorOrder: String = "BGR",
    val normalizationMean: Float = 127.5f,
    val normalizationScale: Float = 128.0f,
    val embeddingDim: Int = 512,
    val alignmentVersion: String = "similarity_5point_v1",
    val qualityConfigId: String = "quality_v3",
    val livenessPolicyId: String = "liveness_v3",
    val acceptThreshold: Float = 0.50f,
    val ambiguousMargin: Float = 0.08f,
    val confirmBelow: Float = 0.60f,
    val duplicateThreshold: Float = 0.50f,
    val continuityThreshold: Float = 0.30f,
    val notFoundCeiling: Float = 0.35f,
    val calibrationId: String = "calib_20261004_bgr_512d"
) {
    companion object {
        val CURRENT = BiometricModelProfile()
    }
}
