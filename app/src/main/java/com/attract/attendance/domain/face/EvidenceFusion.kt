package com.attract.attendance.domain.face

/**
 * Evidence fusion for multi-frame verification (LLD-16).
 *
 * TEST-ONLY strategy exploration layer: all candidate strategies are implemented here as
 * pure functions so the LFW benchmark can compare them on identical inputs. Production
 * currently wires ONLY [EvidenceFusion.conservativeBestFrame]; switching production to a
 * different strategy requires empirical benchmark approval (see docs/16-lld-…).
 *
 * Safety invariants every strategy MUST uphold:
 *  1. Additional frames never weaken an already-safe single-frame Match.
 *  2. Confident Matches to DIFFERENT identities across frames are a conflict -> Ambiguous,
 *     never an arbitrary acceptance.
 *  3. Unusable frames (quality/liveness/embedding failure) contribute no biometric evidence.
 *  4. Static replays count as ONE observation, never independent evidence.
 */
interface EvidenceFusionStrategy {
    val name: String

    /**
     * @param frames per-frame analyses in capture order; only [EvidenceFusionStrategy.FrameAnalysis.usable]
     *               entries carry scores.
     */
    fun fuse(
        frames: List<EvidenceFusionStrategy.FrameAnalysis>,
        templates: List<StudentTemplatePair>,
        decisionEngine: RecognitionDecisionEngine,
    ): FusedOutcome

    /** Per-frame ranking + decision, computed once by the adaptive engine. */
    data class FrameAnalysis(
        val observation: FrameObservation,
        val usable: Boolean,
        /** studentId -> cosine similarity against that frame's embedding (usable frames only). */
        val scores: Map<Long, Float> = emptyMap(),
        val decision: RecognitionOutcome? = null,
    )
}

sealed interface FusedOutcome {
    val framesUsed: Int

    data class Match(
        val studentId: Long,
        val confidence: Float,
        override val framesUsed: Int,
    ) : FusedOutcome

    data class Ambiguous(
        val topStudentId: Long,
        val topConfidence: Float,
        val secondStudentId: Long,
        val secondConfidence: Float,
        override val framesUsed: Int,
    ) : FusedOutcome

    data class Unknown(override val framesUsed: Int) : FusedOutcome
}

object EvidenceFusion {

    // ------------------------------------------------------------------
    // A/D — best individual frame / max template similarity.
    // Per-student MAX similarity across frames, then standard decision rules.
    // ------------------------------------------------------------------
    fun bestIndividualFrameScore(): EvidenceFusionStrategy = object : EvidenceFusionStrategy {
        override val name = "A_BEST_FRAME"
        override fun fuse(
            frames: List<EvidenceFusionStrategy.FrameAnalysis>,
            templates: List<StudentTemplatePair>,
            decisionEngine: RecognitionDecisionEngine,
        ): FusedOutcome {
            val usable = frames.filter { it.usable }
            if (usable.isEmpty()) return FusedOutcome.Unknown(frames.size)
            val merged = mutableMapOf<Long, Float>()
            for (f in usable) f.scores.forEach { (id, s) -> merged[id] = maxOf(merged[id] ?: Float.NEGATIVE_INFINITY, s) }
            return decideFromMerged(merged, frames.size, decisionEngine)
        }
    }

    // ------------------------------------------------------------------
    // B — average identity score across frames.
    // ------------------------------------------------------------------
    fun averageIdentityScore(): EvidenceFusionStrategy = object : EvidenceFusionStrategy {
        override val name = "B_AVG_IDENTITY"
        override fun fuse(
            frames: List<EvidenceFusionStrategy.FrameAnalysis>,
            templates: List<StudentTemplatePair>,
            decisionEngine: RecognitionDecisionEngine,
        ): FusedOutcome {
            val usable = frames.filter { it.usable }
            if (usable.isEmpty()) return FusedOutcome.Unknown(frames.size)
            val sums = mutableMapOf<Long, Float>()
            for (f in usable) f.scores.forEach { (id, s) -> sums[id] = (sums[id] ?: 0f) + s }
            val averaged = sums.mapValues { it.value / usable.size }
            return decideFromMerged(averaged, frames.size, decisionEngine)
        }
    }

