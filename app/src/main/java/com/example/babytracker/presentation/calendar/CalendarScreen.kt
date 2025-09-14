package com.example.babytracker.presentation.calendar


import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.babytracker.data.event.Event
import com.example.babytracker.data.event.EventType
import com.example.babytracker.presentation.viewmodel.BabyViewModel
import com.example.babytracker.presentation.viewmodel.EventViewModel
import java.time.LocalDate
import java.text.SimpleDateFormat
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

@Composable
fun CalendarScreen(
    viewModel: EventViewModel = hiltViewModel(),
    babyViewModel: BabyViewModel = hiltViewModel()
) {
    val eventsByType by viewModel.eventsByType.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val selectedBaby by babyViewModel.selectedBaby.collectAsState()
    val currentBabyId = selectedBaby?.id
    val snackbarHostState = remember { SnackbarHostState() }

    val eventsByDay by viewModel.eventsByDay.collectAsState()
    var currentMonth by rememberSaveable { mutableStateOf(LocalDate.now()) }
    var selectedDate by rememberSaveable { mutableStateOf(LocalDate.now()) }

    // Charger les événements à l'ouverture et à chaque changement de babyId ou de plage de dates
    LaunchedEffect(currentBabyId, viewModel.startDate, viewModel.endDate) {
        currentBabyId?.let { viewModel.loadEventsInRange(currentBabyId) }
    }

    // Show error via Snackbar
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Long)
            viewModel.clearErrorMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                else -> {
                    Column {
                        // Navigation mois
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            IconButton(onClick = { currentMonth = currentMonth.minusMonths(1) }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Prev")
                            }
                            Text(
                                text = currentMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                                style = MaterialTheme.typography.titleMedium
                            )
                            IconButton(onClick = { currentMonth = currentMonth.plusMonths(1) }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next")
                            }
                        }

                        // Grille mensuelle
                        MonthCalendar(
                            year = currentMonth.year,
                            month = currentMonth.monthValue,
                            eventsByDay = eventsByDay,
                            onDayClick = { date ->
                                selectedDate = date
                            }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // List of events for the selected date
                        Text(
                            text = "Events on ${selectedDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))}",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        val dayEvents = eventsByDay[selectedDate].orEmpty()
                        if (dayEvents.isEmpty()) {
                            Text("No events", style = MaterialTheme.typography.bodyMedium)
                        } else {
                            LazyColumn {
                                items(dayEvents) { event ->
                                    val time = event.timestamp.toInstant()
                                        .atZone(ZoneId.systemDefault())
                                        .format(DateTimeFormatter.ofPattern("HH:mm"))
                                    val type = EventType.forClass(event::class)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .background(type.color, CircleShape)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = "$time • ${type.displayName}",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                    Divider()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MonthCalendar(
    year: Int,
    month: Int,
    eventsByDay: Map<LocalDate, List<Event>>,
    onDayClick: (LocalDate) -> Unit
) {
    // 1. Calcul des jours du mois
    val firstOfMonth = LocalDate.of(year, month, 1)
    val daysInMonth = firstOfMonth.lengthOfMonth()
    val startDow = firstOfMonth.dayOfWeek.value % 7  // 0=Dimanche, 6=Samedi

    // 2. Affichage de l’en-tête des jours
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("Sun","Mon","Tue","Wed","Thu","Fri","Sat").forEach { dow ->
                Text(dow, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            }
        }

        // 3. Grille des jours (7 colonnes)
        val totalCells = ((startDow + daysInMonth + 6) / 7) * 7
        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(totalCells) { index ->
                val dayNumber = index - startDow + 1
                if (index < startDow || dayNumber > daysInMonth) {
                    Box(modifier = Modifier.size(40.dp)) { /* case vide */ }
                } else {
                    val date = LocalDate.of(year, month, dayNumber)
                    val events = eventsByDay[date].orEmpty()
                    DayCell(date, events, onDayClick)
                }
            }
        }
    }
}

@Composable
fun DayCell(
    date: LocalDate,
    events: List<Event>,
    onDayClick: (LocalDate) -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .padding(2.dp)
            .clickable { onDayClick(date) },
        contentAlignment = Alignment.TopCenter
    ) {
        Text(
            text = "${date.dayOfMonth}",
            style = MaterialTheme.typography.bodySmall
        )

        val dotSize = 6.dp
        val maxDots = 3
        val eventsToShow = events.take(maxDots)

        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 2.dp)
        ) {
            eventsToShow.forEach { event ->
                // Récupère la couleur via EventType
                val eventType = EventType.forClass(event::class)
                Box(
                    modifier = Modifier
                        .size(dotSize)
                        .background(eventType.color, CircleShape)
                )
            }
            if (events.size > maxDots) {
                Text(
                    text = "+${events.size - maxDots}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
