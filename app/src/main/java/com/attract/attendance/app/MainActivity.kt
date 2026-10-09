package com.attract.attendance.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.attract.attendance.data.local.AttractDatabase
import com.attract.attendance.feature.app.AttractApp
import com.attract.attendance.feature.app.AttractViewModel

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as AttractApplication
        val container = app.container
        val startupError = app.startupError

        val isSafeMode = AttractApplication.isSafeModeActive(this)
        val crashPrefs = getSharedPreferences(AttractApplication.PREFS_CRASH_LOG, MODE_PRIVATE)
        val lastCrash = crashPrefs.getString(AttractApplication.KEY_LAST_CRASH, null)
        val crashTime = crashPrefs.getLong(AttractApplication.KEY_LAST_CRASH_TIME, 0L)
        val isRecentCrash = lastCrash != null && (System.currentTimeMillis() - crashTime) < 8000L

        setContent {
            var launchSafeModeDashboard by remember { mutableStateOf(false) }

            if ((startupError != null || container == null || isRecentCrash || isSafeMode) && !launchSafeModeDashboard) {
                val errorText = startupError?.let { android.util.Log.getStackTraceString(it) }
                    ?: lastCrash
                    ?: if (isSafeMode) "Safe Mode engaged after repeated crashes." else "Startup container initialization failed."
                CrashRecoveryScreen(
                    errorMessage = errorText,
                    isSafeMode = isSafeMode,
                    canLaunchSafeMode = isSafeMode && container != null,
                    onLaunchSafeMode = { launchSafeModeDashboard = true },
                    onRestoreSnapshot = {
                        val restored = AttractDatabase.restoreLatestSnapshot(this)
                        if (restored) {
                            AttractApplication.exitSafeMode(this)
                            Toast.makeText(this, "Snapshot restored successfully. Restarting...", Toast.LENGTH_SHORT).show()
                            recreate()
                        } else {
                            Toast.makeText(this, "No previous database snapshot found to restore.", Toast.LENGTH_LONG).show()
                        }
                    },
                    onDismissAndRetry = {
                        AttractApplication.exitSafeMode(this)
                        crashPrefs.edit().clear().commit()
                        recreate()
                    }
                )
            } else if (container != null) {
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
                                isSafeMode = isSafeMode,
                            ) as T
                    },
                )
            }
        }
    }
}

@Composable
private fun CrashRecoveryScreen(
    errorMessage: String,
    isSafeMode: Boolean,
    canLaunchSafeMode: Boolean = false,
    onLaunchSafeMode: () -> Unit = {},
    onRestoreSnapshot: () -> Unit,
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Image(
                        painter = painterResource(id = com.attract.attendance.R.drawable.app_logo),
                        contentDescription = "Attract Logo",
                        modifier = Modifier.size(56.dp)
                    )
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(36.dp)
                    )
                }
                Text(
                    text = if (isSafeMode) "Attract Safe Mode" else "Attract Startup Recovery",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = if (isSafeMode)
                        "Safe Mode is active after repeated crashes. Your attendance data is completely safe. You can restore the last pre-migration snapshot, export sanitized diagnostics, or retry launch."
                    else
                        "A startup issue was detected. Your database is preserved. You can restore the last snapshot, export diagnostics, or retry:",
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
                        // PR-01: Export diagnostics (app version, DB schema version, snapshots count, sanitized stack trace, no PII)
                        val snapshotList = AttractDatabase.getAvailableSnapshots(context).map { it.name }.joinToString("\n- ", prefix = "- ")
                        val sanitizedStack = errorMessage
                            .replace(Regex("""(?i)\b(student|roll|name)[\w\s:=]+"""), "[REDACTED]")
                        val report = buildString {
                            appendLine("=== Attract Diagnostics Report ===")
                            appendLine("App Version: ${com.attract.attendance.BuildConfig.VERSION_NAME} (${com.attract.attendance.BuildConfig.VERSION_CODE})")
                            appendLine("Android SDK: ${android.os.Build.VERSION.SDK_INT} (${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL})")
                            appendLine("DB Target Version: ${AttractDatabase.CURRENT_VERSION}")
                            appendLine("Safe Mode: $isSafeMode")
                            appendLine("Available Pre-Migration Snapshots:")
                            appendLine(if (snapshotList.isBlank() || snapshotList == "- ") "None" else snapshotList)
                            appendLine("\nSanitized Error Trace:")
                            appendLine(sanitizedStack)
                        }

                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("Attract Diagnostics", report))
                        Toast.makeText(context, "Sanitized diagnostics copied to clipboard", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Export Diagnostics (No PII)")
                }
                Button(
                    onClick = onRestoreSnapshot,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Restore Last Snapshot & Restart")
                }
                if (canLaunchSafeMode) {
                    Button(
                        onClick = onLaunchSafeMode,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Enter Safe Mode (Dashboard Only)")
                    }
                }
                OutlinedButton(
                    onClick = onDismissAndRetry,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isSafeMode) "Exit Safe Mode & Retry Launch" else "Retry Launch")
                }
            }
        }
    }
}
