package com.attract.attendance.domain.face

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

class EmbeddingEngineTest {

    @Test
    fun embeddingSizeIs512() {
        assertEquals(512, EmbeddingEngine.EMBEDDING_SIZE)
    }

    @Test
    fun l2NormalizeProducesUnitLength() {
        val raw = FloatArray(512) { it.toFloat() + 1f }
        val normalized = EmbeddingEngine.l2Normalize(raw)

        assertEquals(512, normalized.size)
        var sumSquares = 0f
        for (v in normalized) {
            sumSquares += v * v
        }
        val norm = sqrt(sumSquares)
        assertTrue("Norm must be approximately 1.0, was $norm", abs(norm - 1.0f) < 1e-5f)
    }

    @Test
    fun l2NormalizeHandlesZeroVectorGracefully() {
        val zero = FloatArray(512) { 0f }
        val result = EmbeddingEngine.l2Normalize(zero)
        assertEquals(512, result.size)
        assertTrue(result.all { it == 0f })
    }

    @Test
    fun combineEmbeddingsAveragesAndNormalizes() {
        val v1 = FloatArray(512) { if (it == 0) 1f else 0f }
        val v2 = FloatArray(512) { if (it == 1) 1f else 0f }
        val combined = EmbeddingEngine.combineEmbeddings(listOf(v1, v2))

        assertEquals(512, combined.size)
        var sumSquares = 0f
        for (v in combined) {
            sumSquares += v * v
        }
        val norm = sqrt(sumSquares)
        assertTrue("Combined norm must be 1.0, was $norm", abs(norm - 1.0f) < 1e-5f)
        assertTrue(abs(combined[0] - combined[1]) < 1e-5f)
    }

    @Test
    fun combineEmbeddingsEmptyListReturnsZeroVector() {
        val result = EmbeddingEngine.combineEmbeddings(emptyList())
        assertEquals(512, result.size)
        assertTrue(result.all { it == 0f })
    }
}
