package com.kouloundissa.twinstracker.ui.components


import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.kouloundissa.twinstracker.R
import com.kouloundissa.twinstracker.data.DiaperEvent
import com.kouloundissa.twinstracker.data.DrugType
import com.kouloundissa.twinstracker.data.DrugsEvent
import com.kouloundissa.twinstracker.data.Event
import com.kouloundissa.twinstracker.data.EventType
import com.kouloundissa.twinstracker.data.EventType.Companion.getDisplayName
import com.kouloundissa.twinstracker.data.FeedingEvent
import com.kouloundissa.twinstracker.data.Firestore.FirestoreTimestampUtils.toLocalDate
import com.kouloundissa.twinstracker.data.GrowthEvent
import com.kouloundissa.twinstracker.data.PumpingEvent
import com.kouloundissa.twinstracker.data.SleepEvent
import com.kouloundissa.twinstracker.data.getDisplayName
import com.kouloundissa.twinstracker.ui.components.Ad.InlineBannerAd
import com.kouloundissa.twinstracker.ui.theme.BackgroundColor
import com.kouloundissa.twinstracker.ui.theme.DarkBlue
import com.kouloundissa.twinstracker.ui.theme.DarkGrey
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

fun LazyListScope.timelineItemsContent(
    eventsByDate: Map<LocalDate, List<Event>>,
    onEdit: (Event) -> Unit,
    onDelete: (Event) -> Unit,
    isLoadingMore: Boolean,
    hasMoreHistory: Boolean,
    eventCard: @Composable (Event, () -> Unit, () -> Unit) -> Unit = { event, onEdit, onDelete ->
        EventCard(event, onEdit, onDelete)
    }
) {
    if (eventsByDate.isEmpty()) {
        item {
            Text(
                stringResource(id = R.string.no_events),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    } else {
        val dates = eventsByDate.keys.toList()
        dates.forEachIndexed { index, date ->
            val dayEvents = eventsByDate.getValue(date)
            item { DayHeader(date, dayEvents) }
            // Show ad every 2 day headers
            if ((index + 1) % 2 == 0) {
                item {
                    // AdManager.showInterstitial(activity)
                    InlineBannerAd(
                        adUnitId = "ca-app-pub-2976291373414752/6090374978" // banner ad unit id
                    )
                }
            }
            items(dayEvents, key = { it.id }) { event ->
                eventCard(event, { onEdit(event) }, { onDelete(event) })
            }
            if (date != eventsByDate.keys.last()) {
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }

    if (isLoadingMore && hasMoreHistory) {
        item { LoadingMoreIndicator() }
    }
}

/**
 * Standalone Timeline component for use in regular Column layouts.
 * Creates its own LazyColumn for scrolling.
 */
@Composable
fun Timeline(
    events: List<Event>,
    onEdit: (Event) -> Unit,
    onDelete: (Event) -> Unit,
    isLoadingMore: Boolean,
    hasMoreHistory: Boolean,
    modifier: Modifier = Modifier,
    lazyListState: LazyListState
) {
    // Group events by date
    val eventsByDate = remember(events) {
        events.groupBy { event ->
            event.timestamp.toLocalDate()
        }.toSortedMap(reverseOrder())  // Most recent first
    }

    LazyColumn(
        modifier = modifier,
        state = lazyListState,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        timelineItemsContent(
            eventsByDate = eventsByDate,
            onEdit = onEdit,
            onDelete = onDelete,
            isLoadingMore = isLoadingMore,
            hasMoreHistory = hasMoreHistory
        )
    }
}

/**
 * Composable that renders a day header with formatted date.
 * Customize the appearance as needed.
 */
@Composable
private fun DayHeader(
    date: LocalDate,
    events: List<Event>,
    modifier: Modifier = Modifier
) {
    val backgroundColor = BackgroundColor
    val contentColor = DarkGrey
    val tint = DarkBlue
    val cornerShape = MaterialTheme.shapes.extraLarge

    val formatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")
    val formattedDate = date.format(formatter)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor.copy(alpha = 0.95f), shape = cornerShape)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = stringResource(
                id = R.string.date_with_event_count,
                formattedDate,
                events.size
            ),
            style = MaterialTheme.typography.labelMedium,
            color = tint
        )

        Divider(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
            color = tint.copy(alpha = 0.5f),
            thickness = 1.dp
        )
    }
}

/**
 * Loading indicator shown when fetching older events.
 */
@Composable
private fun LoadingMoreIndicator(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
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
                    stringResource(id = R.string.loading_events),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Effect that handles infinite scroll logic.
 * Triggers when user scrolls near the end of the list.
 *
 * @param lazyListState State of the LazyColumn
 * @param isLoading Whether data is currently being loaded
 * @param hasMore Whether more data is available
 * @param onLoadMore Callback to trigger loading
 * @param threshold Number of items from end to trigger load
 * @param debounceMs Milliseconds to wait between load attempts
 */
@Composable
fun InfiniteScrollEffect(
    lazyListState: LazyListState,
    isLoading: Boolean,
    hasMore: Boolean,
    onLoadMore: () -> Unit,
    threshold: Int = 3,
    debounceMs: Long = 300,
    maxConsecutiveEmptyLoads: Int = 3
) {
    var lastLoadAttempt by remember { mutableLongStateOf(0L) }
    var lastLoadedCount by remember { mutableIntStateOf(0) }
    var consecutiveEmptyLoads by remember { mutableIntStateOf(0) }
    var loadingStartCount by remember { mutableIntStateOf(0) }

    // Track when loading starts and ends to detect if items were added
    LaunchedEffect(isLoading) {
        if (isLoading) {
            // Loading started - capture current count
            loadingStartCount = lazyListState.layoutInfo.totalItemsCount
        } else if (loadingStartCount > 0) {
            // Loading finished - check if any items were added
            val currentCount = lazyListState.layoutInfo.totalItemsCount
            val itemsAdded = currentCount - loadingStartCount

            if (itemsAdded == 0) {
                // No items were added - stop future attempts
                consecutiveEmptyLoads++
                Log.d(
                    "InfiniteScroll",
                    "Load returned 0 items. Consecutive empty loads: $consecutiveEmptyLoads/$maxConsecutiveEmptyLoads"
                )
                if (consecutiveEmptyLoads >= maxConsecutiveEmptyLoads) {
                    Log.d(
                        "InfiniteScroll",
                        "Max consecutive empty loads reached. Stopping future attempts."
                    )
                }
            } else {
                consecutiveEmptyLoads = 0
                lastLoadedCount = currentCount
                Log.d(
                    "InfiniteScroll",
                    "Load returned $itemsAdded items. Total: $currentCount. Reset empty load counter."
                )
            }

            loadingStartCount = 0
        }
    }

    LaunchedEffect(lazyListState) {
        snapshotFlow { lazyListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .distinctUntilChanged()
            .collect { lastVisibleIndex ->
                val totalItems = lazyListState.layoutInfo.totalItemsCount
                val currentTime = System.currentTimeMillis()

                if (lastVisibleIndex != null && lastVisibleIndex >= totalItems - threshold) {
                    val shouldAttemptLoad = hasMore &&
                            !isLoading &&
                            consecutiveEmptyLoads < maxConsecutiveEmptyLoads &&
                            (lastLoadedCount == 0 || totalItems > lastLoadedCount) &&
                            (currentTime - lastLoadAttempt > debounceMs)

                    if (shouldAttemptLoad) {
                        lastLoadAttempt = currentTime
                        Log.d("InfiniteScroll", "Triggering load. Current items: $totalItems")
                        onLoadMore()
                    } else if (consecutiveEmptyLoads >= maxConsecutiveEmptyLoads) {
                        Log.d(
                            "InfiniteScroll",
                            "Skipping load - max consecutive empty loads reached ($consecutiveEmptyLoads/$maxConsecutiveEmptyLoads)"
                        )
                    }
                }
            }
    }
}

@Composable
fun EventCard(
    event: Event,
    onEdit: () -> Unit,
    onDelete: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val eventType = EventType.forClass(event::class)

    val backgroundColor = BackgroundColor
    val grey = DarkGrey
    val tint = DarkBlue
    val cornerShape = MaterialTheme.shapes.extraLarge

    val context = LocalContext.current
    val deleteAction: () -> Unit = onDelete ?: {
        Toast.makeText(context, "Deleted ${event.id}", Toast.LENGTH_SHORT).show()
    }

    // Swipe & confirmation states
    var offsetX by remember { mutableFloatStateOf(0f) }
    var showConfirm by remember { mutableStateOf(false) }
    val animatedOffset by animateFloatAsState(offsetX, tween(300))

    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current

    val maxSwipePx = with(density) { 120.dp.toPx() }
    val thresholdPx = with(density) { 50.dp.toPx() }

    Box(modifier = modifier.fillMaxWidth()) {

        // Foreground card
        Surface(
            color = backgroundColor.copy(alpha = 0.3f),
            shape = cornerShape,
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(animatedOffset.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (!showConfirm && offsetX <= -thresholdPx) {
                                showConfirm = true
                                offsetX = -maxSwipePx
                            } else {
                                showConfirm = false
                                offsetX = 0f
                            }
                        }
                    ) { change, delta ->
                        change.consume()
                        when {
                            // If confirmation is showing and user drags right, cancel it
                            showConfirm && delta > 0 -> {
                                showConfirm = false
                                offsetX = 0f
                            }
                            // Only allow leftward drag to reveal confirm
                            !showConfirm && delta < 0 -> {
                                offsetX = (offsetX + delta).coerceIn(-maxSwipePx, 0f)
                            }
                            // Ignore other drags
                            else -> { /* no-op */
                            }
                        }
                    }
                }
                .clickable {
                    if (showConfirm) {
                        // Tapped outside bin: cancel
                        showConfirm = false
                        offsetX = 0f
                    } else {
                        onEdit()
                    }
                }
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                eventType.color.copy(alpha = 0.25f),
                                eventType.color.copy(alpha = 0.95f)
                            )
                        ),
                        shape = cornerShape
                    )
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                EventTypeIndicator(eventType)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        buildEventTitle(event, eventType, context),
                        style = MaterialTheme.typography.titleMedium,
                        color = tint
                    )
                    TimeDisplay(event)
                    event.notes?.takeIf(String::isNotBlank)?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = tint.copy(alpha = 0.85f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (event is SleepEvent && event.endTime != null) DurationBadge(event)
                event.photoUrl?.takeIf(String::isNotBlank)?.let {
                    Icon(
                        Icons.Default.PhotoCamera,
                        contentDescription = "Photo attached",
                        tint = tint,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Edit",
                    tint = tint,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Confirm-delete bin overlay
        if (showConfirm) {
            val confirmWidth = with(LocalDensity.current) { (-animatedOffset).toDp() }
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(confirmWidth)
                    .align(Alignment.CenterEnd),
                color = Color.White.copy(alpha = 0.2f),
                shape = cornerShape
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            deleteAction()
                            showConfirm = false
                            offsetX = 0f
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Confirm delete",
                        tint = Color.Red,// MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .size(48.dp)
                            .clickable {
                                deleteAction()
                                showConfirm = false
                                offsetX = 0f
                            }
                    )
                }
            }
        }
    }
}


