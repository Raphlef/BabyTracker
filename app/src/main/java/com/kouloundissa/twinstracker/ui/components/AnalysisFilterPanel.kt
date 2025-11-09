package com.kouloundissa.twinstracker.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kouloundissa.twinstracker.data.Baby
import com.kouloundissa.twinstracker.data.EventType
import com.kouloundissa.twinstracker.presentation.analysis.AnalysisRange
import com.kouloundissa.twinstracker.presentation.viewmodel.EventViewModel
import com.kouloundissa.twinstracker.ui.theme.*
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisFilterPanel(
    filters: AnalysisFilters,
    onFiltersChanged: (AnalysisFilters) -> Unit,
    modifier: Modifier = Modifier,
    eventViewModel: EventViewModel = hiltViewModel(),
) {
    val isLoading by eventViewModel.isLoading.collectAsState()
    var isExpanded by remember { mutableStateOf(false) }

    val backgroundColor = BackgroundColor
    val contentColor = DarkGrey
    val tint = DarkBlue
    val cornerShape = MaterialTheme.shapes.extraLarge

    Box(modifier = modifier) {
        Column {
            // Add blur when loading
            FilterPanelHeader(
                isExpanded = isExpanded,
                filters = filters,
                onExpandToggle = { isExpanded = !isExpanded },
                modifier = Modifier.blur(if (isLoading) 3.dp else 0.dp)
            )

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp).blur(if (isLoading) 3.dp else 0.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Date Range Filter
                    DateRangeFilterSection(
                        filter = filters.dateRange,
                        onFilterChanged = { newDateRange ->
                            onFiltersChanged(filters.copy(dateRange = newDateRange))
                        }
                    )

                    Divider(color = contentColor.copy(alpha = 0.1f), thickness = 1.dp)

                    // Baby Filter
                    BabyFilterSection(
                        filter = filters.babyFilter,
                        onFilterChanged = { newBabyFilter ->
                            onFiltersChanged(filters.copy(babyFilter = newBabyFilter))
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
                        Divider(color = DarkGrey.copy(alpha = 0.1f), thickness = 1.dp)

                        TextButton(
                            onClick = {
                                onFiltersChanged(
                                    AnalysisFilters(
                                        dateRange = filters.dateRange // Keep date range
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
                            Text("Clear Filters")
                        }
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = isLoading,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .background(
                        color = Color.Transparent,
                    )
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(40.dp),
                        strokeWidth = 3.dp,
                        color = DarkBlue,
                        trackColor = DarkBlue.copy(alpha = 0.2f)
                    )
                    Text(
                        text = "Loading events...",
                        style = MaterialTheme.typography.labelSmall,
                        color = DarkBlue,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterPanelHeader(
    isExpanded: Boolean,
    filters: AnalysisFilters,
    onExpandToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = BackgroundColor
    val contentColor = DarkGrey
    val tint = DarkBlue
    val cornerShape = MaterialTheme.shapes.extraLarge

    val activeFilterCount = countActiveFilters(filters)
    val filterSummary = remember(filters) { getFilterSummary(filters) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .clickable(onClick = onExpandToggle),
        color = BackgroundColor.copy(alpha = 0.85f),
        shape = cornerShape,
        border = BorderStroke(1.dp, contentColor.copy(alpha = 0.55f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FilterList,
                    contentDescription = "Filters",
                    tint = DarkBlue,
                    modifier = Modifier.size(20.dp)
                )

                Column {
                    Text(
                        text = "Filters",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = DarkBlue
                    )

                    if (activeFilterCount > 0) {
                        Text(
                            text = filterSummary,
                            style = MaterialTheme.typography.labelSmall,
                            color = DarkGrey.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Text(
                            text = "No filters applied",
                            style = MaterialTheme.typography.labelSmall,
                            color = DarkGrey.copy(alpha = 0.5f)
                        )
                    }
                }
            }
            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                tint = DarkBlue,
                modifier = Modifier
                    .size(24.dp)
                    .rotate(if (isExpanded) 0f else 0f)
            )
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

private fun getFilterSummary(filters: AnalysisFilters): String {
    val parts = mutableListOf<String>()

    parts.add(filters.dateRange.selectedRange.displayName)

    if (filters.babyFilter.selectedBabies.isNotEmpty()) {
        parts.add(filters.babyFilter.selectedBabies.first().name)
    }

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

public data class AnalysisFilters(
    val dateRange: AnalysisFilter.DateRange = AnalysisFilter.DateRange(AnalysisRange.ONE_WEEK),
    val babyFilter: AnalysisFilter.BabyFilter = AnalysisFilter.BabyFilter(),
    val eventTypeFilter: AnalysisFilter.EventTypeFilter = AnalysisFilter.EventTypeFilter(
        selectedTypes = emptySet()
    )
)




