package com.attract.attendance.app

import android.app.Application
import com.attract.attendance.data.importexport.AttendanceExporter
import com.attract.attendance.data.importexport.BackupExporter
import com.attract.attendance.data.importexport.CsvRosterImporter
import com.attract.attendance.data.local.AttractDatabase
import com.attract.attendance.data.repository.AttractRepository
import com.attract.attendance.data.security.PinHasher

class AttractApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(application: Application) {
    private val database = AttractDatabase.create(application)

    // Keystore-backed AEAD cipher for face templates at rest (LLD-13).
    // Lazy so Keystore init happens on first repository access, not app attach.
    private val embeddingCipher: com.attract.attendance.lockdown.domain.EmbeddingCipher by lazy {
        com.attract.attendance.lockdown.data.crypto.KeystoreEmbeddingCipher(
            alias = "attract_face_template_key",
            keyVersion = 1,
        )
    }

    val repository: AttractRepository by lazy {
        AttractRepository(database, PinHasher(), embeddingCipher = embeddingCipher)
    }
    val csvRosterImporter = CsvRosterImporter(application.contentResolver)
    val attendanceExporter = AttendanceExporter(application.contentResolver)
    val backupExporter = BackupExporter(application.contentResolver)
}
