package com.kouloundissa.twinstracker.ui.components

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kouloundissa.twinstracker.data.EventType
import com.kouloundissa.twinstracker.presentation.analysis.AnalysisRange
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
    modifier: Modifier = Modifier
) {
    var showDateRangePicker by remember { mutableStateOf(false) }
    val dateRangePickerState = rememberDateRangePickerState()

    val displayStartDate = remember(filter) {
        when {
            filter.selectedRange == AnalysisRange.CUSTOM && filter.customStartDate != null ->
                filter.customStartDate

            filter.selectedRange != AnalysisRange.CUSTOM ->
                LocalDate.now().minusDays((filter.selectedRange.days - 1).toLong())

            else -> null
        }
    }

    val displayEndDate = remember(filter) {
        when {
            filter.selectedRange == AnalysisRange.CUSTOM && filter.customEndDate != null ->
                filter.customEndDate

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
                text = "Date Range",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = DarkBlue
            )

            if (dateRangeText.isNotEmpty()) {
                Text(
                    text = dateRangeText,
                    style = MaterialTheme.typography.bodySmall,
                    color = DarkBlue.copy(alpha = 0.7f)
                )
            }
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(horizontal = 2.dp)
        ) {
            items(AnalysisRange.entries.toTypedArray()) { range ->
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
                            text = range.displayName,
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    selected = filter.selectedRange == range,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = BackgroundColor.copy(alpha = 0.15f),
                        labelColor = DarkGrey.copy(alpha = 0.85f),
                        selectedContainerColor = BackgroundColor.copy(alpha = 0.95f),
                        selectedLabelColor = DarkBlue
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        borderColor = DarkGrey.copy(alpha = 0.25f),
                        selectedBorderColor = DarkGrey.copy(alpha = 0.95f),
                        borderWidth = 0.5.dp,
                        selectedBorderWidth = 2.dp,
                        enabled = true,
                        selected = false,
                    ),
                    shape = MaterialTheme.shapes.extraLarge
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
                                    .atZone(ZoneId.systemDefault()).toLocalDate()
                                val endDate = Instant.ofEpochMilli(endMillis)
                                    .atZone(ZoneId.systemDefault()).toLocalDate()
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


    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Baby",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = DarkBlue,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            if (selectedBaby.value != null) {
                Text(
                    text = selectedBaby.value!!.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = DarkBlue.copy(alpha = 0.7f)
                )
            }
        }


        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(horizontal = 2.dp)
        ) {
            items(babies) { baby ->
                FilterChip(
                    onClick = {
                        val newSelected = if (filter.selectedBabies.contains(baby)) {
                            emptySet()
                        } else {
                            setOf(baby)  // Store Baby object directly
                        }
                        onFilterChanged(filter.copy(selectedBabies = newSelected))
                    },
                    label = { Text(baby.name, style = MaterialTheme.typography.labelSmall) },
                    selected = filter.selectedBabies.contains(baby),
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = BackgroundColor.copy(alpha = 0.15f),
                        labelColor = DarkGrey.copy(alpha = 0.85f),
                        selectedContainerColor = BackgroundColor.copy(alpha = 0.95f),
                        selectedLabelColor = DarkBlue
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        borderColor = DarkGrey.copy(alpha = 0.25f),
                        selectedBorderColor = DarkGrey.copy(alpha = 0.95f),
                        borderWidth = 0.5.dp,
                        selectedBorderWidth = 2.dp,
                        enabled = true,
                        selected = false,
                    ),
                    shape = MaterialTheme.shapes.extraLarge
                )
            }
        }
    }
}

@Composable
fun EventTypeFilterSection(
    filter: AnalysisFilter.EventTypeFilter,
    onFilterChanged: (AnalysisFilter.EventTypeFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    val eventTypes = EventType.entries
    val selectedCount = filter.selectedTypes.size

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Event Type",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = DarkBlue
            )

            if (selectedCount > 0) {
                Text(
                    text = "$selectedCount selected",
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
            eventTypes.forEach { eventType ->
                val isSelected = filter.selectedTypes.contains(eventType)

                FilterChip(
                    onClick = {
                        val newSelected = filter.selectedTypes.toMutableSet().apply {
                            if (contains(eventType)) {
                                remove(eventType)
                            } else {
                                add(eventType)
                            }
                        }
                        onFilterChanged(filter.copy(selectedTypes = newSelected))
                    },
                    label = { Text(eventType.displayName, style = MaterialTheme.typography.labelSmall) },
                    selected = isSelected,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = BackgroundColor.copy(alpha = 0.15f),
                        labelColor = DarkGrey.copy(alpha = 0.85f),
                        selectedContainerColor = BackgroundColor.copy(alpha = 0.85f),
                        selectedLabelColor = eventType.color
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = false,
                        borderColor = DarkGrey.copy(alpha = 0.25f),
                        selectedBorderColor = DarkGrey.copy(alpha = 0.95f),
                        borderWidth = 0.5.dp,
                        selectedBorderWidth = 2.dp
                    ),
                    leadingIcon = {
                        Icon(
                            imageVector = eventType.icon,
                            contentDescription = eventType.displayName,
                            modifier = Modifier.size(16.dp),
                            tint = if (isSelected) eventType.color else DarkGrey.copy(alpha = 0.6f)
                        )
                    },
                    shape = MaterialTheme.shapes.extraLarge
                )
            }
        }
    }
}
