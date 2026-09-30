package com.attract.attendance.domain.face

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.sqrt

object EmbeddingEngine {

    private const val MODEL_FILE = "mobilefacenet.tflite"
    private const val INPUT_SIZE = 112

    /** Dimensionality of the model output; public for verification by callers/tests. */
    const val EMBEDDING_SIZE = 192

    private var interpreter: Interpreter? = null
    private var isModelLoaded = false

    /** Last initialization failure, for diagnostics (null when healthy). */
    var lastInitError: String? = null
        private set

    @Synchronized
    fun getOrInitInterpreter(context: Context): Interpreter? {
        if (isModelLoaded) return interpreter
        return try {
            val assetManager = context.assets
            val fileDescriptor = assetManager.openFd(MODEL_FILE)
            val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = fileDescriptor.startOffset
            val declaredLength = fileDescriptor.declaredLength
            val buffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
            // Fixed small thread pool: deterministic latency and avoids device-specific
            // delegate/threading faults on heterogeneous-core ARM phones.
            val options = Interpreter.Options().setNumThreads(2)
            interpreter = Interpreter(buffer, options)
            isModelLoaded = true
            lastInitError = null
            interpreter
        } catch (e: Exception) {
            interpreter = null
            isModelLoaded = false
            lastInitError = "${e::class.simpleName}: ${e.message}"
            android.util.Log.e(
                "ATTRACT_FACE",
                "TFLite init FAILED for '$MODEL_FILE' — ${e::class.simpleName}: ${e.message}",
                e,
            )
            throw IllegalStateException("Biometric model asset '$MODEL_FILE' missing or failed to initialize.", e)
        }
    }

    /**
     * Returns true if the TFLite model is loaded and ready for inference.
     * Call this at session start to surface a clear error before students arrive.
     */
    @Synchronized
    fun isAvailable(context: Context): Boolean {
        return try {
            getOrInitInterpreter(context) != null
        } catch (_: Exception) {
            false
        }
    }

    fun cropFaceForEmbedding(bitmap: Bitmap, box: Rect, marginFraction: Float = 0.2f): Bitmap {
        val marginX = (box.width() * marginFraction).toInt()
        val marginY = (box.height() * marginFraction).toInt()
        val left = (box.left - marginX).coerceAtLeast(0)
        val top = (box.top - marginY).coerceAtLeast(0)
        val right = (box.right + marginX).coerceAtMost(bitmap.width)
        val bottom = (box.bottom + marginY).coerceAtMost(bitmap.height)

        val width = (right - left).coerceAtLeast(1)
        val height = (bottom - top).coerceAtLeast(1)
        return Bitmap.createBitmap(bitmap, left, top, width, height)
    }

    /**
     * Extracts a 192-D L2-normalized face embedding from a pre-cropped face bitmap.
     *
     * IMPORTANT: the [faceBitmap] must be a face-cropped image (from [cropFaceFromBitmap]
     * in CameraPreview), NOT the full camera frame. Passing the full frame will produce
     * garbage embeddings since the face occupies <5% of the pixel area.
     *
     * This method is @Synchronized because TFLite Interpreter is NOT thread-safe.
     * Concurrent calls from multiple coroutines would cause crashes or corrupted output.
     */
    @Synchronized
    fun extractEmbedding(context: Context, faceBitmap: Bitmap): FloatArray {
        val activeInterpreter = getOrInitInterpreter(context)
            ?: throw IllegalStateException("Biometric face model interpreter unavailable.")

        val scaledBitmap = Bitmap.createScaledBitmap(faceBitmap, INPUT_SIZE, INPUT_SIZE, true)
        val inputBuffer = ByteBuffer.allocateDirect(1 * INPUT_SIZE * INPUT_SIZE * 3 * 4).apply {
            order(ByteOrder.nativeOrder())
        }

        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        scaledBitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        for (pixel in pixels) {
            val r = ((pixel shr 16) and 0xFF)
            val g = ((pixel shr 8) and 0xFF)
            val b = (pixel and 0xFF)
            inputBuffer.putFloat((r - 127.5f) / 128.0f)
            inputBuffer.putFloat((g - 127.5f) / 128.0f)
            inputBuffer.putFloat((b - 127.5f) / 128.0f)
        }

        val outputEmbedding = FloatArray(EMBEDDING_SIZE)
        val outputBuffer = Array(1) { FloatArray(EMBEDDING_SIZE) }
        try {
            activeInterpreter.run(inputBuffer, outputBuffer)
        } catch (e: Exception) {
            android.util.Log.e(
                "ATTRACT_FACE",
                "TFLite inference FAILED — ${e::class.simpleName}: ${e.message}",
                e,
            )
            throw IllegalStateException("Face model inference failed: ${e::class.simpleName}: ${e.message}", e)
        }
        System.arraycopy(outputBuffer[0], 0, outputEmbedding, 0, EMBEDDING_SIZE)

        // Clean up scaled copy immediately — raw bitmaps must not linger per LLD-13
        if (scaledBitmap !== faceBitmap) scaledBitmap.recycle()

        return l2Normalize(outputEmbedding)
    }


    fun l2Normalize(vec: FloatArray): FloatArray {
        var normSum = 0.0f
        for (v in vec) {
            normSum += v * v
        }
        val norm = sqrt(normSum)
        return if (norm > 0.00001f) {
            FloatArray(vec.size) { vec[it] / norm }
        } else {
            vec
        }
    }

    fun combineEmbeddings(embeddings: List<FloatArray>): FloatArray {
        if (embeddings.isEmpty()) return FloatArray(EMBEDDING_SIZE)
        val normalized = embeddings.map(::l2Normalize)
        val summed = FloatArray(normalized[0].size)
        for (emb in normalized) {
            for (i in emb.indices) {
                summed[i] += emb[i]
            }
        }
        for (i in summed.indices) {
            summed[i] /= normalized.size.toFloat()
        }
        return l2Normalize(summed)
    }
}
