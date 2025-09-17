package com.example.babytracker.presentation.calendar

import TimelineBar
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.babytracker.data.Event
import com.example.babytracker.data.EventType
import com.example.babytracker.presentation.event.EventFormDialog
import com.example.babytracker.presentation.viewmodel.BabyViewModel
import com.example.babytracker.presentation.viewmodel.EventViewModel
import com.example.babytracker.ui.components.CalendarGrid
import com.example.babytracker.ui.components.FilterBar
import com.example.babytracker.ui.components.MonthHeader
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun CalendarScreen(
    listState: LazyListState,
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

    /** Effects **/
    LaunchedEffect(selectedBaby?.id) {
        listState.scrollToItem(0)
        refresh()
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
                    FilterBar(
                        types = availableTypes,
                        selected = filterTypes,
                        onToggle = { type ->
                            filterTypes =
                                if (type in filterTypes) filterTypes - type else filterTypes + type
                        }
                    )
                }
                item {
                    MonthHeader(currentMonth) { delta ->
                        currentMonth = currentMonth.plusMonths(delta)
                    }
                }
                item {
                    CalendarGrid(
                        year = currentMonth.year,
                        month = currentMonth.monthValue,
                        eventsByDay = eventsByDay.mapValues { (_, evts) ->
                            evts.filter { filterTypes.contains(EventType.forClass(it::class)) }
                        },
                        onDayClick = { selectedDate = it },
                        selectedDate = selectedDate
                    )
                }
                item {
                    val dailyEvents = eventsByDay[selectedDate].orEmpty()
                        .filter { filterTypes.contains(EventType.forClass(it::class)) }
                    Text(
                        "Events on ${selectedDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy"))} " +
                                "(${dailyEvents.size})",
                        style = MaterialTheme.typography.titleSmall
                    )
                }
                item {
                    val dailyEvents = eventsByDay[selectedDate].orEmpty()
                        .filter { filterTypes.contains(EventType.forClass(it::class)) }
                    if (dailyEvents.isEmpty()) {
                        Text("No events", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        TimelineBar(
                            events = dailyEvents,
                            onEdit = { editingEvent = it }
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
                        .background(Color.Black.copy(alpha = 0.3f))
                ) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
            }
        }
    }
}
