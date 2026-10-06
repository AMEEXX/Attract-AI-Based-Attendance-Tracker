package com.attract.attendance.domain.face

/**
 * Adaptive 1→2→3 frame biometric verification engine (LLD-16).
 *
 * Replaces the mandatory STRAIGHT→LEFT→RIGHT transaction philosophy:
 *
 *   ONE excellent frontal observation
 *     → if the production decision logic is already confident: ACCEPT immediately.
 *   If uncertain → capture a second independent observation and fuse evidence.
 *   If still insufficient → a third (final) observation, then final fusion.
 *
 * Reuses existing production semantics unchanged:
 *  - FaceQualityEngine / LivenessEngine verdicts are produced by the caller per frame.
 *  - RecognitionDecisionEngine (accept=0.45, margin=0.10 — UNCHANGED) scores each frame.
 *  - Evidence fusion defaults to [EvidenceFusion.conservativeBestFrame]; alternative
 *    strategies exist for the test-only benchmark.
 *
 * Safety invariants (see LLD-16):
 *  S1. A confident single-frame Match is FINAL — later bad frames can never weaken it.
 *  S2. Confident Matches to different identities across frames => Ambiguous (fail closed).
 *  S3. Unusable frames contribute no biometric evidence; they only consume capture budget.
 *  S4. Static replays / near-identical captures count as ONE observation, never as extra
 *      independent evidence.
 *  S5. Pose remains a validity check only: extreme yaw/pitch/roll frames are unusable, but
 *      specific profile poses are no longer required for acceptance.
 *  S6. Hopelessness fail-fast: if the FIRST biometrically usable frame's best gallery score is
 *      below notFoundCeiling, the transaction ends Unknown immediately — no further captures
 *      are requested. Borderline frames (>= ceiling) keep the full 1→2→3 budget.
 */
