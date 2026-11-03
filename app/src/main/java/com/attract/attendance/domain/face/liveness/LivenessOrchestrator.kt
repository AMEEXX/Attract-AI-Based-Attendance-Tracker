package com.attract.attendance.domain.face.liveness

import com.attract.attendance.domain.face.FaceMeshResult
import com.attract.attendance.domain.face.FaceQualitySignals
import com.attract.attendance.domain.face.LivenessPolicy
import com.attract.attendance.domain.face.LivenessReason
import com.attract.attendance.domain.face.LivenessResult
import com.attract.attendance.domain.face.PresentationAttackDetector
import com.attract.attendance.domain.face.PresentationAttackSignals

/**
 * What the UI must ask the student to do while liveness is [LivenessRisk.MEDIUM] (Doc 22 WP-C).
 */
enum class LivenessGuidance {
    /** Nothing to ask (LOW = live, or HIGH = rejected). */
    NONE,
    /** No conclusive 3D parallax yet: ask for a small natural head turn (>= policy.parallaxMinYawDeltaDeg). */
    TURN_HEAD,
    /** 3D depth is live but the PAD CNN is suspicious (possible replay video): random challenge running. */
    CHALLENGE_BLINK,
    CHALLENGE_TURN_LEFT,
    CHALLENGE_TURN_RIGHT,
    CHALLENGE_SMILE,
}

/** Full liveness verdict for one evaluation (Doc 22). */
data class LivenessVerdict(
    val risk: LivenessRisk,
    val result: LivenessResult,
    val guidance: LivenessGuidance,
    val evidence: LivenessEvidence,
    val depth: ParallaxDepthTest.Result,
)

/**
 * Orchestrates multi-signal presentation-attack detection for live attendance (doc-20 WP7.5, Doc 22 WP-C).
 *
 * Doc 22 contract:
 *  - **Single-source history.** Only [onFrame] appends to the mesh / PAD / pixel-veto histories. [evaluate]
 *    is read-only, so evaluating several times on the same frame can never double-count a pixel trip.
 *  - **2-of-3 pixel veto** (`policy.pixelVetoMinHits` of the last `policy.pixelVetoWindow` frames, same reason),
 *    measured on the INNER 1.0x face crop.
 *  - **PAD CNN** = median of the last 3 MiniFASNet live probabilities. Below `policy.padSpoofReject` it only
 *    ESCALATES (random challenge), it never hard-rejects (genuine backlit faces score low in 2–9 % of frames).
 *  - **MEDIUM never maps to [LivenessResult.Passed]**: it maps to `Rejected(ACTIVE_CHECK_REQUIRED)` and the
 *    caller shows [LivenessVerdict.guidance] instead of a spoof message.
 *  - History older than `policy.meshWindowMs` is discarded, so stale frames cannot create fake parallax.
 *
 * Feed it from the live preview (every analysed single-face frame) so 3D parallax and the PAD median build up
 * BEFORE capture; call [reset] when the face track changes or an attempt ends.
 */
