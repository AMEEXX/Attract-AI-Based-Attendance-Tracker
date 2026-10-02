package com.attract.attendance.domain.face

import com.attract.attendance.domain.session.TeacherAuthAction
import com.attract.attendance.domain.session.TeacherAuthorizationGrant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EnrollmentAndDuplicateTest {

    private fun createUnitVector(dim: Int = 512, seed: Float = 1.0f): FloatArray {
        val array = FloatArray(dim) { seed }
        val norm = kotlin.math.sqrt(array.map { it * it }.sum())
        return FloatArray(dim) { array[it] / norm }
    }

    private fun createOrthogonalVector(dim: Int = 512): FloatArray {
        val array = FloatArray(dim) { if (it % 2 == 0) 1.0f else -1.0f }
        val norm = kotlin.math.sqrt(array.map { it * it }.sum())
        return FloatArray(dim) { array[it] / norm }
    }

    @Test
    fun enrollmentValidator_validThreeSamples_passes() {
        val v0 = createUnitVector(512, 1.0f)
        val v1 = createUnitVector(512, 1.05f) // similar to v0 (same person)
        val v2 = createUnitVector(512, 0.95f) // similar to v0 (same person)

        val samples = listOf(
            EnrollmentSample(0, v0, 0.9f, 0f, 1000L),
            EnrollmentSample(1, v1, 0.85f, -25f, 2000L),
            EnrollmentSample(2, v2, 0.85f, 25f, 3000L),
        )

        val grant = TeacherAuthorizationGrant(
            sessionId = 1L,
            classId = 1L,
            studentId = 10L,
            action = TeacherAuthAction.FIRST_ENROLLMENT,
            interactionId = 1L,
            expiresAtMillis = System.currentTimeMillis() + 60_000L
        )

        val result = EnrollmentBatchValidator.validate(10L, 1L, samples, grant)
        assertTrue("Expected valid batch", result is EnrollmentBatchValidator.ValidationResult.Valid)
    }

    @Test
    fun enrollmentValidator_personSwap_rejected() {
        val v0 = createUnitVector(512, 1.0f)
        val v1 = createOrthogonalVector(512) // completely different person!

        val samples = listOf(
            EnrollmentSample(0, v0, 0.9f, 0f, 1000L),
            EnrollmentSample(1, v1, 0.85f, -25f, 2000L),
            EnrollmentSample(2, v0, 0.85f, 25f, 3000L),
        )

        val result = EnrollmentBatchValidator.validate(10L, 1L, samples, null)
        assertTrue("Expected person swap to be rejected", result is EnrollmentBatchValidator.ValidationResult.Invalid)
        assertTrue((result as EnrollmentBatchValidator.ValidationResult.Invalid).reason.contains("Person swap"))
    }

    @Test
    fun enrollmentValidator_reusedFrameTimestamp_rejected() {
        val v0 = createUnitVector(512, 1.0f)
        val samples = listOf(
            EnrollmentSample(0, v0, 0.9f, 0f, 1000L),
            EnrollmentSample(1, v0, 0.85f, -25f, 1000L), // identical timestamp!
            EnrollmentSample(2, v0, 0.85f, 25f, 3000L),
        )

        val result = EnrollmentBatchValidator.validate(10L, 1L, samples, null)
        assertTrue("Expected timestamp reuse to be rejected", result is EnrollmentBatchValidator.ValidationResult.Invalid)
    }

    @Test
    fun enrollmentValidator_insufficientSamples_rejected() {
        val v0 = createUnitVector(512, 1.0f)
        val samples = listOf(
            EnrollmentSample(0, v0, 0.9f, 0f, 1000L),
            EnrollmentSample(1, v0, 0.85f, -25f, 2000L),
        )

        val result = EnrollmentBatchValidator.validate(10L, 1L, samples, null)
        assertTrue("Expected <3 samples to be rejected", result is EnrollmentBatchValidator.ValidationResult.Invalid)
    }

    @Test
    fun duplicateChecker_emptyGallery_returnsClear() {
        val candidate = listOf(createUnitVector(512, 1.0f))
        val result = DuplicateCheckService.checkDuplicate(candidate, emptyList())
        assertEquals(DuplicateResult.Clear, result)
    }

    @Test
    fun duplicateChecker_matchingExistingStudent_returnsSuspicious() {
        val v1 = createUnitVector(512, 1.0f)
        val candidate = listOf(v1)

        val gallery = listOf(
            StudentTemplatePair(studentId = 42L, templateId = 1L, embedding = v1)
        )

        val result = DuplicateCheckService.checkDuplicate(candidate, gallery)
        assertTrue("Expected duplicate to be detected", result is DuplicateResult.Suspicious)
        val suspicious = result as DuplicateResult.Suspicious
        assertEquals(42L, suspicious.existingStudentId)
        assertTrue(suspicious.score >= 0.99f)
    }

    @Test
    fun duplicateChecker_selfExclusionForReEnrollment_returnsClear() {
        val v1 = createUnitVector(512, 1.0f)
        val candidate = listOf(v1)

        val gallery = listOf(
            StudentTemplatePair(studentId = 42L, templateId = 1L, embedding = v1)
        )

        // Exclude student 42L (self)
        val result = DuplicateCheckService.checkDuplicate(candidate, gallery, excludeStudentId = 42L)
        assertEquals(DuplicateResult.Clear, result)
    }
}
