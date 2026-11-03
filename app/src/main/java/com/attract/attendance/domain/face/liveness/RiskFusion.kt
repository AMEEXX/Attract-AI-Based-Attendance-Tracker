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
    val depthInconclusive: Boolean = true,
    val blinkSeen: Boolean = false,
    val flashScore: Float? = null,
    val challengePassed: Boolean? = null,
    val faceLuma: Float = 100f,
    val trackFrames: Int = 1,
    val injectionSuspected: Boolean = false,
) {
    /** Conclusive 3D head-turn parallax that only a real (non-planar) face produces. */
    val depthLive: Boolean get() = !depthInconclusive && depthScore != null && depthScore >= RiskFusion.DEPTH_LIVE
    /** Conclusive planar (photo / screen) homography fit. */
    val depthFlat: Boolean get() = !depthInconclusive && depthScore != null && depthScore < RiskFusion.DEPTH_FLAT
}

/**
 * Multi-factor liveness decision fusion (doc-20 WP7.5, rewritten by Doc 22 WP-B/WP-E).
 *
 * Contract (Doc 22, measured in exp7/exp8/exp10):
 *  - HIGH  = a conclusive attack signature. Caller REJECTS with a spoof reason.
 *  - LOW   = live. Requires CONCLUSIVE 3D depth (or a passed random challenge) — never passive evidence alone.
 *  - MEDIUM = not yet proven live. Caller must NOT accept; it asks for a head turn / challenge
 *             (LivenessReason.ACTIVE_CHECK_REQUIRED). **MEDIUM must never map to LivenessResult.Passed.**
 *
 * The passive PAD CNN is an ESCALATION signal, not a hard reject: genuine backlit faces score < 0.10 in
 * 2–9 % of cases (exp10), so a low PAD only forces an extra random challenge on top of the 3D check.
 */
object RiskFusion {

    const val DEPTH_LIVE = 0.50f
    const val DEPTH_FLAT = 0.20f
    const val FLASH_FAIL = 0.35f
    const val FLASH_LIVE = 0.75f

    fun padSuspect(e: LivenessEvidence, p: LivenessPolicy): Boolean =
        e.padScore != null && e.padScore < p.padSpoofReject

    fun decide(e: LivenessEvidence, p: LivenessPolicy): LivenessRisk {
        // Rule 1: camera injection / synthetic feed timing violation
        if (e.injectionSuspected) return LivenessRisk.HIGH

        // Rule 2: strong pixel veto (confirmed on >= 2 of last 3 frames, measured on the INNER face crop)
        if (e.legacyPixelVeto != null) return LivenessRisk.HIGH

        // Rule 3: random active challenge failed / timed out
        if (e.challengePassed == false) return LivenessRisk.HIGH

        // Rule 4: photometric flash correlation failed
        if (e.flashScore != null && e.flashScore < FLASH_FAIL) return LivenessRisk.HIGH

        // Rule 5: conclusive planar parallax (photo / screen moved or tilted)
        if (e.depthFlat) return LivenessRisk.HIGH

        val suspect = padSuspect(e, p)

        // Rule 6: conclusive 3D depth. Accept unless the PAD CNN is suspicious (possible replay VIDEO of a
        // real person turning) — then a passed random challenge is additionally required.
        if (e.depthLive) {
            if (!suspect) return LivenessRisk.LOW
            if (e.challengePassed == true) return LivenessRisk.LOW
            return LivenessRisk.MEDIUM
        }

        // Rule 7: random challenge passed without conclusive depth (e.g. blink/smile challenge) is enough
        // only when the PAD CNN is not suspicious.
        if (e.challengePassed == true && !suspect) return LivenessRisk.LOW

        // Rule 8: dark-room path — strong flash response + live depth already covered by Rule 6.
        // Rule 9: everything else is inconclusive -> active verification required (NOT a pass).
        return LivenessRisk.MEDIUM
    }
}
