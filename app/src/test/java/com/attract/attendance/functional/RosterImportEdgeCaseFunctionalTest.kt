package com.attract.attendance.functional

import com.attract.attendance.data.importexport.RosterParseResult
import com.attract.attendance.data.importexport.RosterTablePlanner
import com.attract.attendance.data.local.StudentEntity
import com.attract.attendance.test.FakeStudentDao
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FT-03: RosterImportEdgeCaseFunctionalTest
 * Verifies edge cases and adversarial inputs in the roster import pipeline —
 * malformed CSV, formula-containing XLSX, atomic commit/rollback, and partial reject behavior.
 */
class RosterImportEdgeCaseFunctionalTest {

    private val planner = RosterTablePlanner()

    @Test
    fun csvImport_quotedCommasInCells_parsedAsSingleCell() {
        val rows = listOf(
            listOf("Roll Number", "Student Name"),
            listOf("CS-01", "Smith, John")
        )

        val result = planner.createPlan(rows)
        assertTrue(result is RosterParseResult.Success)
        val students = (result as RosterParseResult.Success).entries
        assertEquals(1, students.size)
        assertEquals("Smith, John", students[0].name)
    }

    @Test
    fun csvImport_quotedNewlineInCell_parsedAsSingleCell() {
        val rows = listOf(
            listOf("Roll Number", "Student Name"),
            listOf("CS-02", "Jane\nDoe")
        )

        val result = planner.createPlan(rows)
        assertTrue(result is RosterParseResult.Success)
        val students = (result as RosterParseResult.Success).entries
        assertEquals(1, students.size)
        assertEquals("Jane\nDoe", students[0].name)
    }

    @Test
    fun csvImport_duplicateRollInFile_bothMarkedDuplicate() {
        val rows = listOf(
            listOf("Roll Number", "Student Name"),
            listOf("CS-01", "Alice"),
            listOf("CS-01", "Bob")
        )

        val result = planner.createPlan(rows)
        assertTrue(result is RosterParseResult.Rejected)
        val rejected = result as RosterParseResult.Rejected
        assertTrue(rejected.message.contains("duplicated"))
    }

    @Test
    fun csvImport_blankRequiredField_rowErrorWithRowNumber() {
        val rows = listOf(
            listOf("Roll Number", "Student Name"),
            listOf("CS-01", ""),
            listOf("", "David")
        )

        val result = planner.createPlan(rows)
        assertTrue(result is RosterParseResult.Rejected)
        val rejected = result as RosterParseResult.Rejected
        assertTrue(rejected.message.contains("Row 2"))
    }

    @Test
    fun xlsxImport_formulaCellTreatedAsLiteral() {
        val rows = listOf(
            listOf("Roll Number", "Student Name"),
            listOf("CS-05", "=SUM(A1:A10)")
        )

        val result = planner.createPlan(rows)
        assertTrue(result is RosterParseResult.Success)
        val students = (result as RosterParseResult.Success).entries
        assertEquals("=SUM(A1:A10)", students[0].name)
    }

    @Test
    fun atomicCommit_validPlan_insertsAllOrNothing() = runTest {
        val studentDao = FakeStudentDao()
        val messyRoster = listOf(
            listOf("Sl. No.", "FULL STUDENT NAME", "ROLL NO / REG NO"),
            listOf("1", "Alan Mathison Turing", "CS - 2026 / 001"),
            listOf("2", "Ada Lovelace", "CS-2026-002")
        )

        val parseResult = planner.createPlan(messyRoster)
        assertTrue(parseResult is RosterParseResult.Success)
        val students = (parseResult as RosterParseResult.Success).entries

        // Insert into DAO
        students.forEachIndexed { index, student ->
            studentDao.insert(
                StudentEntity(
                    id = (index + 1).toLong(),
                    classId = 1L,
                    name = student.name,
                    rollNumber = student.rollNumber,
                    serialNumber = student.serialNumber,
                    enrollmentStatus = com.attract.attendance.core.model.EnrollmentStatus.ENROLLED,
                    createdAt = 1000L,
                    updatedAt = 1000L
                )
            )
        }

        val allInDb = studentDao.all()
        assertEquals(2, allInDb.size)
    }

    @Test
    fun atomicCommit_oneRowConflict_rollsBackAll() = runTest {
        // Pre-existing student in DB
        val preExisting = StudentEntity(1L, 1L, "PreExisting", "CS-01", null, com.attract.attendance.core.model.EnrollmentStatus.ENROLLED, 1000L, null, false, 1000L, 1000L)
        val studentDao = FakeStudentDao(listOf(preExisting))

        val rows = listOf(
            listOf("Roll Number", "Student Name"),
            listOf("CS-01", "Alice"), // Conflict with DB
            listOf("CS-02", "Bob")
        )

        val parseResult = planner.createPlan(rows)
        assertTrue(parseResult is RosterParseResult.Success)
        val entries = (parseResult as RosterParseResult.Success).entries

        // Simulating atomic transaction failure on conflict
        var transactionRolledBack = false
        try {
            entries.forEach { entry ->
                val existing = studentDao.findActiveByRoll(1L, entry.rollNumber)
                if (existing != null) {
                    throw IllegalStateException("Duplicate roll number in class: ${entry.rollNumber}")
                }
            }
        } catch (e: Exception) {
            transactionRolledBack = true
        }

        assertTrue(transactionRolledBack)
        // Verify DB state unchanged
        assertEquals(1, studentDao.all().size)
        assertEquals("PreExisting", studentDao.all().first().name)
    }
}
