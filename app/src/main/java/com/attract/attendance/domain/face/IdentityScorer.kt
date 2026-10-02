package com.attract.attendance.domain.face

/**
 * Scored result for an individual student.
 */
data class ScoredCandidate(
    val studentId: Long,
    val bestScore: Float,
    val bestTemplateId: Long? = null
)

/**
 * High-level evaluation outcome derived from grouped-maximum scoring.
 */
sealed interface ScoreEvaluationResult {
    data class Match(
        val studentId: Long,
        val score: Float,
        val margin: Float,
        val secondStudentId: Long? = null,
        val secondScore: Float? = null
    ) : ScoreEvaluationResult

    data class Ambiguous(
        val topStudentId: Long,
        val topScore: Float,
        val secondStudentId: Long,
        val secondScore: Float
    ) : ScoreEvaluationResult

    data class Unknown(
        val topScore: Float? = null,
        val topStudentId: Long? = null
    ) : ScoreEvaluationResult

    data object EmptyGallery : ScoreEvaluationResult
    data class MalformedQuery(val reason: String) : ScoreEvaluationResult
}

/**
 * Shared Grouped-Maximum Identity Scorer (WP02 / R06).
 *
 * Implements the single authority scoring contract:
 *  - score(query, student) = max(cosine(query, valid template) for that student)
 *  - rank by descending score, then stable student ID tie-breaking
 *  - B = top score; S = next DISTINCT student score
 *  - accept only if quality + liveness + profile + B threshold + margin all pass
 *  - exact/near ties => Ambiguous, never choose by insertion order
 *  - zero / NaN / Inf vectors rejected
 */
object IdentityScorer {

    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float =
        TemplateMatcher.cosineSimilarity(a, b)

    /**
     * Scores a query embedding against a list of candidate templates using grouped-maximum.
     * Guaranteed to be permutation-invariant (order of templates does not affect rankings).
     * Tie-breaking is deterministic by studentId.
     */
    fun scoreGroupedMax(
        query: FloatArray,
        templates: List<StudentTemplatePair>
    ): List<ScoredCandidate> {
        if (!TemplateCompatibility.isStrictlyUsableVector(query)) {
            return emptyList()
        }

        // Filter for compatible, strictly usable vectors with matching dimension
        val usable = templates.filter {
            it.embedding.size == query.size &&
                TemplateCompatibility.isStrictlyUsableVector(it.embedding, query.size)
        }
        if (usable.isEmpty()) return emptyList()

        // Group by studentId and record maximum similarity score per student
        val studentMap = mutableMapOf<Long, Pair<Float, Long>>() // studentId -> (maxScore, templateId)

        for (t in usable) {
            val sim = TemplateMatcher.cosineSimilarity(query, t.embedding)
            if (!sim.isFinite()) continue
            val existing = studentMap[t.studentId]
            if (existing == null || sim > existing.first) {
                studentMap[t.studentId] = Pair(sim, t.templateId)
            }
        }

        // Rank by descending score, then ascending studentId for stable tie-breaking
        return studentMap.map { (studentId, pair) ->
            ScoredCandidate(studentId, pair.first, pair.second)
        }.sortedWith(
            compareByDescending<ScoredCandidate> { it.bestScore }
                .thenBy { it.studentId }
        )
    }

    /**
     * Evaluates ranked candidates against accept threshold and ambiguity margin.
     */
    fun evaluate(
        ranked: List<ScoredCandidate>,
        acceptThreshold: Float = BiometricModelProfile.CURRENT.acceptThreshold,
        ambiguousMargin: Float = BiometricModelProfile.CURRENT.ambiguousMargin
    ): ScoreEvaluationResult {
        if (ranked.isEmpty()) return ScoreEvaluationResult.EmptyGallery

        val top = ranked[0]
        if (top.bestScore < acceptThreshold) {
            return ScoreEvaluationResult.Unknown(top.bestScore, top.studentId)
        }

        if (ranked.size >= 2) {
            val second = ranked[1]
            val margin = top.bestScore - second.bestScore
            if (margin < ambiguousMargin) {
                return ScoreEvaluationResult.Ambiguous(
                    topStudentId = top.studentId,
                    topScore = top.bestScore,
                    secondStudentId = second.studentId,
                    secondScore = second.bestScore
                )
            }
            return ScoreEvaluationResult.Match(
                studentId = top.studentId,
                score = top.bestScore,
                margin = margin,
                secondStudentId = second.studentId,
                secondScore = second.bestScore
            )
        }

        // Single candidate case
        return ScoreEvaluationResult.Match(
            studentId = top.studentId,
            score = top.bestScore,
            margin = top.bestScore,
            secondStudentId = null,
            secondScore = null
        )
    }
}
