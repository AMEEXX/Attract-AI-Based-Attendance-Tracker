package com.attract.attendance.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.attract.attendance.feature.app.AttractApp
import com.attract.attendance.feature.app.AttractViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as AttractApplication).container
        setContent {
            AttractApp(
                viewModelFactory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
                        AttractViewModel(
                            repository = container.repository,
                            csvRosterImporter = container.csvRosterImporter,
                            attendanceExporter = container.attendanceExporter,
                            backupExporter = container.backupExporter,
                            themeRepository = com.attract.attendance.data.theme.ThemeRepository(applicationContext),
                        ) as T
                },
            )
        }
    }
}
