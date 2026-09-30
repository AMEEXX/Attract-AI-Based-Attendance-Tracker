package com.attract.attendance.data.importexport

import com.attract.attendance.core.model.RosterStudent
import com.attract.attendance.domain.Validators

internal class RosterTablePlanner {
    fun createPlan(rows: List<List<String>>): RosterParseResult {
        return try {
            val headers = rows.first().map(::normaliseHeader)
            val nameColumn = singleColumn(headers, nameAliases, "name") ?: return RosterParseResult.Rejected("Map one Name column, then try again.")
            val rollColumn = singleColumn(headers, rollAliases, "roll number") ?: return RosterParseResult.Rejected("Map one Roll Number or Student ID column, then try again.")
            val serialColumn = singleColumn(headers, serialAliases, "serial number", required = false)
            val errors = mutableListOf<String>()
            val seenRolls = mutableSetOf<String>()
            val entries = mutableListOf<RosterStudent>()

            rows.drop(1).forEachIndexed { index, row ->
                if (row.all { it.isBlank() }) return@forEachIndexed
                val sourceRow = index + 2
                val rawName = row.getOrNull(nameColumn).orEmpty()
                val rawRoll = row.getOrNull(rollColumn).orEmpty()
                val name = Validators.studentName(rawName).getOrNull()
                val roll = Validators.rollNumber(rawRoll).getOrNull()
                when {
                    name == null -> errors += "Row $sourceRow: Student name is missing or too long."
                    roll == null -> errors += "Row $sourceRow: Roll number is missing or too long."
                    !seenRolls.add(roll) -> errors += "Row $sourceRow: Roll number $roll is duplicated in this file."
                    else -> entries += RosterStudent(
                        sourceRow = sourceRow,
                        name = name,
                        rollNumber = roll,
                        serialNumber = serialColumn?.let { row.getOrNull(it)?.trim()?.takeIf(String::isNotBlank) },
                    )
                }
            }
            if (entries.isEmpty() && errors.isEmpty()) errors += "The roster does not contain any student rows."
            if (errors.isEmpty()) RosterParseResult.Success(entries) else RosterParseResult.Rejected(errors.take(5).joinToString("\n"))
        } catch (error: RosterImportException) {
            RosterParseResult.Rejected(error.message ?: "Invalid roster")
        }
    }

    private fun singleColumn(headers: List<String>, aliases: Set<String>, label: String, required: Boolean = true): Int? {
        val matches = headers.indices.filter { headers[it] in aliases }
        return when {
            matches.size == 1 -> matches.single()
            !required && matches.isEmpty() -> null
            else -> throw RosterImportException("The roster must have exactly one $label column.")
        }
    }

    private fun normaliseHeader(value: String): String = value.lowercase().filter(Char::isLetterOrDigit)

    private companion object {
        val nameAliases = setOf("name", "studentname", "fullstudentname", "fullname", "student")
        val rollAliases = setOf("roll", "rollnumber", "rollno", "studentid", "id", "rollnoregno", "regno", "registrationno", "registrationnumber")
        val serialAliases = setOf("slno", "sno", "serial", "serialnumber", "slnumber")
    }
}
