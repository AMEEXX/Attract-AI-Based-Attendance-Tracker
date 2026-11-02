package com.attract.attendance.domain.face

/**
 * Adaptive template update policy (Face ID-style memory over time, doc-20 WP5.3).
 * Governs when a verified high-confidence live attendance match is safely retained
 * to adapt to changing ambient lighting, haircuts, and natural camera angles.
 */
object TemplateUpdatePolicy {
    const val MIN_SCORE = 0.65f          // Match must be clearly above accept (0.50)
    const val MIN_MARGIN = 0.15f         // and clearly above runner-up
    const val MIN_ANCHOR_SIM = 0.55f     // Must still resemble canonical ENROLLED templates (drift guard)
    const val MAX_AUTO_PER_BUCKET = 2
    const val MAX_AUTO_TOTAL = 8
    const val MIN_HOURS_BETWEEN = 20     // At most ~1 update per student per day

    fun shouldAdd(
        score: Float,
        margin: Float,
        risk: String,
        quality: Float,
        maxSimToEnrolled: Float,
        hoursSinceLastAuto: Double,
    ): Boolean =
        risk == "LOW" &&
            score >= MIN_SCORE &&
            margin >= MIN_MARGIN &&
            quality >= 0.55f &&
            maxSimToEnrolled >= MIN_ANCHOR_SIM &&
            hoursSinceLastAuto >= MIN_HOURS_BETWEEN

    fun shouldAdd(
        score: Float,
        margin: Float,
        risk: com.attract.attendance.domain.face.liveness.LivenessRisk,
        quality: Float,
        maxSimToEnrolled: Float,
        hoursSinceLastAuto: Double,
    ): Boolean =
        shouldAdd(score, margin, risk.name, quality, maxSimToEnrolled, hoursSinceLastAuto)
}
