package com.kouloundissa.twinstracker.ui.components

import android.content.Context
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kouloundissa.twinstracker.R
import com.kouloundissa.twinstracker.data.AnalysisRange
import com.kouloundissa.twinstracker.data.Baby
import com.kouloundissa.twinstracker.data.EventType
import com.kouloundissa.twinstracker.data.getDisplayName
import com.kouloundissa.twinstracker.presentation.viewmodel.EventViewModel
import com.kouloundissa.twinstracker.presentation.viewmodel.EventViewModel.DateRangeParams
import com.kouloundissa.twinstracker.ui.theme.BackgroundColor
import com.kouloundissa.twinstracker.ui.theme.DarkBlue
import com.kouloundissa.twinstracker.ui.theme.DarkGrey
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisFilterPanel(
    filters: AnalysisFilters,
    onFiltersChanged: (AnalysisFilters) -> Unit,
    onExpandedChanged: (Boolean) -> Unit,
    eventViewModel: EventViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
    allowedRanges: Set<AnalysisRange> = AnalysisRange.entries.toSet()
) {
    val isLoading by eventViewModel.isLoading.collectAsState()
    var isExpanded by remember { mutableStateOf(false) }
    val backgroundColor = BackgroundColor
    val contentColor = DarkGrey
    val tint = DarkBlue
    val cornerShape = MaterialTheme.shapes.extraLarge

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        ExpandablePanel(
            headerContent = { isExpandedState ->
                FilterPanelHeader(
                    filters = filters,
                    modifier = Modifier.weight(1f)
                )
                // Right section: Action buttons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.padding(start = 20.dp)
                ) {
                    IconButton(
                        onClick = {
                            isExpanded = !isExpanded
                            onExpandedChanged(isExpanded)
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.Transparent)
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                            contentDescription = "Toggle filter selector",
                            tint = tint,
                        )
                    }
                }
            },
            expandedContent = {
                FilterPanelContent(
                    filters = filters,
                    onFiltersChanged = { newFilters ->
                        val shouldCollapse =
                            newFilters.babyFilter != filters.babyFilter ||
                                    newFilters.dateRange != filters.dateRange

                        onFiltersChanged(newFilters)
                        if (shouldCollapse) {
                            isExpanded = false
                            onExpandedChanged(isExpanded)
                        }
                    },
                    allowedRanges = allowedRanges
                )
            },
            isExpanded = isExpanded,
            onExpandToggle = {
                isExpanded = !isExpanded
                onExpandedChanged(isExpanded)
            },
            modifier = Modifier,
            isLoading = isLoading,
        )
    }
}

