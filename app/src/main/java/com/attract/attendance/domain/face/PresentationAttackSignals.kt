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
) {
    val isFinite: Boolean
        get() = listOf(highFrequencyEnergy, specularHighlightRatio, centerTextureStdDev).all { it.isFinite() }

    companion object {
        val Unavailable = PresentationAttackSignals(
            highFrequencyEnergy = 0f,
            specularHighlightRatio = 0f,
            centerTextureStdDev = Float.MAX_VALUE,
        )
    }
}
