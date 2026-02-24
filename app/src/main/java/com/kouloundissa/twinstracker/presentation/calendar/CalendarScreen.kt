package com.kouloundissa.twinstracker.presentation.calendar

import android.app.Activity
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.kouloundissa.twinstracker.data.AnalysisRange
import com.kouloundissa.twinstracker.data.Event
import com.kouloundissa.twinstracker.data.EventType
import com.kouloundissa.twinstracker.data.Firestore.FirestoreTimestampUtils.toLocalDate
import com.kouloundissa.twinstracker.presentation.event.EventFormDialog
import com.kouloundissa.twinstracker.presentation.settings.SettingsScreen
import com.kouloundissa.twinstracker.presentation.viewmodel.BabyViewModel
import com.kouloundissa.twinstracker.presentation.viewmodel.EventViewModel
import com.kouloundissa.twinstracker.ui.components.Ad.AdManager
import com.kouloundissa.twinstracker.ui.components.AnalysisFilter
import com.kouloundissa.twinstracker.ui.components.AnalysisFilterPanel
import com.kouloundissa.twinstracker.ui.components.AnalysisFilters
import com.kouloundissa.twinstracker.ui.components.Calendar.DayCalendar
import com.kouloundissa.twinstracker.ui.components.Calendar.MonthCalendar
import com.kouloundissa.twinstracker.ui.components.Calendar.WeekCalendar
import com.kouloundissa.twinstracker.ui.components.calculateRange
import com.kouloundissa.twinstracker.ui.components.toDate
import com.kouloundissa.twinstracker.ui.theme.BackgroundColor
import com.kouloundissa.twinstracker.ui.theme.DarkBlue
import com.kouloundissa.twinstracker.ui.theme.DarkGrey
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

