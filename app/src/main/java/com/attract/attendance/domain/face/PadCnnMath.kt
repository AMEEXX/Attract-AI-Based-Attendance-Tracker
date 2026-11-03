package com.attract.attendance.domain.face

import java.nio.ByteBuffer
import kotlin.math.exp

/**
 * Pure (JVM-testable) tensor helpers for the MiniFASNetV2 PAD CNN (`silentface.tflite`, Doc 22 WP-B).
 *
 * Locked contract (exp7 / exp10):
 *  - input  `[1, 3, 80, 80]` float32 **NCHW**, channel planes **B, G, R**, **raw 0..255** (no normalisation)
 *  - output `[1, 3]` float32 logits -> softmax -> index [LIVE_INDEX] = live probability (`padScore`)
 */
object PadCnnMath {

    const val INPUT_SIZE = 80
    const val NUM_CLASSES = 3
    const val LIVE_INDEX = 1
    const val INPUT_FLOATS = 3 * INPUT_SIZE * INPUT_SIZE
    const val INPUT_BYTES = 4 * INPUT_FLOATS

    /**
     * Packs row-major ARGB pixels (80x80) into [out] as NCHW float32, planes in B, G, R order, raw 0..255.
     * [out] must have at least [INPUT_BYTES] remaining capacity and should use native byte order.
     * The buffer is rewound before and after packing.
     */
    fun packNchwBgrRaw(argb: IntArray, out: ByteBuffer) {
        val plane = INPUT_SIZE * INPUT_SIZE
        require(argb.size >= plane) { "Need $plane pixels, got ${argb.size}" }
        require(out.capacity() >= INPUT_BYTES) { "Buffer too small: ${out.capacity()} < $INPUT_BYTES" }
        out.rewind()
        for (i in 0 until plane) out.putFloat((argb[i] and 0xFF).toFloat())          // B
        for (i in 0 until plane) out.putFloat(((argb[i] shr 8) and 0xFF).toFloat())  // G
        for (i in 0 until plane) out.putFloat(((argb[i] shr 16) and 0xFF).toFloat()) // R
        out.rewind()
    }

    /** Same packing into a FloatArray (NCHW, BGR, raw). Used by tests to assert exact layout. */
    fun packNchwBgrRaw(argb: IntArray): FloatArray {
        val plane = INPUT_SIZE * INPUT_SIZE
        require(argb.size >= plane) { "Need $plane pixels, got ${argb.size}" }
        val out = FloatArray(INPUT_FLOATS)
        for (i in 0 until plane) {
            val p = argb[i]
            out[i] = (p and 0xFF).toFloat()
            out[plane + i] = ((p shr 8) and 0xFF).toFloat()
            out[2 * plane + i] = ((p shr 16) and 0xFF).toFloat()
        }
        return out
    }

    /** Numerically stable softmax. */
    fun softmax(logits: FloatArray): FloatArray {
        if (logits.isEmpty()) return FloatArray(0)
        val max = logits.max()
        val e = DoubleArray(logits.size) { exp((logits[it] - max).toDouble()) }
        val sum = e.sum()
        return FloatArray(logits.size) { if (sum > 0.0) (e[it] / sum).toFloat() else 0f }
    }

    /** softmax(logits)[LIVE_INDEX], clamped to 0..1. Non-finite logits yield null (treated as "no score"). */
    fun liveProbability(logits: FloatArray): Float? {
        if (logits.size != NUM_CLASSES || logits.any { !it.isFinite() }) return null
        return softmax(logits)[LIVE_INDEX].coerceIn(0f, 1f)
    }
}
