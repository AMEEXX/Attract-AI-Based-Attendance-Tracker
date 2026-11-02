package com.attract.attendance.domain.face

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Pure Kotlin image quality and normalization metrics for 112x112 aligned face crops.
 * Implements Doc 21 WP-B (illumination-invariant crop sharpness, Immerkaer noise sigma,
 * and robust percentile-based auto-levels).
 *
 * 100% JVM unit-testable without Android framework dependencies.
 */
object CropMetrics {

    const val CROP_SIZE = 112
    const val ROI_ROW_START = 20
    const val ROI_ROW_END = 100 // 80 rows (20 until 100)
    const val ROI_COL_START = 16
    const val ROI_COL_END = 96  // 80 cols (16 until 96)
    const val ROI_HEIGHT = ROI_ROW_END - ROI_ROW_START // 80
    const val ROI_WIDTH = ROI_COL_END - ROI_COL_START  // 80
    const val ROI_PIXELS = ROI_HEIGHT * ROI_WIDTH      // 6400

    private val GAUSS_5_TAP = floatArrayOf(0.0219f, 0.2285f, 0.4992f, 0.2285f, 0.0219f)
    private val SQRT_PI_OVER_2_DIV_6 = (sqrt(Math.PI / 2.0) / 6.0).toFloat()

    /**
     * Compute standard Rec. 601 luma for an ARGB_8888 packed pixel:
     * Y = 0.299 * R + 0.587 * G + 0.114 * B
     */
    fun lumaOf(pixel: Int): Float {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        return 0.299f * r + 0.587f * g + 0.114f * b
    }

    /**
     * Extracts 80x80 central face ROI luma from a 112x112 packed ARGB pixel array.
     */
    fun extractRoiLuma(crop: IntArray, outLuma: FloatArray = FloatArray(ROI_PIXELS)): FloatArray {
        var idx = 0
        for (r in ROI_ROW_START until ROI_ROW_END) {
            val rowOffset = r * CROP_SIZE
            for (c in ROI_COL_START until ROI_COL_END) {
                outLuma[idx++] = lumaOf(crop[rowOffset + c])
            }
        }
        return outLuma
    }

    /**
     * Mean face luma computed over the 80x80 central face region of the raw crop.
     */
    fun computeFaceLuma(crop: IntArray): Float {
        var sum = 0.0
        for (r in ROI_ROW_START until ROI_ROW_END) {
            val rowOffset = r * CROP_SIZE
            for (c in ROI_COL_START until ROI_COL_END) {
                sum += lumaOf(crop[rowOffset + c])
            }
        }
        return (sum / ROI_PIXELS).toFloat()
    }

    /**
     * Immerkaer noise sigma estimator on the 80x80 central ROI luma:
     *   r = conv(Y_roi, [[1, -2, 1], [-2, 4, -2], [1, -2, 1]])
     *   noiseSigma = sqrt(pi/2) * mean(|r|) / 6.0
     */
    fun computeNoiseSigma(crop: IntArray): Float {
        val y = extractRoiLuma(crop)
        var absSum = 0.0
        var count = 0

        // 3x3 convolution over valid interior region (rows 1..78, cols 1..78)
        for (r in 1 until ROI_HEIGHT - 1) {
            val rowPrev = (r - 1) * ROI_WIDTH
            val rowCurr = r * ROI_WIDTH
            val rowNext = (r + 1) * ROI_WIDTH

            for (c in 1 until ROI_WIDTH - 1) {
                val res = (y[rowPrev + c - 1] - 2f * y[rowPrev + c] + y[rowPrev + c + 1]) +
                    (-2f * y[rowCurr + c - 1] + 4f * y[rowCurr + c] - 2f * y[rowCurr + c + 1]) +
                    (y[rowNext + c - 1] - 2f * y[rowNext + c] + y[rowNext + c + 1])

                absSum += abs(res)
                count++
            }
        }

        if (count == 0) return 0f
        val meanAbs = (absSum / count).toFloat()
        return meanAbs * SQRT_PI_OVER_2_DIV_6
    }

