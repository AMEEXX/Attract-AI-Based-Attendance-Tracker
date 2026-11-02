package com.attract.attendance.domain.face.liveness

import com.attract.attendance.domain.face.LivenessPolicy
import com.attract.attendance.domain.face.LivenessReason
import org.junit.Assert.assertEquals
import org.junit.Test

class RiskFusionTest {

    private val policy = LivenessPolicy()

    @Test
    fun rule1_injectionSuspected_returnsHigh() {
        val evidence = LivenessEvidence(injectionSuspected = true)
        assertEquals(LivenessRisk.HIGH, RiskFusion.decide(evidence, policy))
    }

    @Test
    fun rule2_legacyPixelVeto_returnsHigh() {
        val evidence = LivenessEvidence(legacyPixelVeto = LivenessReason.SCREEN_REPLAY_SUSPECTED)
        assertEquals(LivenessRisk.HIGH, RiskFusion.decide(evidence, policy))
    }

    @Test
    fun rule3_padBelowSpoofReject_returnsHigh() {
        val evidence = LivenessEvidence(padScore = 0.20f)
        assertEquals(LivenessRisk.HIGH, RiskFusion.decide(evidence, policy))
    }

    @Test
    fun rule4_flashScoreBelowThreshold_returnsHigh() {
        val evidence = LivenessEvidence(flashScore = 0.25f)
        assertEquals(LivenessRisk.HIGH, RiskFusion.decide(evidence, policy))
    }

    @Test
    fun rule5_challengeFailed_returnsHigh() {
        val evidence = LivenessEvidence(challengePassed = false)
        assertEquals(LivenessRisk.HIGH, RiskFusion.decide(evidence, policy))
    }

    @Test
    fun rule6_conclusiveFlatDepth_returnsHigh() {
        val evidence = LivenessEvidence(depthScore = 0.10f, depthInconclusive = false)
        assertEquals(LivenessRisk.HIGH, RiskFusion.decide(evidence, policy))
    }

    @Test
    fun rule7_highPadCorroboratedByBlink_returnsLow() {
        val evidence = LivenessEvidence(padScore = 0.85f, blinkSeen = true)
        assertEquals(LivenessRisk.LOW, RiskFusion.decide(evidence, policy))
    }

    @Test
    fun rule8_passedChallengeWithPadSupport_returnsLow() {
        val evidence = LivenessEvidence(challengePassed = true, padScore = 0.60f, depthScore = 0.40f)
        assertEquals(LivenessRisk.LOW, RiskFusion.decide(evidence, policy))
    }

    @Test
    fun rule9_darkRoomFlashAndDepth_returnsLow() {
        val evidence = LivenessEvidence(flashScore = 0.80f, depthScore = 0.60f)
        assertEquals(LivenessRisk.LOW, RiskFusion.decide(evidence, policy))
    }

    @Test
    fun rule10_inconclusiveEvidence_returnsMedium() {
        val evidence = LivenessEvidence(padScore = 0.50f, depthInconclusive = true)
        assertEquals(LivenessRisk.MEDIUM, RiskFusion.decide(evidence, policy))
    }
}
