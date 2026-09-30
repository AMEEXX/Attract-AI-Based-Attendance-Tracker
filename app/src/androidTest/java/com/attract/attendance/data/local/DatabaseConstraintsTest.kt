package com.attract.attendance.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.attract.attendance.core.model.EnrollmentStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseConstraintsTest {
    private lateinit var database: AttractDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AttractDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = database.close()

    @Test(expected = android.database.sqlite.SQLiteConstraintException::class)
    fun insertStudent_sameClassAndRoll_rejectsDuplicate() {
        runBlocking {
            val teacherId = database.teacherDao().insert(TeacherEntity(displayName = "Teacher", pinHash = "v1", createdAt = 1, updatedAt = 1))
            val classId = database.classDao().insert(ClassSectionEntity(teacherId = teacherId, name = "Math", subject = null, section = null, semesterBatch = null, requiredAttendancePercent = 75, createdAt = 1, updatedAt = 1))
            database.studentDao().insert(StudentEntity(classId = classId, name = "Aman", rollNumber = "R1", serialNumber = null, enrollmentStatus = EnrollmentStatus.NOT_ENROLLED, createdAt = 1, updatedAt = 1))
            database.studentDao().insert(StudentEntity(classId = classId, name = "Aman 2", rollNumber = "R1", serialNumber = null, enrollmentStatus = EnrollmentStatus.NOT_ENROLLED, createdAt = 1, updatedAt = 1))
        }
    }

    @Test
    fun insertStudent_sameRollInDifferentClasses_isAllowed() = runBlocking {
        val teacherId = database.teacherDao().insert(TeacherEntity(displayName = "Teacher", pinHash = "v1", createdAt = 1, updatedAt = 1))
        val firstClass = database.classDao().insert(ClassSectionEntity(teacherId = teacherId, name = "Math", subject = null, section = null, semesterBatch = null, requiredAttendancePercent = 75, createdAt = 1, updatedAt = 1))
        val secondClass = database.classDao().insert(ClassSectionEntity(teacherId = teacherId, name = "Science", subject = null, section = null, semesterBatch = null, requiredAttendancePercent = 75, createdAt = 1, updatedAt = 1))
        val firstStudent = database.studentDao().insert(StudentEntity(classId = firstClass, name = "Aman", rollNumber = "R1", serialNumber = null, enrollmentStatus = EnrollmentStatus.NOT_ENROLLED, createdAt = 1, updatedAt = 1))
        val secondStudent = database.studentDao().insert(StudentEntity(classId = secondClass, name = "Aman", rollNumber = "R1", serialNumber = null, enrollmentStatus = EnrollmentStatus.NOT_ENROLLED, createdAt = 1, updatedAt = 1))

        assertNotEquals(firstStudent, secondStudent)
    }
}