    // ------------------------------------------------------------------
    // C — quality-weighted average identity score.
    // ------------------------------------------------------------------
    fun weightedAverageIdentityScore(): EvidenceFusionStrategy = object : EvidenceFusionStrategy {
        override val name = "C_WEIGHTED_AVG"
        override fun fuse(
            frames: List<EvidenceFusionStrategy.FrameAnalysis>,
            templates: List<StudentTemplatePair>,
            decisionEngine: RecognitionDecisionEngine,
        ): FusedOutcome {
            val usable = frames.filter { it.usable }
            if (usable.isEmpty()) return FusedOutcome.Unknown(frames.size)
            val weights = usable.map { f ->
                (f.observation.quality as QualityResult.Accepted).score.coerceIn(0f, 1f)
            }
            val totalWeight = weights.sum()
            if (totalWeight <= 0f) return bestIndividualFrameScore().fuse(frames, templates, decisionEngine)
            val sums = mutableMapOf<Long, Float>()
            usable.forEachIndexed { i, f ->
                f.scores.forEach { (id, s) -> sums[id] = (sums[id] ?: 0f) + s * weights[i] }
            }
            val averaged = sums.mapValues { it.value / totalWeight }
            return decideFromMerged(averaged, frames.size, decisionEngine)
        }
    }

    // ------------------------------------------------------------------
    // E — majority identity among confident/lean frames; margin re-checked on the
    // agreeing subset. No majority -> Ambiguous between the two most-voted ids.
    // ------------------------------------------------------------------
    fun majorityIdentity(): EvidenceFusionStrategy = object : EvidenceFusionStrategy {
        override val name = "E_MAJORITY"
        override fun fuse(
            frames: List<EvidenceFusionStrategy.FrameAnalysis>,
            templates: List<StudentTemplatePair>,
            decisionEngine: RecognitionDecisionEngine,
        ): FusedOutcome {
            val usable = frames.filter { it.usable }
            if (usable.isEmpty()) return FusedOutcome.Unknown(frames.size)
            val winners = usable.mapNotNull { f ->
                (f.decision as? RecognitionOutcome.Match)?.studentId
                    ?: (f.decision as? RecognitionOutcome.Ambiguous)?.topStudentId
            }
            val counts = winners.groupingBy { each -> each }.eachCount()
            val top = counts.maxByOrNull { it.value } ?: return FusedOutcome.Unknown(frames.size)
            val runnerUp = counts.entries.filter { it.key != top.key }.maxByOrNull { it.value }?.key
                ?: top.key
            if (top.value <= (runnerUpVotes(counts, top.key))) {
                return FusedOutcome.Ambiguous(top.key, bestScoreFor(usable, top.key),
                    runnerUp, bestScoreFor(usable, runnerUp), frames.size)
            }
            val agreeFrames = usable.filter { f ->
                ((f.decision as? RecognitionOutcome.Match)?.studentId == top.key) ||
                    ((f.decision as? RecognitionOutcome.Ambiguous)?.topStudentId == top.key)
            }
            val merged = mutableMapOf<Long, Float>()
            for (f in agreeFrames) f.scores.forEach { (id, s) -> merged[id] = maxOf(merged[id] ?: Float.NEGATIVE_INFINITY, s) }
            return decideFromMerged(merged, frames.size, decisionEngine)
        }

        private fun runnerUpVotes(counts: Map<Long, Int>, exclude: Long): Int =
            counts.entries.filter { it.key != exclude }.maxOfOrNull { it.value } ?: 0
    }

    // ------------------------------------------------------------------
    // F — all-frame identity consistency: every confident frame must agree.
    // Falls back to A when no frame is confident at all.
    // ------------------------------------------------------------------
    fun allFrameConsistency(): EvidenceFusionStrategy = object : EvidenceFusionStrategy {
        override val name = "F_ALL_CONSISTENT"
        override fun fuse(
            frames: List<EvidenceFusionStrategy.FrameAnalysis>,
            templates: List<StudentTemplatePair>,
            decisionEngine: RecognitionDecisionEngine,
        ): FusedOutcome {
            val matches = frames.mapNotNull { it.decision as? RecognitionOutcome.Match }
            val distinctMatchedIds = matches.map { it.studentId }.distinct()
            if (matches.isNotEmpty()) {
                if (distinctMatchedIds.size == 1) {
                    return FusedOutcome.Match(distinctMatchedIds.single(), matches.maxOf { it.confidence }, frames.size)
                }
                // Conflicting identities -> fail closed.
                val first = matches.first()
                val conflicting = matches.first { it.studentId != first.studentId }
                return FusedOutcome.Ambiguous(first.studentId, first.confidence, conflicting.studentId, conflicting.confidence, frames.size)
            }
            return bestIndividualFrameScore().fuse(frames, templates, decisionEngine)
        }
    }

