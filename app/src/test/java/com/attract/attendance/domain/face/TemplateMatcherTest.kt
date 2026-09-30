package com.attract.attendance.domain.face

import org.junit.Assert.assertEquals
import org.junit.Test

class TemplateMatcherTest {

    @Test
    fun identicalVectors_returnSimilarityOne() {
        val v1 = floatArrayOf(0.5f, 0.5f, 0.5f, 0.5f)
        val similarity = TemplateMatcher.cosineSimilarity(v1, v1)
        assertEquals(1.0f, similarity, 1e-5f)
    }

    @Test
    fun orthogonalVectors_returnSimilarityZero() {
        val v1 = floatArrayOf(1.0f, 0.0f)
        val v2 = floatArrayOf(0.0f, 1.0f)
        val similarity = TemplateMatcher.cosineSimilarity(v1, v2)
        assertEquals(0.0f, similarity, 1e-5f)
    }

    @Test
    fun findBestMatch_selectsHighestSimilarityCandidate() {
        val target = floatArrayOf(1.0f, 0.0f, 0.0f)
        val c1 = floatArrayOf(0.0f, 1.0f, 0.0f)
        val c2 = floatArrayOf(0.9f, 0.1f, 0.0f) // Best match
        val c3 = floatArrayOf(0.5f, 0.5f, 0.0f)

        val (bestIndex, score) = TemplateMatcher.findBestMatch(target, listOf(c1, c2, c3))

        assertEquals(1, bestIndex)
        assertTrue(score > 0.9f)
    }

    @Test
    fun findBestMatch_emptyCandidates_returnsNegativeOneAndZeroScore() {
        val target = floatArrayOf(1.0f, 0.0f)
        val (bestIndex, score) = TemplateMatcher.findBestMatch(target, emptyList())
        assertEquals(-1, bestIndex)
        assertEquals(0.0f, score, 1e-5f)
    }

    @Test
    fun findBestMatch_tieBreakSelection_selectsFirstOccurrence() {
        val target = floatArrayOf(1.0f, 0.0f)
        val c1 = floatArrayOf(1.0f, 0.0f)
        val c2 = floatArrayOf(1.0f, 0.0f)
        val (bestIndex, score) = TemplateMatcher.findBestMatch(target, listOf(c1, c2))
        assertEquals(0, bestIndex)
        assertEquals(1.0f, score, 1e-5f)
    }
}

private fun assertTrue(condition: Boolean) {
    org.junit.Assert.assertTrue(condition)
}
