package com.kouloundissa.twinstracker.presentation.calendar

import DayCalendar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kouloundissa.twinstracker.data.AnalysisRange
import com.kouloundissa.twinstracker.data.Event
import com.kouloundissa.twinstracker.data.EventType
import com.kouloundissa.twinstracker.data.Firestore.FirestoreTimestampUtils.toLocalDate
import com.kouloundissa.twinstracker.presentation.event.EventFormDialog
import com.kouloundissa.twinstracker.presentation.viewmodel.BabyViewModel
import com.kouloundissa.twinstracker.presentation.viewmodel.EventViewModel
import com.kouloundissa.twinstracker.ui.components.AnalysisFilter
import com.kouloundissa.twinstracker.ui.components.AnalysisFilters
import com.kouloundissa.twinstracker.ui.components.Calendar.MonthCalendar
import com.kouloundissa.twinstracker.ui.components.Calendar.WeekCalendar
import com.kouloundissa.twinstracker.ui.components.FilterBar
import com.kouloundissa.twinstracker.ui.components.FilterBarLayoutMode
import com.kouloundissa.twinstracker.ui.theme.BackgroundColor
import com.kouloundissa.twinstracker.ui.theme.DarkBlue
import com.kouloundissa.twinstracker.ui.theme.DarkGrey
import java.time.LocalDate

@Composable
fun CalendarScreen(
    contentPadding: PaddingValues = PaddingValues(),
    isVisible: Boolean,
    eventViewModel: EventViewModel = hiltViewModel(),
    babyViewModel: BabyViewModel = hiltViewModel()
) {
    /** State **/
    val isLoading by eventViewModel.isLoading.collectAsState()
    val errorMessage by eventViewModel.errorMessage.collectAsState()
    val selectedBaby by babyViewModel.selectedBaby.collectAsState()

    val analysisSnapshot by eventViewModel.analysisSnapshot.collectAsState()

    var currentMonth by rememberSaveable { mutableStateOf(LocalDate.now()) }
    var selectedDate by rememberSaveable { mutableStateOf(LocalDate.now()) }
    var filterTypes by rememberSaveable { mutableStateOf<Set<EventType>>(emptySet()) }
    var editingEvent by rememberSaveable { mutableStateOf<Event?>(null) }
    var showDialog by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    /** Compute available types & init filter **/
    val availableTypes = remember(analysisSnapshot, currentMonth) {
        analysisSnapshot.events
            .filter { event ->
                val eventDate = event.timestamp.toLocalDate()
                eventDate.monthValue == currentMonth.monthValue && eventDate.year == currentMonth.year
            }
            .groupingBy { EventType.forClass(it::class) }
            .eachCount()
            .toList()
            .sortedByDescending { it.second }
            .map { it.first }
            .distinct()
            .toSet()
    }
    LaunchedEffect(availableTypes) { filterTypes = availableTypes }

    LaunchedEffect(isVisible, currentMonth, selectedBaby?.id) {
        if (isVisible) {
            selectedBaby?.let { baby ->
                val startOfMonth = currentMonth.withDayOfMonth(1)
                val startOfRange = startOfMonth.minusWeeks(1)
                val endOfMonth = currentMonth.withDayOfMonth(currentMonth.lengthOfMonth())
                    .plusDays(1)

                val endOfRange = endOfMonth.plusWeeks(1)

                val dateRange = AnalysisFilter.DateRange(
                    AnalysisRange.CUSTOM,
                    startOfRange,
                    endOfRange
                )
                val newBabyFilter = AnalysisFilter.BabyFilter(selectedBabies = setOf(baby))
                val eventTypeFilter = AnalysisFilter.EventTypeFilter(selectedTypes = filterTypes)

                val filterValue = AnalysisFilters(
                    dateRange = dateRange,
                    babyFilter = newBabyFilter,
                    eventTypeFilter = eventTypeFilter
                )
                eventViewModel.refreshWithFilters(filterValue)
                val today = LocalDate.now()
                selectedDate =
                    if (today.year == currentMonth.year && today.monthValue == currentMonth.monthValue) {
                        today  // ✅ Aujourd'hui si c'est dans le mois courant
                    } else {
                        currentMonth.withDayOfMonth(1)  // ✅ Premier jour sinon
                    }
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
    val dailyEvents = remember(analysisSnapshot, selectedDate, filterTypes) {
        analysisSnapshot.eventsByDay[selectedDate]
            ?.filter { event ->
                filterTypes.contains(EventType.forClass(event::class))
            }
            ?.takeIf { filterTypes.isNotEmpty() }
            ?: emptyList()
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
            FilterBar(
                types = availableTypes,
                selected = filterTypes,
                onToggle = { type ->
                    filterTypes =
                        if (type in filterTypes) filterTypes - type else filterTypes + type
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                layoutMode = FilterBarLayoutMode.HORIZONTAL_SCROLL
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                item {
                    val eventsByDayForCalendar: Map<LocalDate, List<Event>> =
                        remember(analysisSnapshot, currentMonth, filterTypes) {
                            analysisSnapshot.eventsByDay
                                .filterKeys { date ->
                                    date.monthValue == currentMonth.monthValue && date.year == currentMonth.year
                                }
                                .mapValues { (_, dayEvents) ->
                                    dayEvents.filter { event ->
                                        filterTypes.contains(EventType.forClass(event::class))
                                    }
                                }
                        }

                    MonthCalendar(
                        currentMonth = currentMonth,
                        onMonthChange = { delta ->
                            currentMonth = currentMonth.plusMonths(delta)
                        },
                        eventsByDay = eventsByDayForCalendar,
                        selectedDate = selectedDate,
                        onDayClick = { newDate ->
                            selectedDate = newDate

                            val newMonth = LocalDate.of(newDate.year, newDate.month, 1)
                            if (newMonth.month != currentMonth.month) {
                                currentMonth = newMonth
                            }
                        }
                    )
                }


                item {
                    WeekCalendar(
                        analysisSnapshot = analysisSnapshot,
                        selectedDate = selectedDate,
                        filterTypes = filterTypes,
                        onDayClick = { newDate ->
                            selectedDate = newDate

                            val newMonth = LocalDate.of(newDate.year, newDate.month, 1)
                            if (newMonth.month != currentMonth.month) {
                                currentMonth = newMonth
                            }
                        },
                        onWeekChange = { delta ->
                            // ✅ Calculer la nouvelle date en ajoutant/soustrayant des semaines
                            val newDate = selectedDate.plusWeeks(delta)
                            selectedDate = newDate

                            // Sync mois automatiquement si changement de mois détecté
                            val newMonth = LocalDate.of(newDate.year, newDate.month, 1)
                            if (newMonth.month != currentMonth.month) {
                                currentMonth = newMonth
                            }
                        },
                        onEdit = { editingEvent = it },
                    )
                }

                item {
                    DayCalendar(
                        currentDate = selectedDate,
                        events = dailyEvents,
                        onEdit = { editingEvent = it },
                        onDayChange = { delta ->
                            // ✅ Calculer la nouvelle date en ajoutant/soustrayant des semaines
                            val newDate = selectedDate.plusDays(delta)
                            selectedDate = newDate

                            // Sync mois automatiquement si changement de mois détecté
                            val newMonth = LocalDate.of(newDate.year, newDate.month, 1)
                            if (newMonth.month != currentMonth.month) {
                                currentMonth = newMonth
                            }
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