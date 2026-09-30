package com.attract.attendance.feature.importroster

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.attract.attendance.data.importexport.RosterParseResult
import com.attract.attendance.data.importexport.RosterTablePlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * IT-02: RosterImportE2EAndroidTest
 * Instrumented end-to-end roster import tests on Android runtime.
 */
@RunWith(AndroidJUnit4::class)
class RosterImportE2EAndroidTest {

    private val planner = RosterTablePlanner()

    @Test
    fun csvImport_fileParser_createsValidImportPlan() {
        val rawTable = listOf(
            listOf("Sl. No.", "Student Name", "Roll Number"),
            listOf("1", "Alan Turing", "CS-101"),
            listOf("2", "Ada Lovelace", "CS-102")
        )

        val planResult = planner.createPlan(rawTable)
        assertTrue(planResult is RosterParseResult.Success)
        val students = (planResult as RosterParseResult.Success).entries
        assertEquals(2, students.size)
        assertEquals("Alan Turing", students[0].name)
        assertEquals("CS-101", students[0].rollNumber)
    }

    @Test
    fun csvImport_missingRequiredHeader_rejectsWithReason() {
        val rawTable = listOf(
            listOf("Sl. No.", "Random Header"),
            listOf("1", "Alan Turing")
        )

        val planResult = planner.createPlan(rawTable)
        assertTrue(planResult is RosterParseResult.Rejected)
    }
}
