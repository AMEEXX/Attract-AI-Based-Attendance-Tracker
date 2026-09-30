package com.attract.attendance.feature.attendance

import android.graphics.Bitmap
import android.graphics.Color
import com.attract.attendance.domain.face.PresentationAttackSignals
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Android adapter (LLD-08/LLD-12): computes [PresentationAttackSignals] from the
 * face-cropped bitmap. Runs only on capture CLICK (one frame), not per preview frame.
 *
 * All statistics are computed on a fixed 96x96 grayscale downsample so cost is
 * bounded and results are comparable across devices/resolutions.
 */
object PresentationAttackAnalyzer {

    private const val ANALYSIS_SIZE = 96

    fun analyze(crop: Bitmap?): PresentationAttackSignals? {
        if (crop == null || crop.width < 8 || crop.height < 8) return null
        return runCatching {
            val small = Bitmap.createScaledBitmap(crop, ANALYSIS_SIZE, ANALYSIS_SIZE, true)
            val gray = FloatArray(ANALYSIS_SIZE * ANALYSIS_SIZE)
            var highlightPixels = 0
            for (y in 0 until ANALYSIS_SIZE) {
                for (x in 0 until ANALYSIS_SIZE) {
                    val pixel = small.getPixel(x, y)
                    val r = (pixel shr 16) and 0xFF
                    val g = (pixel shr 8) and 0xFF
                    val b = pixel and 0xFF
                    gray[y * ANALYSIS_SIZE + x] = 0.299f * r + 0.587f * g + 0.114f * b
                    if (r > 235 && g > 235 && b > 235) highlightPixels++
                }
            }

            val highFrequencyEnergy = meanAbsoluteLaplacian(gray)
            val centerTextureStdDev = centerStdDev(gray)

            PresentationAttackSignals(
                highFrequencyEnergy = highFrequencyEnergy,
                specularHighlightRatio = highlightPixels.toFloat() / gray.size,
                centerTextureStdDev = centerTextureStdDev,
            )
        }.getOrNull()
    }

    /** Mean |Laplacian| normalized to 0..1 — moiré/interference energy indicator. */
    private fun meanAbsoluteLaplacian(gray: FloatArray): Float {
        var sum = 0f
        var count = 0
        for (y in 1 until ANALYSIS_SIZE - 1) {
            for (x in 1 until ANALYSIS_SIZE - 1) {
                val i = y * ANALYSIS_SIZE + x
                val laplacian = abs(4f * gray[i] - gray[i - 1] - gray[i + 1] - gray[i - ANALYSIS_SIZE] - gray[i + ANALYSIS_SIZE])
                sum += laplacian
                count++
            }
        }
        return if (count > 0) (sum / count) / 255f else 0f
    }

    /** Std-dev of luminance in the central 50% region — printed-photo flatness indicator. */
    private fun centerStdDev(gray: FloatArray): Float {
        val start = ANALYSIS_SIZE / 4
        val end = ANALYSIS_SIZE - start
        var sum = 0.0
        var count = 0
        for (y in start until end) {
            for (x in start until end) {
                sum += gray[y * ANALYSIS_SIZE + x]
                count++
            }
        }
        if (count == 0) return 0f
        val mean = sum / count
        var variance = 0.0
        for (y in start until end) {
            for (x in start until end) {
                val d = gray[y * ANALYSIS_SIZE + x] - mean
                variance += d * d
            }
        }
        return sqrt(variance / count).toFloat()
    }
}
