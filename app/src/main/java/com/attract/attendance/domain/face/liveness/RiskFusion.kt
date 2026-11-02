package com.attract.attendance.domain.face.liveness

import com.attract.attendance.domain.face.LivenessPolicy
import com.attract.attendance.domain.face.LivenessReason

enum class LivenessRisk {
    LOW,
    MEDIUM,
    HIGH,
}

data class LivenessEvidence(
    val legacyPixelVeto: LivenessReason? = null,
    val padScore: Float? = null,
    val depthScore: Float? = null,
    val depthInconclusive: Boolean = false,
    val blinkSeen: Boolean = false,
    val flashScore: Float? = null,
    val challengePassed: Boolean? = null,
    val faceLuma: Float = 100f,
    val trackFrames: Int = 1,
    val injectionSuspected: Boolean = false,
)

/**
 * Multi-factor liveness decision fusion engine (doc-20 WP7.5).
 * Cascades passive neural PAD, 3D motion-parallax, photometric reflection, and micro-challenges.
 */
object RiskFusion {

    fun decide(e: LivenessEvidence, p: LivenessPolicy): LivenessRisk {
        // Rule 1: Camera injection or synthetic feed timing violation
        if (e.injectionSuspected) return LivenessRisk.HIGH

        // Rule 2: Strong legacy pixel veto (extreme moiré, high glare, flat texture)
        if (e.legacyPixelVeto != null) return LivenessRisk.HIGH

        // Rule 3: Passive PAD CNN classified frame as presentation attack
        val effectiveSpoofReject = if (e.faceLuma < 50f) 0.12f else p.padSpoofReject
        if (e.padScore != null && e.padScore < effectiveSpoofReject) {
            return LivenessRisk.HIGH
        }

        // Rule 4: Photometric flash correlation failed
        if (e.flashScore != null && e.flashScore < 0.35f) {
            return LivenessRisk.HIGH
        }

        // Rule 5: Active challenge failed or timed out
        if (e.challengePassed == false) {
            return LivenessRisk.HIGH
        }

        // Rule 6: Motion parallax fitted flat homography (conclusive 2D plane attack)
        if (e.depthScore != null && e.depthScore < 0.20f && !e.depthInconclusive) {
            return LivenessRisk.HIGH
        }

        // Rule 7: Strong passive PAD corroborated by 3D depth, blink, or flash
        if (e.padScore != null && e.padScore >= p.padLiveAccept) {
            val depthCorroborated = e.depthScore != null && e.depthScore >= 0.50f
            val flashCorroborated = e.flashScore != null && e.flashScore >= 0.60f
            if (depthCorroborated || e.blinkSeen || flashCorroborated) {
                return LivenessRisk.LOW
            }
        }

        // Rule 8: Verified active challenge with supporting evidence
        if (e.challengePassed == true) {
            val padSupport = e.padScore != null && e.padScore >= 0.50f
            val flashSupport = e.flashScore != null && e.flashScore >= 0.60f
            val depthNotFlat = e.depthScore == null || e.depthScore >= 0.30f
            if ((padSupport || flashSupport) && depthNotFlat) {
                return LivenessRisk.LOW
            }
        }

        // Rule 9: Dark-room path where flash and 3D depth prove live human presence
        if (e.flashScore != null && e.flashScore >= 0.75f && e.depthScore != null && e.depthScore >= 0.50f) {
            return LivenessRisk.LOW
        }

        // Rule 10: Inconclusive passive evidence -> request active verification
        return LivenessRisk.MEDIUM
    }
}
