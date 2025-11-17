package com.kouloundissa.twinstracker.presentation.home

import android.annotation.SuppressLint
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil.compose.AsyncImage
import com.kouloundissa.twinstracker.data.Event
import com.kouloundissa.twinstracker.data.EventType
import com.kouloundissa.twinstracker.data.EventTypeOverlayContext
import com.kouloundissa.twinstracker.data.FeedingEvent
import com.kouloundissa.twinstracker.data.SleepEvent
import com.kouloundissa.twinstracker.presentation.baby.BabyFormDialog
import com.kouloundissa.twinstracker.presentation.event.EventFormDialog
import com.kouloundissa.twinstracker.presentation.viewmodel.BabyViewModel
import com.kouloundissa.twinstracker.presentation.viewmodel.EventViewModel
import com.kouloundissa.twinstracker.ui.components.EventCard
import com.kouloundissa.twinstracker.ui.components.EventOverlayInfo
import com.kouloundissa.twinstracker.ui.components.EventTypeDialog
import com.kouloundissa.twinstracker.ui.theme.BackgroundColor
import com.kouloundissa.twinstracker.ui.theme.DarkBlue
import com.kouloundissa.twinstracker.ui.theme.DarkGrey
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.Duration
import java.time.LocalDate
import java.util.Calendar
import kotlin.math.ceil

