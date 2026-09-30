package com.attract.attendance.data.importexport

import android.content.ContentResolver
import android.net.Uri
import com.attract.attendance.data.local.AttendanceRecordEntity
import com.attract.attendance.data.local.AttendanceSessionEntity
import com.attract.attendance.data.local.ClassSectionEntity
import com.attract.attendance.data.local.StudentEntity
import com.attract.attendance.data.local.TeacherEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class BackupSnapshot(
    val generatedAt: Long,
    val teachers: List<TeacherEntity>,
    val classes: List<ClassSectionEntity>,
    val students: List<StudentEntity>,
    val sessions: List<AttendanceSessionEntity>,
    val records: List<AttendanceRecordEntity>,
)

class BackupExporter(
    private val contentResolver: ContentResolver? = null,
) {
    suspend fun export(uri: Uri, snapshot: BackupSnapshot): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = contentResolver ?: error("The destination is unavailable.")
            val output = resolver.openOutputStream(uri) ?: error("The destination is unavailable.")
            output.use { it.write(toJson(snapshot).toByteArray(Charsets.UTF_8)) }
        }
    }

    internal fun toJson(snapshot: BackupSnapshot): String = buildString {
        append("{")
        field("format", "attract-backup-v1")
        comma()
        field("generatedAt", snapshot.generatedAt)
        comma()
        array("teachers", snapshot.teachers) { teacher ->
            obj {
                field("id", teacher.id); comma()
                field("displayName", teacher.displayName); comma()
                field("pinHash", teacher.pinHash); comma()
                field("createdAt", teacher.createdAt); comma()
                field("updatedAt", teacher.updatedAt)
            }
        }
        comma()
        array("classes", snapshot.classes) { item ->
            obj {
                field("id", item.id); comma()
                field("teacherId", item.teacherId); comma()
                field("name", item.name); comma()
                field("subject", item.subject); comma()
                field("section", item.section); comma()
                field("semesterBatch", item.semesterBatch); comma()
                field("requiredAttendancePercent", item.requiredAttendancePercent); comma()
                field("archived", item.archived); comma()
                field("createdAt", item.createdAt); comma()
                field("updatedAt", item.updatedAt)
            }
        }
        comma()
        array("students", snapshot.students) { item ->
            obj {
                field("id", item.id); comma()
                field("classId", item.classId); comma()
                field("name", item.name); comma()
                field("rollNumber", item.rollNumber); comma()
                field("serialNumber", item.serialNumber); comma()
                field("enrollmentStatus", item.enrollmentStatus.name); comma()
                field("enrolledAt", item.enrolledAt); comma()
                field("eligibleFromSessionId", item.eligibleFromSessionId); comma()
                field("archived", item.archived); comma()
                field("createdAt", item.createdAt); comma()
                field("updatedAt", item.updatedAt)
            }
        }
        comma()
        array("sessions", snapshot.sessions) { item ->
            obj {
                field("id", item.id); comma()
                field("classId", item.classId); comma()
                field("sessionDate", item.sessionDate); comma()
                field("timeZoneId", item.timeZoneId); comma()
                field("startedAt", item.startedAt); comma()
                field("endedAt", item.endedAt); comma()
                field("status", item.status.name); comma()
                field("mode", item.mode.name); comma()
                field("createdAt", item.createdAt); comma()
                field("updatedAt", item.updatedAt)
            }
        }
        comma()
        array("records", snapshot.records) { item ->
            obj {
                field("id", item.id); comma()
                field("sessionId", item.sessionId); comma()
                field("studentId", item.studentId); comma()
                field("status", item.status.name); comma()
                field("checkInTime", item.checkInTime); comma()
                field("attendanceMethod", item.attendanceMethod.name); comma()
                field("matchConfidence", item.matchConfidence); comma()
                field("recognitionMetadata", item.recognitionMetadata); comma()
                field("createdAt", item.createdAt); comma()
                field("updatedAt", item.updatedAt)
            }
        }
        append("}")
    }

    private fun <T> StringBuilder.array(name: String, values: List<T>, render: StringBuilder.(T) -> Unit) {
        append("\"").append(name).append("\":[")
        values.forEachIndexed { index, value ->
            if (index > 0) comma()
            render(value)
        }
        append("]")
    }

    private inline fun StringBuilder.obj(render: StringBuilder.() -> Unit) {
        append("{")
        render()
        append("}")
    }

    private fun StringBuilder.field(name: String, value: String?) {
        append("\"").append(name).append("\":")
        if (value == null) append("null") else append("\"").append(value.jsonEscaped()).append("\"")
    }

    private fun StringBuilder.field(name: String, value: Number?) {
        append("\"").append(name).append("\":").append(value ?: "null")
    }

    private fun StringBuilder.field(name: String, value: Boolean) {
        append("\"").append(name).append("\":").append(value)
    }

    private fun StringBuilder.comma() {
        append(",")
    }

    private fun String.jsonEscaped(): String = buildString {
        this@jsonEscaped.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
    }
}
