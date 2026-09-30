package com.attract.attendance.domain

import com.attract.attendance.core.model.AttendanceSource
import com.attract.attendance.core.model.AttendanceStatus
import com.attract.attendance.core.model.SessionStatus
import com.attract.attendance.data.local.AttendanceRecordDao
import com.attract.attendance.data.local.AttendanceRecordEntity
import com.attract.attendance.data.local.SessionDao
import com.attract.attendance.data.local.StudentDao

sealed interface RecordPresentResult {
    data class Success(val recordId: Long) : RecordPresentResult
    data object AlreadyPresent : RecordPresentResult
    data object StudentNotEligible : RecordPresentResult
    data object SessionNotActive : RecordPresentResult
    data object StudentNotFound : RecordPresentResult
}

class RecordPresentCommand(
    private val sessionDao: SessionDao,
    private val studentDao: StudentDao,
    private val attendanceRecordDao: AttendanceRecordDao,
) {
    suspend fun execute(
        sessionId: Long,
        studentId: Long,
        method: AttendanceSource,
        confidence: Float? = null,
        metadata: String? = null,
        timestamp: Long = System.currentTimeMillis(),
    ): RecordPresentResult {
        val session = sessionDao.find(sessionId) ?: return RecordPresentResult.SessionNotActive
        if (session.status != SessionStatus.ACTIVE) {
            return RecordPresentResult.SessionNotActive
        }

        val student = studentDao.find(studentId) ?: return RecordPresentResult.StudentNotFound
        if (student.archived || student.classId != session.classId) {
            return RecordPresentResult.StudentNotEligible
        }

        if (student.eligibleFromSessionId != null && student.eligibleFromSessionId > sessionId) {
            return RecordPresentResult.StudentNotEligible
        }

        val existingRecords = attendanceRecordDao.forSession(sessionId)
        val studentRecord = existingRecords.find { it.studentId == studentId }
        if (studentRecord != null) {
            if (studentRecord.status == AttendanceStatus.PRESENT) {
                return RecordPresentResult.AlreadyPresent
            }
            attendanceRecordDao.updateStatus(
                sessionId = sessionId,
                studentId = studentId,
                status = AttendanceStatus.PRESENT,
                checkInTime = timestamp,
                updatedAt = timestamp,
            )
            return RecordPresentResult.Success(studentRecord.id)
        }

        val entity = AttendanceRecordEntity(
            sessionId = sessionId,
            studentId = studentId,
            status = AttendanceStatus.PRESENT,
            checkInTime = timestamp,
            attendanceMethod = method,
            matchConfidence = confidence,
            recognitionMetadata = metadata,
            createdAt = timestamp,
            updatedAt = timestamp,
        )
        val recordId = attendanceRecordDao.insert(entity)
        return RecordPresentResult.Success(recordId)
    }
}
