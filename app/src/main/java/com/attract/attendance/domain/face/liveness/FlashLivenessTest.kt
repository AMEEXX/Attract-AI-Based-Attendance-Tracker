package com.attract.attendance.domain.face.liveness

import kotlin.math.sqrt

/**
 * Screen-flash photometric liveness analysis (doc-20 WP7.3).
 * Verifies diffuse 3D skin reflection and surface normals against active random screen color flashes.
 */
object FlashLivenessTest {

    data class FlashSample(
        val emittedColorRgb: Triple<Float, Float, Float>, // e.g. (1, 0, 0) for red, (1, 1, 1) for white
        val observedSkinRgb: Triple<Float, Float, Float>,  // Linear RGB from skin patch
        val frontPatchLuma: Float,                        // Nose bridge + cheek front
        val sidePatchLuma: Float,                         // Cheek side / jaw
    )

    /**
     * Pearson correlation coefficient between two float sequences.
     */
    fun pearsonCorrelation(x: List<Float>, y: List<Float>): Float {
        if (x.size != y.size || x.size < 3) return 0f
        val meanX = x.average()
        val meanY = y.average()

        var num = 0.0
        var denX = 0.0
        var denY = 0.0
        for (i in x.indices) {
            val dx = x[i] - meanX
            val dy = y[i] - meanY
            num += dx * dy
            denX += dx * dx
            denY += dy * dy
        }

        val den = sqrt(denX * denY)
        return if (den > 1e-6) (num / den).toFloat() else 0f
    }

    /**
     * Evaluates photometric correlation and 3D differential surface shading.
     */
    fun evaluate(
        samples: List<FlashSample>,
        baselineFrontLuma: Float = 50f,
        baselineSideLuma: Float = 50f,
        minCorrelation: Float = 0.55f,
        minShadingRatio: Float = 1.06f,
    ): Float {
        if (samples.size < 4) return 0f

        // 1. Channel correlation test
        val emittedR = samples.map { it.emittedColorRgb.first }
        val emittedG = samples.map { it.emittedColorRgb.second }
        val emittedB = samples.map { it.emittedColorRgb.third }

        val observedChromaR = samples.map {
            val sum = it.observedSkinRgb.first + it.observedSkinRgb.second + it.observedSkinRgb.third
            if (sum > 1e-4) it.observedSkinRgb.first / sum else 0.33f
        }
        val observedChromaG = samples.map {
            val sum = it.observedSkinRgb.first + it.observedSkinRgb.second + it.observedSkinRgb.third
            if (sum > 1e-4) it.observedSkinRgb.second / sum else 0.33f
        }
        val observedChromaB = samples.map {
            val sum = it.observedSkinRgb.first + it.observedSkinRgb.second + it.observedSkinRgb.third
            if (sum > 1e-4) it.observedSkinRgb.third / sum else 0.33f
        }

        val corrR = pearsonCorrelation(emittedR, observedChromaR)
        val corrG = pearsonCorrelation(emittedG, observedChromaG)
        val corrB = pearsonCorrelation(emittedB, observedChromaB)
        val flashCorr = (corrR + corrG + corrB) / 3f

        // 2. 3D shading ratio test
        val meanFrontLuma = samples.map { it.frontPatchLuma }.average().toFloat()
        val meanSideLuma = samples.map { it.sidePatchLuma }.average().toFloat()

        val frontGain = maxOf(1f, meanFrontLuma - baselineFrontLuma)
        val sideGain = maxOf(1f, meanSideLuma - baselineSideLuma)
        val shadingRatio = frontGain / sideGain

        // 3. Fused flash score [0, 1]
        val corrScore = ((flashCorr - 0.30f) / (minCorrelation - 0.30f)).coerceIn(0f, 1f)
        val shadingScore = ((shadingRatio - 1.0f) / ((minShadingRatio - 1.0f) * 2f)).coerceIn(0f, 1f)

        return (0.6f * corrScore + 0.4f * shadingScore).coerceIn(0f, 1f)
    }
}
