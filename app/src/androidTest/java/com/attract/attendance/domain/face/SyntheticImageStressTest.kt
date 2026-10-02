package com.attract.attendance.domain.face

import android.content.Context
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.InputStream
import kotlin.math.abs

/**
 * SyntheticImageStressTest
 *
 * [SYNTHETIC_IMAGE_TEST]
 * Validates image loading, Bitmap decoding, crop/preprocessing, MobileFaceNet TFLite model execution,
 * 128-D embedding vector shape, and L2 normalization stability.
 *
 * IMPORTANT DISTINCTION (Biometric Standard Rule 1):
 * Synthetic face images test ML pipeline execution and model stability.
 * Synthetic faces are NEVER claimed as proof of real-human identity recognition accuracy.
 */
@RunWith(AndroidJUnit4::class)
class SyntheticImageStressTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        assertTrue("TFLite model asset 'mobilefacenet.tflite' must be available", EmbeddingEngine.isAvailable(context))
    }

    @Test
    fun syntheticImageTest_pipelineInferenceAndNormalization() {
        val assetManager = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context.assets
        val syntheticFiles = try {
            assetManager.list("test-data/synthetic-faces") ?: emptyArray()
        } catch (_: Exception) {
            emptyArray()
        }

        assertTrue("Synthetic dataset missing. Run 'python scripts/prepare_face_test_data.py' first.", syntheticFiles.isNotEmpty())

        var processedCount = 0
        for (fileName in syntheticFiles.take(50)) {
            val path = "test-data/synthetic-faces/$fileName"
            val stream: InputStream = assetManager.open(path)
            val bitmap = BitmapFactory.decodeStream(stream)
            assertNotNull("Synthetic image $fileName must decode to Bitmap", bitmap)

            // Pass through real MobileFaceNet TFLite pipeline
            val embedding = EmbeddingEngine.extractEmbedding(context, bitmap)
            assertNotNull(embedding)
            assertEquals(TemplateCompatibility.CURRENT_EMBEDDING_DIM, embedding.size)

            // Verify L2 normalization: magnitude must be ~1.0
            var normSquare = 0.0f
            for (v in embedding) normSquare += v * v
            assertTrue("Embedding magnitude must be L2 normalized (~1.0)", abs(normSquare - 1.0f) < 0.02f)
            processedCount++
        }

        println("[SYNTHETIC_IMAGE_TEST] Processed $processedCount synthetic photographic images. Pipeline and L2 normalization verified.")
    }
}
