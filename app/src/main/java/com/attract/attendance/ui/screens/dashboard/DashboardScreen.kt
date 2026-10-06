package com.attract.attendance.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Class
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.attract.attendance.core.model.ClassSummary
import com.attract.attendance.ui.components.AttractCard
import com.attract.attendance.ui.components.AttractIconButton
import com.attract.attendance.ui.components.EmptyState
import com.attract.attendance.ui.components.rememberFeedbackClick
import com.attract.attendance.ui.theme.AttractBlue
import com.attract.attendance.ui.theme.Dimens
import com.attract.attendance.ui.theme.Shapes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    teacherName: String,
    classes: List<ClassSummary>,
    onSelectClass: (Long) -> Unit,
    onCreateClassClick: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val greeting = remember {
        val hour = java.time.LocalTime.now().hour
        when (hour) {
            in 5..11 -> "Good morning,"
            in 12..16 -> "Good afternoon,"
            in 17..20 -> "Good evening,"
            else -> "Good night,"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = greeting,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = teacherName.ifBlank { "Teacher" },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                actions = {
                    AttractIconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = {
            val fabClick = rememberFeedbackClick(onCreateClassClick)
            FloatingActionButton(
                onClick = fabClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create Class")
            }
        },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(Dimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.MediumGap)
        ) {
            // Overview Card
            item {
                AttractCard(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Column {
                        Text(
                            text = "TODAY",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${classes.size} classes configured",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${classes.sumOf { it.endedSessionCount }} completed sessions",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(Dimens.SmallGap))
                Text(
                    text = "Your classes",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (classes.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Default.Class,
                        title = "No classes created yet",
                        message = "Tap + below to create your first class section.",
                        actionLabel = "Create Class",
                        onAction = onCreateClassClick
                    )
                }
            } else {
                items(classes, key = { it.id }) { item ->
                    ClassCard(
                        classSummary = item,
                        onClick = { onSelectClass(item.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ClassCard(
    classSummary: ClassSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AttractCard(
        onClick = onClick,
        modifier = modifier
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = classSummary.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${classSummary.subject} • ${classSummary.section ?: "Section A"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${classSummary.activeStudentCount} students · ${classSummary.endedSessionCount} completed",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${classSummary.endedSessionCount}/${classSummary.totalPlannedSessions} Classes",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = AttractBlue
                )
            }

            val sessionProgress = if (classSummary.totalPlannedSessions > 0) {
                (classSummary.endedSessionCount.toFloat() / classSummary.totalPlannedSessions.toFloat()).coerceIn(0f, 1f)
            } else 0f

            LinearProgressIndicator(
                progress = { sessionProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
                color = AttractBlue,
                trackColor = MaterialTheme.colorScheme.surface
            )
        }
    }
}
