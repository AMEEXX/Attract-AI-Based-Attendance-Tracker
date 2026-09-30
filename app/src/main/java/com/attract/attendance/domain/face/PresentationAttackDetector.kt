package com.attract.attendance.domain.face

/**
 * Passive presentation-attack detection over quality-approved frames (LLD-12).
 * Pure decision logic — thresholds are calibration parameters.
 *
 * Detection is deliberately conservative: a genuine user must never be blocked,
 * so gates fire only on strong attack signatures. Reasons surface student-safe
 * guidance; attack classification is never persisted or shown as biometric detail
 * beyond the retake instruction.
 */
object PresentationAttackDetector {

    data class Thresholds(
        val maxHighFrequencyEnergy: Float = 0.18f,
        val maxSpecularHighlightRatio: Float = 0.10f,
        val printFlatTextureStdDev: Float = 6.0f,
        val printMaxBlurVariance: Float = 200f,
    ) {
        init {
            require(maxHighFrequencyEnergy > 0f) { "maxHighFrequencyEnergy must be positive." }
            require(maxSpecularHighlightRatio in 0f..1f) { "maxSpecularHighlightRatio must be in 0..1." }
            require(printFlatTextureStdDev > 0f) { "printFlatTextureStdDev must be positive." }
            require(printMaxBlurVariance > 0f) { "printMaxBlurVariance must be positive." }
        }
    }

    fun analyze(
        signals: PresentationAttackSignals?,
        blurVariance: Float,
        thresholds: Thresholds = Thresholds(),
    ): LivenessResult {
        if (signals == null || !signals.isFinite) return LivenessResult.Passed

        // Screen-replay gate: moiré/interference energy from re-photographed displays.
        if (signals.highFrequencyEnergy > thresholds.maxHighFrequencyEnergy) {
            return LivenessResult.Rejected(
                LivenessReason.SCREEN_REPLAY_SUSPECTED,
                "Screen detected — please present your live face directly to the camera.",
            )
        }

        // Screen-glare gate: saturated specular blobs typical of phone/tablet screens.
        if (signals.specularHighlightRatio > thresholds.maxSpecularHighlightRatio) {
            return LivenessResult.Rejected(
                LivenessReason.SCREEN_REPLAY_SUSPECTED,
                "Screen glare detected — please present your live face directly to the camera.",
            )
        }

        // Printed-photo gate: sharp overall frame but abnormally flat central texture.
        if (signals.centerTextureStdDev < thresholds.printFlatTextureStdDev &&
            blurVariance < thresholds.printMaxBlurVariance
        ) {
            return LivenessResult.Rejected(
                LivenessReason.PRINT_PHOTO_SUSPECTED,
                "Photo detected — please present your live face directly to the camera.",
            )
        }

        return LivenessResult.Passed
    }
}