class AdaptiveVerificationEngine(
    private val templates: List<StudentTemplatePair>,
    private val decisionEngine: RecognitionDecisionEngine = RecognitionDecisionEngine(),
    private val fusionStrategy: EvidenceFusionStrategy = EvidenceFusion.conservativeBestFrame(),
    private val config: FaceQualityConfig = FaceQualityConfig.calibrationDefaults(),
    /** Maximum observations in one verification transaction. */
    private val maxFrames: Int = DEFAULT_MAX_FRAMES,
) {

    init {
        require(maxFrames in 1..3) { "Adaptive verification supports 1..3 frames (got $maxFrames)." }
    }

    /** Immutable snapshot of what happened so far in this transaction. */
    data class Diagnostics(
        val framesSubmitted: Int = 0,
        val usableFrames: Int = 0,
        val rejectedFrames: Int = 0,
        val staticReplays: Int = 0,
        val identityConflicts: Int = 0,
        val crossFrameEmbeddingSimilarity: Float? = null,
        val lastOutcome: FusedOutcome? = null,
        val failFastTriggered: Boolean = false,
    )

    sealed interface Step {
        /** More evidence needed — keep capturing. [reason] is a UI-prompt hint. */
        data class NeedMoreFrames(val framesSubmitted: Int, val reason: String) : Step

        /** Terminal result of the transaction. */
        data class Final(val outcome: Outcome) : Step
    }

    sealed interface Outcome {
        val framesUsed: Int

        data class Match(
            val studentId: Long,
            val confidence: Float,
            override val framesUsed: Int,
        ) : Outcome

        data object Ambiguous : Outcome {
            override val framesUsed: Int get() = -1
        }

        data object Unknown : Outcome {
            override val framesUsed: Int get() = -1
        }

        /** Recoverable failure (model fault etc.) — maps onto existing Error semantics. */
        data class Error(val message: String) : Outcome {
            override val framesUsed: Int get() = -1
        }
    }

    private val analyses = mutableListOf<EvidenceFusionStrategy.FrameAnalysis>()
    private var finished = false
    private var conflictCount = 0
    private var replayCount = 0
    private var lastCrossFrameSim: Float? = null
    private var failFast = false

    val diagnostics: Diagnostics
        get() = Diagnostics(
            framesSubmitted = analyses.size,
            usableFrames = analyses.count { it.usable },
            rejectedFrames = analyses.count { !it.usable },
            staticReplays = replayCount,
            identityConflicts = conflictCount,
            crossFrameEmbeddingSimilarity = lastCrossFrameSim,
            failFastTriggered = failFast,
        )

    val isFinished: Boolean get() = finished

    /**
     * Submits the next observation. Returns either [Step.NeedMoreFrames] or [Step.Final].
     * Callers must stop submitting after any [Step.Final].
     */
    fun submit(observation: FrameObservation): Step {
        check(!finished) { "Verification transaction already finished; start a new engine instance." }
        check(analyses.size < maxFrames) {
            "AdaptiveVerificationEngine budget exhausted; start a new transaction."
        }

        // ---- Model-fault guard: an embedding of wrong dimension/NaN is a recoverable ERROR,
        // not a recognition failure (test matrix #17).
        val embeddingMalformed = observation.embedding != null &&
            (!observation.hasValidEmbedding || !TemplateCompatibility.isStrictlyUsableVector(observation.embedding))
        if (embeddingMalformed) {
            finished = true
            return Step.Final(Outcome.Error("Embedding malformed (dimension/NaN/degenerate norm)"))
        }

        // ---- Static replay detection (S4): never counts as new independent evidence.
        val previous = analyses.lastOrNull()?.observation
        val isReplay = previous != null && observation.looksLikeReplayOf(previous)

        // ---- Usability: quality + liveness + embedding gates must ALL pass independently.
        val usable = observation.isBiometricallyUsable && !(isReplay && analyses.any { it.usable })

        // Cross-frame embedding agreement tracking (diagnostics + consistency tests).
        if (usable && observation.embedding != null) {
            val prevEmb = analyses.lastOrNull { it.usable }?.observation?.embedding
            if (prevEmb != null) {
                lastCrossFrameSim = TemplateMatcher.cosineSimilarity(observation.embedding, prevEmb)
            }
        }
        if (isReplay) replayCount++

        val analysis = if (usable) {
            // Biometric-format gate (LLD-10 / R06): only templates in the SAME representation
            // as the query may be scored; grouped-maximum retains the best score per student.
            val compatibleTemplates = templates.filter { it.embedding.size == observation.embedding!!.size }
            val ranked = IdentityScorer.scoreGroupedMax(observation.embedding!!, compatibleTemplates)
            val scores = ranked.associate { it.studentId to it.bestScore }
            val decision = decisionEngine.evaluate(observation.embedding!!, compatibleTemplates)
            // PHASE-1 DIAGNOSTIC: per-frame recognition transparency.
            run {
                val top = scores.entries.sortedByDescending { it.value }.take(2)
                val norm = kotlin.math.sqrt(observation.embedding!!.fold(0f) { a, v -> a + v * v })
                val msg = "FRAME ${analyses.size + 1}: usable=true embDim=${observation.embedding!!.size} " +
                    "norm=$norm gallery=${templates.size} compatible=${compatibleTemplates.size} " +
                    "top1=${top.getOrNull(0)?.key}:${top.getOrNull(0)?.value} " +
                    "top2=${top.getOrNull(1)?.key}:${top.getOrNull(1)?.value} decision=$decision"
                try { android.util.Log.i("ATTRACT_RECOGNITION", msg) } catch (_: Throwable) { println("[ATTRACT_RECOGNITION] $msg") }
            }
            EvidenceFusionStrategy.FrameAnalysis(observation, usable = true, scores = scores, decision = decision)
        } else {
            val why = when {
                observation.quality is QualityResult.Rejected -> "QUALITY:${(observation.quality as QualityResult.Rejected).reason.name}"
                observation.liveness is LivenessResult.Rejected -> "LIVENESS:${(observation.liveness as LivenessResult.Rejected).reason.name}"
                observation.embedding == null -> "NO_EMBEDDING"
                else -> if (isReplay) "STATIC_REPLAY" else "OTHER"
            }
            val msg = "FRAME ${analyses.size + 1}: usable=false reason=$why faces=${observation.signals.faceCount} yaw=${observation.signals.yawDegrees} pitch=${observation.signals.pitchDegrees} blur=${observation.signals.blurVariance} bright=${observation.signals.brightness}"
            try { android.util.Log.i("ATTRACT_RECOGNITION", msg) } catch (_: Throwable) { println("[ATTRACT_RECOGNITION] $msg") }
            EvidenceFusionStrategy.FrameAnalysis(observation, usable = false)
        }
        analyses.add(analysis)

        val usableSoFar = analyses.count { it.usable }
        val submitted = analyses.size

        // ---- Frame-1 fast path (test matrix #1): a confident FIRST usable frame accepts
        // immediately; no left/right captures are requested.
        // WP-C / §3.3: High-confidence matches (>= confirmBelow 0.60f) accept on frame 1.
        // Borderline matches in [acceptThreshold, confirmBelow) request a second confirming frame.
        if (usable && usableSoFar == 1) {
            val decision = analysis.decision
            if (decision is RecognitionOutcome.Match) {
                if (decision.confidence >= BiometricModelProfile.CURRENT.confirmBelow) {
                    finished = true
                    return Step.Final(Outcome.Match(decision.studentId, decision.confidence, framesUsed = submitted))
                }
            }
        }

        // ---- Hopelessness fail-fast (invariant S6): if the FIRST biometrically usable frame's
        // best gallery score is below notFoundCeiling (or no compatible templates exist), the
        // face can never reach acceptThreshold (0.50). End Unknown immediately without wasting captures.
        if (usable && usableSoFar == 1 && analysis.decision !is RecognitionOutcome.Match) {
            val topScore = analysis.scores.values.maxOrNull()
            val ceiling = BiometricModelProfile.CURRENT.notFoundCeiling
            if (topScore == null || topScore < ceiling) {
                finished = true
                failFast = true
                val msg = "FAILFAST frame1 top=$topScore ceiling=$ceiling"
                try { android.util.Log.i("ATTRACT_RECOGNITION", msg) } catch (_: Throwable) { println("[ATTRACT_RECOGNITION] $msg") }
                return Step.Final(Outcome.Unknown)
            }
        }

        // ---- Early fusion rescue (test matrix #3/#4): from the SECOND usable frame onward,
        // fuse what we have; a confident fused Match ends the transaction without forcing
        // the full three-frame budget.
        if (usable && usableSoFar >= 2) {
            val fused = fusionStrategy.fuse(analyses.toList(), templates, decisionEngine)
            if (fused is FusedOutcome.Match) {
                finished = true
                return Step.Final(Outcome.Match(fused.studentId, fused.confidence, framesUsed = submitted))
            }
            if (submitted >= maxFrames) {
                finished = true
                return Step.Final(fused.toOutcome().also { conflictTally(fused) })
            }
        }

        // ---- Budget exhausted -> final fusion.
        if (submitted >= maxFrames) {
            finished = true
            return Step.Final(finalize())
        }

        // ---- Still budget left.
        val priorMatches = analyses.mapNotNull { it.decision as? RecognitionOutcome.Match }
        if (priorMatches.map { it.studentId }.distinct().size > 1) conflictCount++
        val reason = when {
            isReplay -> "Hold still was detected as identical frame — natural movement helps. Capture another angle naturally."
            !usable -> when {
                observation.quality is QualityResult.Rejected -> "Retake needed (${observation.quality.reason.name.lowercase()})."
                observation.liveness is LivenessResult.Rejected -> "Retake needed (${observation.liveness.message})."
                else -> "Capture another natural observation."
            }
            else -> "One more clear look helps us verify you. Look at the camera naturally."
        }
        return Step.NeedMoreFrames(framesSubmitted = submitted, reason = reason)
    }

    /** Forces terminal fusion with whatever evidence exists (e.g. user cancels capture). */
    fun finalizeNow(): Step.Final {
        finished = true
        return Step.Final(finalize())
    }

    // ------------------------------------------------------------------

    private fun conflictTally(fused: FusedOutcome) {
        if (fused is FusedOutcome.Ambiguous) conflictCount++
    }

    private fun FusedOutcome.toOutcome(): Outcome = when (this) {
        is FusedOutcome.Match -> Outcome.Match(studentId, confidence, framesUsed)
        is FusedOutcome.Ambiguous -> Outcome.Ambiguous
        is FusedOutcome.Unknown -> Outcome.Unknown
    }

    private fun finalize(): Outcome {
        if (analyses.all { !it.usable }) return Outcome.Unknown

        val fused = fusionStrategy.fuse(analyses.toList(), templates, decisionEngine)
        return when (fused) {
            is FusedOutcome.Match -> Outcome.Match(fused.studentId, fused.confidence, fused.framesUsed)
            is FusedOutcome.Ambiguous -> {
                conflictCount++
                Outcome.Ambiguous
            }
            is FusedOutcome.Unknown -> Outcome.Unknown
        }
    }

    companion object {
        const val DEFAULT_MAX_FRAMES = 3

        /**
         * Pose validity for SUPPORTING frames (frames 2–3): extreme poses are unusable,
         * but no specific pose is required (LLD-16). Uses ONLY existing production config
         * values — no new thresholds.
         */
        fun isAcceptableSupportPose(signals: FaceQualitySignals, config: FaceQualityConfig): Boolean =
            kotlin.math.abs(signals.pitchDegrees) <= config.maxPoseDegrees &&
                kotlin.math.abs(signals.yawDegrees) <= config.profileMaxYawDegrees
    }
}
