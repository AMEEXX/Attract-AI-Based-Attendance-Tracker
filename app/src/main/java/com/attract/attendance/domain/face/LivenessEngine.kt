package com.attract.attendance.domain.face

/**
 * Anti-spoofing & Liveness Validation Engine (LLD-12).
 * Evaluates face quality signals and frame sequence metrics to detect photo/screen spoofing.
 */
enum class LivenessReason {
    PASSED,
    EYES_CLOSED_OR_STATIC,
    PASSIVE_SPOOF_SUSPECTED,
    SCREEN_REPLAY_SUSPECTED,
    PRINT_PHOTO_SUSPECTED,
    INSUFFICIENT_VARIANCE
}

sealed interface LivenessResult {
    data object Passed : LivenessResult
    data class Rejected(val reason: LivenessReason, val message: String) : LivenessResult
}

object LivenessEngine {

    fun check(
        signals: FaceQualitySignals,
        previousSignals: FaceQualitySignals? = null,
        presentationAttackSignals: PresentationAttackSignals? = null,
    ): LivenessResult {
        // 1. Basic Eye Openness Gate
        val leftEye = signals.leftEyeOpenProbability ?: 1.0f
        val rightEye = signals.rightEyeOpenProbability ?: 1.0f

        if (leftEye < 0.25f || rightEye < 0.25f) {
            return LivenessResult.Rejected(
                LivenessReason.EYES_CLOSED_OR_STATIC,
                "Please keep eyes open and face straight."
            )
        }

        // 2. Presentation-attack gates (LLD-12): screen-replay / printed-photo detection.
        val patOutcome = PresentationAttackDetector.analyze(presentationAttackSignals, signals.blurVariance)
        if (patOutcome is LivenessResult.Rejected) return patOutcome

        // 3. Passive anti-spoofing check using brightness & blur variance ratio
        // Photos of screens or printed cards have unnaturally high/low contrast or static glare
        if (signals.blurVariance < 15.0f) {
            return LivenessResult.Rejected(
                LivenessReason.PASSIVE_SPOOF_SUSPECTED,
                "Liveness check failed — image appears static or low quality."
            )
        }

        if (signals.brightness < 45.0f) {
            return LivenessResult.Rejected(
                LivenessReason.PASSIVE_SPOOF_SUSPECTED,
                "Lighting too low for biometric liveness verification."
            )
        }

        // 4. Eye open probability variance check across consecutive frames if available
        if (previousSignals != null) {
            val prevLeft = previousSignals.leftEyeOpenProbability ?: leftEye
            val prevRight = previousSignals.rightEyeOpenProbability ?: rightEye
            val leftDiff = kotlin.math.abs(leftEye - prevLeft)
            val rightDiff = kotlin.math.abs(rightEye - prevRight)

            // Extremely identical static values down to 4 decimals indicate static photo presentation
            if (leftDiff < 0.0001f && rightDiff < 0.0001f && kotlin.math.abs(signals.yawDegrees - previousSignals.yawDegrees) < 0.001f) {
                return LivenessResult.Rejected(
                    LivenessReason.INSUFFICIENT_VARIANCE,
                    "Spoofing detected — live natural movement required."
                )
            }
        }

        return LivenessResult.Passed
    }
}
