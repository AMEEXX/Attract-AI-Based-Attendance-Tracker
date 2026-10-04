package com.attract.attendance.test

import com.attract.attendance.core.model.AttendanceStatus
import com.attract.attendance.core.model.SessionStatus
import com.attract.attendance.data.local.ActiveSessionRow
import com.attract.attendance.data.local.AttendanceRecordDao
import com.attract.attendance.data.local.AttendanceRecordEntity
import com.attract.attendance.data.local.AttendanceSessionEntity
import com.attract.attendance.data.local.ClassReportStudentRow
import com.attract.attendance.data.local.SessionDao
import com.attract.attendance.data.local.SessionExportRow
import com.attract.attendance.data.local.SessionRow
import com.attract.attendance.data.local.SessionStudentRow
import com.attract.attendance.data.local.StudentDao
import com.attract.attendance.data.local.StudentEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class FakeSessionDao(var activeSession: AttendanceSessionEntity? = null) : SessionDao {
    override suspend fun activeFaceSession(): AttendanceSessionEntity? = activeSession
    override suspend fun activeForClass(classId: Long): AttendanceSessionEntity? = activeSession
    override fun observeActiveForClass(classId: Long): Flow<AttendanceSessionEntity?> = flowOf(activeSession)
    override fun observeActiveSummaryForClass(classId: Long): Flow<ActiveSessionRow?> = flowOf(null)
    override suspend fun all(): List<AttendanceSessionEntity> = listOfNotNull(activeSession)
    override suspend fun find(sessionId: Long): AttendanceSessionEntity? = activeSession
    override suspend fun latestIdForClass(classId: Long): Long? = activeSession?.id
    override suspend fun insert(value: AttendanceSessionEntity): Long = value.id
    override suspend fun finish(sessionId: Long, status: SessionStatus, endedAt: Long, updatedAt: Long): Int {
        if (activeSession != null && activeSession?.id == sessionId) {
            activeSession = activeSession?.copy(status = status, endedAt = endedAt, updatedAt = updatedAt)
            return 1
        }
        return 0
    }
    override fun observeEndedForClass(classId: Long): Flow<List<SessionRow>> = flowOf(emptyList())
    override suspend fun deleteEnded(sessionId: Long): Int = 0
    override fun observeSessionDaysForMonth(classId: Long, yearMonthPrefix: String): Flow<List<Int>> = flowOf(emptyList())
    override fun observeSessionsForDate(classId: Long, dateString: String): Flow<List<SessionRow>> = flowOf(emptyList())
    override suspend fun deleteActiveFaceSessionsForClass(classId: Long): Int = 0
    override suspend fun deleteAllActiveFaceSessions(): Int = 0
}

class FakeStudentDao(initialStudents: List<StudentEntity> = emptyList()) : StudentDao {
    private val studentsList = initialStudents.toMutableList()
    override fun observeActiveForClass(classId: Long): Flow<List<StudentEntity>> = flowOf(studentsList)
    override suspend fun activeForClass(classId: Long): List<StudentEntity> = studentsList
    override suspend fun find(studentId: Long): StudentEntity? = studentsList.find { it.id == studentId }
    override suspend fun all(): List<StudentEntity> = studentsList
    override suspend fun findActiveByRoll(classId: Long, rollNumber: String): StudentEntity? = studentsList.find { it.rollNumber == rollNumber }
    override suspend fun insert(value: StudentEntity): Long {
        studentsList.add(value)
        return value.id
    }
    override suspend fun setArchived(studentId: Long, archived: Boolean, updatedAt: Long): Int = 1
    override suspend fun update(student: StudentEntity) {
        val index = studentsList.indexOfFirst { it.id == student.id }
        if (index != -1) {
            studentsList[index] = student
        }
    }
    override suspend fun resetEnrollmentForClass(classId: Long): Int = 0
    override suspend fun resetAllEnrollments(): Int = 0
}

class FakeAttendanceRecordDao : AttendanceRecordDao {
    private val records = mutableListOf<AttendanceRecordEntity>()
    private var nextId = 1L

    override suspend fun insert(value: AttendanceRecordEntity): Long {
        val record = value.copy(id = nextId++)
        records.add(record)
        return record.id
    }

    override suspend fun insertAll(values: List<AttendanceRecordEntity>) {
        values.forEach { insert(it) }
    }

    override suspend fun forSession(sessionId: Long): List<AttendanceRecordEntity> {
        return records.filter { it.sessionId == sessionId }
    }

    override fun observeStudentsForSession(classId: Long, sessionId: Long): Flow<List<SessionStudentRow>> = flowOf(emptyList())

    override suspend fun updateStatus(sessionId: Long, studentId: Long, status: AttendanceStatus, checkInTime: Long?, updatedAt: Long): Int {
        val index = records.indexOfFirst { it.sessionId == sessionId && it.studentId == studentId }
        if (index != -1) {
            records[index] = records[index].copy(status = status, checkInTime = checkInTime, updatedAt = updatedAt)
            return 1
        }
        return 0
    }

    override suspend fun deleteForSession(sessionId: Long): Int {
        val count = records.count { it.sessionId == sessionId }
        records.removeAll { it.sessionId == sessionId }
        return count
    }

    override suspend fun deleteActiveFaceSessionRecordsForClass(classId: Long): Int = 0
    override suspend fun deleteAllActiveFaceSessionRecords(): Int = 0

    override suspend fun presentCount(sessionId: Long): Int = records.count { it.sessionId == sessionId && it.status == AttendanceStatus.PRESENT }
    override suspend fun all(): List<AttendanceRecordEntity> = records
    override suspend fun classReport(classId: Long): List<ClassReportStudentRow> = emptyList()
    override suspend fun sessionExportRows(classId: Long): List<SessionExportRow> = emptyList()
}
