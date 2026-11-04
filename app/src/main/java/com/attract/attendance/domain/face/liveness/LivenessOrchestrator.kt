package com.attract.attendance.domain.face.liveness

import com.attract.attendance.domain.face.FaceMeshResult
import com.attract.attendance.domain.face.FaceQualitySignals
import com.attract.attendance.domain.face.LivenessPolicy
import com.attract.attendance.domain.face.LivenessReason
import com.attract.attendance.domain.face.LivenessResult
import com.attract.attendance.domain.face.PresentationAttackDetector
import com.attract.attendance.domain.face.PresentationAttackSignals

/**
 * Proof method used to achieve low-risk liveness (Doc 23 WP-3).
 */
enum class LivenessProofMethod {
    PARALLAX_MESH,
    PARALLAX_5PT,
    CHALLENGE,
    PASSIVE_STRONG,
}

/**
 * Latched liveness decision for the current track (Doc 23 WP-3).
 */
data class LivenessLatch(
    val risk: LivenessRisk = LivenessRisk.LOW,
    val provenAtMs: Long,
    val trackId: Int?,
    val method: LivenessProofMethod = LivenessProofMethod.PARALLAX_MESH,
)

/**
 * What the UI must ask the student to do while liveness is [LivenessRisk.MEDIUM] (Doc 22 WP-C, Doc 23 WP-5/WP-8).
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
    /** Stuck in MEDIUM past timeout: escape hatch to teacher assist (Doc 23 WP-8). */
    TEACHER_ASSIST,
}

/** Full liveness verdict for one evaluation (Doc 22, Doc 23). */
data class LivenessVerdict(
    val risk: LivenessRisk,
    val result: LivenessResult,
    val guidance: LivenessGuidance,
    val evidence: LivenessEvidence,
    val depth: ParallaxDepthTest.Result,
    val latch: LivenessLatch? = null,
)

