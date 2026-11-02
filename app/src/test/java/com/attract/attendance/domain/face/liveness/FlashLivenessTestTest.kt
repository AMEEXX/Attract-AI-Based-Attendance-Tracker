package com.attract.attendance.domain.face.liveness

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for photometric screen-flash liveness and 3D surface shading analysis (doc-20 WP7.3).
 */
class FlashLivenessTestTest {

    @Test
    fun pearsonCorrelation_identicalSequences_returnsOne() {
        val x = listOf(1f, 2f, 3f, 4f, 5f)
        val y = listOf(1f, 2f, 3f, 4f, 5f)
        val corr = FlashLivenessTest.pearsonCorrelation(x, y)
        assertEquals(1.0f, corr, 1e-4f)
    }

    @Test
    fun pearsonCorrelation_oppositeSequences_returnsNegativeOne() {
        val x = listOf(1f, 2f, 3f, 4f, 5f)
        val y = listOf(5f, 4f, 3f, 2f, 1f)
        val corr = FlashLivenessTest.pearsonCorrelation(x, y)
        assertEquals(-1.0f, corr, 1e-4f)
    }

    @Test
    fun pearsonCorrelation_constantSequence_returnsZero() {
        val x = listOf(1f, 2f, 3f, 4f)
        val y = listOf(5f, 5f, 5f, 5f)
        val corr = FlashLivenessTest.pearsonCorrelation(x, y)
        assertEquals(0.0f, corr, 1e-4f)
    }

    @Test
    fun insufficientSamples_returnsZeroScore() {
        val samples = listOf(
            FlashLivenessTest.FlashSample(
                emittedColorRgb = Triple(1f, 0f, 0f),
                observedSkinRgb = Triple(0.8f, 0.2f, 0.2f),
                frontPatchLuma = 60f,
                sidePatchLuma = 55f
            )
        )
        val score = FlashLivenessTest.evaluate(samples)
        assertEquals(0f, score, 1e-4f)
    }

    @Test
    fun liveHumanFace_highCorrelationAndDifferentialShading_yieldsHighScore() {
        // Emitted: Red, Green, Blue, White
        // Live face: observed chroma rises in the emitted channel, front patches brighten significantly more than sides
        val samples = listOf(
            FlashLivenessTest.FlashSample(
                emittedColorRgb = Triple(1f, 0f, 0f), // Red flash
                observedSkinRgb = Triple(0.85f, 0.15f, 0.15f),
                frontPatchLuma = 75f,
                sidePatchLuma = 58f
            ),
            FlashLivenessTest.FlashSample(
                emittedColorRgb = Triple(0f, 1f, 0f), // Green flash
                observedSkinRgb = Triple(0.20f, 0.70f, 0.20f),
                frontPatchLuma = 78f,
                sidePatchLuma = 60f
            ),
            FlashLivenessTest.FlashSample(
                emittedColorRgb = Triple(0f, 0f, 1f), // Blue flash
                observedSkinRgb = Triple(0.15f, 0.20f, 0.80f),
                frontPatchLuma = 74f,
                sidePatchLuma = 57f
            ),
            FlashLivenessTest.FlashSample(
                emittedColorRgb = Triple(1f, 1f, 1f), // White flash
                observedSkinRgb = Triple(0.40f, 0.40f, 0.40f),
                frontPatchLuma = 85f,
                sidePatchLuma = 65f
            )
        )

        val score = FlashLivenessTest.evaluate(
            samples = samples,
            baselineFrontLuma = 50f,
            baselineSideLuma = 50f
        )
        assertTrue("Expected score >= 0.70 for live human face under flash, was $score", score >= 0.70f)
    }

    @Test
    fun screenReplayAttack_staticReflectionAndFlatShading_yieldsLowScore() {
        // Screen replay emitting its own display: constant observed skin chroma regardless of screen flash,
        // and uniform/equal gain across front and side patches.
        val samples = listOf(
            FlashLivenessTest.FlashSample(
                emittedColorRgb = Triple(1f, 0f, 0f),
                observedSkinRgb = Triple(0.40f, 0.35f, 0.30f),
                frontPatchLuma = 55f,
                sidePatchLuma = 55f
            ),
            FlashLivenessTest.FlashSample(
                emittedColorRgb = Triple(0f, 1f, 0f),
                observedSkinRgb = Triple(0.40f, 0.35f, 0.30f),
                frontPatchLuma = 55f,
                sidePatchLuma = 55f
            ),
            FlashLivenessTest.FlashSample(
                emittedColorRgb = Triple(0f, 0f, 1f),
                observedSkinRgb = Triple(0.40f, 0.35f, 0.30f),
                frontPatchLuma = 56f,
                sidePatchLuma = 56f
            ),
            FlashLivenessTest.FlashSample(
                emittedColorRgb = Triple(1f, 1f, 1f),
                observedSkinRgb = Triple(0.40f, 0.35f, 0.30f),
                frontPatchLuma = 56f,
                sidePatchLuma = 56f
            )
        )

        val score = FlashLivenessTest.evaluate(
            samples = samples,
            baselineFrontLuma = 50f,
            baselineSideLuma = 50f
        )
        assertTrue("Expected score < 0.25 for flat screen replay, was $score", score < 0.25f)
    }
}
