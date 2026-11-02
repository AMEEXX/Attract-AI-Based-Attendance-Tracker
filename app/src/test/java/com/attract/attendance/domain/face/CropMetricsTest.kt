package com.attract.attendance.domain.face

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class CropMetricsTest {

    private fun makeUniformCrop(color: Int): IntArray {
        return IntArray(CropMetrics.CROP_SIZE * CropMetrics.CROP_SIZE) { color }
    }

    private fun makeRgbPixel(r: Int, g: Int, b: Int): Int {
        return (0xFF shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)
    }

    @Test
    fun computeFaceLuma_uniformGrey_returnsExactLuma() {
        // Grey 100
        val crop = makeUniformCrop(makeRgbPixel(100, 100, 100))
        val luma = CropMetrics.computeFaceLuma(crop)
        assertEquals(100f, luma, 0.01f)
    }

    @Test
    fun computeFaceLuma_blackAndWhite_returnsExpected() {
        val black = makeUniformCrop(makeRgbPixel(0, 0, 0))
        val white = makeUniformCrop(makeRgbPixel(255, 255, 255))
        assertEquals(0f, CropMetrics.computeFaceLuma(black), 0.001f)
        assertEquals(255f, CropMetrics.computeFaceLuma(white), 0.001f)
    }

    @Test
    fun computeNoiseSigma_smoothCrop_returnsZero() {
        val smooth = makeUniformCrop(makeRgbPixel(128, 128, 128))
        val sigma = CropMetrics.computeNoiseSigma(smooth)
        assertEquals(0f, sigma, 0.001f)
    }

    @Test
    fun computeNoiseSigma_withSyntheticNoise_returnsExpectedOrderOfMagnitude() {
        val rng = Random(42)
        val targetSigma = 10f
        val noisy = IntArray(CropMetrics.CROP_SIZE * CropMetrics.CROP_SIZE) {
            val noise = (rng.nextGaussian() * targetSigma).toInt()
            val v = (128 + noise).coerceIn(0, 255)
            makeRgbPixel(v, v, v)
        }
        val estimatedSigma = CropMetrics.computeNoiseSigma(noisy)
        // Immerkaer estimate should be within ~20% of synthetic Gaussian noise sigma
        assertTrue("Estimated sigma $estimatedSigma should be near $targetSigma", estimatedSigma in 7.0f..13.0f)
    }

    @Test
    fun applyLevels_lowContrastCrop_returnsUnchanged() {
        // Range 100..105 (span < 8)
        val lowContrast = IntArray(CropMetrics.CROP_SIZE * CropMetrics.CROP_SIZE) { idx ->
            val v = 100 + (idx % 5)
            makeRgbPixel(v, v, v)
        }
        val result = CropMetrics.applyLevels(lowContrast)
        // Should be identical
        for (i in lowContrast.indices) {
            assertEquals(lowContrast[i], result[i])
        }
    }

    @Test
    fun applyLevels_dimFace_increasesBrightnessAndContrast() {
        // Dim face with pixel values mostly between 15 and 45
        val rng = Random(123)
        val dimFace = IntArray(CropMetrics.CROP_SIZE * CropMetrics.CROP_SIZE) {
            val v = (15 + rng.nextInt(31))
            makeRgbPixel(v, v, v)
        }
        val rawLuma = CropMetrics.computeFaceLuma(dimFace)
        assertTrue(rawLuma < 50f)

        val leveled = CropMetrics.applyLevels(dimFace)
        val leveledLuma = CropMetrics.computeFaceLuma(leveled)
        assertTrue("Leveled luma ($leveledLuma) should be significantly higher than raw ($rawLuma)", leveledLuma > 80f)
    }

    @Test
    fun computeCropSharpness_flatCrop_returnsZero() {
        val flat = makeUniformCrop(makeRgbPixel(150, 150, 150))
        val sharpness = CropMetrics.computeCropSharpness(flat)
        assertEquals(0f, sharpness, 0.001f)
    }

    @Test
    fun computeCropSharpness_sharpEdgeVsBlurredEdge() {
        // Sharp vertical bar in ROI
        val sharp = IntArray(CropMetrics.CROP_SIZE * CropMetrics.CROP_SIZE) { idx ->
            val c = idx % CropMetrics.CROP_SIZE
            val v = if (c % 8 < 4) 40 else 200
            makeRgbPixel(v, v, v)
        }

        // Blurred version (gradual transition)
        val blurred = IntArray(CropMetrics.CROP_SIZE * CropMetrics.CROP_SIZE) { idx ->
            val c = idx % CropMetrics.CROP_SIZE
            val v = 120 + (40 * kotlin.math.sin(c * 0.1)).toInt()
            makeRgbPixel(v, v, v)
        }

        val sharpScore = CropMetrics.computeCropSharpness(sharp)
        val blurredScore = CropMetrics.computeCropSharpness(blurred)

        assertTrue("Sharp score ($sharpScore) should be > 0.15", sharpScore > 0.15f)
        assertTrue("Blurred score ($blurredScore) should be lower than sharp score ($sharpScore)", blurredScore < sharpScore)
    }

    private fun Random.nextGaussian(): Double {
        // Box-Muller transform
        val u1 = maxOf(1e-10, nextDouble())
        val u2 = nextDouble()
        return kotlin.math.sqrt(-2.0 * kotlin.math.log(u1, Math.E)) * kotlin.math.cos(2.0 * Math.PI * u2)
    }
}