    /**
     * Robust auto-levels for the 112x112 crop using the 2nd and 98th percentile of ROI luma.
     *   gain = min(200.0 / (hi - lo), 4.0)
     *   channel' = clamp((channel - lo) * gain + 28, 0, 255)
     */
    fun applyLevels(crop: IntArray): IntArray {
        // 1. Build 256-bin histogram of ROI luma
        val hist = IntArray(256)
        for (r in ROI_ROW_START until ROI_ROW_END) {
            val rowOffset = r * CROP_SIZE
            for (c in ROI_COL_START until ROI_COL_END) {
                val lum = lumaOf(crop[rowOffset + c]).roundToInt().coerceIn(0, 255)
                hist[lum]++
            }
        }

        // 2. Find P2 and P98
        val p2Target = (ROI_PIXELS * 0.02f).roundToInt()
        val p98Target = (ROI_PIXELS * 0.98f).roundToInt()

        var accum = 0
        var lo = 0
        for (i in 0..255) {
            accum += hist[i]
            if (accum >= p2Target) {
                lo = i
                break
            }
        }

        accum = 0
        var hi = 255
        for (i in 0..255) {
            accum += hist[i]
            if (accum >= p98Target) {
                hi = i
                break
            }
        }

        if (hi - lo < 8) {
            return crop.copyOf()
        }

        val gain = min(200.0f / (hi - lo), 4.0f)

        // Precompute lookup table for 0..255
        val lut = IntArray(256)
        for (v in 0..255) {
            val transformed = ((v - lo) * gain + 28f).roundToInt()
            lut[v] = transformed.coerceIn(0, 255)
        }

        val out = IntArray(crop.size)
        for (i in crop.indices) {
            val p = crop[i]
            val a = (p shr 24) and 0xFF
            val r = lut[(p shr 16) and 0xFF]
            val g = lut[(p shr 8) and 0xFF]
            val b = lut[p and 0xFF]
            out[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
        return out
    }

    /**
     * Contrast-normalized Laplacian energy on the central face region:
     *   1. Y = 80x80 ROI luma
     *   2. Y = separable 5-tap GaussianBlur(Y, sigma=0.6)
     *   3. L = 4-neighbour Laplacian(Y)
     *   4. cropSharpness = std(L) / (std(Y) + 1e-3)
     */
    fun computeCropSharpness(crop: IntArray): Float {
        val y = extractRoiLuma(crop)

        // Separable 5-tap Gaussian blur (sigma=0.6)
        // Horizontal pass
        val tmp = FloatArray(ROI_PIXELS)
        for (r in 0 until ROI_HEIGHT) {
            val rowOffset = r * ROI_WIDTH
            for (c in 0 until ROI_WIDTH) {
                var sum = 0f
                for (k in -2..2) {
                    val sc = (c + k).coerceIn(0, ROI_WIDTH - 1)
                    sum += y[rowOffset + sc] * GAUSS_5_TAP[k + 2]
                }
                tmp[rowOffset + c] = sum
            }
        }

        // Vertical pass
        val blurred = FloatArray(ROI_PIXELS)
        for (r in 0 until ROI_HEIGHT) {
            val rowOffset = r * ROI_WIDTH
            for (c in 0 until ROI_WIDTH) {
                var sum = 0f
                for (k in -2..2) {
                    val sr = (r + k).coerceIn(0, ROI_HEIGHT - 1)
                    sum += tmp[sr * ROI_WIDTH + c] * GAUSS_5_TAP[k + 2]
                }
                blurred[rowOffset + c] = sum
            }
        }

        // Standard deviation of blurred Y
        var sumY = 0.0
        for (i in 0 until ROI_PIXELS) {
            sumY += blurred[i]
        }
        val meanY = sumY / ROI_PIXELS
        var varSumY = 0.0
        for (i in 0 until ROI_PIXELS) {
            val diff = blurred[i] - meanY
            varSumY += diff * diff
        }
        val stdY = sqrt(varSumY / ROI_PIXELS).toFloat()

        // 4-neighbour Laplacian on valid region (rows 1..78, cols 1..78)
        val validPixels = (ROI_HEIGHT - 2) * (ROI_WIDTH - 2)
        var sumL = 0.0
        val lap = FloatArray(validPixels)
        var lapIdx = 0

        for (r in 1 until ROI_HEIGHT - 1) {
            val rowPrev = (r - 1) * ROI_WIDTH
            val rowCurr = r * ROI_WIDTH
            val rowNext = (r + 1) * ROI_WIDTH

            for (c in 1 until ROI_WIDTH - 1) {
                val lVal = blurred[rowPrev + c] +
                    blurred[rowNext + c] +
                    blurred[rowCurr + c - 1] +
                    blurred[rowCurr + c + 1] -
                    4f * blurred[rowCurr + c]
                lap[lapIdx++] = lVal
                sumL += lVal
            }
        }

        val meanL = sumL / validPixels
        var varSumL = 0.0
        for (i in 0 until validPixels) {
            val diff = lap[i] - meanL
            varSumL += diff * diff
        }
        val stdL = sqrt(varSumL / validPixels).toFloat()

        return stdL / (stdY + 1e-3f)
    }
}
