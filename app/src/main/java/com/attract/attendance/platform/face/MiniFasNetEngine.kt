package com.attract.attendance.platform.face

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.Log
import com.attract.attendance.domain.face.PadCnnMath
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * MiniFASNetV2 passive presentation-attack-detection engine (Doc 22 WP-B).
 *
 * Model: `silentface.tflite` (Silent-Face-Anti-Spoofing MiniFASNetV2, scale 2.7).
 * Contract (verified offline in exp7/exp10 — DO NOT change without re-running them):
 *  - Input  `[1, 3, 80, 80]` float32, **NCHW**, **BGR** channel order, **raw 0..255** (no /255, no mean/std).
 *  - Output `[1, 3]` float32 logits. softmax(output[0])[1] = **live probability** (`padScore`).
 *  - Crop: the **2.7x** expanded face box ([com.attract.attendance.domain.face.PadCrop.box] scale 2.7),
 *    i.e. `FrameBundle.wideCrop`, resized to 80x80. NOT the tight / aligned face crop.
 *
 * Decision use (LivenessPolicy / RiskFusion): median of the last 3 scores; `< padSpoofReject (0.10)` only
 * ESCALATES to an active check, it never hard-rejects (genuine window-backlit faces fall below 0.10 in 8.7 %
 * of frames, exp10).
 *
 * Safe fallback: if the asset is missing or fails to load, [score] returns null and liveness falls back to
 * 3D parallax + challenge (RiskFusion treats a null PAD as "not suspect").
 *
 * Thread-safety: TFLite Interpreter is not thread-safe; all inference is @Synchronized. Called from the
 * single camera analysis executor.
 */
object MiniFasNetEngine {

    private const val TAG = "MiniFasNetEngine"
    const val MODEL_ASSET = "silentface.tflite"
    const val INPUT_SIZE = PadCnnMath.INPUT_SIZE
    const val NUM_CLASSES = PadCnnMath.NUM_CLASSES

    private var interpreter: Interpreter? = null
    private var initAttempted = false

    var lastError: String? = null
        private set

    // Pre-allocated buffers (zero allocation per frame)
    private val inputBuffer: ByteBuffer =
        ByteBuffer.allocateDirect(PadCnnMath.INPUT_BYTES).order(ByteOrder.nativeOrder())
    private val outputBuffer = Array(1) { FloatArray(NUM_CLASSES) }
    private val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
    private var scaledBitmap: Bitmap? = null
    private var canvas: Canvas? = null
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val srcRect = Rect()
    private val dstRect = Rect(0, 0, INPUT_SIZE, INPUT_SIZE)

    val isAvailable: Boolean
        @Synchronized get() = interpreter != null

    /** Loads the model once. Returns true when ready. Never throws. */
    @Synchronized
    fun init(context: Context): Boolean {
        if (initAttempted) return interpreter != null
        initAttempted = true
        return try {
            val fd = context.assets.openFd(MODEL_ASSET)
            val buffer = FileInputStream(fd.fileDescriptor).channel.use { ch ->
                ch.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
            }
            val interp = Interpreter(buffer, Interpreter.Options().setNumThreads(2))

            val inShape = interp.getInputTensor(0).shape()
            val outShape = interp.getOutputTensor(0).shape()
            check(inShape.contentEquals(intArrayOf(1, 3, INPUT_SIZE, INPUT_SIZE))) {
                "silentface input shape mismatch: expected [1,3,80,80], got ${inShape.contentToString()}"
            }
            check(outShape.contentEquals(intArrayOf(1, NUM_CLASSES))) {
                "silentface output shape mismatch: expected [1,3], got ${outShape.contentToString()}"
            }

            interpreter = interp
            lastError = null
            Log.i(TAG, "MiniFASNetV2 loaded ($MODEL_ASSET)")
            true
        } catch (t: Throwable) {
            interpreter = null
            lastError = "${t::class.simpleName}: ${t.message}"
            Log.w(TAG, "MiniFASNetV2 unavailable: $lastError")
            false
        }
    }

    /**
     * Live probability (0..1) for a 2.7x wide face crop, or null when the model is unavailable or the crop
     * is invalid. The crop may be any size; it is resized to 80x80 (bilinear) here.
     */
    @Synchronized
    fun score(wideCrop: Bitmap?): Float? {
        val interp = interpreter ?: return null
        if (wideCrop == null || wideCrop.isRecycled || wideCrop.width < 8 || wideCrop.height < 8) return null
        return try {
            val input = resizeTo80(wideCrop)
            input.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
            PadCnnMath.packNchwBgrRaw(pixels, inputBuffer)
            interp.run(inputBuffer, outputBuffer)
            PadCnnMath.liveProbability(outputBuffer[0])
        } catch (t: Throwable) {
            Log.e(TAG, "MiniFASNetV2 inference failed: ${t.message}", t)
            null
        }
    }

    private fun resizeTo80(src: Bitmap): Bitmap {
        if (src.width == INPUT_SIZE && src.height == INPUT_SIZE && src.config == Bitmap.Config.ARGB_8888) return src
        val dst = scaledBitmap ?: Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888)
            .also { scaledBitmap = it }
        val c = canvas ?: Canvas(dst).also { canvas = it }
        srcRect.set(0, 0, src.width, src.height)
        c.drawBitmap(src, srcRect, dstRect, paint)
        return dst
    }

    @Synchronized
    fun close() {
        try {
            interpreter?.close()
        } catch (_: Throwable) {
        } finally {
            interpreter = null
            initAttempted = false
            scaledBitmap?.recycle()
            scaledBitmap = null
            canvas = null
        }
    }
}
