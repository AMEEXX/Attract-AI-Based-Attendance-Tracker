package com.attract.attendance.domain.face

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateUpdatePolicyTest {

    @Test
    fun shouldAdd_validCandidate_accepted() {
        val accepted = TemplateUpdatePolicy.shouldAdd(
            score = 0.72f,
            margin = 0.20f,
            risk = "LOW",
            quality = 0.65f,
            maxSimToEnrolled = 0.60f,
            hoursSinceLastAuto = 24.0
        )
        assertTrue(accepted)
    }

    @Test
    fun shouldAdd_failsAnyBoundary_rejected() {
        // High risk
        assertFalse(
            TemplateUpdatePolicy.shouldAdd(
                score = 0.72f, margin = 0.20f, risk = "HIGH",
                quality = 0.65f, maxSimToEnrolled = 0.60f, hoursSinceLastAuto = 24.0
            )
        )

        // Low match score
        assertFalse(
            TemplateUpdatePolicy.shouldAdd(
                score = 0.60f, margin = 0.20f, risk = "LOW",
                quality = 0.65f, maxSimToEnrolled = 0.60f, hoursSinceLastAuto = 24.0
            )
        )

        // Low margin
        assertFalse(
            TemplateUpdatePolicy.shouldAdd(
                score = 0.72f, margin = 0.10f, risk = "LOW",
                quality = 0.65f, maxSimToEnrolled = 0.60f, hoursSinceLastAuto = 24.0
            )
        )

        // Anchor drift (dissimilar to enrolled)
        assertFalse(
            TemplateUpdatePolicy.shouldAdd(
                score = 0.72f, margin = 0.20f, risk = "LOW",
                quality = 0.65f, maxSimToEnrolled = 0.50f, hoursSinceLastAuto = 24.0
            )
        )

        // Too soon (< 20 hours)
        assertFalse(
            TemplateUpdatePolicy.shouldAdd(
                score = 0.72f, margin = 0.20f, risk = "LOW",
                quality = 0.65f, maxSimToEnrolled = 0.60f, hoursSinceLastAuto = 5.0
            )
        )

        // LivenessRisk overload
        assertTrue(
            TemplateUpdatePolicy.shouldAdd(
                score = 0.72f, margin = 0.20f,
                risk = com.attract.attendance.domain.face.liveness.LivenessRisk.LOW,
                quality = 0.65f, maxSimToEnrolled = 0.60f, hoursSinceLastAuto = 24.0
            )
        )
    }
}
