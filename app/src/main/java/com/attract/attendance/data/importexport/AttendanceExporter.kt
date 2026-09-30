package com.attract.attendance.data.importexport

import android.content.ContentResolver
import android.net.Uri
import com.attract.attendance.core.model.AttendancePercentage
import com.attract.attendance.data.local.ClassReportStudentRow
import com.attract.attendance.data.local.SessionExportRow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AttendanceExporter(
    private val contentResolver: ContentResolver? = null,
) {
    suspend fun exportClassReport(
        uri: Uri,
        className: String,
        generatedAtMillis: Long,
        reportRows: List<ClassReportStudentRow>,
        sessionRows: List<SessionExportRow>,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = contentResolver ?: error("The destination is unavailable.")
            val output = resolver.openOutputStream(uri) ?: error("The destination is unavailable.")
            output.use { stream ->
                stream.write(buildCsv(className, generatedAtMillis, reportRows, sessionRows).toByteArray(Charsets.UTF_8))
            }
        }
    }

    internal fun buildCsv(
        className: String,
        generatedAtMillis: Long,
        reportRows: List<ClassReportStudentRow>,
        sessionRows: List<SessionExportRow>,
    ): String = buildString {
        appendLine("Attract Attendance Export")
        appendCsvRow(listOf("Class", className))
        appendCsvRow(listOf("Generated At", timestamp(generatedAtMillis)))
        appendLine()
        appendCsvRow(listOf("Roll Number", "Name", "Serial Number", "Enrollment", "Present", "Eligible", "Attendance %"))
        reportRows.forEach { row ->
            appendCsvRow(
                listOf(
                    row.rollNumber,
                    row.name,
                    row.serialNumber.orEmpty(),
                    row.enrollmentStatus.name,
                    row.presentSessions.toString(),
                    row.eligibleSessions.toString(),
                    AttendancePercentage(row.presentSessions, row.eligibleSessions).display,
                ),
            )
        }

        val distinctSessions = sessionRows.distinctBy { Pair(it.sessionId, it.sessionDate) }
            .sortedBy { it.sessionDate }

        if (distinctSessions.isNotEmpty()) {
            appendLine()
            appendCsvRow(listOf("Per-Session Attendance Matrix"))
            val dateHeaders = distinctSessions.map { "${it.sessionDate} (${it.mode.name})" }
            val headerRow = listOf("Roll Number", "Name", "Serial Number") + dateHeaders + listOf("Attendance %")
            appendCsvRow(headerRow)

            reportRows.forEach { student ->
                val dateStatuses = distinctSessions.map { session ->
                    val record = sessionRows.find { it.sessionId == session.sessionId && it.rollNumber == student.rollNumber }
                    if (record?.status?.name == "PRESENT") "P" else "A"
                }
                val percentage = AttendancePercentage(student.presentSessions, student.eligibleSessions).display
                val studentRow = listOf(student.rollNumber, student.name, student.serialNumber.orEmpty()) + dateStatuses + listOf(percentage)
                appendCsvRow(studentRow)
            }
        }

        appendLine()
        appendCsvRow(listOf("Session ID", "Date", "Mode", "Roll Number", "Name", "Serial Number", "Status", "Method", "Check-in Time"))
        sessionRows.forEach { row ->
            appendCsvRow(
                listOf(
                    row.sessionId.toString(),
                    row.sessionDate,
                    row.mode.name,
                    row.rollNumber,
                    row.studentName,
                    row.serialNumber.orEmpty(),
                    row.status.name,
                    row.attendanceMethod.name,
                    row.checkInTime?.let(::timestamp).orEmpty(),
                ),
            )
        }
    }

    private fun StringBuilder.appendCsvRow(values: List<String>) {
        appendLine(values.joinToString(",") { raw ->
            val value = if (raw.startsWith("=") || raw.startsWith("+") || raw.startsWith("-") || raw.startsWith("@")) {
                "'$raw"
            } else {
                raw
            }
            "\"" + value.replace("\"", "\"\"") + "\""
        })
    }

    private fun timestamp(millis: Long): String = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()),
    )
}
