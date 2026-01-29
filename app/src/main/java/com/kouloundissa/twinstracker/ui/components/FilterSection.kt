package com.kouloundissa.twinstracker.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kouloundissa.twinstracker.R
import com.kouloundissa.twinstracker.data.AnalysisRange
import com.kouloundissa.twinstracker.data.EventType
import com.kouloundissa.twinstracker.data.EventType.Companion.getDisplayName
import com.kouloundissa.twinstracker.data.Firestore.FirestoreTimestampUtils.toLocalDate
import com.kouloundissa.twinstracker.data.getDisplayName
import com.kouloundissa.twinstracker.presentation.viewmodel.BabyViewModel
import com.kouloundissa.twinstracker.ui.theme.BackgroundColor
import com.kouloundissa.twinstracker.ui.theme.DarkBlue
import com.kouloundissa.twinstracker.ui.theme.DarkGrey
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangeFilterSection(
    filter: AnalysisFilter.DateRange,
    onFilterChanged: (AnalysisFilter.DateRange) -> Unit,
    modifier: Modifier = Modifier,
    allowedRanges: Set<AnalysisRange> = AnalysisRange.entries.toSet()
) {
    val context = LocalContext.current
    val backgroundColor = BackgroundColor
    val contentColor = DarkGrey
    val tint = DarkBlue

    val cornerShape = MaterialTheme.shapes.extraLarge

    var showDateRangePicker by remember { mutableStateOf(false) }
    val dateRangePickerState = rememberDateRangePickerState()

    val displayStartDate = remember(filter) {
        when {
            filter.selectedRange == AnalysisRange.CUSTOM && filter.customStartDate != null ->
                filter.customStartDate?.toLocalDate()

            filter.selectedRange != AnalysisRange.CUSTOM ->
                LocalDate.now().minusDays((filter.selectedRange.days - 1).toLong())

            else -> null
        }
    }

    val displayEndDate = remember(filter) {
        when {
            filter.selectedRange == AnalysisRange.CUSTOM && filter.customEndDate != null ->
                filter.customEndDate?.toLocalDate()

            filter.selectedRange != AnalysisRange.CUSTOM -> LocalDate.now()
            else -> null
        }
    }

    val dateRangeText = remember(displayStartDate, displayEndDate) {
        if (displayStartDate != null && displayEndDate != null) {
            val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
            if (displayStartDate == displayEndDate) {
                displayStartDate.format(formatter)
            } else {
                "${displayStartDate.format(formatter)} - ${displayEndDate.format(formatter)}"
            }
        } else {
            ""
        }
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(id = R.string.date_range),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = tint
            )

            if (dateRangeText.isNotEmpty()) {
                Text(
                    text = dateRangeText,
                    style = MaterialTheme.typography.bodySmall,
                    color = tint.copy(alpha = 0.7f)
                )
            }
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(horizontal = 2.dp)
        ) {
            items(allowedRanges.toList()) { range ->
                val isSelected = filter.selectedRange == range
                FilterChip(
                    onClick = {
                        if (range == AnalysisRange.CUSTOM) {
                            showDateRangePicker = true
                        } else {
                            onFilterChanged(
                                filter.copy(
                                    selectedRange = range,
                                    customStartDate = null,
                                    customEndDate = null
                                )
                            )
                        }
                    },
                    label = {
                        Text(
                            text = range.getDisplayName(context),
                            style = if (isSelected) {
                                MaterialTheme.typography.labelMedium
                            } else {
                                MaterialTheme.typography.labelSmall
                            }
                        )
                    },
                    selected = isSelected,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = backgroundColor.copy(alpha = 0.25f),
                        labelColor = contentColor.copy(alpha = 0.5f),
                        selectedContainerColor = backgroundColor.copy(alpha = 0.85f),
                        selectedLabelColor = tint
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = contentColor.copy(alpha = 0.55f),
                        selectedBorderColor = tint.copy(alpha = 0.55f),
                        borderWidth = 0.5.dp,
                        selectedBorderWidth = 1.dp
                    ),
                    shape = cornerShape
                )
            }
        }
    }

    if (showDateRangePicker) {
        DatePickerDialog(
            onDismissRequest = { showDateRangePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        dateRangePickerState.selectedStartDateMillis?.let { startMillis ->
                            dateRangePickerState.selectedEndDateMillis?.let { endMillis ->
                                val startDate = Instant.ofEpochMilli(startMillis)
                                    .atZone(ZoneId.systemDefault())
                                    .toLocalDate()
                                    .atStartOfDay()
                                    .toDate()

                                val endDate = Instant.ofEpochMilli(endMillis)
                                    .atZone(ZoneId.systemDefault())
                                    .toLocalDate()
                                    .atTime(23, 59, 59)
                                    .toDate()

                                onFilterChanged(
                                    filter.copy(
                                        selectedRange = AnalysisRange.CUSTOM,
                                        customStartDate = startDate,
                                        customEndDate = endDate
                                    )
                                )
                            }
                        }
                        showDateRangePicker = false
                    },
                    enabled = dateRangePickerState.selectedEndDateMillis != null
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDateRangePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DateRangePicker(
                state = dateRangePickerState,
                title = { Text("Select Date Range") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(500.dp)
                    .padding(16.dp)
            )
        }
    }
}

