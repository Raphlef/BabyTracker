package com.kouloundissa.twinstracker.presentation.home

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.kouloundissa.twinstracker.data.DrugsEvent
import com.kouloundissa.twinstracker.data.Event
import com.kouloundissa.twinstracker.data.EventType
import com.kouloundissa.twinstracker.data.FeedType
import com.kouloundissa.twinstracker.data.FeedingEvent
import com.kouloundissa.twinstracker.data.GrowthEvent
import com.kouloundissa.twinstracker.data.SleepEvent
import com.kouloundissa.twinstracker.presentation.baby.BabyFormDialog
import com.kouloundissa.twinstracker.presentation.event.EventFormDialog
import com.kouloundissa.twinstracker.presentation.viewmodel.BabyViewModel
import com.kouloundissa.twinstracker.presentation.viewmodel.EventViewModel
import com.kouloundissa.twinstracker.ui.components.TimelineEventItem
import com.kouloundissa.twinstracker.ui.components.TimelineList
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlin.math.ceil

@SuppressLint("DefaultLocale", "UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun HomeScreen(
    contentPadding: PaddingValues = PaddingValues(),
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
    val babies by babyViewModel.babies.collectAsState()
    var editingEvent by remember { mutableStateOf<Event?>(null) }
    var showEventDialog by remember { mutableStateOf(false) }
    var showBabyDialog by remember { mutableStateOf(false) }

    val isLoading by eventViewModel.isLoading.collectAsState()
    val errorMessage by eventViewModel.errorMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedType by remember { mutableStateOf<EventType?>(null) }
    var showTypeDialog by remember { mutableStateOf(false) }

    LaunchedEffect(selectedBaby?.id) {
        selectedBaby?.id?.let { babyId ->
            // week‐long window; use 30L for month
            eventViewModel.setDateRangeForLastDays(30L)
            eventViewModel.streamEventsInRangeForBaby(babyId)
        }
    }
    LaunchedEffect(editingEvent) {
        editingEvent?.let {
            eventViewModel.loadEventIntoForm(it)
            showEventDialog = true
        }
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
                        val totalMl =
                            todayList.sumOf { (it as FeedingEvent).amountMl ?: 0.0 }.toInt()
                        val breastCount =
                            todayList.count { (it as FeedingEvent).feedType == FeedType.BREAST_MILK }
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
                        val mins =
                            Duration.between(last.timestamp.toInstant(), Instant.now()).toMinutes()
                        val h = mins / 60
                        val m = mins % 60
                        "${h}h ${m}m ago"
                    }
                }

                EventType.DRUGS -> {
                    if (todayList.isEmpty()) {
                        "No drugs"
                    } else {
                        val doses = todayList.size
                        val last = todayList
                            .filterIsInstance<DrugsEvent>()
                            .maxByOrNull { it.timestamp }!!
                        val doseValue = last.dosage?.toInt() ?: "-"
                        "${doses} today • ${last.drugType.displayName} ${doseValue}${last.unit}"
                    }
                }
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            Modifier
                .fillMaxSize()
        ) {
            if (selectedBaby == null) {
                // Empty state when no baby selected
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {

                    if (babies.isEmpty()) {
                        Button(
                            onClick = {
                                showBabyDialog = true
                            },
                            modifier = Modifier
                                .height(56.dp)
                                .width(180.dp)
                        ) {
                            Text("Add a Baby")
                        }
                    }else{
                        Text(
                            "Select a baby to see data",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                // Build filtered lists for the selected baby
                val babyEvents = remember(eventsByType, selectedBaby) {
                    eventsByType.values.flatten()
                        .filter { it.babyId == selectedBaby!!.id }
                        .sortedByDescending { it.timestamp }
                }
                val lastByType = remember(babyEvents) {
                    EventType.entries.associateWith { type ->
                        babyEvents.filter { EventType.forClass(it::class) == type }
                            .maxByOrNull { it.timestamp }
                    }
                }

                LazyColumn(
                    contentPadding = contentPadding,
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
                                    onClick = {
                                        selectedType = type
                                        showTypeDialog = true
                                    },
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
            if (showEventDialog && editingEvent != null) {
                EventFormDialog(
                    babyId = selectedBaby?.id ?: return@Box,
                    onDismiss = {
                        showEventDialog = false
                        editingEvent = null
                        eventViewModel.resetFormState()
                        // refresh if needed
                    }
                )
            }
            if (showBabyDialog) {
                BabyFormDialog(
                    babyToEdit = null,
                    onBabyUpdated = { savedOrDeletedBaby ->
                        showBabyDialog = false
                        savedOrDeletedBaby?.let { babyViewModel.selectBaby(it) }
                    },
                    onCancel = { showBabyDialog = false },
                    babyViewModel = babyViewModel
                )
            }
            if (showTypeDialog && selectedType != null) {
                // Filter events for this type and selected baby
                val filtered = babyEvents
                    .filter { EventType.forClass(it::class) == selectedType }
                EventTypeDialog(
                    type = selectedType!!,
                    events = filtered,
                    onDismiss = {
                        showTypeDialog = false
                        selectedType = null
                    },
                    onEdit = { event ->
                        editingEvent = event
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
                style = MaterialTheme.typography.titleMedium,
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

@Composable
private fun EventTypeDialog(
    type: EventType,
    events: List<Event>,
    onDismiss: () -> Unit,
    onEdit: (Event) -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(type.displayName) },
        text = {
            if (events.isEmpty()) {
                Text("No ${type.displayName.lowercase()} events yet")
            } else {
                LazyColumn(
                    Modifier.heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(events) { event ->
                        TimelineEventItem(
                            event = event,
                            onEdit = onEdit
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}