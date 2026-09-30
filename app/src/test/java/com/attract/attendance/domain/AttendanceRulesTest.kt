package com.attract.attendance.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AttendanceRulesTest {
    @Test
    fun isEligible_originalRosterStudent_isAlwaysEligible() {
        assertTrue(AttendanceRules.isEligible(null, 1))
        assertTrue(AttendanceRules.isEligible(null, 500))
    }

    @Test
    fun isEligible_lateJoiner_excludesEarlierSessions() {
        assertFalse(AttendanceRules.isEligible(11, 10))
        assertTrue(AttendanceRules.isEligible(11, 11))
        assertTrue(AttendanceRules.isEligible(11, 12))
    }

    @Test
    fun percentage_noClassesHeld_hasHumanSafeDisplay() {
        assertEquals("No classes held", AttendanceRules.percentage(0, 0).display)
    }

    @Test
    fun percentage_roundsOnlyForDisplay() {
        assertEquals("66.7%", AttendanceRules.percentage(2, 3).display)
    }
}
