package com.attract.attendance.domain.face

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraTuningTest {

    @Test
    fun chooseAeFpsRange_selectsLowestLowerBoundMeetingCriteria() {
        val ranges = listOf(
            IntRange(15, 15),
            IntRange(7, 30),
            IntRange(10, 30),
            IntRange(30, 30)
        )
        val selected = CameraTuning.chooseAeFpsRange(ranges)
        assertEquals(IntRange(7, 30), selected)
    }

    @Test
    fun chooseAeFpsRange_returnsNullWhenNoRangeMeetsCriteria() {
        val ranges = listOf(
            IntRange(30, 30), // first >= 7 and last >= 24, wait: this actually meets criteria (30 >= 7 and 30 >= 24)
            IntRange(5, 15)   // first < 7, last < 24
        )
        // A list with only invalid ranges:
        val onlyInvalid = listOf(
            IntRange(4, 20),
            IntRange(5, 15)
        )
        assertNull(CameraTuning.chooseAeFpsRange(onlyInvalid))
    }

    @Test
    fun nextExposureIndex_stepsTowardsTargetLuma() {
        val range = IntRange(-4, 4)
        // Too dark: increments EV index
        assertEquals(1, CameraTuning.nextExposureIndex(75f, current = 0, range = range))
        // At max: does not exceed range.last
        assertEquals(4, CameraTuning.nextExposureIndex(75f, current = 4, range = range))

        // Too bright: decrements EV index
        assertEquals(-1, CameraTuning.nextExposureIndex(160f, current = 0, range = range))
        // At min: does not decrease below range.first
        assertEquals(-4, CameraTuning.nextExposureIndex(160f, current = -4, range = range))

        // Within target window (90..150): holds steady
        assertEquals(2, CameraTuning.nextExposureIndex(110f, current = 2, range = range))
    }

    @Test
    fun shouldUseScreenFlash_appliesHysteresisAndThresholds() {
        // Very low light: activates immediately
        assertTrue(CameraTuning.shouldUseScreenFlash(faceLuma = 30f, evIndex = 0, evMax = 4, currentlyActive = false))

        // Moderate low light without max EV: does not activate
        assertFalse(CameraTuning.shouldUseScreenFlash(faceLuma = 55f, evIndex = 2, evMax = 4, currentlyActive = false))

        // Moderate low light with max EV reached: activates
        assertTrue(CameraTuning.shouldUseScreenFlash(faceLuma = 55f, evIndex = 4, evMax = 4, currentlyActive = false))

        // Hysteresis: stays active until luma > 85
        assertTrue(CameraTuning.shouldUseScreenFlash(faceLuma = 75f, evIndex = 4, evMax = 4, currentlyActive = true))
        assertFalse(CameraTuning.shouldUseScreenFlash(faceLuma = 90f, evIndex = 4, evMax = 4, currentlyActive = true))
    }
}
