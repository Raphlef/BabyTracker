package com.kouloundissa.twinstracker.presentation.calendar

import DayTimeline
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kouloundissa.twinstracker.data.Event
import com.kouloundissa.twinstracker.data.EventType
import com.kouloundissa.twinstracker.data.SleepEvent
import com.kouloundissa.twinstracker.presentation.event.EventFormDialog
import com.kouloundissa.twinstracker.presentation.viewmodel.BabyViewModel
import com.kouloundissa.twinstracker.presentation.viewmodel.EventViewModel
import com.kouloundissa.twinstracker.ui.components.FilterBar
import com.kouloundissa.twinstracker.ui.components.SwipeableCalendar
import com.kouloundissa.twinstracker.ui.theme.BackgroundColor
import com.kouloundissa.twinstracker.ui.theme.DarkBlue
import com.kouloundissa.twinstracker.ui.theme.DarkGrey
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date

@Composable
fun CalendarScreen(
    contentPadding: PaddingValues = PaddingValues(),
    isVisible: Boolean,
    eventViewModel: EventViewModel = hiltViewModel(),
    babyViewModel: BabyViewModel = hiltViewModel()
) {
    /** State **/
    val allEvents by eventViewModel.events.collectAsState()
    val eventCountsByDay by eventViewModel.eventCountsByDay.collectAsState()
    val isLoading by eventViewModel.isLoading.collectAsState()
    val errorMessage by eventViewModel.errorMessage.collectAsState()
    val selectedBaby by babyViewModel.selectedBaby.collectAsState()
    val eventsByDay by eventViewModel.eventsByDay.collectAsState()

    var currentMonth by rememberSaveable { mutableStateOf(LocalDate.now()) }
    var selectedDate by rememberSaveable { mutableStateOf(LocalDate.now()) }
    var filterTypes by rememberSaveable { mutableStateOf<Set<EventType>>(emptySet()) }
    var editingEvent by rememberSaveable { mutableStateOf<Event?>(null) }
    var showDialog by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    /** Compute available types & init filter **/
    val availableTypes = remember(eventsByDay, currentMonth) {
        eventsByDay.filterKeys {
            it.monthValue == currentMonth.monthValue && it.year == currentMonth.year
        }
            .values.flatten()
            .map { EventType.forClass(it::class) }
            .distinct().toSet()
    }
    LaunchedEffect(availableTypes) { filterTypes = availableTypes }
    fun LocalDate.toDate(): Date = Date.from(this.atStartOfDay(ZoneId.systemDefault()).toInstant())
    LaunchedEffect(isVisible, selectedDate, selectedBaby?.id) {
        if (isVisible) {
            selectedBaby?.id?.let {
                val startDate = selectedDate.minusDays(1)
                    .atStartOfDay(ZoneId.systemDefault())
                    .toInstant()
                val endDate = selectedDate.plusDays(1)
                    .atStartOfDay(ZoneId.systemDefault())
                    .toInstant()

                eventViewModel.refreshWithCustomRange(
                    it,
                    Date.from(startDate),
                    Date.from(endDate)
                )
            }
        }
    }
    LaunchedEffect(isVisible, currentMonth, selectedBaby?.id) {
        if (isVisible) {
            selectedBaby?.id?.let { babyId ->
                val startOfMonth = currentMonth.withDayOfMonth(1)
                val endOfMonth = currentMonth.withDayOfMonth(currentMonth.lengthOfMonth())
                    .plusDays(1)

                // Stream des COMPTAGES pour tout le mois (léger)
                eventViewModel.refreshCountWithCustomRange(
                    babyId,
                    startOfMonth.toDate(),
                    endOfMonth.toDate()
                )
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
    val backgroundColor = BackgroundColor.copy(alpha = 0.2f)
    val contentColor = DarkGrey.copy(alpha = 0.5f)
    val tint = DarkBlue
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
                    .padding(16.dp)
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
                    val eventsByDayForCalendar: Map<LocalDate, Int> =
                        remember(eventCountsByDay, currentMonth) {
                            eventCountsByDay.mapKeys { (dateStr, _) ->
                                LocalDate.parse(dateStr) // "yyyy-MM-dd" -> LocalDate
                            }
                                .filterKeys {
                                    it.monthValue == currentMonth.monthValue &&
                                            it.year == currentMonth.year
                                }
                                .mapValues { (_, count) -> count.count }
                        }
                    SwipeableCalendar(
                        currentMonth = currentMonth,
                        onMonthChange = { delta -> currentMonth = currentMonth.plusMonths(delta) },
                        eventCountsByDay = eventsByDayForCalendar,
                        selectedDate = selectedDate,
                        onDayClick = { selectedDate = it }
                    )
                }

                val dailyEvents = allEvents
                    .filter { filterTypes.contains(EventType.forClass(it::class)) }
                    .filter { event ->
                        val systemZone = ZoneId.systemDefault()

                        // Get event start date
                        val eventStartDate = event.timestamp.toInstant()
                            .atZone(systemZone)
                            .toLocalDate()

                        // Get event end date (handles duration for SleepEvent)
                        val eventEndDate = when (event) {
                            is SleepEvent -> event.endTime?.toInstant()
                                ?.atZone(systemZone)
                                ?.toLocalDate() ?: eventStartDate
                            else -> eventStartDate  // Punctual events
                        }

                        // Event occurs on selectedDate if selectedDate is between start and end
                        !selectedDate.isBefore(eventStartDate) && !selectedDate.isAfter(eventEndDate)
                    }
                item {
                    Text(
                        "Events on ${selectedDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy"))} " +
                                "(${dailyEvents.size})",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.headlineMedium,
                        color = BackgroundColor
                    )
                }
                item {
                    if (dailyEvents.isEmpty()) {
                        Text("No events", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        DayTimeline(
                            date = selectedDate,
                            events = dailyEvents,
                            onEdit = { editingEvent = it }
                        )
                    }
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

            // Loading Overlay
            if (isLoading) {
                Box(
                    Modifier
                        .fillMaxSize()
                ) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
            }
        }
    }
}