    // ------------------------------------------------------------------
    // G — cross-frame embedding agreement: mean embedding then one decision.
    // ------------------------------------------------------------------
    fun crossFrameEmbeddingAgreement(): EvidenceFusionStrategy = object : EvidenceFusionStrategy {
        override val name = "G_EMBED_AGREEMENT"
        override fun fuse(
            frames: List<EvidenceFusionStrategy.FrameAnalysis>,
            templates: List<StudentTemplatePair>,
            decisionEngine: RecognitionDecisionEngine,
        ): FusedOutcome {
            val embeddings = frames.filter { it.usable }.mapNotNull { it.observation.embedding }
            if (embeddings.isEmpty()) return FusedOutcome.Unknown(frames.size)
            val mean = EmbeddingEngine.combineEmbeddings(embeddings)
            val outcome = decisionEngine.evaluate(mean, templates)
            return when (outcome) {
                is RecognitionOutcome.Match -> FusedOutcome.Match(outcome.studentId, outcome.confidence, frames.size)
                is RecognitionOutcome.Ambiguous -> FusedOutcome.Ambiguous(
                    outcome.topStudentId, outcome.topConfidence, outcome.secondStudentId, outcome.secondConfidence, frames.size)
                else -> FusedOutcome.Unknown(frames.size)
            }
        }
    }

    // ------------------------------------------------------------------
    // PRODUCTION DEFAULT — conservative best-frame fusion.
    //  * Any confident frame-level Match stands unless another frame confidently
    //    matches a DIFFERENT identity (conflict -> Ambiguous).
    //  * Otherwise falls back to merged max-similarity decision rules (strategy A).
    // Guarantees parity with today's single-frame security for frame-1 accepts.
    // ------------------------------------------------------------------
    fun conservativeBestFrame(): EvidenceFusionStrategy = object : EvidenceFusionStrategy {
        override val name = "PROD_CONSERVATIVE"
        override fun fuse(
            frames: List<EvidenceFusionStrategy.FrameAnalysis>,
            templates: List<StudentTemplatePair>,
            decisionEngine: RecognitionDecisionEngine,
        ): FusedOutcome {
            val matches = frames.mapNotNull { it.decision as? RecognitionOutcome.Match }
            val distinctIds = matches.map { it.studentId }.distinct()
            if (matches.isNotEmpty()) {
                return if (distinctIds.size == 1) {
                    FusedOutcome.Match(distinctIds.single(), matches.maxOf { it.confidence }, frames.size)
                } else {
                    val first = matches.first()
                    val conflicting = matches.first { it.studentId != first.studentId }
                    FusedOutcome.Ambiguous(first.studentId, first.confidence, conflicting.studentId, conflicting.confidence, frames.size)
                }
            }
            return bestIndividualFrameScore().fuse(frames, templates, decisionEngine)
        }
    }

    fun allStrategies(): List<EvidenceFusionStrategy> = listOf(
        bestIndividualFrameScore(),
        averageIdentityScore(),
        weightedAverageIdentityScore(),
        majorityIdentity(),
        allFrameConsistency(),
        crossFrameEmbeddingAgreement(),
        conservativeBestFrame(),
    )

    // ------------------------- shared helpers -------------------------

    private fun bestScoreFor(frames: List<EvidenceFusionStrategy.FrameAnalysis>, id: Long): Float =
        frames.maxOfOrNull { it.scores[id] ?: Float.NEGATIVE_INFINITY }?.takeUnless { it == Float.NEGATIVE_INFINITY } ?: 0f

    /** Applies the UNCHANGED production accept/margin semantics to merged scores. */
    private fun decideFromMerged(
        merged: Map<Long, Float>,
        framesUsed: Int,
        decisionEngine: RecognitionDecisionEngine,
    ): FusedOutcome {
        val ranked = merged.entries.sortedByDescending { it.value }
        val top = ranked.getOrNull(0) ?: return FusedOutcome.Unknown(framesUsed)
        if (top.value < decisionEngine.acceptThreshold) return FusedOutcome.Unknown(framesUsed)
        val second = ranked.getOrNull(1)
        if (second != null && top.value - second.value < decisionEngine.ambiguousMargin) {
            return FusedOutcome.Ambiguous(top.key, top.value, second.key, second.value, framesUsed)
        }
        return FusedOutcome.Match(top.key, top.value, framesUsed)
    }
}