@OptIn(FlowPreview::class)
@SuppressLint("DefaultLocale", "UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun HomeScreen(
    contentPadding: PaddingValues = PaddingValues(),
    isVisible: Boolean,
    babyViewModel: BabyViewModel = hiltViewModel(),
    eventViewModel: EventViewModel = hiltViewModel()
) {
    val backgroundColor = BackgroundColor
    val contentColor = DarkGrey
    val tint = DarkBlue

    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val spacing = 12.dp
    val columns = 2
    val cardWidth = (screenWidth - spacing * (columns + 1)) / columns
    val cardHeight = cardWidth * 1.2f
    // Number of rows needed for the grid
    val rows = ceil(EventType.entries.size / columns.toFloat()).toInt()
    val gridHeight = cardHeight * rows + spacing * (rows - 1)

    val selectedBaby by babyViewModel.selectedBaby.collectAsState()
    val babies by babyViewModel.babies.collectAsState()

    var editingEvent by remember { mutableStateOf<Event?>(null) }
    var showEventDialog by remember { mutableStateOf(false) }
    var showBabyDialog by remember { mutableStateOf(false) }

    val allEvents by eventViewModel.events.collectAsState()
    val eventsByDay by eventViewModel.eventsByDay.collectAsState()
    val eventsByType by eventViewModel.eventsByType.collectAsState()
    val isLoading by eventViewModel.isLoading.collectAsState()
    val isLoadingMore by eventViewModel.isLoadingMore.collectAsState()
    val hasMoreHistory by eventViewModel.hasMoreHistory.collectAsState()
    val errorMessage by eventViewModel.errorMessage.collectAsState()
    val lastGrowthEvent by eventViewModel.lastGrowthEvent.collectAsState()
    val favoriteEventTypes by eventViewModel.favoriteEventTypes.collectAsState()

    val sortedEventTypes = remember(favoriteEventTypes) {
        eventViewModel.getSortedEventTypes(favoriteEventTypes)
    }

    val snackbarHostState = remember { SnackbarHostState() }

    var selectedType by remember { mutableStateOf<EventType?>(null) }
    var showTypeDialog by remember { mutableStateOf(false) }
    val deleteSuccess by eventViewModel.deleteSuccess.collectAsState()
    val deleteError by eventViewModel.deleteError.collectAsState()
    val isDeleting by eventViewModel.isDeleting.collectAsState()

    val lazyListState = rememberLazyListState()


    LaunchedEffect(lazyListState) {
        var lastLoadedCount = 0
        var lastLoadAttempt = 0L

        snapshotFlow { lazyListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .distinctUntilChanged()
            .collect { lastVisibleIndex ->
                val totalItems = lazyListState.layoutInfo.totalItemsCount
                val currentTime = System.currentTimeMillis()

                // Trigger when user is within last 3 items
                if (lastVisibleIndex != null && lastVisibleIndex >= totalItems - 3) {
                    // Only attempt to load if:
                    // 1. Has more data available
                    // 2. Not currently loading
                    // 3. Items were actually loaded in last attempt (or first attempt)
                    // 4. Prevent rapid consecutive attempts
                    val shouldAttemptLoad = hasMoreHistory &&
                            !isLoadingMore &&
                            (lastLoadedCount == 0 || totalItems > lastLoadedCount) &&
                            (currentTime - lastLoadAttempt > 300)

                    if (shouldAttemptLoad) {
                        lastLoadedCount = totalItems
                        lastLoadAttempt = currentTime
                        eventViewModel.loadMoreHistoricalEvents()
                    }
                }
            }
    }


    LaunchedEffect(editingEvent) {
        editingEvent?.let {
            eventViewModel.loadEventIntoForm(it)
        }
    }
    LaunchedEffect(isVisible, selectedBaby?.id) {
        if (isVisible) {
            selectedBaby?.id?.let {
                Log.d("HomeScreen", "Starting stream - babyId: $it, isVisible: $isVisible")
                val endDate = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }.time

                val startDate = Calendar.getInstance().apply {
                    time = endDate
                    add(Calendar.DAY_OF_MONTH, -1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.time

                eventViewModel.refreshWithCustomRange(it, startDate, endDate)
                eventViewModel.loadLastGrowth(it)
            }
        }
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
    val babyEvents = remember(eventsByType, selectedBaby) {
        eventsByType.values.flatten()
            .filter { it.babyId == selectedBaby?.id }
    }
    val todayEvents = eventsByDay[today].orEmpty()
    val todaysByType = remember(todayEvents) {
        todayEvents.groupBy { EventType.forClass(it::class) }
    }

    val activeSleepEvent: SleepEvent? = allEvents
        .filterIsInstance<SleepEvent>()
        .sortedByDescending { it.timestamp }
        .firstOrNull { it.endTime == null && it.beginTime != null }

    val nextFeedingTimeMs: Long? = run {
        val lastFeeding = allEvents
            .filterIsInstance<FeedingEvent>().maxByOrNull { it.timestamp } ?: return@run null

        val recentFeedings = allEvents
            .filterIsInstance<FeedingEvent>()
            .sortedByDescending { it.timestamp }
            .take(10)

        if (recentFeedings.size < 2) return@run null

        val intervals = recentFeedings
            .zipWithNext { a, b ->
                Duration.between(b.timestamp.toInstant(), a.timestamp.toInstant()).toMillis()
            }
            .sorted()

        val medianIntervalMs = intervals[intervals.size / 2]

        val filteredIntervals = intervals
            .drop(intervals.size / 5)
            .dropLast(intervals.size / 5)
            .ifEmpty { intervals }

        val predictedIntervalMs = filteredIntervals.average().toLong()
        val minIntervalMs = 90 * 60 * 1000L
        val medianPredictionMs = maxOf(medianIntervalMs, minIntervalMs)
        val weightPredictionMs = maxOf(predictedIntervalMs, minIntervalMs)
        lastFeeding.timestamp.time + weightPredictionMs
    }
    val currentOverlay by remember(selectedType, activeSleepEvent, nextFeedingTimeMs) {
        derivedStateOf {
            selectedType?.let { type ->
                type.createOverlay(
                    EventTypeOverlayContext(
                        activeSleepEvent = activeSleepEvent,
                        nextFeedingTimeMs = nextFeedingTimeMs,
                        onTimerClick = {
                            editingEvent =
                                if (selectedType == EventType.SLEEP) activeSleepEvent else null
                            editingEvent?.let { event ->
                                // Load event into form
                            }
                            showEventDialog = true
                        }
                    )
                )
            } ?: EventOverlayInfo()
        }
    }

    val summaries = remember(todaysByType, lastGrowthEvent) {
        EventType.entries.associateWith { type ->
            val todayList = todaysByType[type].orEmpty()
            type.generateSummary(todayList, lastGrowthEvent)
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
                            color = backgroundColor
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
                    state = lazyListState,
                    contentPadding = contentPadding,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Text(
                            "Aujourd'hui",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = backgroundColor,
                        )
                    }
                    if (isLoading && !isLoadingMore) {
                        item {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(32.dp),
                                    strokeWidth = 3.dp
                                )
                            }
                        }
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
                            items(sortedEventTypes) { type ->
                                EventTypeCard(
                                    type = type,
                                   // summary = summaries[type]!!,
                                    isFavorite = type in favoriteEventTypes,
                                    onFavoriteToggle = { eventViewModel.toggleFavorite(type) },
                                    onClick = {
                                        selectedType = type
                                        showTypeDialog = true
                                    },
                                    onLongClick = {
                                        selectedType = type
                                        showEventDialog = true
                                    },
                                    width = cardWidth,
                                    height = cardHeight,
                                    overlayContent = type.overlayBuilder(
                                        EventTypeOverlayContext(
                                            activeSleepEvent = activeSleepEvent,
                                            nextFeedingTimeMs = nextFeedingTimeMs,
                                            onTimerClick = {
                                                selectedType = type
                                                editingEvent =
                                                    if (type == EventType.SLEEP) activeSleepEvent else null
                                                editingEvent?.let { event -> eventViewModel.loadEventIntoForm(event) }
                                                showEventDialog = true
                                            }
                                        )
                                    )
                                )
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
                            color = backgroundColor,
                        )
                    }

                    // Timeline of recent events
                    items(babyEvents, key = { it.id }) { event ->
                        EventCard(
                            event = event,
                            onEdit = {
                                editingEvent = event;
                                eventViewModel.loadEventIntoForm(event)
                                showEventDialog = true
                            },
                            onDelete = { eventViewModel.deleteEvent(event) }
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
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                                            alpha = 0.8f
                                        )
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

            // dialog handling:
            if (showEventDialog && editingEvent != null) {
                EventFormDialog(
                    initialBabyId = selectedBaby?.id ?: return@Box,
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
                    initialBabyId = selectedBaby?.id ?: return@Box,
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
                    },
                    overlay = currentOverlay
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
    //summary: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    width: Dp,
    height: Dp,
    isFavorite: Boolean = false,
    onFavoriteToggle: (() -> Unit)? = null,
    overlayContent: (@Composable BoxScope.() -> Unit)? = null
) {
    val backgroundColor = BackgroundColor
    val contentColor = DarkGrey
    val tint = DarkBlue

    val borderWidth = 1.dp
    val cornerShape = MaterialTheme.shapes.extraLarge

    Surface(
        shape = cornerShape,
        color = Color.Transparent,
        border = BorderStroke(borderWidth, type.color),
        modifier = Modifier
            .width(width)
            .height(height)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick ?: {}
            )

    ) {
        // Background image
        AsyncImage(
            model = type.drawableRes,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.95f)
                .blur(2.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            type.color.copy(alpha = 0.75f),
                            type.color.copy(alpha = 0.15f),
                            backgroundColor.copy(alpha = 0.45f)
                        )
                    ),
                    shape = cornerShape,
                )
        ) {
            // Event name at top-left
            Text(
                text = type.displayName,
                style = MaterialTheme.typography.titleMedium,
                color = backgroundColor,
                modifier = Modifier
                    .zIndex(3f)
                    .align(Alignment.TopStart)
                    .padding(start = 24.dp, top = 20.dp)
            )

            // Favorite star icon at top-right
            if (onFavoriteToggle != null) {
                IconButton(
                    onClick = onFavoriteToggle,
                    modifier = Modifier
                        .zIndex(4f)
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(36.dp)
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                        tint = if (isFavorite) Color(0xFFFFD700) else contentColor.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Center content - show overlay and summary together
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .zIndex(2f)
                    .padding(start = 5.dp)
            ) {
                if (overlayContent != null) {
                    Column(
                        horizontalAlignment = Alignment.Start,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Overlay at top
                        this@Box.overlayContent()

//                        // Summary directly below overlay
//                        Text(
//                            text = summary,
//                            style = MaterialTheme.typography.bodySmall,
//                            color = backgroundColor.copy(alpha = 0.7f)
//                        )
                    }
                } else {
//                    // Default summary text centered
//                    Text(
//                        text = summary,
//                        style = MaterialTheme.typography.bodyLarge,
//                        color = backgroundColor.copy(alpha = 0.85f)
//                    )
                }
            }

            // Small "+" button at bottom-right
            if (onLongClick != null) {
                IconButton(
                    onClick = onLongClick,
                    modifier = Modifier
                        .zIndex(4f)
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(32.dp)
                        .background(
                            color = Color.Transparent,
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add ${type.displayName}",
                        tint = contentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

