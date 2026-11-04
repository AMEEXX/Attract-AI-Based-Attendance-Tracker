package com.attract.attendance.domain.face

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Doc 22 WP-B: silentface.tflite tensor contract ([1,3,80,80] NCHW, BGR, raw 0..255; softmax[1] = live). */
class PadCnnMathTest {

    private val plane = PadCnnMath.INPUT_SIZE * PadCnnMath.INPUT_SIZE

    private fun argb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    @Test
    fun constants() {
        assertEquals(80, PadCnnMath.INPUT_SIZE)
        assertEquals(3, PadCnnMath.NUM_CLASSES)
        assertEquals(1, PadCnnMath.LIVE_INDEX)
        assertEquals(3 * 80 * 80 * 4, PadCnnMath.INPUT_BYTES)
    }

    @Test
    fun packing_isNchw_bgrPlanes_raw0to255() {
        val px = IntArray(plane) { argb(10, 20, 30) }
        px[0] = argb(255, 128, 1)
        px[plane - 1] = argb(7, 8, 9)
        val f = PadCnnMath.packNchwBgrRaw(px)
        assertEquals(3 * plane, f.size)
        // pixel 0
        assertEquals(1f, f[0], 0f)              // B plane
        assertEquals(128f, f[plane], 0f)        // G plane
        assertEquals(255f, f[2 * plane], 0f)    // R plane (raw, NOT /255)
        // pixel 1
        assertEquals(30f, f[1], 0f)
        assertEquals(20f, f[plane + 1], 0f)
        assertEquals(10f, f[2 * plane + 1], 0f)
        // last pixel
        assertEquals(9f, f[plane - 1], 0f)
        assertEquals(8f, f[2 * plane - 1], 0f)
        assertEquals(7f, f[3 * plane - 1], 0f)
    }

    @Test
    fun byteBufferPacking_matchesFloatArray() {
        val px = IntArray(plane) { i -> argb(i % 256, (i * 7) % 256, (i * 13) % 256) }
        val expected = PadCnnMath.packNchwBgrRaw(px)
        val buf = ByteBuffer.allocateDirect(PadCnnMath.INPUT_BYTES).order(ByteOrder.nativeOrder())
        PadCnnMath.packNchwBgrRaw(px, buf)
        assertEquals(0, buf.position())
        val fb = buf.asFloatBuffer()
        for (i in expected.indices) assertEquals("index $i", expected[i], fb.get(i), 0f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun tooFewPixels_throws() {
        PadCnnMath.packNchwBgrRaw(IntArray(10))
    }

    @Test
    fun softmax_sumsToOne_andIsStableForLargeLogits() {
        val p = PadCnnMath.softmax(floatArrayOf(1000f, 1001f, 999f))
        assertEquals(1f, p.sum(), 1e-5f)
        assertTrue(p[1] > p[0] && p[0] > p[2])
    }

    @Test
    fun liveProbability_readsIndexOne() {
        assertEquals(1f / 3f, PadCnnMath.liveProbability(floatArrayOf(0f, 0f, 0f))!!, 1e-6f)
        assertTrue(PadCnnMath.liveProbability(floatArrayOf(-5f, 8f, -5f))!! > 0.99f)
        assertTrue(PadCnnMath.liveProbability(floatArrayOf(8f, -5f, -5f))!! < 0.01f)
        assertTrue(PadCnnMath.liveProbability(floatArrayOf(-5f, -5f, 8f))!! < 0.01f)
    }

    @Test
    fun liveProbability_rejectsBadOutput() {
        assertNull(PadCnnMath.liveProbability(floatArrayOf(1f, 2f)))
        assertNull(PadCnnMath.liveProbability(floatArrayOf(Float.NaN, 0f, 0f)))
        assertNull(PadCnnMath.liveProbability(floatArrayOf(Float.POSITIVE_INFINITY, 0f, 0f)))
    }
}
