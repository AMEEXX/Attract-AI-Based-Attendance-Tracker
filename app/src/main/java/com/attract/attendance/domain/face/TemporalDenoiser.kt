package com.attract.attendance.domain.face

import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Multi-frame temporal denoising of aligned 112x112 facial crops (doc-20 WP3.3).
 * Averages aligned face crops along the same identity track to boost SNR (Night Sight style).
 */
object TemporalDenoiser {

    const val MIN_NCC_CORRELATION = 0.92f

    /**
     * Computes Normalized Cross-Correlation (NCC) between two ARGB buffers on the green/luminance channel.
     */
    fun normalizedCrossCorrelation(a: IntArray, b: IntArray): Float {
        if (a.size != b.size || a.isEmpty()) return 0f

        var sumA = 0.0
        var sumB = 0.0
        val n = a.size
        for (i in 0 until n) {
            sumA += (a[i] shr 8) and 0xFF
            sumB += (b[i] shr 8) and 0xFF
        }
        val meanA = sumA / n
        val meanB = sumB / n

        var num = 0.0
        var denA = 0.0
        var denB = 0.0
        for (i in 0 until n) {
            val devA = ((a[i] shr 8) and 0xFF) - meanA
            val devB = ((b[i] shr 8) and 0xFF) - meanB
            num += devA * devB
            denA += devA * devA
            denB += devB * devB
        }

        val den = sqrt(denA * denB)
        return if (den > 1e-6) (num / den).toFloat() else 0f
    }

    /**
     * Denoises aligned crops by averaging them if their NCC relative to the newest crop >= 0.92.
     * If fewer than 2 crops pass NCC, returns the newest crop untouched.
     */
    fun denoise(crops: List<IntArray>, width: Int = 112, height: Int = 112): IntArray {
        if (crops.isEmpty()) return IntArray(0)
        val newest = crops.last()
        if (crops.size < 2) return newest.copyOf()

        val validCrops = mutableListOf<IntArray>()
        for (crop in crops) {
            if (crop.size == newest.size) {
                if (crop === newest || normalizedCrossCorrelation(crop, newest) >= MIN_NCC_CORRELATION) {
                    validCrops.add(crop)
                }
            }
        }

        if (validCrops.size < 2) return newest.copyOf()

        val numCrops = validCrops.size
        val result = IntArray(newest.size)
        val pixelCount = newest.size

        for (i in 0 until pixelCount) {
            var sumA = 0
            var sumR = 0
            var sumG = 0
            var sumB = 0
            for (crop in validCrops) {
                val p = crop[i]
                sumA += (p shr 24) and 0xFF
                sumR += (p shr 16) and 0xFF
                sumG += (p shr 8) and 0xFF
                sumB += p and 0xFF
            }
            val a = (sumA / numCrops).coerceIn(0, 255)
            val r = (sumR / numCrops).coerceIn(0, 255)
            val g = (sumG / numCrops).coerceIn(0, 255)
            val b = (sumB / numCrops).coerceIn(0, 255)
            result[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }

        return result
    }
}