@Composable
fun BabyFilterSection(
    filter: AnalysisFilter.BabyFilter,
    babyViewModel: BabyViewModel = hiltViewModel(),
    onFilterChanged: (AnalysisFilter.BabyFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedBaby = babyViewModel.selectedBaby.collectAsState()
    val babies by babyViewModel.babies.collectAsState()


    BabySelectorRow(
        babies = babies,
        selectedBaby = selectedBaby.value,
        onSelectBaby = {
            val babySet = setOf(it)
            onFilterChanged(filter.copy(selectedBabies = babySet))
        },
        onAddBaby = null
    )
}

@Composable
fun EventTypeFilterSection(
    filter: AnalysisFilter.EventTypeFilter,
    onFilterChanged: (AnalysisFilter.EventTypeFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    val eventTypes = EventType.entries
    val selectedCount = filter.selectedTypes.size

    val backgroundColor = BackgroundColor
    val contentColor = DarkGrey
    val tint = DarkBlue

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(id = R.string.event_type_label),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = DarkBlue
            )

            if (selectedCount > 0) {
                Text(
                    text = stringResource(id = R.string.selected_count_format, selectedCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = DarkBlue.copy(alpha = 0.7f)
                )
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            FilterBar(
                types = eventTypes.toSet(),
                selected = filter.selectedTypes,
                onToggle = { eventType ->
                    val newSelected = filter.selectedTypes.toMutableSet().apply {
                        if (contains(eventType)) remove(eventType) else add(eventType)
                    }
                    onFilterChanged(filter.copy(selectedTypes = newSelected))
                },
                layoutMode = FilterBarLayoutMode.FLOW_WRAP
            )
        }
    }
}

@Composable
fun FilterBar(
    types: Set<EventType>,
    selected: Set<EventType>,
    onToggle: (EventType) -> Unit,
    modifier: Modifier = Modifier,
    layoutMode: FilterBarLayoutMode = FilterBarLayoutMode.HORIZONTAL_SCROLL,
) {
    val containerModifier = when (layoutMode) {
        FilterBarLayoutMode.HORIZONTAL_SCROLL -> {
            modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        }

        FilterBarLayoutMode.FLOW_WRAP -> {
            modifier.fillMaxWidth()
        }
    }

    val content: @Composable () -> Unit = {
        types.forEach { type ->
            FilterChipContent(
                type = type,
                isSelected = selected.contains(type),
                onToggle = onToggle,
            )
        }
    }
    when (layoutMode) {
        FilterBarLayoutMode.HORIZONTAL_SCROLL -> {
            Row(
                modifier = containerModifier,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                content()
            }
        }

        FilterBarLayoutMode.FLOW_WRAP -> {
            FlowRow(
                modifier = containerModifier,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                content()
            }
        }
    }
}

/**
 * Extracted FilterChip logic for reusability
 * Reduces duplication and makes styling changes easier
 */
@Composable
fun FilterChipContent(
    type: EventType,
    isSelected: Boolean,
    onToggle: (EventType) -> Unit,
) {
    val contentColor = DarkGrey.copy(alpha = 0.5f)
    val backgroundColor = BackgroundColor.copy(alpha = 0.2f)
    val tint = DarkBlue
    FilterChip(
        selected = isSelected,
        onClick = { onToggle(type) },
        label = {
            Text(
                type.getDisplayName(context = LocalContext.current),
                style = if (isSelected) {
                    MaterialTheme.typography.labelMedium
                } else {
                    MaterialTheme.typography.labelSmall
                }
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = backgroundColor.copy(alpha = 0.25f),
            labelColor = contentColor.copy(alpha = 0.5f),
            selectedContainerColor = backgroundColor.copy(alpha = 0.85f),
            selectedLabelColor = type.color
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = isSelected,
            borderColor = type.color.copy(alpha = 0.25f),
            selectedBorderColor = type.color.copy(alpha = 0.65f),
            borderWidth = 0.5.dp,
            selectedBorderWidth = 1.dp
        ),
        leadingIcon = {
            Icon(
                imageVector = type.icon,
                contentDescription = type.getDisplayName(context = LocalContext.current),
                modifier = Modifier.size(16.dp),
                tint = if (isSelected) type.color else DarkGrey.copy(alpha = 0.6f)
            )
        },
        shape = MaterialTheme.shapes.extraLarge
    )
}
enum class FilterBarLayoutMode {
    HORIZONTAL_SCROLL,
    FLOW_WRAP
}


