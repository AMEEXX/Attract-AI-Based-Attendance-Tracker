package com.attract.attendance.domain.face

/**
 * Pixel-statistics signals consumed by [PresentationAttackDetector] (LLD-12).
 * Computed by the Android adapter on the face-cropped bitmap so the decision
 * logic stays JVM-testable and platform-independent.
 */
data class PresentationAttackSignals(
    /**
     * Mean absolute high-pass (Laplacian) energy normalized to 0..1 on a fixed-size
     * downsample of the face crop. Screen re-photography exhibits moiré/interference
     * signatures that inflate this value.
     */
    val highFrequencyEnergy: Float,
    /**
     * Fraction (0..1) of near-saturated white pixels in the crop — specular
     * screen-glare/reflection signature.
     */
    val specularHighlightRatio: Float,
    /**
     * Std-deviation of grayscale luminance in the central region. Printed photos
     * re-photographed sharply show abnormally flat local texture.
     */
    val centerTextureStdDev: Float,
    val noiseSigma: Float = 0f,
    /**
     * Doc 22 WP-B: MiniFASNetV2 (silentface.tflite) live probability for this frame, measured on the 2.7x
     * wide crop (softmax index 1). Null when the model is unavailable. The pixel statistics above are
     * measured on the INNER 1.0x face crop; this score is the only field that comes from the wide crop.
     */
    val padScore: Float? = null,
) {
    /**
     * Noise-compensated high frequency energy (Doc 21 WP-D):
     * Expected |Laplacian| contribution of white noise of sigma ns is ~ 0.8 * sqrt(20) * ns ≈ 3.6 * ns.
     * hfeCompensated = max(0, hfe * 255 - 3.6 * noiseSigma) / 255
     */
    val hfeCompensated: Float
        get() = maxOf(0f, highFrequencyEnergy * 255f - 3.6f * noiseSigma) / 255f

    val isFinite: Boolean
        get() = listOf(highFrequencyEnergy, specularHighlightRatio, centerTextureStdDev, noiseSigma).all { it.isFinite() }

    companion object {
        val Unavailable = PresentationAttackSignals(
            highFrequencyEnergy = 0f,
            specularHighlightRatio = 0f,
            centerTextureStdDev = Float.MAX_VALUE,
            noiseSigma = 0f,
        )
    }
}
