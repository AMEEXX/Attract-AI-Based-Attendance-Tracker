package com.attract.attendance.feature.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.attract.attendance.core.model.SessionSummary
import com.attract.attendance.data.repository.AttractRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

data class CalendarState(
    val currentMonth: YearMonth = YearMonth.now(),
    val selectedDate: LocalDate? = LocalDate.now(),
    val daysWithSessions: Set<Int> = emptySet(),
    val sessionsForDate: List<SessionSummary> = emptyList(),
    val isLoading: Boolean = false
)

class CalendarViewModel(
    private val classId: Long,
    private val repository: AttractRepository
) : ViewModel() {

    private val _currentMonth = MutableStateFlow(YearMonth.now())
    private val _selectedDate = MutableStateFlow<LocalDate?>(LocalDate.now())

    @OptIn(ExperimentalCoroutinesApi::class)
    private val daysWithSessionsFlow = _currentMonth.flatMapLatest { month ->
        val prefix = String.format("%04d-%02d", month.year, month.monthValue)
        repository.observeSessionDaysForMonth(classId, prefix)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val sessionsForDateFlow = _selectedDate.flatMapLatest { date ->
        if (date == null) {
            flowOf(emptyList())
        } else {
            val dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
            repository.observeSessionsForDate(classId, dateStr)
        }
    }

    val uiState: StateFlow<CalendarState> = combine(
        _currentMonth,
        _selectedDate,
        daysWithSessionsFlow,
        sessionsForDateFlow
    ) { month, selectedDate, days, sessions ->
        CalendarState(
            currentMonth = month,
            selectedDate = selectedDate,
            daysWithSessions = days,
            sessionsForDate = sessions,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CalendarState()
    )

    fun previousMonth() {
        _currentMonth.update { it.minusMonths(1) }
        _selectedDate.update { null }
    }

    fun nextMonth() {
        _currentMonth.update { it.plusMonths(1) }
        _selectedDate.update { null }
    }

    fun selectDate(date: LocalDate) {
        _selectedDate.update { date }
    }
}
