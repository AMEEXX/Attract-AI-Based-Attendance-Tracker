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

        // Fail-closed (LLD-11 / R07): malformed/non-finite or degenerate norm query never matches.
        if (!TemplateCompatibility.isStrictlyUsableVector(targetEmbedding)) {
            return RecognitionOutcome.Unknown
        }

        val compatibleTemplates = activeTemplates.filter { it.embedding.size == targetEmbedding.size }
        if (compatibleTemplates.size < activeTemplates.size) {
            logDimensionSkip(activeTemplates.size - compatibleTemplates.size, targetEmbedding.size)
        }

        val ranked = IdentityScorer.scoreGroupedMax(targetEmbedding, compatibleTemplates)
        if (ranked.isEmpty()) {
            return RecognitionOutcome.Unknown
        }

        val evalResult = IdentityScorer.evaluate(ranked, acceptThreshold, ambiguousMargin)
        val decision = when (evalResult) {
            is ScoreEvaluationResult.Match -> RecognitionOutcome.Match(evalResult.studentId, evalResult.score)
            is ScoreEvaluationResult.Ambiguous -> RecognitionOutcome.Ambiguous(
                evalResult.topStudentId, evalResult.topScore, evalResult.secondStudentId, evalResult.secondScore
            )
            is ScoreEvaluationResult.Unknown -> RecognitionOutcome.Unknown
            is ScoreEvaluationResult.EmptyGallery -> RecognitionOutcome.NoTemplatesAvailable
            is ScoreEvaluationResult.MalformedQuery -> RecognitionOutcome.Unknown
        }

        // PHASE-1 DIAGNOSTIC (LLD-11): full decision transparency, JVM-safe.
        run {
            val t1 = ranked.getOrNull(0)
            val t2 = ranked.getOrNull(1)
            val margin = if (t1 != null && t2 != null) t1.bestScore - t2.bestScore else Float.NaN
            val msg = "DECIDE: gallery=${activeTemplates.size} compatible=${compatibleTemplates.size} " +
                "top1=${t1?.studentId}:${t1?.bestScore} top2=${t2?.studentId}:${t2?.bestScore} " +
                "margin=$margin thr=$acceptThreshold margReq=$ambiguousMargin -> $decision"
            try { android.util.Log.i("ATTRACT_RECOGNITION", msg) } catch (_: Throwable) { println("[ATTRACT_RECOGNITION] $msg") }
        }

        return decision
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
