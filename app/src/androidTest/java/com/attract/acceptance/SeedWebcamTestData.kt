package com.attract.acceptance

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.attract.attendance.core.model.CommandResult
import com.attract.attendance.data.local.AttractDatabase
import com.attract.attendance.data.repository.AttractRepository
import com.attract.attendance.data.repository.CreateClassCommand
import com.attract.attendance.data.repository.CreateStudentCommand
import com.attract.attendance.data.security.PinHasher
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SeedWebcamTestData {
    @Test
    fun seed() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.databaseBuilder(context, AttractDatabase::class.java, "attract.db")
            .addMigrations(AttractDatabase.MIGRATION_1_2, AttractDatabase.MIGRATION_2_3, AttractDatabase.MIGRATION_3_4)
            .build()
        val repo = AttractRepository(db, PinHasher(), embeddingCipher = null)
        repo.registerTeacher("Test Teacher", "123456".toCharArray())
        val classResult = repo.createClass(CreateClassCommand(name = "Webcam Test"))
        val classId = when (classResult) {
            is CommandResult.Success -> classResult.value
            else -> db.classDao().find(1L)?.id ?: 1L
        }
        for ((name, roll) in listOf("AMIT" to "W-01", "Student B" to "W-02", "Student C" to "W-03")) {
            val existing = db.studentDao().activeForClass(classId).find { it.name == name }
            if (existing == null) repo.addStudent(CreateStudentCommand(classId = classId, name = name, rollNumber = roll))
        }
        println("[SEED] DONE: classId=$classId students=${db.studentDao().activeForClass(classId).map { it.name }}")
        db.close()
    }
}
