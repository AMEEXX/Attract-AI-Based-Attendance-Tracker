package com.attract.attendance.domain.face

import com.attract.attendance.domain.session.TeacherAuthorizationGrant
import kotlin.math.abs

/**
 * Single accepted observation during guided multi-angle enrollment.
 */
data class EnrollmentSample(
    val slotIndex: Int, // 0 = STRAIGHT, 1 = LEFT, 2 = RIGHT
    val embedding: FloatArray,
    val qualityScore: Float,
    val yawDegrees: Float,
    val timestampNanos: Long,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as EnrollmentSample
        if (slotIndex != other.slotIndex) return false
        if (!embedding.contentEquals(other.embedding)) return false
        if (qualityScore != other.qualityScore) return false
        if (yawDegrees != other.yawDegrees) return false
        if (timestampNanos != other.timestampNanos) return false
        return true
    }

    override fun hashCode(): Int {
        var result = slotIndex
        result = 31 * result + embedding.contentHashCode()
        result = 31 * result + qualityScore.hashCode()
        result = 31 * result + yawDegrees.hashCode()
        result = 31 * result + timestampNanos.hashCode()
        return result
    }
}

/**
 * Approved, validated batch of exactly 3 distinct face observations
 * produced strictly through [EnrollmentBatchValidator].
 */
data class ValidatedEnrollmentBatch(
    val studentId: Long,
    val classId: Long,
    val samples: List<EnrollmentSample>,
    val profileId: String,
    val authorizationGrant: TeacherAuthorizationGrant?
) {
    init {
        require(samples.size == 3) { "Enrollment requires exactly 3 validated samples (STRAIGHT, LEFT, RIGHT)" }
    }
}

/**
 * Result of duplicate check against the class gallery.
 */
sealed interface DuplicateResult {
    data object Clear : DuplicateResult
    data class Suspicious(val existingStudentId: Long, val score: Float) : DuplicateResult
    data class Unavailable(val reason: String) : DuplicateResult
}

/**
 * Typed domain enrollment results.
 */
sealed interface EnrollmentResult {
    data class Committed(
        val studentId: Long,
        val templateIds: List<Long>,
        val attendanceRecordId: Long?,
        val galleryVersion: Long,
    ) : EnrollmentResult

    data class AlreadyEnrolled(val studentId: Long) : EnrollmentResult
    data class DuplicateSuspected(val existingStudentId: Long, val score: Float) : EnrollmentResult
    data class ApprovalExpired(val reason: String) : EnrollmentResult
    data class Ineligible(val reason: String) : EnrollmentResult
    data class InsufficientSamples(val count: Int) : EnrollmentResult
    data class CaptureRejected(val reason: String) : EnrollmentResult
    data class Busy(val message: String) : EnrollmentResult
    data class Failed(val reason: String) : EnrollmentResult
    data object Cancelled : EnrollmentResult
}

/**
 * Domain validator ensuring all 3 enrollment samples meet geometry,
 * temporal diversity, vector quality, and same-person continuity contracts.
 */
object EnrollmentBatchValidator {

    const val SAME_PERSON_CONTINUITY_THRESHOLD = 0.30f
    const val MIN_CAPTURE_YAW_SEPARATION_DEGREES = 8f

    sealed interface ValidationResult {
        data class Valid(val batch: ValidatedEnrollmentBatch) : ValidationResult
        data class Invalid(val reason: String) : ValidationResult
    }

