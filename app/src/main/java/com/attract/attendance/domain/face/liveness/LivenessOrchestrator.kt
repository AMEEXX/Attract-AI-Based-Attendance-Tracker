package com.attract.attendance.domain.face.liveness

import com.attract.attendance.domain.face.FaceMeshResult
import com.attract.attendance.domain.face.FaceQualitySignals
import com.attract.attendance.domain.face.LivenessPolicy
import com.attract.attendance.domain.face.LivenessReason
import com.attract.attendance.domain.face.LivenessResult
import com.attract.attendance.domain.face.PresentationAttackDetector
import com.attract.attendance.domain.face.PresentationAttackSignals

/**
 * Orchestrates multi-sensor presentation-attack detection and liveness evaluation (doc-20 WP7.5).
 * Replaces direct LivenessEngine calls in live attendance sessions.
 */
class LivenessOrchestrator(
    private val policy: LivenessPolicy = LivenessPolicy(),
) {
    private val meshHistory = mutableListOf<FaceMeshResult>()
    private val padScores = mutableListOf<Float>()
    private val recentPixelPadReasons = mutableListOf<LivenessReason?>()
    private var activeChallenge: ChallengeController? = null
    private var activeChallengeResult: Boolean? = null
    private var flashScore: Float? = null
    private var injectionSuspected: Boolean = false

    fun onFrame(
        signals: FaceQualitySignals,
        patSignals: PresentationAttackSignals?,
        mesh: FaceMeshResult?,
        padScore: Float?,
    ) {
        if (mesh != null) {
            meshHistory.add(mesh)
            if (meshHistory.size > 20) meshHistory.removeAt(0)
        }

        if (padScore != null) {
            padScores.add(padScore)
            if (padScores.size > 10) padScores.removeAt(0)
        }

        val pixRes = PresentationAttackDetector.analyze(patSignals, signals.blurVariance)
        val reason = (pixRes as? LivenessResult.Rejected)?.reason
        recentPixelPadReasons.add(reason)
        if (recentPixelPadReasons.size > 3) recentPixelPadReasons.removeAt(0)

        // Active challenge progression if running
        val challenge = activeChallenge
        if (challenge != null && mesh != null && activeChallengeResult == null) {
            when (challenge.update(mesh, System.currentTimeMillis())) {
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

    fun evaluate(
        signals: FaceQualitySignals,
        patSignals: PresentationAttackSignals?,
    ): Pair<LivenessRisk, LivenessResult> {
        // 1. Pixel-level analysis: Require same PAD reason on >= 2 of last 3 frames (Doc 21 WP-D)
        val currentTrip = (PresentationAttackDetector.analyze(patSignals, signals.blurVariance) as? LivenessResult.Rejected)?.reason
        if (recentPixelPadReasons.isEmpty() || recentPixelPadReasons.size < 3) {
            recentPixelPadReasons.add(currentTrip)
        }
        val pixelVeto = recentPixelPadReasons.filterNotNull()
            .groupBy { it }
            .filter { it.value.size >= 2 }
            .keys.firstOrNull()

        // 2. Median PAD score
        val medianPad = if (padScores.isNotEmpty()) {
            val sorted = padScores.sorted()
            sorted[sorted.size / 2]
        } else null

        // 3. Motion-parallax 3D depth
        val depthRes = ParallaxDepthTest.evaluate(
            frames = meshHistory,
            minYawDeltaDeg = policy.parallaxMinYawDeltaDeg,
            parallaxLiveResidual = policy.parallaxLiveResidual
        )

        // 4. Blink detection
        val blinkSeen = BlinkDetector.hasBlink(
            history = meshHistory,
            closedScore = policy.blinkClosedScore,
            openScore = policy.blinkOpenScore
        )

        val evidence = LivenessEvidence(
            legacyPixelVeto = pixelVeto,
            padScore = medianPad,
            depthScore = depthRes.depthScore,
            depthInconclusive = depthRes.depthInconclusive,
            blinkSeen = blinkSeen,
            flashScore = flashScore,
            challengePassed = activeChallengeResult,
            faceLuma = signals.brightness,
            trackFrames = maxOf(meshHistory.size, padScores.size),
            injectionSuspected = injectionSuspected
        )

        val risk = RiskFusion.decide(evidence, policy)

        val result = when (risk) {
            LivenessRisk.LOW -> LivenessResult.Passed
            LivenessRisk.MEDIUM -> LivenessResult.Passed // Intermediate state for progressive gathering
            LivenessRisk.HIGH -> {
                val reason = pixelVeto ?: LivenessReason.PASSIVE_SPOOF_SUSPECTED
                LivenessResult.Rejected(
                    reason = reason,
                    message = "Liveness check failed — please present your live face directly to the camera."
                )
            }
        }

        return Pair(risk, result)
    }

    fun startChallenge(): ChallengeController {
        val ctrl = ChallengeController()
        activeChallenge = ctrl
        activeChallengeResult = null
        return ctrl
    }

    fun reset() {
        meshHistory.clear()
        padScores.clear()
        recentPixelPadReasons.clear()
        activeChallenge = null
        activeChallengeResult = null
        flashScore = null
        injectionSuspected = false
    }
}
