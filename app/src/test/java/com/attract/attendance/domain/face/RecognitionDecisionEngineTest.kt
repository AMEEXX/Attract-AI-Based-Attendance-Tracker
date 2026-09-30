package com.attract.attendance.domain.face

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionDecisionEngineTest {

    private val engine = RecognitionDecisionEngine(acceptThreshold = 0.75f, ambiguousMargin = 0.08f)

    @Test
    fun noTemplates_returnsNoTemplatesAvailable() {
        val target = floatArrayOf(1.0f, 0.0f)
        val outcome = engine.evaluate(target, emptyList())
        assertEquals(RecognitionOutcome.NoTemplatesAvailable, outcome)
    }

    @Test
    fun highMatch_returnsMatch() {
        val target = floatArrayOf(1.0f, 0.0f)
        val t1 = StudentTemplatePair(studentId = 1L, templateId = 10L, embedding = floatArrayOf(0.98f, 0.02f))
        val t2 = StudentTemplatePair(studentId = 2L, templateId = 20L, embedding = floatArrayOf(0.1f, 0.9f))

        val outcome = engine.evaluate(target, listOf(t1, t2))
        assertTrue(outcome is RecognitionOutcome.Match)
        val match = outcome as RecognitionOutcome.Match
        assertEquals(1L, match.studentId)
    }

    @Test
    fun lowSimilarity_returnsUnknown() {
        val target = floatArrayOf(1.0f, 0.0f)
        val t1 = StudentTemplatePair(studentId = 1L, templateId = 10L, embedding = floatArrayOf(0.5f, 0.5f)) // score ~0.707 < 0.75

        val outcome = engine.evaluate(target, listOf(t1))
        assertEquals(RecognitionOutcome.Unknown, outcome)
    }

    @Test
    fun closeScores_returnsAmbiguous() {
        val target = floatArrayOf(1.0f, 0.0f)
        val t1 = StudentTemplatePair(studentId = 1L, templateId = 10L, embedding = floatArrayOf(0.9f, 0.1f)) // ~0.90
        val t2 = StudentTemplatePair(studentId = 2L, templateId = 20L, embedding = floatArrayOf(0.88f, 0.12f)) // ~0.88 (diff = 0.02 < 0.08)

        val outcome = engine.evaluate(target, listOf(t1, t2))
        assertTrue(outcome is RecognitionOutcome.Ambiguous)
    }
}
