package com.attract.attendance.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidatorsTest {
    @Test
    fun normalizeRollNumber_collapsesWhitespaceAndUsesCaseInsensitiveForm() {
        assertEquals("CS 101 A", Validators.normalizeRollNumber("  cs   101 a  "))
    }

    @Test
    fun rollNumber_blankValue_isRejected() {
        assertTrue(Validators.rollNumber("   ").isFailure)
    }

    @Test
    fun percentage_onlyAcceptsInclusiveRange() {
        assertTrue(Validators.percentage(0).isSuccess)
        assertTrue(Validators.percentage(100).isSuccess)
        assertTrue(Validators.percentage(-1).isFailure)
        assertTrue(Validators.percentage(101).isFailure)
    }

    @Test
    fun studentName_validatesLengthAndBlank() {
        assertTrue(Validators.studentName("John Doe").isSuccess)
        assertTrue(Validators.studentName("   ").isFailure)
        assertTrue(Validators.studentName("A".repeat(161)).isFailure)
        assertTrue(Validators.studentName("A".repeat(160)).isSuccess)
    }

    @Test
    fun className_validatesLengthAndBlank() {
        assertTrue(Validators.className("Computer Science 101").isSuccess)
        assertTrue(Validators.className("   ").isFailure)
        assertTrue(Validators.className("B".repeat(121)).isFailure)
        assertTrue(Validators.className("B".repeat(120)).isSuccess)
    }
}
