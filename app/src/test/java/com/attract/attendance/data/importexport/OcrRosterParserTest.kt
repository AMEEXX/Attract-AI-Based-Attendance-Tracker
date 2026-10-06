package com.attract.attendance.data.importexport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class OcrRosterParserTest {

    private val parser = OcrRosterParser()

    @Test
    fun extractStudentFromLine_numberedEntry() {
        val line = "1. 21CS001 Alex Sharma"
        val student = parser.extractStudentFromLine(line, 1)
        assertNotNull(student)
        assertEquals("Alex Sharma", student?.name)
        assertEquals("21CS001", student?.rollNumber)
    }

    @Test
    fun extractStudentFromLine_numericRollFirst() {
        val line = "101 Bhavna Patel"
        val student = parser.extractStudentFromLine(line, 2)
        assertNotNull(student)
        assertEquals("Bhavna Patel", student?.name)
        assertEquals("101", student?.rollNumber)
    }

    @Test
    fun extractStudentFromLine_nameThenRoll() {
        val line = "Chirag Verma  21CS003"
        val student = parser.extractStudentFromLine(line, 3)
        assertNotNull(student)
        assertEquals("Chirag Verma", student?.name)
        assertEquals("21CS003", student?.rollNumber)
    }

    @Test
    fun extractStudentFromLine_ignoresHeaderLine() {
        val line = "Roll No    Student Name   Signature"
        val student = parser.extractStudentFromLine(line, 1)
        assertNull(student)
    }

    @Test
    fun extractStudentFromLine_alphanumericWithPunctuation() {
        val line = "3 - IT-2024-042  Drishya Nair"
        val student = parser.extractStudentFromLine(line, 4)
        assertNotNull(student)
        assertEquals("Drishya Nair", student?.name)
        assertEquals("IT-2024-042", student?.rollNumber)
    }
}
