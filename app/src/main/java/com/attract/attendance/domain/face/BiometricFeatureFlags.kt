package com.attract.attendance.domain.face

/** Single switchboard for the doc-20 subsystems. Defaults = production intent. */
data class BiometricFeatureFlags(
    val lowLightEnhancement: Boolean = true,      // WP3
    val temporalDenoise: Boolean = true,          // WP3.3
    val faceMeteredExposure: Boolean = true,      // WP2
    val screenFlash: Boolean = true,              // WP2.4 / WP7.3
    val faceMesh: Boolean = true,                 // WP4 (MediaPipe)
    val padCnn: Boolean = true,                   // WP6
    val padEnforce: Boolean = false,              // WP6.6 - shadow mode first
    val parallaxDepth: Boolean = true,            // WP7.1
    val flashLiveness: Boolean = true,            // WP7.3
    val activeChallenge: Boolean = true,          // WP7.4
    val adaptiveTemplates: Boolean = true,        // WP5.3
    val flipTta: Boolean = false,                 // WP5.4 — OFF until calibrated
    val autoCapture: Boolean = true,              // WP8
) {
    companion object {
        val CURRENT = BiometricFeatureFlags()
    }
}
