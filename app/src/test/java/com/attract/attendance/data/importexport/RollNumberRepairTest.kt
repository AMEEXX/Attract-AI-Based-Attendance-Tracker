package com.attract.attendance.data.importexport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RollNumberRepairTest {

    private val base = (1..10).map { "B123" + it.toString().padStart(3, '0') }

    private fun run(vararg overrides: Pair<Int, String>): List<RepairedRow> {
        val rolls = base.toMutableList()
        overrides.forEach { (i, v) -> rolls[i] = v }
        return RollNumberRepair.repair(rolls.map { RawRosterRow(roll = it, name = "Student") })
    }

    @Test
    fun `learns the pattern`() {
        assertEquals("B1230##", RollNumberRepair.describePattern(base))
    }

    @Test
    fun `restores a lost leading letter`() {
        val out = run(5 to "123006")
        assertEquals("B123006", out[5].roll)
        assertEquals(RollStatus.FIXED, out[5].status)
    }

    @Test
    fun `fixes letter O read in a digit position`() {
        val out = run(7 to "B1230O8")
        assertEquals("B123008", out[7].roll)
        assertEquals(RollStatus.FIXED, out[7].status)
    }

    @Test
    fun `fixes 8 read instead of B`() {
        val out = run(0 to "8123001")
        assertEquals("B123001", out[0].roll)
        assertEquals(RollStatus.FIXED, out[0].status)
    }

    @Test
    fun `fills an illegible character inside the fixed prefix`() {
        val out = run(4 to "B12?005")
        assertEquals("B123005", out[4].roll)
        assertEquals(RollStatus.FIXED, out[4].status)
    }

    @Test
    fun `infers a missing roll only when both neighbours pin it down`() {
        val out = run(3 to "")
        assertEquals("B123004", out[3].roll)
        assertEquals(RollStatus.INFERRED, out[3].status)
        assertTrue(out[3].needsReview)
    }

    @Test
    fun `uses a partially read roll as a wildcard check`() {
        val out = run(3 to "B1230?4")
        assertEquals("B123004", out[3].roll)
        assertEquals(RollStatus.INFERRED, out[3].status)
    }

    @Test
    fun `corrects a single wrong character when the sequence agrees`() {
        val out = run(3 to "B129004")
        assertEquals("B123004", out[3].roll)
        assertEquals(RollStatus.INFERRED, out[3].status)
    }

    @Test
    fun `last row unreadable gets a hint but is not auto-filled`() {
        val out = run(9 to "")
        assertEquals("", out[9].roll)
        assertEquals(RollStatus.MISSING, out[9].status)
        assertTrue(out[9].note.contains("B123010"))
    }

    @Test
    fun `ambiguous extra character is left alone`() {
        val out = run(2 to "B1230013")
        assertEquals(RollStatus.SUSPECT, out[2].status)
        assertEquals("B1230013", out[2].roll)
    }

    @Test
    fun `duplicated leading letter is dropped`() {
        val out = run(2 to "BB123003")
        assertEquals("B123003", out[2].roll)
        assertEquals(RollStatus.FIXED, out[2].status)
    }

    @Test
    fun `duplicates are flagged not dropped`() {
        val out = run(6 to "B123003")
        assertEquals(RollStatus.SUSPECT, out[2].status)
        assertEquals(RollStatus.SUSPECT, out[6].status)
        assertEquals(10, out.size)
    }

    @Test
    fun `short numeric rolls are left untouched`() {
        val out = RollNumberRepair.repair((1..30).map { RawRosterRow(roll = it.toString(), name = "S") })
        assertNull(RollNumberRepair.describePattern((1..30).map { it.toString() }))
        assertTrue(out.all { it.status == RollStatus.OK })
    }

    @Test
    fun `two cohorts on one sheet are not forced into one pattern`() {
        val rolls = (1..5).map { "B123" + it.toString().padStart(3, '0') } +
            (1..5).map { "B124" + it.toString().padStart(3, '0') }
        val out = RollNumberRepair.repair(rolls.map { RawRosterRow(roll = it, name = "S") })
        assertTrue(out.all { it.status == RollStatus.OK })
    }
}