@Composable
private fun FilterPanelHeader(
    filters: AnalysisFilters,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activeFilterCount = countActiveFilters(filters)
    val filterSummary = remember(filters) { getFilterSummary(filters, context) }

    val tint = DarkBlue
    val contentColor = DarkGrey

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        Icon(
            imageVector = Icons.Default.FilterList,
            contentDescription = "Filters",
            tint = tint,
            modifier = Modifier
                .padding(end = 12.dp)
                .size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(id = R.string.filters),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = tint
            )

            if (activeFilterCount > 0) {
                Text(
                    text = filterSummary,
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(
                    text = "No filters applied",
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
private fun FilterPanelContent(
    filters: AnalysisFilters,
    onFiltersChanged: (AnalysisFilters) -> Unit,
    modifier: Modifier = Modifier,
    allowedRanges: Set<AnalysisRange> = AnalysisRange.entries.toSet()
) {
    val backgroundColor = BackgroundColor
    val contentColor = DarkGrey
    val tint = DarkBlue
    val cornerShape = MaterialTheme.shapes.extraLarge

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Baby Filter
        BabyFilterSection(
            filter = filters.babyFilter,
            onFilterChanged = { newBabyFilter ->
                onFiltersChanged(filters.copy(babyFilter = newBabyFilter))
            }
        )

        Divider(color = contentColor.copy(alpha = 0.1f), thickness = 1.dp)

        // Date Range Filter
        DateRangeFilterSection(
            filter = filters.dateRange,
            onFilterChanged = { newDateRange ->
                onFiltersChanged(filters.copy(dateRange = newDateRange))
            },
            allowedRanges = allowedRanges
        )

        Divider(color = contentColor.copy(alpha = 0.1f), thickness = 1.dp)

        // Event Type Filter
        EventTypeFilterSection(
            filter = filters.eventTypeFilter,
            onFilterChanged = { newEventTypeFilter ->
                onFiltersChanged(filters.copy(eventTypeFilter = newEventTypeFilter))
            }
        )

        // Clear All Filters Button
        if (countActiveFilters(filters) > 0) {
            Divider(color = contentColor.copy(alpha = 0.1f), thickness = 1.dp)

            TextButton(
                onClick = {
                    onFiltersChanged(
                        AnalysisFilters(
                            dateRange = filters.dateRange
                        )
                    )
                },
                modifier = Modifier.align(Alignment.End)
            ) {
                Icon(
                    imageVector = Icons.Default.Clear,
                    contentDescription = "Clear filters",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(id = R.string.clear_filters))
            }
        }
    }
}

private fun countActiveFilters(filters: AnalysisFilters): Int {
    var count = 0

    // Count date range as active
    count++

    // Count baby filters
    count += filters.babyFilter.selectedBabies.size

    // Count event type filters
    count += filters.eventTypeFilter.selectedTypes.size

    return count
}

private fun getFilterSummary(filters: AnalysisFilters, context: Context): String {

    val parts = mutableListOf<String>()

    if (filters.babyFilter.selectedBabies.isNotEmpty()) {
        parts.add(filters.babyFilter.selectedBabies.first().name)
    }

    parts.add(filters.dateRange.selectedRange.getDisplayName(context))

    if (filters.eventTypeFilter.selectedTypes.isNotEmpty()) {
        parts.add("${filters.eventTypeFilter.selectedTypes.size} event type")
    }

    return parts.joinToString(", ")
}

sealed class AnalysisFilter {
    data class DateRange(
        val selectedRange: AnalysisRange,
        var customStartDate: Date? = null,
        var customEndDate: Date? = null
    ) : AnalysisFilter()

    data class BabyFilter(val selectedBabies: Set<Baby> = emptySet()) : AnalysisFilter()
    data class EventTypeFilter(val selectedTypes: Set<EventType> = emptySet()) : AnalysisFilter()
}

data class AnalysisFilters(
    val dateRange: AnalysisFilter.DateRange = AnalysisFilter.DateRange(AnalysisRange.THREE_DAYS),
    val babyFilter: AnalysisFilter.BabyFilter = AnalysisFilter.BabyFilter(),
    val eventTypeFilter: AnalysisFilter.EventTypeFilter = AnalysisFilter.EventTypeFilter(
        selectedTypes = emptySet()
    )
)

fun calculateRange(
    dateRange: AnalysisFilter.DateRange,
    zone: ZoneId = ZoneId.systemDefault()
): DateRangeParams {
    val today = LocalDate.now()
    val range = dateRange.selectedRange
    val customStartDate = dateRange.customStartDate
    val customEndDate = dateRange.customEndDate

    return when (range) {
        AnalysisRange.ONE_DAY,
        AnalysisRange.THREE_DAYS,
        AnalysisRange.ONE_WEEK,
        AnalysisRange.TWO_WEEKS,
        AnalysisRange.ONE_MONTH,
        AnalysisRange.THREE_MONTHS -> {
            val startDate = today.minusDays(range.days.toLong() - 1)
                .atStartOfDay(zone).toInstant()
            val endDate = today.atTime(23, 59, 59).atZone(zone).toInstant()
            DateRangeParams(
                Date.from(startDate),
                Date.from(endDate)
            ).also {
                Log.d(
                    "DateRange",
                    "LastDays result: ${it.startDate} → ${it.endDate} (${range.days} days)"
                )
            }
        }

        AnalysisRange.CUSTOM -> {
            val start = customStartDate ?: Date()
            val end = customEndDate ?: Date()
            val daysBetween = ChronoUnit.DAYS.between(
                start.toInstant(),
                end.toInstant()
            ) + 1
            DateRangeParams(start, end).also {
                Log.d(
                    "DateRange",
                    "Custom range: ${it.startDate} → ${it.endDate} ($daysBetween days)"
                )
            }
        }
    }
}
fun LocalDate.toDate(): Date =
    this.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli().let { Date(it) }
fun LocalDateTime.toDate(): Date {
    return Date.from(this.atZone(ZoneId.systemDefault()).toInstant())
}