class LivenessOrchestrator(
    private val policy: LivenessPolicy = LivenessPolicy(),
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private data class Timed<T>(val tMs: Long, val value: T)

    private val meshHistory = ArrayDeque<Timed<FaceMeshResult>>()
    private val padScores = ArrayDeque<Timed<Float>>()
    private val pixelReasons = ArrayDeque<LivenessReason?>()
    private var activeChallenge: ChallengeController? = null
    private var activeChallengeResult: Boolean? = null
    private var flashScore: Float? = null
    private var injectionSuspected: Boolean = false
    private var lastTrackId: Int? = null

    private val pixelThresholds = PresentationAttackDetector.Thresholds(
        maxHighFrequencyEnergy = policy.maxHighFrequencyEnergy,
        maxSpecularHighlightRatio = policy.maxSpecularHighlightRatio,
        printFlatTextureStdDev = policy.printFlatTextureStdDev,
        printMaxBlurVariance = policy.printMaxBlurVariance,
    )

    /** Number of frames currently in the mesh history (for telemetry / tests). */
    val meshFrameCount: Int get() = meshHistory.size

    /** Number of PAD CNN scores currently in the history (for telemetry / tests). */
    val padFrameCount: Int get() = padScores.size

    /** Currently running challenge, if any (UI shows its instruction). */
    val currentChallenge: LivenessChallengeType? get() = activeChallenge?.challenge

    /**
     * Appends one analysed frame. The ONLY method that mutates history.
     *
     * @param padScore MiniFASNet live probability for this frame; defaults to [PresentationAttackSignals.padScore].
     * @param trackId  face-track id; a change of track clears all history (prevents a real face's head turn
     *                 being credited to a photo swapped in afterwards).
     * @param nowMs    frame timestamp in ms (monotonic within a session).
     */
    fun onFrame(
        signals: FaceQualitySignals,
        patSignals: PresentationAttackSignals?,
        mesh: FaceMeshResult?,
        padScore: Float? = patSignals?.padScore,
        trackId: Int? = null,
        nowMs: Long = clock(),
    ) {
        if (trackId != null) {
            if (lastTrackId != null && lastTrackId != trackId) reset()
            lastTrackId = trackId
        }
        prune(nowMs)

        if (mesh != null) {
            meshHistory.addLast(Timed(nowMs, mesh))
            while (meshHistory.size > MAX_MESH_FRAMES) meshHistory.removeFirst()
        }

        if (padScore != null && padScore.isFinite()) {
            padScores.addLast(Timed(nowMs, padScore.coerceIn(0f, 1f)))
            while (padScores.size > PAD_MEDIAN_WINDOW) padScores.removeFirst()
        }

        // Pixel PAD trip for THIS frame (null = clean). Recorded only when signals exist; a frame without
        // pixel signals neither confirms nor clears an earlier trip.
        if (patSignals != null) {
            val reason = (PresentationAttackDetector.analyze(patSignals, signals.blurVariance, pixelThresholds)
                as? LivenessResult.Rejected)?.reason
            pixelReasons.addLast(reason)
            while (pixelReasons.size > policy.pixelVetoWindow) pixelReasons.removeFirst()
        }

        // Active challenge progression
        val challenge = activeChallenge
        if (challenge != null && mesh != null && activeChallengeResult == null) {
            when (challenge.update(mesh, nowMs)) {
                ChallengeStatus.PASSED -> activeChallengeResult = true
                ChallengeStatus.TIMED_OUT -> activeChallengeResult = false
                ChallengeStatus.IN_PROGRESS -> {}
            }
        }
    }

    fun setFlashScore(score: Float) {
        flashScore = score
    }

    fun markInjectionSuspected() {
        injectionSuspected = true
    }

    /** Pixel veto reason confirmed on >= pixelVetoMinHits of the last pixelVetoWindow frames, else null. */
    fun confirmedPixelVeto(): LivenessReason? =
        pixelReasons.filterNotNull()
            .groupingBy { it }
            .eachCount()
            .filter { it.value >= policy.pixelVetoMinHits }
            .keys
            .firstOrNull()

    /** Median of the last [PAD_MEDIAN_WINDOW] PAD CNN scores (exp10 `pad_median3`), or null when none. */
    fun medianPadScore(): Float? {
        if (padScores.isEmpty()) return null
        val sorted = padScores.map { it.value }.sorted()
        return if (sorted.size % 2 == 1) {
            sorted[sorted.size / 2]
        } else {
            (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2f
        }
    }

    /**
     * Read-only fusion of the accumulated history. Never mutates the frame histories.
     * May START a random challenge when depth is live but the PAD CNN is suspicious (Rule 6 of [RiskFusion]).
     */
    fun evaluate(signals: FaceQualitySignals, nowMs: Long = clock()): LivenessVerdict {
        val window = meshHistory.filter { nowMs - it.tMs <= policy.meshWindowMs }.map { it.value }

        val depthRes = ParallaxDepthTest.evaluate(
            frames = window,
            minYawDeltaDeg = policy.parallaxMinYawDeltaDeg,
            parallaxLiveResidual = policy.parallaxLiveResidual,
        )

        val blinkSeen = BlinkDetector.hasBlink(
            history = window,
            closedScore = policy.blinkClosedScore,
            openScore = policy.blinkOpenScore,
        )

        val pixelVeto = confirmedPixelVeto()
        val evidence = LivenessEvidence(
            legacyPixelVeto = pixelVeto,
            padScore = medianPadScore(),
            depthScore = depthRes.depthScore,
            depthInconclusive = depthRes.depthInconclusive,
            blinkSeen = blinkSeen,
            flashScore = flashScore,
            challengePassed = activeChallengeResult,
            faceLuma = if (signals.faceLuma >= 0f) signals.faceLuma else signals.brightness,
            trackFrames = maxOf(window.size, padScores.size),
            injectionSuspected = injectionSuspected,
        )

        val risk = RiskFusion.decide(evidence, policy)

        // Depth is live but the PAD CNN is suspicious -> start ONE random challenge (if none ran yet).
        if (risk == LivenessRisk.MEDIUM && evidence.depthLive && activeChallenge == null) {
            startChallenge(nowMs)
        }

        val guidance = when (risk) {
            LivenessRisk.LOW, LivenessRisk.HIGH -> LivenessGuidance.NONE
            LivenessRisk.MEDIUM -> guidanceFor(evidence)
        }

        val result: LivenessResult = when (risk) {
            LivenessRisk.LOW -> LivenessResult.Passed
            LivenessRisk.MEDIUM -> LivenessResult.Rejected(
                reason = LivenessReason.ACTIVE_CHECK_REQUIRED,
                message = messageFor(guidance),
            )
            LivenessRisk.HIGH -> LivenessResult.Rejected(
                reason = pixelVeto ?: LivenessReason.PASSIVE_SPOOF_SUSPECTED,
                message = "Liveness check failed — please present your live face directly to the camera.",
            )
        }

        return LivenessVerdict(risk, result, guidance, evidence, depthRes)
    }

    private fun guidanceFor(e: LivenessEvidence): LivenessGuidance {
        if (!e.depthLive) return LivenessGuidance.TURN_HEAD
        return when (activeChallenge?.challenge) {
            LivenessChallengeType.BLINK -> LivenessGuidance.CHALLENGE_BLINK
            LivenessChallengeType.TURN_SLIGHT_LEFT -> LivenessGuidance.CHALLENGE_TURN_LEFT
            LivenessChallengeType.TURN_SLIGHT_RIGHT -> LivenessGuidance.CHALLENGE_TURN_RIGHT
            LivenessChallengeType.SMILE -> LivenessGuidance.CHALLENGE_SMILE
            null -> LivenessGuidance.TURN_HEAD
        }
    }

    fun startChallenge(nowMs: Long = clock()): ChallengeController {
        val ctrl = ChallengeController(startTimestampMs = nowMs, maxChallengeMs = policy.maxChallengeMs)
        activeChallenge = ctrl
        activeChallengeResult = null
        return ctrl
    }

    fun reset() {
        meshHistory.clear()
        padScores.clear()
        pixelReasons.clear()
        activeChallenge = null
        activeChallengeResult = null
        flashScore = null
        injectionSuspected = false
        lastTrackId = null
    }

    private fun prune(nowMs: Long) {
        while (meshHistory.isNotEmpty() && nowMs - meshHistory.first().tMs > policy.meshWindowMs) meshHistory.removeFirst()
        while (padScores.isNotEmpty() && nowMs - padScores.first().tMs > policy.meshWindowMs) padScores.removeFirst()
    }

    companion object {
        /** exp10 `pad_median3`. */
        const val PAD_MEDIAN_WINDOW = 3
        const val MAX_MESH_FRAMES = 30

        fun messageFor(g: LivenessGuidance): String = when (g) {
            LivenessGuidance.NONE -> ""
            LivenessGuidance.TURN_HEAD -> "Slowly turn your head a little to the left and right."
            LivenessGuidance.CHALLENGE_BLINK -> "Please blink once."
            LivenessGuidance.CHALLENGE_TURN_LEFT -> "Please turn your head slightly to the left."
            LivenessGuidance.CHALLENGE_TURN_RIGHT -> "Please turn your head slightly to the right."
            LivenessGuidance.CHALLENGE_SMILE -> "Please smile."
        }
    }
}
