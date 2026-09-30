package com.attract.attendance.data.importexport

import android.content.ContentResolver
import android.net.Uri
import com.attract.attendance.core.model.RosterStudent
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface RosterParseResult {
    data class Success(val entries: List<RosterStudent>) : RosterParseResult
    data class Rejected(val message: String) : RosterParseResult
}

/** Safe CSV reader for rosters. It treats every cell as text and never evaluates formulas or macros. */
class CsvRosterImporter(
    private val contentResolver: ContentResolver,
) {
    private val planner = RosterTablePlanner()
    private val xlsxReader = XlsxRosterReader()

    suspend fun parse(uri: Uri): RosterParseResult = withContext(Dispatchers.IO) {
        runCatching {
            val input = contentResolver.openInputStream(uri) ?: throw RosterImportException("The selected file is no longer available.")
            input.use { stream ->
                if (uri.toString().lowercase().endsWith(".xlsx")) {
                    planner.createPlan(xlsxReader.read(stream))
                } else {
                    val text = readBoundedUtf8(stream)
                    planner.createPlan(parseCsv(text).map { it.values })
                }
            }
        }.getOrElse { error ->
            RosterParseResult.Rejected(error.message ?: "The roster could not be read. Choose another CSV or XLSX file.")
        }
    }

    private fun readBoundedUtf8(input: java.io.InputStream): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8_192)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (output.size() + read > maxBytes) throw RosterImportException("This file is larger than 5 MB.")
            output.write(buffer, 0, read)
        }
        return output.toString(Charsets.UTF_8.name())
    }

    private fun parseCsv(text: String): List<CsvRow> {
        val rows = mutableListOf<CsvRow>()
        val values = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var rowNumber = 1
        var index = 0

        fun finishField() {
            values += field.toString()
            field.clear()
        }
        fun finishRow() {
            finishField()
            rows += CsvRow(rowNumber, values.toList())
            values.clear()
            rowNumber += 1
        }

        while (index < text.length) {
            val char = text[index]
            when {
                inQuotes && char == '"' && index + 1 < text.length && text[index + 1] == '"' -> {
                    field.append('"')
                    index += 1
                }
                char == '"' -> inQuotes = !inQuotes
                inQuotes -> field.append(char)
                char == ',' -> finishField()
                char == '\n' -> finishRow()
                char == '\r' -> {
                    finishRow()
                    if (index + 1 < text.length && text[index + 1] == '\n') index += 1
                }
                else -> field.append(char)
            }
            index += 1
        }
        if (inQuotes) throw RosterImportException("The CSV has an unfinished quoted value.")
        if (field.isNotEmpty() || values.isNotEmpty()) finishRow()
        if (rows.isEmpty()) throw RosterImportException("The CSV is empty.")
        if (rows.size > maxRows + 1) throw RosterImportException("A roster can contain at most $maxRows students.")
        if (rows.any { it.values.size > maxColumns }) throw RosterImportException("A roster can contain at most $maxColumns columns.")
        return rows
    }

    private data class CsvRow(val number: Int, val values: List<String>)

    private companion object {
        const val maxBytes = 5 * 1024 * 1024
        const val maxRows = 2_000
        const val maxColumns = 50
    }
}

internal class RosterImportException(message: String) : IllegalArgumentException(message)
