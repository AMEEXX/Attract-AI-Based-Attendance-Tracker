package com.attract.attendance.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.attract.attendance.feature.app.AttractApp
import com.attract.attendance.feature.app.AttractViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as AttractApplication
        val container = app.container
        val startupError = app.startupError

        val crashPrefs = getSharedPreferences("attract_crash_log", MODE_PRIVATE)
        val lastCrash = crashPrefs.getString("last_crash", null)
        val crashTime = crashPrefs.getLong("crash_time", 0L)
        val isRecentCrash = lastCrash != null && (System.currentTimeMillis() - crashTime) < 8000L

        if (startupError != null || container == null || isRecentCrash) {
            val errorText = startupError?.let { android.util.Log.getStackTraceString(it) }
                ?: lastCrash
                ?: "Startup container initialization failed."
            setContent {
                CrashRecoveryScreen(
                    errorMessage = errorText,
                    onResetDatabase = {
                        crashPrefs.edit().clear().commit()
                        deleteDatabase("attract.db")
                        recreate()
                    },
                    onDismissAndRetry = {
                        crashPrefs.edit().clear().commit()
                        recreate()
                    }
                )
            }
            return
        }

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

@Composable
private fun CrashRecoveryScreen(
    errorMessage: String,
    onResetDatabase: () -> Unit,
    onDismissAndRetry: () -> Unit
) {
    val context = LocalContext.current
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(28.dp))
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(52.dp)
                )
                Text(
                    text = "Attract Startup Recovery",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "A startup issue was detected. You can review the details, retry, or reset the local database:",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    SelectionContainer {
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                Button(
                    onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("Attract Crash", errorMessage))
                        Toast.makeText(context, "Error copied to clipboard", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Copy Error Details")
                }
                Button(
                    onClick = onResetDatabase,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Reset Local Database & Restart")
                }
                OutlinedButton(
                    onClick = onDismissAndRetry,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Retry Launch")
                }
            }
        }
    }
}
