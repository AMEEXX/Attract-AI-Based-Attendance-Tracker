package com.attract.attendance.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import com.attract.attendance.ui.components.AttractTextButton
import com.attract.attendance.ui.components.rememberFeedbackClick
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

enum class HistorySortOrder {
    NEWEST_FIRST,
    OLDEST_FIRST
}

data class HistoryFilterState(
    val sortOrder: HistorySortOrder = HistorySortOrder.NEWEST_FIRST,
    val selectedMonth: String? = null // e.g. "2026-08"
) {
    val isFiltered: Boolean get() = sortOrder != HistorySortOrder.NEWEST_FIRST || selectedMonth != null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryFilterBottomSheet(
    onDismissRequest: () -> Unit,
    filterState: HistoryFilterState,
    availableMonths: List<String>,
    onApplyFilter: (HistoryFilterState) -> Unit,
    onResetFilter: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Filter Sessions",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                if (filterState.isFiltered) {
                    AttractTextButton(onClick = onResetFilter) {
                        Text("Reset")
                    }
                }
            }

            Text(
                text = "Sort Order",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val selectNewest = rememberFeedbackClick { onApplyFilter(filterState.copy(sortOrder = HistorySortOrder.NEWEST_FIRST)) }
                val selectOldest = rememberFeedbackClick { onApplyFilter(filterState.copy(sortOrder = HistorySortOrder.OLDEST_FIRST)) }
                SegmentedButton(
                    selected = filterState.sortOrder == HistorySortOrder.NEWEST_FIRST,
                    onClick = selectNewest,
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) {
                    Text("Newest first")
                }
                SegmentedButton(
                    selected = filterState.sortOrder == HistorySortOrder.OLDEST_FIRST,
                    onClick = selectOldest,
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) {
                    Text("Oldest first")
                }
            }

            if (availableMonths.isNotEmpty()) {
                Text(
                    text = "Filter by Month",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(availableMonths) { month ->
                        val isSelected = filterState.selectedMonth == month
                        val chipClick = rememberFeedbackClick {
                            val next = if (isSelected) null else month
                            onApplyFilter(filterState.copy(selectedMonth = next))
                        }
                        FilterChip(
                            selected = isSelected,
                            onClick = chipClick,
                            label = { Text(month) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
