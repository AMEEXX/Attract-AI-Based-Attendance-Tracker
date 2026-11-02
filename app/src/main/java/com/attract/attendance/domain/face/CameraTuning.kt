package com.attract.attendance.domain.face

/**
 * Pure camera parameter tuning functions for low-light adaptation (doc-20 WP2).
 * Designed for unit-testability without Android HAL dependencies.
 */
object CameraTuning {

    /**
     * Chooses an AE FPS range with the lowest lower bound >= 7 and upper bound >= 24.
     * Allows longer exposure times (up to ~100ms) under low ambient illumination.
     */
    fun chooseAeFpsRange(ranges: List<IntRange>): IntRange? {
        val candidates = ranges.filter { it.first >= 7 && it.last >= 24 }
        if (candidates.isEmpty()) return null
        return candidates.minWithOrNull(
            compareBy<IntRange> { it.first }.thenByDescending { it.last }
        )
    }

    /**
     * Returns the next Exposure Value (EV) compensation index.
     * Target face luma is 110 ± 20. Steps by at most 1 index per call to prevent flicker.
     */
    fun nextExposureIndex(faceLuma: Float, current: Int, range: IntRange): Int = when {
        faceLuma < 90f && current < range.last -> current + 1
        faceLuma > 150f && current > range.first -> current - 1
        else -> current
    }

    /**
     * Determines whether screen flash assistance should be activated.
     * Trigger rule: face luma < 60 while EV compensation is at maximum, or face luma < 35 regardless.
     * Hysteresis: once active, turns off only when face luma exceeds 85.
     */
    fun shouldUseScreenFlash(
        faceLuma: Float,
        evIndex: Int,
        evMax: Int,
        currentlyActive: Boolean = false,
    ): Boolean {
        return if (currentlyActive) {
            faceLuma <= 85f
        } else {
            faceLuma < 35f || (faceLuma < 60f && evIndex >= evMax)
        }
    }
}
