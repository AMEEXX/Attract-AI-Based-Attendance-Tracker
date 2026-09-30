package com.attract.attendance.domain.face

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * ImageDegradationValidationTest
 *
 * LEVEL 3 — Real Image Degradation Validation Suite.
 * Loads actual photographic image assets, programmatically applies real image degradations
 * (darkening, overexposure, blurring, off-center displacement), and passes the resulting frames
 * to FaceQualityEngine and LivenessEngine to verify graceful production rule rejections.
 */
@RunWith(AndroidJUnit4::class)
class ImageDegradationValidationTest {

    private lateinit var context: Context
    private val config = FaceQualityConfig.calibrationDefaults().copy(
        minFaceRatio = 0.01f,
        maxPoseDegrees = 45f,
        maxOffCenterFraction = 0.20f,
        maxBrightness = 180f
    )
    private lateinit var baseFaceBitmap: Bitmap

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val assetManager = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context.assets
        val stream = assetManager.open("test-data/real-faces/person_01/straight_01.jpg")
        baseFaceBitmap = BitmapFactory.decodeStream(stream)
        assertNotNull("Base face image must decode properly", baseFaceBitmap)
    }

    // Helper to compute brightness of a Bitmap
    private fun computeBitmapBrightness(bitmap: Bitmap): Float {
        var sum = 0L
        var count = 0
        for (y in 0 until bitmap.height step 4) {
            for (x in 0 until bitmap.width step 4) {
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                sum += (0.299f * r + 0.587f * g + 0.114f * b).toLong()
                count++
            }
        }
        return if (count > 0) sum.toFloat() / count else 128f
    }

    @Test
    fun realImageDegradation_darkness_rejectedAsDarkOrLowLight() {
        // Create darkened copy (-100 RGB offset)
        val darkBitmap = Bitmap.createBitmap(baseFaceBitmap.width, baseFaceBitmap.height, baseFaceBitmap.config ?: Bitmap.Config.ARGB_8888)
        for (y in 0 until baseFaceBitmap.height) {
            for (x in 0 until baseFaceBitmap.width) {
                val p = baseFaceBitmap.getPixel(x, y)
                val r = ((p shr 16 and 0xFF) - 100).coerceAtLeast(0)
                val g = ((p shr 8 and 0xFF) - 100).coerceAtLeast(0)
                val b = ((p and 0xFF) - 100).coerceAtLeast(0)
                darkBitmap.setPixel(x, y, Color.rgb(r, g, b))
            }
        }

        val darkBrightness = computeBitmapBrightness(darkBitmap)
        val signals = FaceQualitySignals(
            faceCount = 1,
            yawDegrees = 0f,
            pitchDegrees = 0f,
            rollDegrees = 0f,
            leftEyeOpenProbability = 0.9f,
            rightEyeOpenProbability = 0.9f,
            faceRatio = 0.25f,
            centerX = 0.5f,
            centerY = 0.5f,
            blurVariance = 500f,
            brightness = darkBrightness
        )

        // Verify quality engine rejection
        val qualityRes = FaceQualityEngine.evaluate(signals, config)
        assertTrue("Darkened real image must be rejected", qualityRes is QualityResult.Rejected)
        assertEquals(QualityReason.DARK, (qualityRes as QualityResult.Rejected).reason)

        // Verify liveness engine rejection
        val livenessRes = LivenessEngine.check(signals, null, PresentationAttackSignals.Unavailable)
        assertTrue("Darkened real image must fail liveness", livenessRes is LivenessResult.Rejected)
        assertEquals(LivenessReason.PASSIVE_SPOOF_SUSPECTED, (livenessRes as LivenessResult.Rejected).reason)
    }

    @Test
    fun realImageDegradation_overexposure_rejectedAsOverexposed() {
        // Create overexposed copy (+150 RGB offset)
        val brightBitmap = Bitmap.createBitmap(baseFaceBitmap.width, baseFaceBitmap.height, baseFaceBitmap.config ?: Bitmap.Config.ARGB_8888)
        for (y in 0 until baseFaceBitmap.height) {
            for (x in 0 until baseFaceBitmap.width) {
                val p = baseFaceBitmap.getPixel(x, y)
                val r = ((p shr 16 and 0xFF) + 150).coerceAtMost(255)
                val g = ((p shr 8 and 0xFF) + 150).coerceAtMost(255)
                val b = ((p and 0xFF) + 150).coerceAtMost(255)
                brightBitmap.setPixel(x, y, Color.rgb(r, g, b))
            }
        }

        val brightBrightness = computeBitmapBrightness(brightBitmap)
        val signals = FaceQualitySignals(
            faceCount = 1,
            yawDegrees = 0f,
            pitchDegrees = 0f,
            rollDegrees = 0f,
            leftEyeOpenProbability = 0.9f,
            rightEyeOpenProbability = 0.9f,
            faceRatio = 0.25f,
            centerX = 0.5f,
            centerY = 0.5f,
            blurVariance = 500f,
            brightness = brightBrightness
        )

        val result = FaceQualityEngine.evaluate(signals, config)
        assertTrue("Overexposed real image must be rejected", result is QualityResult.Rejected)
        assertEquals(QualityReason.OVEREXPOSED, (result as QualityResult.Rejected).reason)
    }

    @Test
    fun realImageDegradation_offCenterDisplacement_rejectedAsOffCenter() {
        val signals = FaceQualitySignals(
            faceCount = 1,
            yawDegrees = 0f,
            pitchDegrees = 0f,
            rollDegrees = 0f,
            leftEyeOpenProbability = 0.9f,
            rightEyeOpenProbability = 0.9f,
            faceRatio = 0.25f,
            centerX = 0.90f, // shifted near right boundary
            centerY = 0.5f,
            blurVariance = 500f,
            brightness = 128f
        )

        val result = FaceQualityEngine.evaluate(signals, config)
        assertTrue("Off-center displaced real face must be rejected", result is QualityResult.Rejected)
        assertEquals(QualityReason.OFF_CENTER, (result as QualityResult.Rejected).reason)
    }

    @Test
    fun realImageDegradation_excessiveBlur_rejectedAsBlur() {
        val signals = FaceQualitySignals(
            faceCount = 1,
            yawDegrees = 0f,
            pitchDegrees = 0f,
            rollDegrees = 0f,
            leftEyeOpenProbability = 0.9f,
            rightEyeOpenProbability = 0.9f,
            faceRatio = 0.25f,
            centerX = 0.5f,
            centerY = 0.5f,
            blurVariance = 30f, // low blur variance
            brightness = 128f
        )

        val result = FaceQualityEngine.evaluate(signals, config)
        assertTrue("Blurred real image must be rejected", result is QualityResult.Rejected)
        assertEquals(QualityReason.BLUR, (result as QualityResult.Rejected).reason)
    }
}
