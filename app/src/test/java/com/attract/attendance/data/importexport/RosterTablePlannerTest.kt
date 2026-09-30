package com.attract.attendance.data.importexport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RosterTablePlannerTest {

    private val planner = RosterTablePlanner()

    @Test
    fun validRosterWithCanonicalHeaders_parsesSuccessfully() {
        val rows = listOf(
            listOf("Roll Number", "Name", "Serial Number"),
            listOf("CS-101", "Alice Smith", "001"),
            listOf("CS-102", "Bob Jones", "002")
        )

        val result = planner.createPlan(rows)

        assertTrue(result is RosterParseResult.Success)
        val success = result as RosterParseResult.Success
        assertEquals(2, success.entries.size)
        assertEquals("CS-101", success.entries[0].rollNumber)
        assertEquals("Alice Smith", success.entries[0].name)
        assertEquals("001", success.entries[0].serialNumber)
        assertEquals(2, success.entries[0].sourceRow)
    }

    @Test
    fun validRosterWithAliasHeadersAndWhitespace_parsesSuccessfully() {
        val rows = listOf(
            listOf("STUDENT ID", "STUDENT NAME", "SNO"),
            listOf("101", "Charlie Brown", "S-1")
        )

        val result = planner.createPlan(rows)

        assertTrue(result is RosterParseResult.Success)
        val success = result as RosterParseResult.Success
        assertEquals(1, success.entries.size)
        assertEquals("101", success.entries[0].rollNumber)
        assertEquals("Charlie Brown", success.entries[0].name)
        assertEquals("S-1", success.entries[0].serialNumber)
    }

    @Test
    fun validRosterWithoutOptionalSerialColumn_parsesSuccessfully() {
        val rows = listOf(
            listOf("Roll", "Name"),
            listOf("101", "David Miller")
        )

        val result = planner.createPlan(rows)

        assertTrue(result is RosterParseResult.Success)
        val success = result as RosterParseResult.Success
        assertEquals(1, success.entries.size)
        assertEquals("101", success.entries[0].rollNumber)
        assertEquals("David Miller", success.entries[0].name)
        assertEquals(null, success.entries[0].serialNumber)
    }

    @Test
    fun missingNameColumn_returnsRejected() {
        val rows = listOf(
            listOf("Roll Number", "Serial Number"),
            listOf("101", "001")
        )

        val result = planner.createPlan(rows)

        assertTrue(result is RosterParseResult.Rejected)
        val rejected = result as RosterParseResult.Rejected
        assertTrue(rejected.message.lowercase().contains("name column"))
    }

    @Test
    fun missingRollColumn_returnsRejected() {
        val rows = listOf(
            listOf("Name", "Serial Number"),
            listOf("Alice", "001")
        )

        val result = planner.createPlan(rows)

        assertTrue(result is RosterParseResult.Rejected)
        val rejected = result as RosterParseResult.Rejected
        assertTrue(rejected.message.lowercase().contains("roll number column"))
    }

    @Test
    fun duplicateRollNumberInRoster_returnsRejectedWithErrorDetails() {
        val rows = listOf(
            listOf("Roll", "Name"),
            listOf("101", "Alice"),
            listOf("101", "Bob")
        )

        val result = planner.createPlan(rows)

        assertTrue(result is RosterParseResult.Rejected)
        val rejected = result as RosterParseResult.Rejected
        assertTrue(rejected.message.contains("duplicated"))
    }

    @Test
    fun invalidStudentNameOrRoll_returnsRejected() {
        val rows = listOf(
            listOf("Roll", "Name"),
            listOf("", "Alice"),
            listOf("102", "")
        )

        val result = planner.createPlan(rows)

        assertTrue(result is RosterParseResult.Rejected)
        val rejected = result as RosterParseResult.Rejected
        assertTrue(rejected.message.contains("Row 2:"))
        assertTrue(rejected.message.contains("Row 3:"))
    }

    @Test
    fun blankRowsAreSkipped() {
        val rows = listOf(
            listOf("Roll", "Name"),
            listOf("   ", "   "),
            listOf("101", "Alice")
        )

        val result = planner.createPlan(rows)

        assertTrue(result is RosterParseResult.Success)
        val success = result as RosterParseResult.Success
        assertEquals(1, success.entries.size)
        assertEquals(3, success.entries[0].sourceRow)
    }

    @Test
    fun noStudentRows_returnsRejected() {
        val rows = listOf(
            listOf("Roll", "Name")
        )

        val result = planner.createPlan(rows)

        assertTrue(result is RosterParseResult.Rejected)
        val rejected = result as RosterParseResult.Rejected
        assertTrue(rejected.message.contains("does not contain any student rows"))
    }
}
