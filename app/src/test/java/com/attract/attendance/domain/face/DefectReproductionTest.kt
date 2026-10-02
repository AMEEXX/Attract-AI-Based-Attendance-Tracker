package com.attract.attendance.domain.face

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * DefectReproductionTest — Reproductions of confirmed architectural and pipeline defects (R01-R09).
 *
 * Demonstrates the exact failure modes identified in the audit before the repairs are implemented.
 */
class DefectReproductionTest {

    private fun unit(idx: Int, dim: Int = 512): FloatArray {
        val v = FloatArray(dim)
        v[idx % dim] = 1f
        return v
    }

    private fun mix(dim: Int, vararg weights: Pair<Int, Float>): FloatArray {
        val v = FloatArray(dim)
        for ((idx, w) in weights) {
            v[idx % dim] = w
        }
        var sum = 0f
        for (x in v) sum += x * x
        val norm = kotlin.math.sqrt(sum)
        if (norm > 0f) {
            for (i in v.indices) v[i] /= norm
        }
        return v
    }

    // -----------------------------------------------------------------------------------------
    // R06 / P0: associate { it.studentId to score } overwrites multiple templates with the LAST one.
    // Grouped maximum retains the highest similarity for each student.
    // -----------------------------------------------------------------------------------------
    @Test
    fun `R06 reproduction - associate keeps last template score while grouped max keeps highest`() {
        val query = unit(0)

        // Student 1L has two templates:
        // Template 1: high similarity (unit(0), similarity = 1.0)
        // Template 2: low similarity (unit(10), similarity = 0.0)
        // Student 2L has one template:
        // Template 1: similarity = 0.60
        val tA1 = StudentTemplatePair(studentId = 1L, templateId = 101L, embedding = unit(0))
        val tA2 = StudentTemplatePair(studentId = 1L, templateId = 102L, embedding = unit(10))
        val tB1 = StudentTemplatePair(studentId = 2L, templateId = 201L, embedding = mix(512, 0 to 0.6f, 1 to 0.8f))

        // Order 1: tA1 then tA2 then tB1
        val templatesOrder1 = listOf(tA1, tA2, tB1)
        val associateScores1 = templatesOrder1.associate {
            it.studentId to TemplateMatcher.cosineSimilarity(query, it.embedding)
        }

        // In Order 1, tA2 (sim = 0.0) overwrote tA1 (sim = 1.0)!
        assertEquals("associate in order 1 keeps last write (0.0)", 0.0f, associateScores1[1L]!!, 0.001f)

        // Order 2: tA2 then tA1 then tB1
        val templatesOrder2 = listOf(tA2, tA1, tB1)
        val associateScores2 = templatesOrder2.associate {
            it.studentId to TemplateMatcher.cosineSimilarity(query, it.embedding)
        }
        // In Order 2, tA1 (sim = 1.0) overwrote tA2 (sim = 0.0)!
        assertEquals("associate in order 2 keeps last write (1.0)", 1.0f, associateScores2[1L]!!, 0.001f)

        // Notice that the scoring outcome of associate is completely dependent on row order!
        assertNotEquals(associateScores1[1L], associateScores2[1L])

        // Correct Grouped Maximum approach:
        fun groupedMaxScores(templates: List<StudentTemplatePair>, q: FloatArray): Map<Long, Float> {
            return templates
                .map { it.studentId to TemplateMatcher.cosineSimilarity(q, it.embedding) }
                .groupBy({ it.first }, { it.second })
                .mapValues { (_, scores) -> scores.maxOrNull() ?: 0f }
        }

        val groupedScores1 = groupedMaxScores(templatesOrder1, query)
        val groupedScores2 = groupedMaxScores(templatesOrder2, query)

        assertEquals("Grouped max is 1.0 regardless of row order", 1.0f, groupedScores1[1L]!!, 0.001f)
        assertEquals("Grouped max is identical across permutations", groupedScores1[1L], groupedScores2[1L])
    }

    // -----------------------------------------------------------------------------------------
    // R04 / P0: Migration deactivating templates leaves students ENROLLED, but active sweep misses them.
    // -----------------------------------------------------------------------------------------
    @Test
    fun `R04 reproduction - active-only sweep misses ENROLLED students with deactivated templates`() {
        data class MockStudent(val id: Long, val name: String, val status: String)
        data class MockTemplate(val id: Long, val studentId: Long, val dim: Int, val active: Boolean)

        val students = listOf(
            MockStudent(1L, "Amit", "ENROLLED"),
            MockStudent(2L, "Bob", "ENROLLED")
        )

        // Suppose migration ran: UPDATE face_templates SET active = 0 WHERE embedding_dim != 512
        // student 1L had a 192-D template, so active is set to false. student 2L has a 512-D template.
        val templates = listOf(
            MockTemplate(1L, 1L, 192, false),
            MockTemplate(2L, 2L, 512, true)
        )

        // The flawed active-only sweep:
        val activeTemplates = templates.filter { it.active }
        val studentsWithActiveTemplates = activeTemplates.map { it.studentId }.toSet()

        // Defect: The sweep only checks students that have rows in activeTemplates!
        // student 1L is NOT in activeTemplates, so 1L is completely missed and left orphaned as ENROLLED
        // without any active templates!
        assertTrue("student 1L has no active templates", !studentsWithActiveTemplates.contains(1L))

        // An all-roster reconciliation MUST check all ENROLLED students against active templates:
        val orphanedStudents = students
            .filter { it.status == "ENROLLED" }
            .filter { !studentsWithActiveTemplates.contains(it.id) }

        assertEquals(1, orphanedStudents.size)
        assertEquals(1L, orphanedStudents.first().id)
        // These orphaned students MUST be marked as REENROLL_REQUIRED!
    }