@Composable
fun CalendarScreen(
    navController: NavController,
    contentPadding: PaddingValues = PaddingValues(),
    isVisible: Boolean,
    eventViewModel: EventViewModel = hiltViewModel(),
    babyViewModel: BabyViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val activity = context as Activity

    /** State **/
    val isLoading by eventViewModel.isLoading.collectAsState()
    val errorMessage by eventViewModel.errorMessage.collectAsState()
    val selectedBaby by babyViewModel.selectedBaby.collectAsState()
    val favoriteEventTypes by eventViewModel.favoriteEventTypes.collectAsState()

    val analysisSnapshot by eventViewModel.analysisSnapshot.collectAsState()

    var selectedDate by rememberSaveable { mutableStateOf(LocalDate.now()) }
    //var filterTypes by rememberSaveable { mutableStateOf<Set<EventType>>(emptySet()) }
    var editingEvent by rememberSaveable { mutableStateOf<Event?>(null) }
    var showDialog by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }


    val filters = remember {
        mutableStateOf(
            AnalysisFilters(
                dateRange = AnalysisFilter.DateRange(AnalysisRange.ONE_WEEK)
            )
        )
    }

    var isFilterExpanded by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(selectedBaby, favoriteEventTypes) {
        val newBabyFilter = selectedBaby?.let { baby ->
            AnalysisFilter.BabyFilter(selectedBabies = setOf(baby))
        } ?: AnalysisFilter.BabyFilter()

        val selectedTypes = favoriteEventTypes.ifEmpty {
            EventType.entries.toSet()
        }

        val eventTypeFilter = filters.value.eventTypeFilter
            .takeIf { it.selectedTypes.isNotEmpty() }
            ?: AnalysisFilter.EventTypeFilter(selectedTypes = selectedTypes)

        filters.value = filters.value.copy(
            babyFilter = newBabyFilter,
            eventTypeFilter = eventTypeFilter
        )
    }

    LaunchedEffect(isVisible, filters.value) {
        if (isVisible) {
            filters.value.babyFilter.selectedBabies.firstOrNull()?.let { baby ->

                babyViewModel.selectBaby(baby)
                Log.d("CalendarScreen", "selectedRange Starting stream for babyId: ${baby}")
            }

            val dateRange = filters.value.dateRange
            val hasCustomDates =
                dateRange.customStartDate != null && dateRange.customEndDate != null

            val (startDate, endDate) = if (hasCustomDates) {
                dateRange.customStartDate?.toLocalDate()?.minusDays(1)?.atStartOfDay()
                    ?.toDate() to dateRange.customEndDate?.toLocalDate()?.atTime(23, 59, 59)
                    ?.toDate()
            } else {
                selectedDate = LocalDate.now()
                val params = calculateRange(dateRange)
                params.startDate.toLocalDate().minusDays(1).atStartOfDay()
                    .toDate() to params.endDate

            }

            val updatedFilters = filters.value.copy(
                dateRange = AnalysisFilter.DateRange(
                    selectedRange = AnalysisRange.CUSTOM,
                    customStartDate = startDate,
                    customEndDate = endDate
                )
            )
            Log.d("FilterUpdate", "Adjusted dates - Start: $startDate, End: $endDate")
            eventViewModel.refreshWithFilters(updatedFilters)

            if (filters.value.dateRange != AnalysisFilter.DateRange(AnalysisRange.ONE_DAY) &&
                filters.value.dateRange != AnalysisFilter.DateRange(AnalysisRange.ONE_WEEK)
            ) {
                AdManager.showInterstitial(activity)
            }
        }
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            eventViewModel.clearErrorMessage()
        }
    }
    LaunchedEffect(editingEvent) {
        editingEvent?.let {
            eventViewModel.loadEventIntoForm(it)
            showDialog = true
        }
    }
    val backgroundColor = BackgroundColor
    val contentColor = DarkGrey.copy(alpha = 0.5f)
    val tint = DarkBlue
    val cornerShape = MaterialTheme.shapes.extraLarge
    /** UI **/
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = Modifier.fillMaxSize()
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AnalysisFilterPanel(
                    filters = filters.value,
                    onFiltersChanged = { newFilters ->
                        filters.value = newFilters
                    },
                    onExpandedChanged = { isExpanded ->
                        isFilterExpanded = isExpanded
                    },
                    modifier = Modifier.weight(1f),
                    allowedRanges = setOf(
                        AnalysisRange.ONE_DAY,
                        AnalysisRange.ONE_WEEK,
                        AnalysisRange.ONE_MONTH
                    )
                )

                // Settings button
                if (!isFilterExpanded) {
                    IconButton(
                        onClick = {
                            showSettingsDialog = true
                        },
                        modifier = Modifier
                            .background(
                                BackgroundColor,
                                CircleShape
                            )
                            .size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = tint
                        )
                    }
                }
            }

            if (showSettingsDialog) {
                SettingsScreen(
                    navController = navController,
                    onDismiss = { showSettingsDialog = false }
                )
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                if (filters.value.dateRange.selectedRange == AnalysisRange.ONE_MONTH)
                    item {
                        MonthCalendar(
                            isloading = isLoading,
                            onMonthChange = { delta ->
                                val newDate = selectedDate.plusMonths(delta)
                                selectedDate = newDate

                                val newDateRange = filters.value.dateRange.copy(
                                    customStartDate = newDate.with(
                                        TemporalAdjusters.firstDayOfMonth()
                                    ).atStartOfDay().toDate(),
                                    customEndDate = newDate.with(
                                        TemporalAdjusters.lastDayOfMonth()
                                    ).atTime(23, 59, 59).toDate()
                                )

                                Log.d(
                                    "CalendarScreen",
                                    "Month range: ${newDateRange.customStartDate} to ${newDateRange.customEndDate}"
                                )
                                filters.value = filters.value.copy(dateRange = newDateRange)
                            },
                            eventsByDay = analysisSnapshot.eventsByDay,
                            selectedDate = selectedDate,
                            onDayClick = { newDate ->
                                selectedDate = newDate
                            }
                        )
                    }

                if (filters.value.dateRange.selectedRange == AnalysisRange.ONE_WEEK)
                    item {
                        WeekCalendar(
                            analysisSnapshot = analysisSnapshot,
                            selectedDate = selectedDate,
                            analysisFilter = filters.value,
                            onDayClick = { newDate ->
                                selectedDate = newDate
                            },
                            onWeekChange = { delta ->
                                val newDate = selectedDate.plusWeeks(delta)
                                selectedDate = newDate

                                val newDateRange = filters.value.dateRange.copy(
                                    customStartDate = newDate.with(
                                        TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)
                                    ).atStartOfDay().toDate(),
                                    customEndDate = newDate.with(
                                        TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)
                                    ).atTime(23, 59, 59).toDate()
                                )
                                Log.d(
                                    "CalendarScreen",
                                    "Week range: ${newDateRange.customStartDate} to ${newDateRange.customEndDate}"
                                )
                                filters.value.dateRange.customStartDate =
                                    newDateRange.customStartDate

                                filters.value = filters.value.copy(dateRange = newDateRange)
                            },
                            onEdit = { editingEvent = it },
                        )
                    }


                if (filters.value.dateRange.selectedRange == AnalysisRange.ONE_DAY || filters.value.dateRange.selectedRange == AnalysisRange.ONE_MONTH)
                    item {
                        DayCalendar(
                            currentDate = selectedDate,
                            analysisSnapshot = analysisSnapshot,
                            filterTypes = filters.value.eventTypeFilter.selectedTypes,
                            onEdit = { editingEvent = it },
                            onDayChange = { delta ->
                                val newDate = selectedDate.plusDays(delta)
                                val oldMonth = selectedDate.month
                                val newMonth = newDate.month

                                selectedDate = newDate

                                // Si on est en mode ONE_MONTH et qu'on change de mois
                                if (filters.value.dateRange.selectedRange == AnalysisRange.ONE_MONTH &&
                                    oldMonth != newMonth) {

                                    // Même logique que onMonthChange
                                    val newDateRange = filters.value.dateRange.copy(
                                        customStartDate = newDate.with(
                                            TemporalAdjusters.firstDayOfMonth()
                                        ).atStartOfDay().toDate(),
                                        customEndDate = newDate.with(
                                            TemporalAdjusters.lastDayOfMonth()
                                        ).atTime(23, 59, 59).toDate()
                                    )

                                    Log.d(
                                        "CalendarScreen",
                                        "Month range (from day change): ${newDateRange.customStartDate} to ${newDateRange.customEndDate}"
                                    )
                                    filters.value = filters.value.copy(dateRange = newDateRange)

                                } else if (filters.value.dateRange.selectedRange == AnalysisRange.ONE_DAY) {
                                    // Logique pour ONE_DAY uniquement
                                    val newDateRange = filters.value.dateRange.copy(
                                        customStartDate = newDate.atStartOfDay().toDate(),
                                        customEndDate = newDate.atTime(23, 59, 59).toDate()
                                    )

                                    Log.d(
                                        "CalendarScreen",
                                        "Day range: ${newDateRange.customStartDate} to ${newDateRange.customEndDate}"
                                    )
                                    filters.value = filters.value.copy(dateRange = newDateRange)
                                }
                                // Sinon (mode ONE_MONTH sans changement de mois) : ne rien faire
                            }
                        )
                    }
            }

            // Edit Dialog
            if (showDialog) {
                EventFormDialog(
                    initialBabyId = selectedBaby?.id ?: return@Column,
                    onDismiss = {
                        showDialog = false
                        editingEvent = null
                        eventViewModel.resetFormState()
                    }
                )
            }
        }
    }
}