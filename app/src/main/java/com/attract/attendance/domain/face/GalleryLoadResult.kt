package com.attract.attendance.domain.face

/**
 * Health statistics of a class template gallery.
 */
data class GalleryHealthSummary(
    val totalEnrolledStudents: Int,
    val studentsWithTemplates: Int,
    val activeTemplatesCount: Int,
    val staleTemplatesCount: Int,
    val malformedTemplatesCount: Int
)

/**
 * Typed gallery load boundary (WP03 / Section 5.2).
 *
 * Ensures storage, crypto, profile mismatch, or corrupt gallery states
 * are strictly distinguished from an ordinary empty gallery or Unknown face.
 */
sealed interface GalleryLoadResult {
    /** Gallery loaded successfully with active, valid templates matching the current profile. */
    data class Ready(
        val profileId: String,
        val version: Long,
        val templates: List<StudentTemplatePair>,
        val healthSummary: GalleryHealthSummary
    ) : GalleryLoadResult

    /** Class has zero enrolled students and zero templates; legitimate clean state for first enrollment. */
    data object EmptyHealthy : GalleryLoadResult

    /** One or more enrolled students have missing/stale/incompatible templates needing repair/re-enrollment. */
    data class NeedsRepair(
        val affectedStudentIds: List<Long>,
        val reason: String
    ) : GalleryLoadResult

    /** Fatal or recoverable system error: Keystore key invalidated, DB corruption, or model profile mismatch. */
    data class Unavailable(
        val errorCategory: String,
        val message: String,
        val cause: Throwable? = null
    ) : GalleryLoadResult
}