    fun validate(
        studentId: Long,
        classId: Long,
        samples: List<EnrollmentSample>,
        authorizationGrant: TeacherAuthorizationGrant? = null,
        profile: BiometricModelProfile = BiometricModelProfile.CURRENT,
    ): ValidationResult {
        if (samples.size != 3) {
            return ValidationResult.Invalid("Enrollment requires exactly 3 valid samples, got ${samples.size}")
        }

        // 1. Vector dimension, finite checks, and unit-norm checks
        for (sample in samples) {
            if (sample.embedding.size != profile.embeddingDim) {
                return ValidationResult.Invalid("Sample slot ${sample.slotIndex} has wrong dimension ${sample.embedding.size}")
            }
            if (!TemplateCompatibility.isStrictlyUsableVector(sample.embedding)) {
                return ValidationResult.Invalid("Sample slot ${sample.slotIndex} contains non-finite or malformed embedding")
            }
        }

        val straight = samples.find { it.slotIndex == 0 }
            ?: return ValidationResult.Invalid("Missing STRAIGHT sample (slot 0)")
        val left = samples.find { it.slotIndex == 1 }
            ?: return ValidationResult.Invalid("Missing LEFT sample (slot 1)")
        val right = samples.find { it.slotIndex == 2 }
            ?: return ValidationResult.Invalid("Missing RIGHT sample (slot 2)")

        // 2. Relative pose window checks (WP-B / RC-1)
        val deltaLeft = left.yawDegrees - straight.yawDegrees
        val deltaRight = right.yawDegrees - straight.yawDegrees

        if (abs(straight.yawDegrees) > 12f) {
            return ValidationResult.Invalid("STRAIGHT sample yaw must be within [-12°, 12°], got ${straight.yawDegrees}°")
        }
        if (deltaLeft !in -40f..-8f) {
            return ValidationResult.Invalid("LEFT sample relative yaw must be turned left [-40°, -8°], got delta ${deltaLeft}°")
        }
        if (deltaRight !in 8f..40f) {
            return ValidationResult.Invalid("RIGHT sample relative yaw must be turned right [8°, 40°], got delta ${deltaRight}°")
        }

        // 3. Temporal and diversity checks (no identical frames reused)
        val timestamps = samples.map { it.timestampNanos }.toSet()
        if (timestamps.size != 3) {
            return ValidationResult.Invalid("Enrollment frames must be captured at distinct timestamps (no frame reuse)")
        }

        val simLeft = IdentityScorer.cosineSimilarity(straight.embedding, left.embedding)
        val simRight = IdentityScorer.cosineSimilarity(straight.embedding, right.embedding)

        val yawDiverse = abs(deltaLeft) >= 8f && abs(deltaRight) >= 8f
        val embeddingDiverse = simLeft < 0.97f || simRight < 0.97f
        if (!yawDiverse && !embeddingDiverse) {
            return ValidationResult.Invalid("Captured samples lack sufficient angle or embedding diversity")
        }

        // 4. Same-person continuity check across the batch
        if (simLeft < SAME_PERSON_CONTINUITY_THRESHOLD || simRight < SAME_PERSON_CONTINUITY_THRESHOLD) {
            return ValidationResult.Invalid(
                "Same-person continuity check failed (straight-left sim=$simLeft, straight-right sim=$simRight). Person swap suspected."
            )
        }

        return ValidationResult.Valid(
            ValidatedEnrollmentBatch(
                studentId = studentId,
                classId = classId,
                samples = listOf(straight, left, right),
                profileId = profile.profileId,
                authorizationGrant = authorizationGrant
            )
        )
    }
}

/**
 * Shared Duplicate Check Service (WP-C).
 * Evaluates candidate samples against active gallery templates.
 * Takes the maximum similarity over all (candidate x template) pairs to return the best match.
 */
object DuplicateCheckService {

    const val DUPLICATE_DETECTION_THRESHOLD = 0.50f

    fun checkDuplicate(
        candidateSamples: List<FloatArray>,
        galleryTemplates: List<StudentTemplatePair>,
        excludeStudentId: Long? = null,
        threshold: Float = DUPLICATE_DETECTION_THRESHOLD,
    ): DuplicateResult {
        if (galleryTemplates.isEmpty()) {
            return DuplicateResult.Clear
        }

        val filteredTemplates = galleryTemplates.filter { it.studentId != excludeStudentId }
        if (filteredTemplates.isEmpty()) {
            return DuplicateResult.Clear
        }

        var maxScore = -1f
        var maxStudentId: Long? = null

        // Max over all (candidate x template) pairs
        for (candidate in candidateSamples) {
            for (template in filteredTemplates) {
                val score = IdentityScorer.cosineSimilarity(candidate, template.embedding)
                if (score > maxScore) {
                    maxScore = score
                    maxStudentId = template.studentId
                }
            }
        }

        if (maxScore >= threshold && maxStudentId != null) {
            return DuplicateResult.Suspicious(
                existingStudentId = maxStudentId,
                score = maxScore
            )
        }

        return DuplicateResult.Clear
    }
}
