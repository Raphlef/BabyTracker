package com.example.babytracker.presentation.home

import android.annotation.SuppressLint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.babytracker.data.Event
import com.example.babytracker.data.EventType
import com.example.babytracker.data.FeedType
import com.example.babytracker.data.FeedingEvent
import com.example.babytracker.data.GrowthEvent
import com.example.babytracker.data.SleepEvent
import com.example.babytracker.presentation.event.EventFormDialog
import com.example.babytracker.presentation.viewmodel.BabyViewModel
import com.example.babytracker.presentation.viewmodel.EventViewModel
import com.example.babytracker.ui.components.TimelineList
import java.text.SimpleDateFormat
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.ceil

@SuppressLint("DefaultLocale")
@Composable
fun HomeScreen(
    listState: LazyListState,
    babyViewModel: BabyViewModel = hiltViewModel(),
    eventViewModel: EventViewModel = hiltViewModel()
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val spacing = 16.dp
    val columns = 2
    val cardSize = (screenWidth - spacing * (columns + 1)) / columns

    // Number of rows needed for the grid
    val rows = ceil(EventType.entries.size / columns.toFloat()).toInt()
    val gridHeight = cardSize * rows + spacing * (rows + 1)

    val selectedBaby by babyViewModel.selectedBaby.collectAsState()

    var editingEvent by remember { mutableStateOf<Event?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    val isLoading by eventViewModel.isLoading.collectAsState()
    val errorMessage by eventViewModel.errorMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(selectedBaby?.id) {
        selectedBaby?.id?.let { babyId ->
            // week‐long window; use 30L for month
            eventViewModel.setDateRangeForLastDays(30L)
            eventViewModel.streamEventsInRangeForBaby(babyId)
        }
    }
    // Reset scroll when baby changes
    LaunchedEffect(selectedBaby?.id) {
        listState.scrollToItem(0)
    }
    DisposableEffect(Unit) {
        onDispose { eventViewModel.stopStreaming() }
    }
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            eventViewModel.clearErrorMessage()
        }
    }
    val today = LocalDate.now()
    val eventsByDay by eventViewModel.eventsByDay.collectAsState()
    val eventsByType by eventViewModel.eventsByType.collectAsState()
    val babyEvents = remember(eventsByType, selectedBaby) {
        eventsByType.values.flatten()
            .filter { it.babyId == selectedBaby?.id }
    }
    val todayEvents = eventsByDay[today].orEmpty()
    val todaysByType = remember(todayEvents) {
        todayEvents.groupBy { EventType.forClass(it::class) }
    }

    val summaries = remember(todaysByType, babyEvents) {
        EventType.entries.associateWith { type ->
            val todayList = todaysByType[type].orEmpty()
            when (type) {
                EventType.DIAPER -> {
                    val count = todayList.size
                    if (count > 0) "$count today" else "No diaper"
                }
                EventType.FEEDING -> {
                    if (todayList.isEmpty()) "No feeding"
                    else {
                        val totalMl = todayList.sumOf { (it as FeedingEvent).amountMl ?: 0.0 }.toInt()
                        val breastCount = todayList.count { (it as FeedingEvent).feedType == FeedType.BREAST_MILK }
                        "${totalMl}ml • $breastCount breast"
                    }
                }
                EventType.SLEEP -> {
                    val totalMin = todayList.sumOf { (it as SleepEvent).durationMinutes ?: 0L }
                    if (totalMin > 0) {
                        val h = totalMin / 60
                        val m = totalMin % 60
                        "Today: ${h}h ${m}m"
                    } else "No sleep"
                }
                EventType.GROWTH -> {
                    // Use last existing growth event, not limited to today
                    babyEvents
                        .filterIsInstance<GrowthEvent>()
                        .maxByOrNull { it.timestamp }
                        ?.let { "${it.weightKg ?: "-"}kg • ${it.heightCm ?: "-"}cm" }
                        ?: "No growth"
                }
                EventType.PUMPING -> {
                    if (todayList.isEmpty()) "No pumping"
                    else {
                        val last = todayList.maxByOrNull { it.timestamp }!!
                        val mins = Duration.between(last.timestamp.toInstant(), Instant.now()).toMinutes()
                        val h = mins / 60
                        val m = mins % 60
                        "${h}h ${m}m ago"
                    }
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (selectedBaby == null) {
                // Empty state when no baby selected
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Select a baby to see data",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // Build filtered lists for the selected baby
                val babyEvents = remember(eventsByType, selectedBaby) {
                    eventsByType.values.flatten()
                        .filter { it.babyId == selectedBaby!!.id }
                        .sortedByDescending { it.timestamp }
                }
                val lastByType = remember(babyEvents) {
                    EventType.values().associateWith { type ->
                        babyEvents.filter { EventType.forClass(it::class) == type }
                            .maxByOrNull { it.timestamp }
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 2 x N Cards for each EventType
                    item {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(columns),
                            userScrollEnabled = false,
                            modifier = Modifier
                                .height(gridHeight)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(spacing),
                            verticalArrangement = Arrangement.spacedBy(spacing)
                        ) {
                            items(EventType.entries) { type ->
                                EventTypeCard(
                                    type = type,
                                    summary = summaries[type]!!,
                                    lastEvent = lastByType[type],
                                    onClick = { /* navigate */ },
                                    size = cardSize
                                )
                            }
                        }
                    }

                    item {
                        Spacer(Modifier.height(24.dp))
                        Text(
                            "Recent Events",
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }

                    // Timeline of recent events
                    item {
                        if (babyEvents.isNotEmpty()) {
                            TimelineList(
                                events = babyEvents.take(20),
                                modifier = Modifier.fillMaxWidth(),
                                onEdit = { event ->
                                    editingEvent = event
                                    showDialog = true
                                }
                            )
                        } else {
                            // Empty state
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Event,
                                    contentDescription = "No events",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    text = "No events yet",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Start tracking by adding your first event",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
            // Loading overlay
            if (isLoading) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                        .clickable(enabled = false) { },
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            // dialog handling:
            if (showDialog && editingEvent != null) {
                EventFormDialog(
                    babyId = selectedBaby?.id ?: return@Box,
                    onDismiss = {
                        showDialog = false
                        editingEvent = null
                        eventViewModel.resetFormState()
                        // refresh if needed
                    }
                )
            }
        }
    }
}

@Composable
fun EventTypeCard(
    type: EventType,
    summary: String,
    lastEvent: Event?,
    onClick: () -> Unit,
    size: Dp
) {
    Surface(
        shape = RoundedCornerShape(30),
        tonalElevation = 4.dp,
        modifier = Modifier
            .size(size)
            .clickable(onClick = onClick)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 1️⃣ Event name at top-left
            Text(
                text = type.displayName,
                style =  MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 20.dp, top = 20.dp)
            )

            // Summary in center
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 20.dp)
            )

            // 3️⃣ Icon in bottom-right corner
            Icon(
                imageVector = type.icon,
                contentDescription = type.displayName,
                tint = type.color,
                modifier = Modifier
                    .size(72.dp)
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 20.dp)
            )
        }
    }
}

