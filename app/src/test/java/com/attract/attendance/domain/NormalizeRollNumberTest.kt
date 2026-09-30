package com.attract.attendance.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class NormalizeRollNumberTest {

    @Test
    fun trimsLeadingAndTrailingWhitespace() {
        assertEquals("CS-101", NormalizeRollNumber.normalizeForDisplay("  CS-101  "))
    }

    @Test
    fun collapsesMultipleInternalSpaces() {
        assertEquals("CS 101 B", NormalizeRollNumber.normalizeForDisplay("CS   101   B"))
    }

    @Test
    fun comparisonNormalizationConvertsToLowercase() {
        assertEquals("cs 101 b", NormalizeRollNumber.normalizeForComparison(" CS   101   B "))
    }

    @Test(expected = IllegalArgumentException::class)
    fun emptyRollNumberThrowsException() {
        NormalizeRollNumber.normalizeForDisplay("   ")
    }
}
