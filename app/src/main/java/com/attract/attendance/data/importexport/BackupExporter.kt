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

data class BackupFaceTemplate(
    val id: Long = 0,
    val studentId: Long,
    val modelVersion: String = "v1",
    val embeddingDim: Int = 0,
    val poseBucket: String = "FRONTAL",
    val qualityScore: Float = 1.0f,
    val embeddingBase64: String,
    val capturedAt: Long = 0L,
    val source: String = "enrollment",
    val active: Boolean = true,
)

data class BackupSnapshot(
    val generatedAt: Long,
    val teachers: List<TeacherEntity>,
    val classes: List<ClassSectionEntity>,
    val students: List<StudentEntity>,
    val sessions: List<AttendanceSessionEntity>,
    val records: List<AttendanceRecordEntity>,
    val faceTemplates: List<BackupFaceTemplate> = emptyList(),
)


class BackupExporter(
    private val contentResolver: ContentResolver? = null,
) {
    suspend fun export(uri: Uri, snapshot: BackupSnapshot, pin: CharArray? = null): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = contentResolver ?: error("The destination is unavailable.")
            val output = resolver.openOutputStream(uri) ?: error("The destination is unavailable.")
            val plaintext = toJson(snapshot)
            val outputBytes = if (pin != null) {
                BackupCrypto.encrypt(plaintext, pin).toByteArray(Charsets.UTF_8)
            } else {
                plaintext.toByteArray(Charsets.UTF_8)
            }
            output.use { it.write(outputBytes) }
        }
    }

    suspend fun import(uri: Uri, pin: CharArray? = null): Result<BackupSnapshot> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = contentResolver ?: error("The storage provider is unavailable.")
            val input = resolver.openInputStream(uri) ?: error("The backup file could not be opened.")
            val text = input.use { it.bufferedReader(Charsets.UTF_8).readText() }
            val json = if (BackupCrypto.isEncryptedEnvelope(text)) {
                requireNotNull(pin) { "This backup is encrypted. Teacher PIN is required to restore." }
                BackupCrypto.decrypt(text, pin)
            } else {
                text
            }
            fromJson(json)
        }
    }

    fun toJson(snapshot: BackupSnapshot): String = buildString {
        append("{")
        field("format", "attract-backup-v2")
        comma()
        field("generatedAt", snapshot.generatedAt)
        comma()
        array("teachers", snapshot.teachers) { teacher ->
            obj {
                field("id", teacher.id); comma()
                field("displayName", teacher.displayName); comma()
                // PR-03: pinHash removed. It uses Keystore pepper and cannot be migrated.
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
        comma()
        // PR-03 / SDD §66: Face templates are hardware-bound biometric artifacts and excluded from backups
        append("\"faceTemplates\":[]")
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

    fun fromJson(json: String): BackupSnapshot {
        val root = org.json.JSONObject(json)
        val format = root.optString("format", "")
        if (format.isNotBlank()) {
            require(format == "attract-backup-v1" || format == "attract-backup-v2") {
                "Unsupported backup format version: $format"
            }
        }
        val generatedAt = root.optLong("generatedAt", System.currentTimeMillis())

        val teachersJson = root.optJSONArray("teachers") ?: org.json.JSONArray()
        val teachers = mutableListOf<TeacherEntity>()
        for (i in 0 until teachersJson.length()) {
            val obj = teachersJson.getJSONObject(i)
            teachers.add(
                TeacherEntity(
                    id = obj.getLong("id"),
                    displayName = obj.getString("displayName"),
                    pinHash = obj.optString("pinHash", ""), // Restored PIN requires reset
                    createdAt = obj.getLong("createdAt"),
                    updatedAt = obj.getLong("updatedAt"),
                )
            )
        }

        val classesJson = root.optJSONArray("classes") ?: org.json.JSONArray()
        val classes = mutableListOf<ClassSectionEntity>()
        for (i in 0 until classesJson.length()) {
            val obj = classesJson.getJSONObject(i)
            classes.add(
                ClassSectionEntity(
                    id = obj.getLong("id"),
                    teacherId = obj.getLong("teacherId"),
                    name = obj.getString("name"),
                    subject = obj.getString("subject"),
                    section = obj.getString("section"),
                    semesterBatch = obj.getString("semesterBatch"),
                    requiredAttendancePercent = obj.getInt("requiredAttendancePercent"),
                    archived = obj.optBoolean("archived", false),
                    createdAt = obj.getLong("createdAt"),
                    updatedAt = obj.getLong("updatedAt"),
                )
            )
        }

        val studentsJson = root.optJSONArray("students") ?: org.json.JSONArray()
        val students = mutableListOf<StudentEntity>()
        for (i in 0 until studentsJson.length()) {
            val obj = studentsJson.getJSONObject(i)
            val statusStr = obj.optString("enrollmentStatus", "NOT_ENROLLED")
            val status = runCatching {
                com.attract.attendance.core.model.EnrollmentStatus.valueOf(statusStr)
            }.getOrDefault(com.attract.attendance.core.model.EnrollmentStatus.NOT_ENROLLED)

            students.add(
                StudentEntity(
                    id = obj.getLong("id"),
                    classId = obj.getLong("classId"),
                    name = obj.getString("name"),
                    rollNumber = obj.getString("rollNumber"),
                    serialNumber = if (obj.isNull("serialNumber")) null else obj.optString("serialNumber"),
                    enrollmentStatus = status,
                    enrolledAt = if (obj.isNull("enrolledAt")) null else obj.optLong("enrolledAt"),
                    eligibleFromSessionId = if (obj.isNull("eligibleFromSessionId")) null else obj.optLong("eligibleFromSessionId"),
                    archived = obj.optBoolean("archived", false),
                    createdAt = obj.getLong("createdAt"),
                    updatedAt = obj.getLong("updatedAt"),
                )
            )
        }

        val sessionsJson = root.optJSONArray("sessions") ?: org.json.JSONArray()
        val sessions = mutableListOf<AttendanceSessionEntity>()
        for (i in 0 until sessionsJson.length()) {
            val obj = sessionsJson.getJSONObject(i)
            val sessionStatusStr = obj.optString("status", "ENDED")
            val sessionStatus = runCatching {
                com.attract.attendance.core.model.SessionStatus.valueOf(sessionStatusStr)
            }.getOrDefault(com.attract.attendance.core.model.SessionStatus.ENDED)

            val sessionModeStr = obj.optString("mode", "FACE")
            val sessionMode = runCatching {
                com.attract.attendance.core.model.SessionMode.valueOf(sessionModeStr)
            }.getOrDefault(com.attract.attendance.core.model.SessionMode.FACE)

            sessions.add(
                AttendanceSessionEntity(
                    id = obj.getLong("id"),
                    classId = obj.getLong("classId"),
                    sessionDate = obj.getString("sessionDate"),
                    timeZoneId = obj.getString("timeZoneId"),
                    startedAt = obj.getLong("startedAt"),
                    endedAt = if (obj.isNull("endedAt")) null else obj.optLong("endedAt"),
                    status = sessionStatus,
                    mode = sessionMode,
                    createdAt = obj.getLong("createdAt"),
                    updatedAt = obj.getLong("updatedAt"),
                )
            )
        }

        val recordsJson = root.optJSONArray("records") ?: org.json.JSONArray()
        val records = mutableListOf<AttendanceRecordEntity>()
        for (i in 0 until recordsJson.length()) {
            val obj = recordsJson.getJSONObject(i)
            val attStatusStr = obj.optString("status", "PRESENT")
            val attStatus = runCatching {
                com.attract.attendance.core.model.AttendanceStatus.valueOf(attStatusStr)
            }.getOrDefault(com.attract.attendance.core.model.AttendanceStatus.PRESENT)

            val attMethodStr = obj.optString("attendanceMethod", "AI_RECOGNITION")
            val attMethod = runCatching {
                com.attract.attendance.core.model.AttendanceSource.valueOf(attMethodStr)
            }.getOrDefault(com.attract.attendance.core.model.AttendanceSource.AI_RECOGNITION)

            records.add(
                AttendanceRecordEntity(
                    id = obj.getLong("id"),
                    sessionId = obj.getLong("sessionId"),
                    studentId = obj.getLong("studentId"),
                    status = attStatus,
                    checkInTime = if (obj.isNull("checkInTime")) null else obj.optLong("checkInTime"),
                    attendanceMethod = attMethod,
                    matchConfidence = if (obj.isNull("matchConfidence")) null else obj.optDouble("matchConfidence").toFloat(),
                    recognitionMetadata = if (obj.isNull("recognitionMetadata")) null else obj.optString("recognitionMetadata"),
                    createdAt = obj.getLong("createdAt"),
                    updatedAt = obj.getLong("updatedAt"),
                )
            )
        }

        val templatesJson = root.optJSONArray("faceTemplates") ?: org.json.JSONArray()
        val faceTemplates = mutableListOf<BackupFaceTemplate>()
        for (i in 0 until templatesJson.length()) {
            val obj = templatesJson.getJSONObject(i)
            faceTemplates.add(
                BackupFaceTemplate(
                    id = obj.optLong("id", 0L),
                    studentId = obj.getLong("studentId"),
                    modelVersion = obj.optString("modelVersion", "v1"),
                    embeddingDim = obj.optInt("embeddingDim", 0),
                    poseBucket = obj.optString("poseBucket", "FRONTAL"),
                    qualityScore = obj.optDouble("qualityScore", 1.0).toFloat(),
                    embeddingBase64 = obj.getString("embeddingBase64"),
                    capturedAt = obj.optLong("capturedAt", generatedAt),
                    source = obj.optString("source", "enrollment"),
                    active = obj.optBoolean("active", true),
                )
            )
        }

        return BackupSnapshot(
            generatedAt = generatedAt,
            teachers = teachers,
            classes = classes,
            students = students,
            sessions = sessions,
            records = records,
            faceTemplates = faceTemplates,
        )
    }
}
