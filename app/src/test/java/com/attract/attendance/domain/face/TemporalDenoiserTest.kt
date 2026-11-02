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

    @Test
    fun temporalCropBuffer_keepsAtMostMaxCapacity() {
        val buffer = TemporalCropBuffer(maxCapacity = 4)
        val dummyBox = FaceBox(10f, 10f, 100f, 100f)
        val dummyCrop = IntArray(112 * 112) { 0xFF000000.toInt() }

        for (i in 1..10) {
            buffer.add(dummyCrop, timestampNanos = i * 30_000_000L, faceBox = dummyBox)
        }
        assertEquals(4, buffer.size())
    }

    @Test
    fun temporalCropBuffer_clearsWhenTrackMovesSignificantly() {
        val buffer = TemporalCropBuffer(maxCapacity = 4)
        val boxA = FaceBox(10f, 10f, 100f, 100f)
        val dummyCrop = IntArray(112 * 112) { 0xFF000000.toInt() }

        buffer.add(dummyCrop, timestampNanos = 100_000_000L, faceBox = boxA)
        buffer.add(dummyCrop, timestampNanos = 130_000_000L, faceBox = boxA)
        assertEquals(2, buffer.size())

        // Completely disjoint box (IoU = 0)
        val boxB = FaceBox(200f, 200f, 300f, 300f)
        buffer.add(dummyCrop, timestampNanos = 160_000_000L, faceBox = boxB)
        // Previous entries cleared, buffer now has 1
        assertEquals(1, buffer.size())
    }

    @Test
    fun fuse_appliesLevelsWhenDimOrBacklit() {
        val size = 112 * 112
        // Dim crops with values between 20 and 40
        val dimCrops = List(3) {
            IntArray(size) { (0xFF shl 24) or (30 shl 16) or (30 shl 8) or 30 }
        }
        // faceLuma 30 (< 90) -> should trigger levels
        val normalFused = TemporalDenoiser.fuse(dimCrops, frameMeanLuma = 40f, faceLuma = 30f)
        val rawLuma = CropMetrics.computeFaceLuma(dimCrops[0])
        val fusedLuma = CropMetrics.computeFaceLuma(normalFused)
        // With levels applied, dim crop should have higher brightness
        assertTrue("Fused luma ($fusedLuma) should be >= raw luma ($rawLuma)", fusedLuma >= rawLuma)
    }
}
