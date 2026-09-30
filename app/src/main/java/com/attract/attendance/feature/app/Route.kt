package com.attract.attendance.feature.app

sealed interface TeacherRoute {
    data object Dashboard : TeacherRoute
    data class ClassWorkspace(val classId: Long) : TeacherRoute
    data class Students(val classId: Long) : TeacherRoute
    data class History(val classId: Long) : TeacherRoute
    data class ManualSession(val classId: Long) : TeacherRoute
}

sealed interface AttendanceRoute {
    data class ActiveSession(val sessionId: Long) : AttendanceRoute
    data class Enrollment(val sessionId: Long) : AttendanceRoute
    data class TeacherAssist(val sessionId: Long) : AttendanceRoute
}