    // -----------------------------------------------------------------------------------------
    // R07 / P0: Zero-vector, NaN, Inf or incomplete batch validation
    // -----------------------------------------------------------------------------------------
    @Test
    fun `R07 reproduction - zero-vector has zero norm and must be rejected as invalid`() {
        val zeroVector = FloatArray(512) { 0f }
        val nanVector = FloatArray(512) { Float.NaN }

        // A naive check (like vector.size == 512 && vector.all { it.isFinite() }) would pass zeroVector!
        // But cosine similarity with a zero vector has norm = 0, which cannot be matched or enrolled.
        fun isStrictlyUsableVector(v: FloatArray?): Boolean {
            if (v == null || v.size != 512) return false
            var sumSq = 0f
            for (x in v) {
                if (!x.isFinite()) return false
                sumSq += x * x
            }
            // Norm must be positive and near 1.0 (e.g., between 0.8 and 1.2) for unit-normalized embeddings
            val norm = kotlin.math.sqrt(sumSq)
            return norm in 0.8f..1.2f
        }

        assertTrue("Zero vector must be rejected", !isStrictlyUsableVector(zeroVector))
        assertTrue("NaN vector must be rejected", !isStrictlyUsableVector(nanVector))
        assertTrue("Unit vector must be accepted", isStrictlyUsableVector(unit(0, 512)))
    }

    // -----------------------------------------------------------------------------------------
    // R05 / P0: AdaptiveVerificationEngine finished state cannot accept new submissions
    // -----------------------------------------------------------------------------------------
    @Test
    fun `R05 reproduction - finished engine throws IllegalStateException if called before reset`() {
        val decisionEngine = RecognitionDecisionEngine(
            acceptThreshold = 0.25f,
            ambiguousMargin = 0.05f
        )
        val engine = AdaptiveVerificationEngine(
            decisionEngine = decisionEngine,
            templates = listOf(StudentTemplatePair(studentId = 1L, templateId = 101L, embedding = unit(0))),
            maxFrames = 1
        )

        val signals = FaceQualitySignals(
            faceCount = 1,
            yawDegrees = 0f,
            pitchDegrees = 0f,
            rollDegrees = 0f,
            leftEyeOpenProbability = 0.95f,
            rightEyeOpenProbability = 0.95f,
            faceRatio = 0.25f,
            centerX = 0.5f,
            centerY = 0.5f,
            blurVariance = 500f,
            brightness = 120f
        )

        val obs = FrameObservation(
            signals = signals,
            quality = FaceQualityEngine.evaluate(signals, FaceQualityConfig.calibrationDefaults()),
            liveness = LivenessEngine.check(signals, null, PresentationAttackSignals.Unavailable),
            embedding = unit(0)
        )

        val step1 = engine.submit(obs)
        assertTrue(step1 is AdaptiveVerificationEngine.Step.Final)

        // If the UI exposes READY before creating a new engine or resetting,
        // a quick second tap calls submit on the finished engine and throws!
        try {
            engine.submit(obs)
            fail("Expected IllegalStateException on finished engine")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("already finished") == true)
        }
    }

    // -----------------------------------------------------------------------------------------
    // R01 / P0: Candidate list exposing enrolled students vs NOT_ENROLLED only
    // -----------------------------------------------------------------------------------------
    @Test
    fun `R01 reproduction - candidate list for enrollment must be NOT_ENROLLED only`() {
        data class Student(val id: Long, val name: String, val enrollmentState: String)
        val students = listOf(
            Student(1L, "Amit", "ENROLLED"),
            Student(2L, "Bob", "NOT_ENROLLED"),
            Student(3L, "Charlie", "ENROLLED")
        )
        val presentIds = setOf(1L)

        // The flawed UI filter (line 314-327):
        val flawedCandidates = students.filter { it.id !in presentIds }
        // Charlie is ENROLLED, but not yet present. The flawed filter includes Charlie!
        assertTrue(flawedCandidates.any { it.name == "Charlie" })

        // The required repaired filter:
        val repairedCandidates = students.filter { it.enrollmentState == "NOT_ENROLLED" }
        assertEquals(1, repairedCandidates.size)
        assertEquals("Bob", repairedCandidates.first().name)
    }

    // -----------------------------------------------------------------------------------------
    // R03 / P0: Duplicate identity protection
    // -----------------------------------------------------------------------------------------
    @Test
    fun `R03 reproduction - candidate identical to existing student must be flagged as duplicate`() {
        val enrolledStudentEmbedding = unit(5)
        val existingGallery = listOf(
            StudentTemplatePair(studentId = 1L, templateId = 101L, embedding = enrolledStudentEmbedding)
        )

        val candidateEmbedding = unit(5) // Same face!
        val candidateStudentId = 2L // Trying to enroll as another student!

        // Checking candidate against gallery:
        val maxSim = existingGallery
            .filter { it.studentId != candidateStudentId }
            .maxOfOrNull { TemplateMatcher.cosineSimilarity(candidateEmbedding, it.embedding) } ?: 0f

        val duplicateThreshold = 0.50f // Duplicate sensitivity threshold
        val isDuplicate = maxSim >= duplicateThreshold

        assertTrue("Identical face must be detected as duplicate", isDuplicate)
    }
}
