package com.attract.attendance.feature.attendance

import com.attract.attendance.domain.face.FrameBundle
import com.attract.attendance.domain.face.QualityResult

enum class AutoCaptureState {
    SEARCHING,
    TRACKING,
    LIVENESS,
    RECOGNISING,
    RESULT,
}

data class RankedFrame(
    val bundle: FrameBundle,
    val score: Float,
)

/**
 * State machine governing auto-capture and progressive quality selection (doc-20 WP8).
 * Eliminates the need for manual button presses in regular kiosk attendance.
 */
class AutoCaptureController {
    var state: AutoCaptureState = AutoCaptureState.SEARCHING
        private set

    private var currentTrackId: Int? = null
    private var trackingFramesCount: Int = 0
    private var searchingStartTimeMs: Long = System.currentTimeMillis()
    private val candidateFrames = mutableListOf<RankedFrame>()

    fun onFrame(
        bundle: FrameBundle,
        quality: QualityResult,
        nowMs: Long = System.currentTimeMillis(),
        force: Boolean = false,
    ): AutoCaptureState {
        if (force) {
            if (bundle.hasFace) {
                val score = (quality as? QualityResult.Accepted)?.score ?: 0.5f
                addCandidate(bundle, score)
            }
            state = AutoCaptureState.RECOGNISING
            return state
        }

        when (state) {
            AutoCaptureState.SEARCHING -> {
                if (quality is QualityResult.Accepted && bundle.hasFace) {
                    currentTrackId = bundle.trackId
                    trackingFramesCount = 1
                    candidateFrames.clear()
                    addCandidate(bundle, quality.score)
                    state = AutoCaptureState.TRACKING
                }
            }
            AutoCaptureState.TRACKING -> {
                if (quality is QualityResult.Accepted && bundle.hasFace && bundle.trackId == currentTrackId) {
                    trackingFramesCount++
                    addCandidate(bundle, quality.score)
                    if (trackingFramesCount >= 3) {
                        state = AutoCaptureState.LIVENESS
                    }
                } else if (!bundle.hasFace || bundle.trackId != currentTrackId) {
                    reset()
                }
            }
            AutoCaptureState.LIVENESS -> {
                if (quality is QualityResult.Accepted && bundle.hasFace && bundle.trackId == currentTrackId) {
                    addCandidate(bundle, quality.score)
                } else if (!bundle.hasFace || bundle.trackId != currentTrackId) {
                    reset()
                }
            }
            AutoCaptureState.RECOGNISING, AutoCaptureState.RESULT -> {
                // Held in processing/result
            }
        }

        return state
    }

    private fun addCandidate(bundle: FrameBundle, baseScore: Float) {
        val signals = bundle.qualitySignals
        val eyeBonus = if ((signals?.leftEyeOpenProbability ?: 0f) >= 0.6f &&
            (signals?.rightEyeOpenProbability ?: 0f) >= 0.6f
        ) 0.10f else 0f
        val finalScore = baseScore + eyeBonus

        candidateFrames.add(RankedFrame(bundle, finalScore))
        candidateFrames.sortByDescending { it.score }
        if (candidateFrames.size > 4) {
            candidateFrames.removeAt(candidateFrames.size - 1)
        }
    }

    fun getBestFrames(): List<FrameBundle> = candidateFrames.map { it.bundle }

    fun transitionToRecognising() {
        state = AutoCaptureState.RECOGNISING
    }

    fun transitionToResult() {
        state = AutoCaptureState.RESULT
    }

    fun reset() {
        state = AutoCaptureState.SEARCHING
        currentTrackId = null
        trackingFramesCount = 0
        candidateFrames.clear()
        searchingStartTimeMs = System.currentTimeMillis()
    }
}
