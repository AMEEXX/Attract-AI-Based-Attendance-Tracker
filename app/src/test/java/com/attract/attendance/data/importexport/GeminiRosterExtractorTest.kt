package com.attract.attendance.data.importexport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiRosterExtractorTest {

    private val extractor = GeminiRosterExtractor()

    @Test
    fun parse_standardRowsObject_returnsParsedStudents() {
        val json = """
            {
                "rows": [
                    { "serial": "1", "roll": "21BCE1001", "name": "Aarav Sharma", "nameConfidence": "high" },
                    { "serial": "2", "roll": "21BCE1002", "name": "Bhavya Patel", "nameConfidence": "low" }
                ]
            }
        """.trimIndent()

        val rows = extractor.parse(json)
        assertEquals(2, rows.size)
        assertEquals("1", rows[0].serial)
        assertEquals("21BCE1001", rows[0].roll)
        assertEquals("Aarav Sharma", rows[0].name)
        assertFalse(rows[0].nameUncertain)

        assertEquals("2", rows[1].serial)
        assertEquals("21BCE1002", rows[1].roll)
        assertEquals("Bhavya Patel", rows[1].name)
        assertTrue(rows[1].nameUncertain)
    }

    @Test
    fun parse_wrappedInMarkdownCodeFences_parsesSuccessfully() {
        val markdownJson = """
            ```json
            {
                "rows": [
                    { "serial": "10", "roll": "CS010", "name": "Deepak Kumar", "nameConfidence": "high" }
                ]
            }
            ```
        """.trimIndent()

        val rows = extractor.parse(markdownJson)
        assertEquals(1, rows.size)
        assertEquals("CS010", rows[0].roll)
        assertEquals("Deepak Kumar", rows[0].name)
    }

    @Test
    fun parse_directJsonArray_parsesSuccessfully() {
        val arrayJson = """
            [
                { "serial": 1, "roll": "CS001", "name": "Anita Roy", "nameConfidence": 1.0 },
                { "serial": 2, "roll": "CS002", "name": "Bina Das", "nameConfidence": "low" }
            ]
        """.trimIndent()

        val rows = extractor.parse(arrayJson)
        assertEquals(2, rows.size)
        assertEquals("1", rows[0].serial)
        assertEquals("CS001", rows[0].roll)
        assertEquals("Anita Roy", rows[0].name)
        assertFalse(rows[0].nameUncertain)

        assertEquals("2", rows[1].serial)
        assertEquals("CS002", rows[1].roll)
        assertEquals("Bina Das", rows[1].name)
        assertTrue(rows[1].nameUncertain)
    }

    @Test
    fun parse_alternativeStudentsKey_parsesSuccessfully() {
        val json = """
            {
                "students": [
                    { "roll": "R101", "name": "Chetan Bhagat" }
                ]
            }
        """.trimIndent()

        val rows = extractor.parse(json)
        assertEquals(1, rows.size)
        assertEquals("R101", rows[0].roll)
        assertEquals("Chetan Bhagat", rows[0].name)
    }

    @Test
    fun parse_conversationalPreamble_extractsJsonSafely() {
        val text = """
            Here is the extracted roster from the image:
            {
                "rows": [
                    { "roll": "ROLL-99", "name": "Final Student" }
                ]
            }
            Hope this helps!
        """.trimIndent()

        val rows = extractor.parse(text)
        assertEquals(1, rows.size)
        assertEquals("ROLL-99", rows[0].roll)
        assertEquals("Final Student", rows[0].name)
    }

    @Test
    fun parse_emptyOrBlank_returnsEmptyListWithoutThrowing() {
        assertTrue(extractor.parse("").isEmpty())
        assertTrue(extractor.parse("   ").isEmpty())
        assertTrue(extractor.parse("No JSON found here").isEmpty())
    }
}
