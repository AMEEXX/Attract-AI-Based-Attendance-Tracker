package com.attract.attendance.domain.face

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class LowLightEnhancerTest {

    @Test
    fun gammaFor_calculatesExpectedCurves() {
        val gamma128 = LowLightEnhancer.gammaFor(128f)
        assertTrue("Expected gammaFor(128) ~ 1.0, was $gamma128", abs(gamma128 - 1.0f) < 0.20f)

        val gamma20 = LowLightEnhancer.gammaFor(20f)
        assertEquals(0.35f, gamma20, 0.01f)
    }

    @Test
    fun gammaLut_isMonotonic() {
        val lut = LowLightEnhancer.gammaLut(0.5f)
        assertEquals(256, lut.size)
        assertEquals(0, lut[0])
        assertEquals(255, lut[255])
        for (i in 1..255) {
            assertTrue("LUT must be monotonic at index $i", lut[i] >= lut[i - 1])
        }
    }

    @Test
    fun clahe_onConstantImage_returnsSameConstant() {
        val w = 64
        val h = 64
        val constantColor = (0xFF shl 24) or (100 shl 16) or (100 shl 8) or 100
        val input = IntArray(w * h) { constantColor }

        val output = LowLightEnhancer.clahe(input, w, h)
        for (i in output.indices) {
            val r = (output[i] shr 16) and 0xFF
            assertTrue("Expected constant R ~ 100, was $r", abs(r - 100) <= 5)
        }
    }

    @Test
    fun clahe_onDarkGradient_enhancesMeanLuma() {
        val w = 64
        val h = 64
        val input = IntArray(w * h) { i ->
            val v = (i % 64).coerceIn(10, 50)
            (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }

        val inputMean = input.map { it and 0xFF }.average()
        val output = LowLightEnhancer.clahe(input, w, h)
        val outputMean = output.map { it and 0xFF }.average()

        assertTrue("Output mean ($outputMean) should be > input mean ($inputMean)", outputMean > inputMean)
    }
}