/**
 * Orchestrates multi-signal presentation-attack detection for live attendance (doc-20 WP7.5, Doc 22 WP-C, Doc 23).
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

    // Doc 23 additions
    var latch: LivenessLatch? = null
        private set
    val activeLatch: LivenessLatch? get() = latch
    private var lastSeenSingleFaceMs: Long = 0L
    private var multiFaceCount: Int = 0
    private var firstMediumSeenAtMs: Long? = null

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
     */
    fun onFrame(
        signals: FaceQualitySignals? = null,
        patSignals: PresentationAttackSignals? = null,
        mesh: FaceMeshResult? = null,
        padScore: Float? = patSignals?.padScore,
        trackId: Int? = null,
        faceCount: Int = signals?.faceCount ?: 0,
        nowMs: Long = clock(),
    ) {
        if (faceCount == 1) {
            if (trackId != null && lastTrackId != null && trackId != lastTrackId) {
                reset()
            }
            lastTrackId = trackId
            lastSeenSingleFaceMs = nowMs
            multiFaceCount = 0
        } else if (faceCount == 0) {
            // Brief dropout (< 800ms) preserves history
            if (lastSeenSingleFaceMs > 0L && nowMs - lastSeenSingleFaceMs > policy.faceDropoutGraceMs) {
                reset()
            }
        } else if (faceCount > 1) {
            multiFaceCount++
            if (multiFaceCount >= 3) {
                reset()
            }
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

        if (patSignals != null) {
            val reason = (PresentationAttackDetector.analyze(patSignals, signals?.blurVariance ?: 0f, pixelThresholds)
                as? LivenessResult.Rejected)?.reason
            pixelReasons.addLast(reason)
            while (pixelReasons.size > policy.pixelVetoWindow) pixelReasons.removeFirst()
        }

        // Active challenge progression
        val challenge = activeChallenge
        if (challenge != null && mesh != null && activeChallengeResult == null) {
            when (challenge.update(mesh, nowMs)) {
                ChallengeStatus.PASSED -> {
                    activeChallengeResult = true
                    if (confirmedPixelVeto() == null && !injectionSuspected) {
                        latch = LivenessLatch(
                            risk = LivenessRisk.LOW,
                            provenAtMs = nowMs,
                            trackId = lastTrackId,
                            method = LivenessProofMethod.CHALLENGE,
                        )
                    }
                }
                ChallengeStatus.TIMED_OUT -> activeChallengeResult = false
                ChallengeStatus.IN_PROGRESS -> {}
            }
        }

        // Incremental latch evaluation (Doc 23 WP-3)
        if (latch == null && confirmedPixelVeto() == null && !injectionSuspected) {
            val window = meshHistory.filter { nowMs - it.tMs <= policy.meshWindowMs }.map { it.value }
            if (window.size >= 2) {
                val depthRes = ParallaxDepthTest.evaluate(
                    frames = window,
                    minYawDeltaDeg = policy.parallaxMinYawDeltaDeg,
                    parallaxLiveResidual = policy.parallaxLiveResidual,
                )
                val medPad = medianPadScore()
                val padSuspect = medPad != null && medPad < policy.padSpoofReject
                if (!depthRes.depthInconclusive && (depthRes.depthScore ?: 0f) >= 0.50f && !padSuspect) {
                    latch = LivenessLatch(
                        risk = LivenessRisk.LOW,
                        provenAtMs = nowMs,
                        trackId = lastTrackId,
                        method = LivenessProofMethod.PARALLAX_MESH,
                    )
                }
            }
        }
    }

    fun setFlashScore(score: Float) {
        flashScore = score
    }

    fun markInjectionSuspected() {
        injectionSuspected = true
        latch = null
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
     */
    fun evaluate(signals: FaceQualitySignals, nowMs: Long = clock()): LivenessVerdict {
        val pixelVeto = confirmedPixelVeto()
        if (pixelVeto != null || injectionSuspected) {
            latch = null
            firstMediumSeenAtMs = null
            val evidence = LivenessEvidence(
                legacyPixelVeto = pixelVeto,
                padScore = medianPadScore(),
                depthScore = null,
                depthInconclusive = true,
                blinkSeen = false,
                flashScore = flashScore,
                challengePassed = activeChallengeResult,
                faceLuma = if (signals.faceLuma >= 0f) signals.faceLuma else signals.brightness,
                trackFrames = maxOf(meshHistory.size, padScores.size),
                injectionSuspected = injectionSuspected,
            )
            return LivenessVerdict(
                risk = LivenessRisk.HIGH,
                result = LivenessResult.Rejected(
                    reason = pixelVeto ?: LivenessReason.PASSIVE_SPOOF_SUSPECTED,
                    message = "Liveness check failed — please present your live face directly to the camera.",
                ),
                guidance = LivenessGuidance.NONE,
                evidence = evidence,
                depth = ParallaxDepthTest.Result(depthScore = null, depthInconclusive = true),
                latch = null,
            )
        }

        // Active latch within TTL (Doc 23 WP-3)
        val activeLatch = latch
        if (activeLatch != null && (nowMs - activeLatch.provenAtMs <= policy.latchTtlMs)) {
            firstMediumSeenAtMs = null
            val evidence = LivenessEvidence(
                legacyPixelVeto = null,
                padScore = medianPadScore() ?: 0.9f,
                depthScore = 0.8f,
                depthInconclusive = false,
                blinkSeen = true,
                flashScore = flashScore,
                challengePassed = true,
                faceLuma = if (signals.faceLuma >= 0f) signals.faceLuma else signals.brightness,
                trackFrames = maxOf(meshHistory.size, padScores.size),
                injectionSuspected = false,
            )
            return LivenessVerdict(
                risk = LivenessRisk.LOW,
                result = LivenessResult.Passed,
                guidance = LivenessGuidance.NONE,
                evidence = evidence,
                depth = ParallaxDepthTest.Result(depthScore = 0.8f, depthInconclusive = false),
                latch = activeLatch,
            )
        }

        // Sliding window evaluation
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

        if (risk == LivenessRisk.LOW) {
            latch = LivenessLatch(
                risk = LivenessRisk.LOW,
                provenAtMs = nowMs,
                trackId = lastTrackId,
                method = LivenessProofMethod.PARALLAX_MESH,
            )
            firstMediumSeenAtMs = null
        } else if (risk == LivenessRisk.MEDIUM) {
            if (firstMediumSeenAtMs == null) firstMediumSeenAtMs = nowMs
            if (evidence.depthLive && activeChallenge == null) {
                startChallenge(nowMs)
            }
        } else {
            firstMediumSeenAtMs = null
        }

        val guidance = when (risk) {
            LivenessRisk.LOW, LivenessRisk.HIGH -> LivenessGuidance.NONE
            LivenessRisk.MEDIUM -> {
                val mediumDuration = firstMediumSeenAtMs?.let { nowMs - it } ?: 0L
                if (mediumDuration >= policy.maxMediumMsBeforeAssist) {
                    LivenessGuidance.TEACHER_ASSIST
                } else {
                    guidanceFor(evidence)
                }
            }
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

        return LivenessVerdict(risk, result, guidance, evidence, depthRes, latch)
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
        val ctrl = ChallengeController(
            startTimestampMs = nowMs,
            maxChallengeMs = policy.maxChallengeMs,
            turnThresholdDeg = policy.challengeTurnDeg,
        )
        ctrl.armAt(nowMs)
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
        latch = null
        firstMediumSeenAtMs = null
        multiFaceCount = 0
        lastSeenSingleFaceMs = 0L
    }

    private fun prune(nowMs: Long) {
        while (meshHistory.isNotEmpty() && nowMs - meshHistory.first().tMs > policy.meshWindowMs) meshHistory.removeFirst()
        while (padScores.isNotEmpty() && nowMs - padScores.first().tMs > policy.meshWindowMs) padScores.removeFirst()
    }

    companion object {
        /** exp10 `pad_median3`. */
        const val PAD_MEDIAN_WINDOW = 3
        const val MAX_MESH_FRAMES = 45 // Doc 23 WP-2.4: raised to 45

        fun messageFor(g: LivenessGuidance): String = when (g) {
            LivenessGuidance.NONE -> ""
            LivenessGuidance.TURN_HEAD -> "Slowly turn your head a little to the left and right."
            LivenessGuidance.CHALLENGE_BLINK -> "Please blink once."
            LivenessGuidance.CHALLENGE_TURN_LEFT -> "Please turn your head slightly to the left."
            LivenessGuidance.CHALLENGE_TURN_RIGHT -> "Please turn your head slightly to the right."
            LivenessGuidance.CHALLENGE_SMILE -> "Please smile."
            LivenessGuidance.TEACHER_ASSIST -> "Having trouble verifying? Tap below for Teacher Assistance."
        }
    }
}