@Composable
private fun EventTypeIndicator(eventType: EventType) {
    val backgroundColor = BackgroundColor
    val grey = DarkGrey
    val tint = DarkBlue
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(grey.copy(alpha = 0.45f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = eventType.icon,
            contentDescription = null,
            tint = eventType.color,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun TimeDisplay(event: Event) {
    val formatter = DateTimeFormatter.ofPattern("MMM dd, HH:mm")  // e.g. "Sep 30, 14:45"
    val contentColor = DarkBlue
    val timeText = when (event) {
        is SleepEvent -> {
            val startTime = event.beginTime?.toInstant()
                ?.atZone(ZoneId.systemDefault())
                ?.format(formatter) ?: "Unknown"

            if (event.endTime != null) {
                val endTime = event.endTime.toInstant()
                    .atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("HH:mm"))  // only show time for end
                "$startTime - $endTime"
            } else {
                "Started at $startTime"
            }
        }

        else -> {
            event.timestamp.toInstant()
                .atZone(ZoneId.systemDefault())
                .format(formatter)
        }
    }

    Text(
        text = timeText,
        style = MaterialTheme.typography.labelSmall,  // small font size
        color = contentColor
    )
}


@Composable
private fun DurationBadge(event: SleepEvent) {
    if (event.endTime == null) return

    val duration = Duration.between(
        event.beginTime?.toInstant() ?: event.timestamp.toInstant(),
        event.endTime.toInstant()
    )

    val hours = duration.toHours()
    val minutes = duration.toMinutes() % 60

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.padding(4.dp)
    ) {
        Text(
            text = "${hours}h ${minutes}m",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

// Build event-specific titles using your existing data classes
private fun buildEventTitle(
    event: Event, eventType: EventType,
    context: Context
): String {
    return when (event) {
        is DiaperEvent -> {
            val typeInfo = event.diaperType.getDisplayName(context)
            "${eventType.getDisplayName(context)} - $typeInfo"
        }

        is FeedingEvent -> {
            val details = buildString {
                append(eventType.getDisplayName(context))
                event.amountMl?.takeIf { it > 0 }?.let { append(" - ${it.toInt()}ml") }
                event.durationMinutes?.takeIf { it > 0 }?.let { append(" (${it}min)") }
                event.breastSide?.let {
                    append(
                        " - ${
                            it.name.lowercase().replaceFirstChar { char -> char.uppercase() }
                        }"
                    )
                }
            }
            details
        }

        is SleepEvent -> {
            if (event.isSleeping) {
                "${eventType.getDisplayName(context)} - Ongoing"
            } else {
                eventType.getDisplayName(context)
            }
        }

        is GrowthEvent -> {
            val measurements = buildString {
                append(eventType.getDisplayName(context))
                val parts = mutableListOf<String>()
                event.weightKg?.let { parts.add("${String.format("%.1f", it)}kg") }
                event.heightCm?.let { parts.add("${it.toInt()}cm") }
                event.headCircumferenceCm?.let { parts.add("Head: ${String.format("%.1f", it)}cm") }
                if (parts.isNotEmpty()) {
                    append(" - ${parts.joinToString(", ")}")
                }
            }
            measurements
        }

        is PumpingEvent -> {
            val details = buildString {
                append(eventType.getDisplayName(context))
                event.amountMl?.let { append(" - ${it.toInt()}ml") }
                event.durationMinutes?.let { append(" (${it}min)") }
                event.breastSide?.let {
                    append(
                        " - ${
                            it.name.lowercase().replaceFirstChar { char -> char.uppercase() }
                        }"
                    )
                }
            }
            details
        }

        is DrugsEvent -> {
            val details = buildString {
                append(eventType.getDisplayName(context))
                val drugName = if (event.drugType == DrugType.OTHER) {
                    event.otherDrugName ?: "Unknown"
                } else {
                    event.drugType.getDisplayName(context)
                }
                append(" - $drugName")
                event.dosage?.let { append(" ${it.toInt()}${event.unit}") }
            }
            details
        }

        else -> eventType.getDisplayName(context)
    }
}
