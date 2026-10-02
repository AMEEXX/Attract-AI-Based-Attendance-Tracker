package com.attract.attendance.domain.face

sealed interface RecognitionOutcome {
    data class Match(val studentId: Long, val confidence: Float) : RecognitionOutcome
    data class Ambiguous(
        val topStudentId: Long,
        val topConfidence: Float,
        val secondStudentId: Long,
        val secondConfidence: Float,
    ) : RecognitionOutcome

    data object Unknown : RecognitionOutcome
    data object NoTemplatesAvailable : RecognitionOutcome
}

data class StudentTemplatePair(
    val studentId: Long,
    val templateId: Long,
    val embedding: FloatArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is StudentTemplatePair) return false
        return studentId == other.studentId &&
            templateId == other.templateId &&
            embedding.contentEquals(other.embedding)
    }

    override fun hashCode(): Int {
        var result = studentId.hashCode()
        result = 31 * result + templateId.hashCode()
        result = 31 * result + embedding.contentHashCode()
        return result
    }
}

class RecognitionDecisionEngine(
    /**
     * Calibrated accept threshold for ArcFace 512-D embeddings.
     * Empirical genuine scores min=0.25, mean=0.58; impostor scores max=0.16, mean=-0.05.
     */
    val acceptThreshold: Float = 0.25f,
    /** Calibrated ambiguity margin; separates top candidate from second candidate. */
    val ambiguousMargin: Float = 0.05f,
) {
    fun evaluate(
        targetEmbedding: FloatArray,
        activeTemplates: List<StudentTemplatePair>,
    ): RecognitionOutcome {
        if (activeTemplates.isEmpty()) {
            return RecognitionOutcome.NoTemplatesAvailable
        }

        // Fail-closed (LLD-11): a malformed/NaN query never matches anything.
        if (targetEmbedding.any { !it.isFinite() }) {
            return RecognitionOutcome.Unknown
        }

        // Biometric-format gate (LLD-10 migration amendment): a stored template whose
        // dimension differs from the query embedding belongs to a DIFFERENT model
        // representation (e.g. 32-D prototype vs 192-D MobileFaceNet). It must NEVER
        // reach cosine similarity — such comparisons are meaningless and previously
        // crashed with IllegalArgumentException. Skipped, never accepted.
        val compatibleTemplates = activeTemplates.filter { it.embedding.size == targetEmbedding.size }
        if (compatibleTemplates.size < activeTemplates.size) {
            logDimensionSkip(activeTemplates.size - compatibleTemplates.size, targetEmbedding.size)
        }

        // Compute similarity for each template; non-finite scores (corrupt templates)
        // are excluded rather than compared — NaN would otherwise bypass thresholds
        // because all NaN comparisons are false.
        val scored = compatibleTemplates.mapNotNull { pair ->
            val score = TemplateMatcher.cosineSimilarity(targetEmbedding, pair.embedding)
            if (score.isFinite()) Pair(pair.studentId, score) else null
        }

        // Group by studentId and take maximum score per student
        val studentScores = scored.groupBy { it.first }
            .mapValues { entry -> entry.value.maxOf { it.second } }
            .toList()
            .sortedByDescending { it.second }

        if (studentScores.isEmpty()) {
            return RecognitionOutcome.Unknown
        }

        val (topStudentId, topScore) = studentScores[0]

        if (topScore < acceptThreshold) {
            return RecognitionOutcome.Unknown
        }

        // Check for ambiguity (second best candidate score within margin)
        if (studentScores.size >= 2) {
            val (secondStudentId, secondScore) = studentScores[1]
            if ((topScore - secondScore) < ambiguousMargin) {
                return RecognitionOutcome.Ambiguous(
                    topStudentId = topStudentId,
                    topConfidence = topScore,
                    secondStudentId = secondStudentId,
                    secondConfidence = secondScore,
                )
            }
        }

        val ranked = studentScores

        // PHASE-1 DIAGNOSTIC (LLD-11): full decision transparency, JVM-safe.
        run {
            val t1 = ranked.getOrNull(0)
            val t2 = ranked.getOrNull(1)
            val margin = if (t1 != null && t2 != null) t1.second - t2.second else Float.NaN
            val msg = "DECIDE: gallery=${activeTemplates.size} compatible=${compatibleTemplates.size} " +
                "top1=${t1?.first}:${t1?.second} top2=${t2?.first}:${t2?.second} " +
                "margin=$margin thr=$acceptThreshold margReq=$ambiguousMargin -> MATCH"
            try { android.util.Log.i("ATTRACT_RECOGNITION", msg) } catch (_: Throwable) { println("[ATTRACT_RECOGNITION] $msg") }
        }

        return RecognitionOutcome.Match(studentId = topStudentId, confidence = topScore)
    }

    /** JVM-test-safe warning (android.util.Log is unmocked on the host JVM). */
    private fun logDimensionSkip(skipped: Int, queryDim: Int) {
        val message = "Recognition: $skipped template(s) skipped (dimension mismatch vs $queryDim-D query). Re-enrollment required."
        try {
            android.util.Log.w("ATTRACT_FACE", message)
        } catch (_: Throwable) {
            println("[ATTRACT_FACE] $message")
        }
    }
}
