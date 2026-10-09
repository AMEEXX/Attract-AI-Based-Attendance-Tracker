package com.attract.attendance.ui.screens.settings

import android.text.format.DateUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.attract.attendance.data.drive.DriveSyncStatus
import com.attract.attendance.ui.components.AttractCard
import com.attract.attendance.ui.components.AttractOutlinedButton
import com.attract.attendance.ui.components.AttractPrimaryButton
import com.attract.attendance.ui.components.AttractTextButton
import com.attract.attendance.ui.theme.Dimens
import com.attract.attendance.ui.theme.Shapes
import com.attract.attendance.ui.theme.SuccessGreen

@Composable
fun GoogleDriveSyncCard(
    accountEmail: String?,
    syncStatus: DriveSyncStatus,
    lastSyncMillis: Long,
    onConnectClick: () -> Unit,
    onDisconnectClick: () -> Unit,
    onSyncNowClick: () -> Unit,
    onRestoreDriveClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showRestoreConfirmDialog by remember { mutableStateOf(false) }

    AttractCard(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Dimens.MediumGap)
        ) {
            // Header with Cloud Icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Image(
                            painter = painterResource(id = com.attract.attendance.R.drawable.ic_google_drive),
                            contentDescription = "Google Drive",
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(Dimens.MediumGap))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Google Drive Cloud Backup",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Silent background backup to private AppData",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (accountEmail == null) {
                // Not Connected State
                Text(
                    text = "Sign in to your Google Account to automatically back up classes, students, and attendance sessions to Google Drive when internet is available.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                AttractPrimaryButton(
                    onClick = onConnectClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Image(
                        painter = painterResource(id = com.attract.attendance.R.drawable.ic_google_drive),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Connect Google Drive", fontWeight = FontWeight.SemiBold)
                }
            } else {
                // Connected State
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = SuccessGreen,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Connected Account",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = accountEmail,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Sync status information
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val relativeTime = if (lastSyncMillis > 0) {
                        val now = System.currentTimeMillis()
                        val diff = now - lastSyncMillis
                        if (diff < 60_000) "Just now"
                        else DateUtils.getRelativeTimeSpanString(lastSyncMillis, now, DateUtils.MINUTE_IN_MILLIS).toString()
                    } else {
                        "Never"
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Last backup: $relativeTime",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (lastSyncMillis > 0 && syncStatus !is DriveSyncStatus.Error) {
                            Surface(
                                shape = Shapes.pill,
                                color = SuccessGreen.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = "Backed up",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = SuccessGreen,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    // Dynamic sync progress or error banner
                    when (syncStatus) {
                        is DriveSyncStatus.Syncing -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Text(
                                    text = "Backing up to Google Drive...",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        is DriveSyncStatus.Success -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudDone,
                                    contentDescription = null,
                                    tint = SuccessGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Cloud backup up to date",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SuccessGreen,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        is DriveSyncStatus.Error -> {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ErrorOutline,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = syncStatus.message,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                    AttractTextButton(onClick = onSyncNowClick) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(2.dp))
                                        Text("Retry", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                        DriveSyncStatus.Idle -> Unit
                    }
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.SmallGap),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AttractPrimaryButton(
                        onClick = onSyncNowClick,
                        enabled = syncStatus !is DriveSyncStatus.Syncing,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Sync Now", fontWeight = FontWeight.SemiBold)
                    }

                    if (onRestoreDriveClick != null) {
                        AttractOutlinedButton(
                            onClick = { showRestoreConfirmDialog = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Restore", fontWeight = FontWeight.SemiBold)
                        }
                    }

                    AttractTextButton(
                        onClick = onDisconnectClick
                    ) {
                        Text("Disconnect", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    if (showRestoreConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirmDialog = false },
            icon = {
                Image(
                    painter = painterResource(id = com.attract.attendance.R.drawable.ic_google_drive),
                    contentDescription = null,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = { Text("Restore from Google Drive?", fontWeight = FontWeight.Bold) },
            text = { Text("This will download the latest backup from Google Drive and restore all classes, students, and attendance records. Current records will be merged or replaced.") },
            confirmButton = {
                AttractPrimaryButton(
                    onClick = {
                        showRestoreConfirmDialog = false
                        onRestoreDriveClick?.invoke()
                    }
                ) {
                    Text("Restore Data")
                }
            },
            dismissButton = {
                AttractTextButton(onClick = { showRestoreConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
