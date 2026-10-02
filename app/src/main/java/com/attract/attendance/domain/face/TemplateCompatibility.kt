package com.attract.attendance.domain.face

/**
 * Biometric template compatibility / migration gate (LLD-10 amendment, LLD-16 diagnostics).
 *
 * ROOT CAUSE (production phone, 2026-08): a template enrolled by an EARLIER build whose
 * model emitted 32-D embeddings was loaded by the current 192-D MobileFaceNet build and
 * reached TemplateMatcher.cosineSimilarity -> IllegalArgumentException (192 vs 32).
 * Both builds stamped modelVersion="v1", so the version string alone could not detect the
 * mismatch. This object is the single authority for embedding-format compatibility.
 *
 * Rules:
 *  - A template whose dimension differs from [CURRENT_EMBEDDING_DIM] is STALE. It must be
 *    filtered out BEFORE TemplateMatcher and can only be fixed by RE-ENROLLMENT.
 *  - NEVER truncate, pad, or mathematically convert between representations — different
 *    models produce non-equivalent biometric spaces.
 *  - Fail closed: stale/malformed templates are ignored for recognition, never accepted.
 */
object TemplateCompatibility {

    /** Dimensionality produced by the current production model profile. */
    val CURRENT_EMBEDDING_DIM: Int = EmbeddingEngine.EMBEDDING_SIZE // 512

    /**
     * Distinguishes model profiles. Encodes model name and dimension.
     */
    const val CURRENT_MODEL_ID = "arcface_512d_v3"

    data class ModelProfile(val modelId: String, val embeddingDim: Int)

    fun currentProfile() = ModelProfile(CURRENT_MODEL_ID, CURRENT_EMBEDDING_DIM)

    /** A query/template vector usable for cosine similarity against current-model data. */
    fun isUsableVector(vector: FloatArray?): Boolean =
        vector != null && vector.size == CURRENT_EMBEDDING_DIM && vector.all { it.isFinite() }

    /** Malformed but not merely stale: wrong size OR non-finite values. */
    fun classify(vector: FloatArray?): VectorClass = when {
        vector == null -> VectorClass.MISSING
        vector.size != CURRENT_EMBEDDING_DIM -> VectorClass.STALE_DIMENSION
        vector.any { !it.isFinite() } -> VectorClass.MALFORMED
        else -> VectorClass.CURRENT
    }

    enum class VectorClass { MISSING, STALE_DIMENSION, MALFORMED, CURRENT }

    /**
     * Splits recognition candidates into usable vs incompatible.
     * Incompatible templates NEVER reach TemplateMatcher.
     */
    fun partition(
        templates: List<StudentTemplatePair>,
    ): Partitioned {
        val usable = mutableListOf<StudentTemplatePair>()
        val stale = mutableListOf<StudentTemplatePair>()
        val malformed = mutableListOf<StudentTemplatePair>()
        for (t in templates) {
            when (classify(t.embedding)) {
                VectorClass.CURRENT -> usable.add(t)
                VectorClass.STALE_DIMENSION -> stale.add(t)
                else -> malformed.add(t)
            }
        }
        return Partitioned(usable, stale, malformed)
    }

    data class Partitioned(
        val usable: List<StudentTemplatePair>,
        val stale: List<StudentTemplatePair>,
        val malformed: List<StudentTemplatePair>,
    ) {
        val hasIncompatible: Boolean get() = stale.isNotEmpty() || malformed.isNotEmpty()
    }

    /**
     * Validates embeddings offered for ENROLLMENT: enrollment must always persist the
     * current format; anything else is rejected before any DB write.
     */
    fun validateEnrollment(embeddings: List<FloatArray>): EnrollmentValidation {
        if (embeddings.isEmpty()) return EnrollmentValidation.Rejected("No enrollment observations.")
        val bad = embeddings.withIndex().filter { classify(it.value) != VectorClass.CURRENT }
        return if (bad.isEmpty()) {
            EnrollmentValidation.Ok(embeddings.map { it.size }.distinct().single())
        } else {
            val (i, v) = bad.first()
            EnrollmentValidation.Rejected(
                "Observation #$i is not in the current biometric format " +
                    "(dim=${v?.size}, expected=$CURRENT_EMBEDDING_DIM). Re-capture required.",
            )
        }
    }

    sealed interface EnrollmentValidation {
        data class Ok(val dim: Int) : EnrollmentValidation
        data class Rejected(val reason: String) : EnrollmentValidation
    }
}
