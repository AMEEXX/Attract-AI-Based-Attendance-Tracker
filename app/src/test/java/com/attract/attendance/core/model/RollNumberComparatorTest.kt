package com.attract.attendance.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RollNumberComparatorTest {

    @Test
    fun sortsNumericAscending() {
        val rolls = listOf("10", "2", "1", "20", "11", "3")
        val sorted = rolls.sortedWith(RollNumberComparator)
        assertEquals(listOf("1", "2", "3", "10", "11", "20"), sorted)
    }

    @Test
    fun sortsAlphanumericPrefixAscending() {
        val rolls = listOf("CS-10", "CS-2", "CS-1", "IT-1", "CS-20")
        val sorted = rolls.sortedWith(RollNumberComparator)
        assertEquals(listOf("CS-1", "CS-2", "CS-10", "CS-20", "IT-1"), sorted)
    }

    @Test
    fun handlesNullAndEmpty() {
        val rolls = listOf("2", null, "1", "")
        val sorted = rolls.sortedWith(RollNumberComparator)
        assertEquals(listOf(null, "", "1", "2"), sorted)
    }
}
