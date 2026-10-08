package com.attract.attendance.data.importexport

import org.junit.Assert.assertEquals
import org.junit.Test

class RosterGeometryTest {

    /** Every word is 20px tall; [row] places it on a 40px grid. */
    private fun w(text: String, left: Int, right: Int, row: Int) =
        OcrWord(text, left, row * 40, right, row * 40 + 20)

    @Test
    fun `reads a table row by row even when words arrive column by column`() {
        val words = listOf(
            // header
            w("S.No", 10, 60, 0), w("Roll", 100, 140, 0), w("No", 150, 175, 0),
            w("Student", 300, 380, 0), w("Name", 395, 440, 0), w("Signature", 600, 700, 0),
            // rows
            w("1", 10, 25, 1), w("B123001", 100, 200, 1), w("Sheetal", 300, 380, 1), w("Das", 395, 430, 1),
            w("2", 10, 25, 2), w("B123002", 100, 200, 2), w("Amit", 300, 350, 2), w("Kumar", 365, 430, 2), w("Hota", 445, 500, 2),
            w("3", 10, 25, 3), w("B123003", 100, 200, 3), w("K.", 300, 320, 3), w("Sahoo", 330, 400, 3)
        ).sortedBy { it.left }   // column-major order, like ML Kit often produces for tables

        val rows = RosterGeometry.rowsFromWords(words)

        assertEquals(3, rows.size)
        assertEquals(listOf("B123001", "B123002", "B123003"), rows.map { it.roll })
        assertEquals(listOf("Sheetal Das", "Amit Kumar Hota", "K. Sahoo"), rows.map { it.name })
        assertEquals(listOf("1", "2", "3"), rows.map { it.serial })
    }

    @Test
    fun `names that contain a header word are not dropped`() {
        val rows = RosterGeometry.rowsFromWords(
            listOf(w("B123009", 100, 200, 1), w("Sheetal", 300, 380, 1))
        )
        assertEquals(1, rows.size)
        assertEquals("Sheetal", rows[0].name)
    }

    @Test
    fun `name normalisation keeps initials and apostrophes`() {
        assertEquals("D'Souza", RosterGeometry.normalizeName("D'SOUZA"))
        assertEquals("Amit Kumar Hota", RosterGeometry.normalizeName("amit kumar hota"))
        assertEquals("K. Sahoo", RosterGeometry.normalizeName("K. Sahoo"))
        assertEquals("McDonald", RosterGeometry.normalizeName("McDonald"))
        assertEquals("Anil Kumar", RosterGeometry.normalizeName("| Anil  Kumar ,"))
    }
}
