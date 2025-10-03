package com.kouloundissa.twinstracker.presentation.calendar

import DayTimeline
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kouloundissa.twinstracker.R
import com.kouloundissa.twinstracker.data.Event
import com.kouloundissa.twinstracker.data.EventType
import com.kouloundissa.twinstracker.presentation.event.EventFormDialog
import com.kouloundissa.twinstracker.presentation.viewmodel.BabyViewModel
import com.kouloundissa.twinstracker.presentation.viewmodel.EventViewModel
import com.kouloundissa.twinstracker.ui.components.BackgroundContainer
import com.kouloundissa.twinstracker.ui.components.CalendarGrid
import com.kouloundissa.twinstracker.ui.components.FilterBar
import com.kouloundissa.twinstracker.ui.components.MonthHeader
import com.kouloundissa.twinstracker.ui.components.SwipeableCalendar
import com.kouloundissa.twinstracker.ui.theme.BackgroundColor
import com.kouloundissa.twinstracker.ui.theme.DarkBlue
import com.kouloundissa.twinstracker.ui.theme.DarkGrey
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun CalendarScreen(
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: EventViewModel = hiltViewModel(),
    babyViewModel: BabyViewModel = hiltViewModel()
) {
    /** State **/
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val selectedBaby by babyViewModel.selectedBaby.collectAsState()
    val eventsByDay by viewModel.eventsByDay.collectAsState()
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

    /** Helpers **/
    fun refresh() {
        selectedBaby?.id?.let {
            viewModel.setDateRangeForMonth(currentMonth)
            viewModel.loadEventsInRange(it)
        }
    }

    LaunchedEffect(currentMonth, selectedBaby?.id) { refresh() }
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearErrorMessage()
        }
    }
    LaunchedEffect(editingEvent) {
        editingEvent?.let {
            viewModel.loadEventIntoForm(it)
            showDialog = true
        }
    }
    val contentColor = DarkGrey.copy(alpha = 0.5f)
    val backgroundColor = BackgroundColor.copy(alpha = 0.2f)
    val tint = DarkBlue
    /** UI **/
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = Modifier.fillMaxSize()
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                item {
                    SwipeableCalendar(
                        currentMonth = currentMonth,
                        onMonthChange = { delta -> currentMonth = currentMonth.plusMonths(delta) },
                        eventsByDay = eventsByDay.mapValues { (_, evts) ->
                            evts.filter { filterTypes.contains(EventType.forClass(it::class)) }
                        },
                        selectedDate = selectedDate,
                        onDayClick = { selectedDate = it }
                    )
                }
                item {
                    val dailyEvents = eventsByDay[selectedDate].orEmpty()
                        .filter { filterTypes.contains(EventType.forClass(it::class)) }
                    Text(
                        "Events on ${selectedDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy"))} " +
                                "(${dailyEvents.size})",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.headlineMedium,
                        color = BackgroundColor,
                    )
                }
                item {
                    val dailyEvents = eventsByDay[selectedDate].orEmpty()
                        .filter { filterTypes.contains(EventType.forClass(it::class)) }
                    if (dailyEvents.isEmpty()) {
                        Text("No events", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        DayTimeline(
                            events = dailyEvents,
                            onEdit = { editingEvent = it },
                        )
                    }
                }
            }

            // Edit Dialog
            if (showDialog) {
                EventFormDialog(
                    babyId = selectedBaby?.id ?: return@Box,
                    onDismiss = {
                        showDialog = false
                        editingEvent = null
                        viewModel.resetFormState()
                        refresh()
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