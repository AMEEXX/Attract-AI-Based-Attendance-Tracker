package com.attract.attendance.domain.face

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.sqrt

class TemporalDenoiserTest {

    @Test
    fun denoise_lowersNoiseStandardDeviationByAtLeast40Percent() {
        val size = 112 * 112
        val rnd = Random(42)

        // Base image with spatial gradient (simulates facial features, high spatial variance)
        val baseSignal = IntArray(size) { i -> 50 + (i % 112) }

        val noisyCrops = List(4) {
            IntArray(size) { i ->
                val noise = rnd.nextInt(21) - 10 // [-10, 10]
                val v = (baseSignal[i] + noise).coerceIn(0, 255)
                (0xFF shl 24) or (v shl 16) or (v shl 8) or v
            }
        }

        // Noise std on single frame relative to ground truth signal
        val noiseSingle = noisyCrops[0].indices.map { i ->
            val v = noisyCrops[0][i] and 0xFF
            (v - baseSignal[i]).toDouble()
        }
        val std1 = sqrt(noiseSingle.map { it * it }.average())

        val denoised = TemporalDenoiser.denoise(noisyCrops)
        val noiseDenoised = denoised.indices.map { i ->
            val v = denoised[i] and 0xFF
            (v - baseSignal[i]).toDouble()
        }
        val stdDenoised = sqrt(noiseDenoised.map { it * it }.average())

        val reduction = (std1 - stdDenoised) / std1
        assertTrue("Expected >= 40% noise reduction, got ${reduction * 100}%", reduction >= 0.40)
    }

    @Test
    fun denoise_excludesShiftedOrCorruptedCrop() {
        val size = 112 * 112
        val cropA = IntArray(size) { (0xFF shl 24) or (100 shl 16) or (100 shl 8) or 100 }
        // Very different image (inverted pattern)
        val shiftedCrop = IntArray(size) { i ->
            val v = if (i % 2 == 0) 0 else 255
            (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }

        val result = TemporalDenoiser.denoise(listOf(shiftedCrop, cropA))
        // Since shiftedCrop has NCC < 0.92, only cropA is valid (< 2 valid crops), so cropA is returned
        assertEquals(cropA[0], result[0])
    }
}
