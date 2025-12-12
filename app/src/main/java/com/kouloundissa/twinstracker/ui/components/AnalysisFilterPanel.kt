package com.kouloundissa.twinstracker.ui.components

import android.content.Context
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
import com.kouloundissa.twinstracker.ui.theme.BackgroundColor
import com.kouloundissa.twinstracker.ui.theme.DarkBlue
import com.kouloundissa.twinstracker.ui.theme.DarkGrey
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisFilterPanel(
    filters: AnalysisFilters,
    onFiltersChanged: (AnalysisFilters) -> Unit,
    eventViewModel: EventViewModel = hiltViewModel(),
) {
    val isLoading by eventViewModel.isLoading.collectAsState()
    var isExpanded by remember { mutableStateOf(false) }

    val backgroundColor = BackgroundColor
    val contentColor = DarkGrey
    val tint = DarkBlue
    val cornerShape = MaterialTheme.shapes.extraLarge

    Box(
        modifier = Modifier
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(start = 12.dp)
                ) {
                    IconButton(
                        onClick = { isExpanded = !isExpanded },
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
                        }
                    },
                )
            },
            isExpanded = isExpanded,
            onExpandToggle = { isExpanded = !isExpanded },
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
            modifier = Modifier.size(20.dp)
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
    modifier: Modifier = Modifier
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
            }
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
        val customStartDate: LocalDate? = null,
        val customEndDate: LocalDate? = null
    ) : AnalysisFilter()

    data class BabyFilter(val selectedBabies: Set<Baby> = emptySet()) : AnalysisFilter()
    data class EventTypeFilter(val selectedTypes: Set<EventType> = emptySet()) : AnalysisFilter()
    // Easy to add more filters in the future
}

data class AnalysisFilters(
    val dateRange: AnalysisFilter.DateRange = AnalysisFilter.DateRange(AnalysisRange.ONE_WEEK),
    val babyFilter: AnalysisFilter.BabyFilter = AnalysisFilter.BabyFilter(),
    val eventTypeFilter: AnalysisFilter.EventTypeFilter = AnalysisFilter.EventTypeFilter(
        selectedTypes = emptySet()
    )
)




