package com.attract.attendance.domain.face

import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Pure Kotlin low-light enhancement engine for dark classrooms (doc-20 WP3).
 * Operates on packed ARGB IntArray buffers without OpenCV or Android dependencies.
 */
object LowLightEnhancer {

    /**
     * Computes adaptive gamma curve mapping current mean luma to target ~114 (0.45 * 255).
     * Clamped to [0.35, 1.0] to prevent extreme noise amplification.
     */
    fun gammaFor(meanLuma: Float): Float {
        val m = (meanLuma / 255f).coerceIn(0.02f, 0.95f)
        val gamma = (ln(0.45) / ln(m.toDouble())).toFloat()
        return gamma.coerceIn(0.35f, 1.0f)
    }

    /** Precomputes 256-element gamma lookup table. */
    fun gammaLut(gamma: Float): IntArray = IntArray(256) { i ->
        (255.0 * (i / 255.0).pow(gamma.toDouble())).roundToInt().coerceIn(0, 255)
    }

    /**
     * Contrast-Limited Adaptive Histogram Equalization (CLAHE) on luminance channel.
     * Preserves color hue by scaling RGB channels proportionally to Y equalization ratio.
     */
    fun clahe(argb: IntArray, w: Int, h: Int, tiles: Int = 4, clip: Float = 2.0f): IntArray {
        if (w <= 0 || h <= 0 || argb.size < w * h) return argb

        // 1. Extract luminance (Y) channel
        val yPlane = IntArray(w * h)
        for (i in 0 until w * h) {
            val p = argb[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            yPlane[i] = ((77 * r + 150 * g + 29 * b) shr 8).coerceIn(0, 255)
        }

        val tileW = (w + tiles - 1) / tiles
        val tileH = (h + tiles - 1) / tiles

        // 2. Build clipped histogram and CDF mapping table for each tile
        val tileLuts = Array(tiles * tiles) { IntArray(256) }

        for (ty in 0 until tiles) {
            for (tx in 0 until tiles) {
                val startX = tx * tileW
                val startY = ty * tileH
                val endX = minOf(startX + tileW, w)
                val endY = minOf(startY + tileH, h)
                val regionPixels = max(1, (endX - startX) * (endY - startY))

                val hist = IntArray(256)
                for (y in startY until endY) {
                    val rowOffset = y * w
                    for (x in startX until endX) {
                        hist[yPlane[rowOffset + x]]++
                    }
                }

                // Clip histogram
                val clipLimit = max(1, (clip * regionPixels / 256f).roundToInt())
                var excess = 0
                for (v in 0..255) {
                    if (hist[v] > clipLimit) {
                        excess += hist[v] - clipLimit
                        hist[v] = clipLimit
                    }
                }

                // Redistribute excess evenly across all bins
                val bonus = excess / 256
                val remainder = excess % 256
                for (v in 0..255) {
                    hist[v] += bonus + if (v < remainder) 1 else 0
                }

                // Compute CDF and resulting lookup table
                var cdf = 0
                val lut = tileLuts[ty * tiles + tx]
                for (v in 0..255) {
                    cdf += hist[v]
                    lut[v] = ((cdf.toFloat() / regionPixels.toFloat()) * 255f).roundToInt().coerceIn(0, 255)
                }
            }
        }

        // 3. Bilinear interpolation between tile centers & color reconstitution
        val result = IntArray(w * h)
        for (y in 0 until h) {
            val tyFloat = (y.toFloat() / tileH.toFloat()) - 0.5f
            val ty0 = tyFloat.toInt().coerceIn(0, tiles - 1)
            val ty1 = (ty0 + 1).coerceIn(0, tiles - 1)
            val yWeight = (tyFloat - ty0).coerceIn(0f, 1f)

            val rowOffset = y * w
            for (x in 0 until w) {
                val txFloat = (x.toFloat() / tileW.toFloat()) - 0.5f
                val tx0 = txFloat.toInt().coerceIn(0, tiles - 1)
                val tx1 = (tx0 + 1).coerceIn(0, tiles - 1)
                val xWeight = (txFloat - tx0).coerceIn(0f, 1f)

                val idx00 = ty0 * tiles + tx0
                val idx01 = ty0 * tiles + tx1
                val idx10 = ty1 * tiles + tx0
                val idx11 = ty1 * tiles + tx1

                val oldY = yPlane[rowOffset + x]
                val v00 = tileLuts[idx00][oldY]
                val v01 = tileLuts[idx01][oldY]
                val v10 = tileLuts[idx10][oldY]
                val v11 = tileLuts[idx11][oldY]

                val top = (1f - xWeight) * v00 + xWeight * v01
                val bot = (1f - xWeight) * v10 + xWeight * v11
                val newY = ((1f - yWeight) * top + yWeight * bot).roundToInt().coerceIn(0, 255)

                val ratio = (newY + 1f) / (oldY + 1f)
                val p = argb[rowOffset + x]
                val a = (p shr 24) and 0xFF
                val r = (((p shr 16) and 0xFF) * ratio).roundToInt().coerceIn(0, 255)
                val g = (((p shr 8) and 0xFF) * ratio).roundToInt().coerceIn(0, 255)
                val b = ((p and 0xFF) * ratio).roundToInt().coerceIn(0, 255)

                result[rowOffset + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
        }

        return result
    }

    /**
     * Enhances an ARGB image by applying adaptive gamma correction followed by CLAHE.
     */
    fun enhance(argb: IntArray, w: Int, h: Int, meanLuma: Float): IntArray {
        val gamma = gammaFor(meanLuma)
        val lut = gammaLut(gamma)
        val gammaCorrected = IntArray(argb.size) { i ->
            val p = argb[i]
            val a = p and 0xFF000000.toInt()
            val r = lut[(p shr 16) and 0xFF]
            val g = lut[(p shr 8) and 0xFF]
            val b = lut[p and 0xFF]
            a or (r shl 16) or (g shl 8) or b
        }
        return clahe(gammaCorrected, w, h)
    }
}
