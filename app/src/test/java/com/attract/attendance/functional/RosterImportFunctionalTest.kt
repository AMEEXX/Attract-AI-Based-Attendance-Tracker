package com.attract.attendance.functional

import com.attract.attendance.data.importexport.RosterParseResult
import com.attract.attendance.data.importexport.RosterTablePlanner
import com.attract.attendance.domain.NormalizeRollNumber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Stage 2 Functional Test: Roster Import & Roster Table Planner
 * Verifies parsing, heuristic header identification, name/roll validation,
 * and roll number normalization across realistic messy CSV input data.
 */
class RosterImportFunctionalTest {

    @Test
    fun rosterImportFlow_messyInputData_parsesAndNormalizesRoster() {
        val planner = RosterTablePlanner()
        val messyRoster = listOf(
            listOf("Sl. No.", "FULL STUDENT NAME", "ROLL NO / REG NO"),
            listOf("1", "   Alan Mathison Turing   ", "  CS - 2026 / 001  "),
            listOf("2", "Ada Lovelace", "cs/2026/002"),
            listOf("3", "Grace Hopper", "CS2026003")
        )

        val result = planner.createPlan(messyRoster)

        assertTrue(result is RosterParseResult.Success)
        val students = (result as RosterParseResult.Success).entries

        assertEquals(3, students.size)

        // Normalize roll numbers and verify
        val normalizedRolls = students.map { NormalizeRollNumber.normalizeForDisplay(it.rollNumber) }

        assertEquals("Alan Mathison Turing", students[0].name)
        assertEquals("CS - 2026 / 001", normalizedRolls[0])

        assertEquals("Ada Lovelace", students[1].name)
        assertEquals("CS/2026/002", normalizedRolls[1])

        assertEquals("Grace Hopper", students[2].name)
        assertEquals("CS2026003", normalizedRolls[2])
    }
}
