package com.attract.attendance.ui.screens.classworkspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.attract.attendance.core.model.SessionSummary
import com.attract.attendance.core.model.StudentSummary
import com.attract.attendance.feature.app.ClassWorkspace
import com.attract.attendance.ui.components.EmptyState
import com.attract.attendance.ui.components.SearchField
import com.attract.attendance.ui.components.SessionCard
import com.attract.attendance.ui.components.StudentRow
import com.attract.attendance.ui.theme.Dimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassWorkspaceScreen(
    workspace: ClassWorkspace?,
    initialTab: Int = 0,
    onBack: () -> Unit,
    onAddStudent: () -> Unit,
    onStudentClick: (Long) -> Unit,
    onImportRoster: () -> Unit,
    onManualAttendance: (String) -> Unit,
    onFaceAttendance: (String) -> Unit,
    onSessionOpen: (SessionSummary) -> Unit,
    onAddStudentSubmit: (name: String, rollNumber: String, serialNumber: String?, (com.attract.attendance.core.model.CommandResult<Long>) -> Unit) -> Unit = { _, _, _, _ -> },
    onExportReport: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (workspace == null) return

    var activeTab by remember(initialTab) { mutableIntStateOf(initialTab) }
    var showAddStudentSheet by remember { mutableStateOf(false) }
    var duplicateRollError by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = workspace.summary.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        workspace.summary.section?.let {
                            Text(
                                text = "Section $it",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onExportReport) {
                        Icon(Icons.Default.Download, contentDescription = "Export Report")
                    }
                }
            )
        },
        floatingActionButton = {
            if (activeTab == 1) {
                FloatingActionButton(
                    onClick = {
                        duplicateRollError = null
                        showAddStudentSheet = true
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Student")
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        if (showAddStudentSheet) {
            com.attract.attendance.ui.components.AddStudentBottomSheet(
                onDismissRequest = { showAddStudentSheet = false },
                duplicateRollError = duplicateRollError,
                onAddStudent = { name, roll, serial ->
                    onAddStudentSubmit(name, roll, serial) { result ->
                        if (result is com.attract.attendance.core.model.CommandResult.Success) {
                            showAddStudentSheet = false
                            duplicateRollError = null
                        } else if (result is com.attract.attendance.core.model.CommandResult.Failure && result.error is com.attract.attendance.core.model.AppError.DuplicateRollNumber) {
                            duplicateRollError = "That roll number is already used in this class."
                        }
                    }
                }
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TabRow(
                selectedTabIndex = activeTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                indicator = { tabPositions ->
                    if (activeTab < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[activeTab]),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            ) {
                Tab(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    text = { Text("CALENDAR", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    text = { Text("STUDENTS", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = activeTab == 2,
                    onClick = { activeTab = 2 },
                    text = { Text("HISTORY", fontWeight = FontWeight.Bold) }
                )
            }

            when (activeTab) {
                0 -> {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val repository = remember(context) {
                        (context.applicationContext as? com.attract.attendance.app.AttractApplication)?.container?.repository
                    }
                    if (repository != null) {
                        val calendarViewModel = remember(workspace.summary.id) {
                            com.attract.attendance.feature.calendar.CalendarViewModel(workspace.summary.id, repository)
                        }
                        val calendarState by calendarViewModel.uiState.collectAsState()

                        com.attract.attendance.feature.calendar.CalendarScreen(
                            state = calendarState,
                            onPreviousMonth = calendarViewModel::previousMonth,
                            onNextMonth = calendarViewModel::nextMonth,
                            onSelectDate = calendarViewModel::selectDate,
                            onViewSessionDetails = { },
                            onFaceAttendance = onFaceAttendance,
                            onManualAttendance = onManualAttendance
                        )
                    }
                }
                1 -> WorkspaceStudentsTab(
                    students = workspace.students,
                    onStudentClick = onStudentClick,
                    onImportRoster = onImportRoster,
                    onAddStudent = onAddStudent
                )
                2 -> WorkspaceHistoryTab(
                    sessions = workspace.sessions,
                    onSessionOpen = onSessionOpen,
                    onExportReport = onExportReport
                )
            }
        }
    }
}

@Composable
private fun WorkspaceStudentsTab(
    students: List<StudentSummary>,
    onStudentClick: (Long) -> Unit,
    onImportRoster: () -> Unit,
    onAddStudent: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredStudents = remember(students, searchQuery) {
        if (searchQuery.isBlank()) students else students.filter {
            it.name.contains(searchQuery, ignoreCase = true) || it.rollNumber.contains(searchQuery, ignoreCase = true)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Dimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.SmallGap)
    ) {
        item {
            SearchField(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                placeholder = "Search students...",
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${filteredStudents.size} Students Enrolled",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = onImportRoster) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Import CSV")
                }
            }
        }

        if (filteredStudents.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Default.Person,
                    title = if (searchQuery.isBlank()) "No students enrolled yet" else "No matching students",
                    message = if (searchQuery.isBlank()) "Import a CSV roster or tap + to add students." else "Try a different search term.",
                    actionLabel = "Import Roster CSV",
                    onAction = onImportRoster
                )
            }
        } else {
            items(filteredStudents, key = { it.id }) { student ->
                StudentRow(
                    student = student,
                    onClick = { onStudentClick(student.id) }
                )
            }
        }
    }
}

@Composable
private fun WorkspaceHistoryTab(
    sessions: List<SessionSummary>,
    onSessionOpen: (SessionSummary) -> Unit,
    onExportReport: () -> Unit
) {
    var filterState by remember { mutableStateOf(com.attract.attendance.ui.components.HistoryFilterState()) }
    var showFilterSheet by remember { mutableStateOf(false) }

    val availableMonths = remember(sessions) {
        sessions.map { it.sessionDate.take(7) }.distinct().sortedDescending()
    }

    val filteredSessions = remember(sessions, filterState) {
        var list = sessions
        if (filterState.selectedMonth != null) {
            list = list.filter { it.sessionDate.startsWith(filterState.selectedMonth!!) }
        }
        if (filterState.sortOrder == com.attract.attendance.ui.components.HistorySortOrder.OLDEST_FIRST) {
            list = list.sortedBy { it.sessionDate }
        } else {
            list = list.sortedByDescending { it.sessionDate }
        }
        list
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Dimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.SmallGap)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${filteredSessions.size} Attendance Sessions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showFilterSheet = true }) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Filter Sessions",
                            tint = if (filterState.isFiltered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = onExportReport) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Export CSV")
                    }
                }
            }
        }

        if (filteredSessions.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Default.Check,
                    title = if (filterState.isFiltered) "No matching sessions" else "No attendance sessions yet",
                    message = if (filterState.isFiltered) "Try adjusting or clearing your filters." else "Go to the Calendar tab to start face or manual attendance."
                )
            }
        } else {
            items(filteredSessions, key = { it.id }) { session ->
                SessionCard(
                    session = session,
                    onClick = { onSessionOpen(session) }
                )
            }
        }
    }

    if (showFilterSheet) {
        com.attract.attendance.ui.components.HistoryFilterBottomSheet(
            onDismissRequest = { showFilterSheet = false },
            filterState = filterState,
            availableMonths = availableMonths,
            onApplyFilter = { nextState ->
                filterState = nextState
            },
            onResetFilter = {
                filterState = com.attract.attendance.ui.components.HistoryFilterState()
            }
        )
    }
}
