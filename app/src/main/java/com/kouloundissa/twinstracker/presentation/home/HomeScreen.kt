package com.kouloundissa.twinstracker.presentation.home

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil.compose.rememberAsyncImagePainter
import com.kouloundissa.twinstracker.data.Baby
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
import com.kouloundissa.twinstracker.ui.components.EventCard
import com.kouloundissa.twinstracker.ui.components.SleepTimer
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
    val contentColor = Color.White
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
    val isLoadingMore by eventViewModel.isLoadingMore.collectAsState()
    val hasMoreHistory by eventViewModel.hasMoreHistory.collectAsState()
    val errorMessage by eventViewModel.errorMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedType by remember { mutableStateOf<EventType?>(null) }
    var showTypeDialog by remember { mutableStateOf(false) }
    val deleteSuccess by eventViewModel.deleteSuccess.collectAsState()
    val deleteError by eventViewModel.deleteError.collectAsState()
    val isDeleting by eventViewModel.isDeleting.collectAsState()

    LaunchedEffect(selectedBaby?.id) {
        selectedBaby?.id?.let { babyId ->
            eventViewModel.resetDateRangeAndHistory()
            eventViewModel.setDateRangeForLastDays(30L)
            eventViewModel.streamEventsInRangeForBaby(babyId)
        }

    }
    val loadMoreHistory = remember(selectedBaby) {
        {
            selectedBaby?.id?.let { _ ->
                eventViewModel.loadMoreHistoricalEvents()
            }
        }
    }

    LaunchedEffect(editingEvent) {
        editingEvent?.let {
            eventViewModel.loadEventIntoForm(it)
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
    LaunchedEffect(deleteSuccess, deleteError) {
        when {
            deleteSuccess -> {
                snackbarHostState.showSnackbar("Event deleted")
                eventViewModel.resetDeleteState()
            }

            deleteError != null -> {
                snackbarHostState.showSnackbar("Delete failed: $deleteError")
                eventViewModel.resetDeleteState()
            }
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
// In HomeScreen, after calculating todayEvents and todaysByType
    val activeSleepEvent: SleepEvent? = todayEvents
        .filterIsInstance<SleepEvent>()
        .firstOrNull { it.endTime == null && it.beginTime != null }


    val summaries = remember(todaysByType, babyEvents) {
        EventType.entries.associateWith { type ->
            val todayList = todaysByType[type].orEmpty()
            when (type) {
                EventType.DIAPER -> {
                    val count = todayList.size
                    if (count > 0) "$count today" else "No diaper today"
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
                    } else "No sleep today"
                }

                EventType.GROWTH -> {
                    // Use last existing growth event, not limited to today
                    babyEvents
                        .filterIsInstance<GrowthEvent>()
                        .maxByOrNull { it.timestamp }
                        ?.let { "${it.weightKg ?: "-"}kg • ${it.heightCm ?: "-"}cm" }
                        ?: "No growth today"
                }

                EventType.PUMPING -> {
                    if (todayList.isEmpty()) "No pumping today"
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
                        "No drugs today"
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
            Modifier.fillMaxSize()
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
                    } else {
                        Text(
                            "Select a baby to see data",
                            style = MaterialTheme.typography.bodyLarge,
                            color = contentColor
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
                    item {
                        Spacer(Modifier.height(24.dp))
                        Text(
                            "Aujourd'hui",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = contentColor,
                        )
                    }

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
                                if (type == EventType.SLEEP && activeSleepEvent != null) {
                                    EventTypeCard(
                                        type = type,
                                        summary = summaries[type]!!,
                                        lastEvent = lastByType[type],
                                        onClick = {
                                            selectedType = type
                                            showTypeDialog = true
                                        },
                                        onLongClick = {
                                            selectedType = type
                                            showEventDialog = true
                                        },
                                        size = cardSize,
                                        overlayContent = {
                                            SleepTimer(
                                                sleepEvent = activeSleepEvent,
                                                onClick = {
                                                    editingEvent = activeSleepEvent
                                                    showEventDialog = true
                                                },
                                                modifier = Modifier
                                                    .align(Alignment.CenterStart)
                                            )
                                        }
                                    )
                                } else {
                                    EventTypeCard(
                                        type = type,
                                        summary = summaries[type]!!,
                                        lastEvent = lastByType[type],
                                        onClick = {
                                            selectedType = type
                                            showTypeDialog = true
                                        },
                                        onLongClick = {
                                            selectedType = type
                                            showEventDialog = true
                                        },
                                        size = cardSize
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Spacer(Modifier.height(24.dp))
                        Text(
                            "Recent Events",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = contentColor,
                        )
                    }

                    // Timeline of recent events
                    items(babyEvents, key = { it.id }) { event ->
                        EventCard(
                            event = event,
                            onEdit = { editingEvent = event; showEventDialog = true },
                            onDelete = { eventViewModel.deleteEvent(event.id, selectedBaby!!.id) }
                        )
                    }
                    // Discrete “load more” indicator at bottom
                    if (isLoadingMore && hasMoreHistory) {
                        item {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                                    ),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                ) {
                                    Row(
                                        Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CircularProgressIndicator(
                                            Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            "Loading older events…",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // End-of-history footer
                    if (!hasMoreHistory && babyEvents.isNotEmpty()) {
                        item {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.History,
                                        contentDescription = "No more history",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Text(
                                        "Beginning reached",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
            // Loading overlay
            if (isLoading && !isLoadingMore) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Transparent),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                strokeWidth = 3.dp
                            )
                            Text(
                                text = "Loading baby events...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
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
            if (showEventDialog && selectedType != null && editingEvent == null) {
                EventFormDialog(
                    babyId = selectedBaby?.id ?: return@Box,
                    initialEventType = selectedType,
                    onDismiss = {
                        showEventDialog = false
                        eventViewModel.resetFormState()
                    }
                )
            }
            if (showTypeDialog && selectedType != null) {
                // Filter events for this type and selected baby
                val filtered = babyEvents
                    .filter { EventType.forClass(it::class) == selectedType }
                EventTypeDialog(
                    type = selectedType!!,
                    selectedBaby = selectedBaby,
                    events = filtered,
                    onDismiss = {
                        showTypeDialog = false
                        selectedType = null
                    },
                    onEdit = { event ->
                        editingEvent = event
                        eventViewModel.loadEventIntoForm(event)
                        showEventDialog = true
                    },
                    onAdd = { type ->
                        selectedType = type
                        showEventDialog = true
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

        }
    }
}


@Composable
fun EventTypeCard(
    type: EventType,
    summary: String,
    lastEvent: Event?,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    size: Dp,
    overlayContent: (@Composable BoxScope.() -> Unit)? = null
) {
    val contentColor = Color.White
    val cornerShape = MaterialTheme.shapes.extraLarge
    Surface(
        shape = cornerShape,
        color = Color.Transparent,
        modifier = Modifier
            .size(size)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick ?: {}
            )

    ) {
        // 1. Background image sized to the dialog
        Image(
            painter = painterResource(id = type.drawableRes),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.85f)
                .blur(5.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            type.color.copy(alpha = 0.7f),
                            type.color.copy(alpha = 0.3f)
                        )
                    ),
                    shape = cornerShape,
                )
        ) {
            // 1️⃣ Event name at top-left
            Text(
                text = type.displayName,
                style = MaterialTheme.typography.titleMedium,
                color = contentColor,
                modifier = Modifier
                    .zIndex(3f)
                    .align(Alignment.TopStart)
                    .padding(start = 20.dp, top = 20.dp)
            )

            // 2️⃣ Center content - show overlay if available, otherwise show summary
            if (overlayContent != null) {
                // Overlay content replaces summary
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .zIndex(2f)
                        .padding(start = 20.dp)
                        .fillMaxSize()
                ) {
                    overlayContent()
                }
            } else {
                // Default summary text
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyLarge,
                    color = contentColor.copy(alpha = 0.85f),
                    modifier = Modifier
                        .zIndex(2f)
                        .align(Alignment.CenterStart)
                        .padding(start = 20.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventTypeDialog(
    type: EventType,
    events: List<Event>,
    onDismiss: () -> Unit,
    onEdit: (Event) -> Unit,
    onAdd: (EventType) -> Unit,
    eventViewModel: EventViewModel = hiltViewModel(),
    selectedBaby: Baby?
) {
    val contentColor = Color.White
    val cornerShape = MaterialTheme.shapes.extraLarge


    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,  // skips intermediate state to start fully expanded
    )
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Transparent,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.75f)
                .clip(cornerShape)
        ) {
            val blurRadius = if (events.size > 5) 5.dp else 1.dp
            //  Background image sized to the dialog
            Image(
                painter = painterResource(id = type.drawableRes),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(blurRadius)
            )

            //  Semi-transparent overlay tint
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                type.color.copy(alpha = 0.35f),
                                type.color.copy(alpha = 0.15f)
                            )
                        ),
                        shape = cornerShape,
                    )
            )

            Column(
                modifier = Modifier
                    .matchParentSize()
                    .padding(16.dp)               // uniform padding inside edges
            ) {
                Text(
                    text = type.displayName,
                    style = MaterialTheme.typography.titleLarge,
                    color = contentColor
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (events.isEmpty()) {
                        Text("No ${type.displayName.lowercase()} events yet")
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                        ) {
                            items(events) { event ->
                                EventCard(
                                    event = event,
                                    onEdit = { onEdit(event) },
                                    onDelete = {
                                        selectedBaby?.let {
                                            eventViewModel.deleteEvent(
                                                event.id,
                                                it.id
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        onClick = { onAdd(type) }
                    ) {
                        Text("Add New Event", color = contentColor)
                    }

                    TextButton(onClick = onDismiss) {
                        Text("Close", color = contentColor)
                    }
                }
            }
        }

    }
}
