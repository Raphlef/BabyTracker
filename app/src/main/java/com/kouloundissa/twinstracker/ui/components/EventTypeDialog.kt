package com.kouloundissa.twinstracker.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil.compose.AsyncImage
import com.kouloundissa.twinstracker.data.Baby
import com.kouloundissa.twinstracker.data.Event
import com.kouloundissa.twinstracker.data.EventType
import com.kouloundissa.twinstracker.presentation.viewmodel.EventViewModel
import com.kouloundissa.twinstracker.ui.theme.BackgroundColor
import com.kouloundissa.twinstracker.ui.theme.DarkBlue
import com.kouloundissa.twinstracker.ui.theme.DarkGrey
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun EventTypeDialog(
    type: EventType,
    events: List<Event>,
    onDismiss: () -> Unit,
    onEdit: (Event) -> Unit,
    onAdd: (EventType) -> Unit,
    eventViewModel: EventViewModel = hiltViewModel(),
    selectedBaby: Baby?,
    overlay: EventOverlayInfo = EventOverlayInfo()
) {
    // Wrap in Dialog for full-screen behavior
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        EventTypeDialogContent(
            type = type,
            events = events,
            onDismiss = onDismiss,
            onEdit = onEdit,
            onAdd = onAdd,
            eventViewModel = eventViewModel,
            selectedBaby = selectedBaby,
            overlay = overlay
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
private fun EventTypeDialogContent(
    type: EventType,
    events: List<Event>,
    onDismiss: () -> Unit,
    onEdit: (Event) -> Unit,
    onAdd: (EventType) -> Unit,
    eventViewModel: EventViewModel = hiltViewModel(),
    selectedBaby: Baby?,
    overlay: EventOverlayInfo = EventOverlayInfo()
) {
    val backgroundColor = BackgroundColor
    val contentColor = DarkGrey
    val tint = DarkBlue
    val cornerShape = MaterialTheme.shapes.extraLarge

    val isLoadingMore by eventViewModel.isLoadingMore.collectAsState()
    val hasMoreHistory by eventViewModel.hasMoreHistory.collectAsState()
    val eventsByDay by eventViewModel.eventsByDay.collectAsState()
    val lastGrowthEvent by eventViewModel.lastGrowthEvent.collectAsState()

    val today = LocalDate.now()
    val todayEvents = eventsByDay[today].orEmpty().filter { event ->
        EventType.forClass(event::class) == type
    }
    val lazyListState = rememberLazyListState()
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,  // skips intermediate state to start fully expanded
    )

    // Generate summary using the new refactored method
    val summary = remember(todayEvents) {
        type.generateSummary(todayEvents, lastGrowthEvent)
    }

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

    BackHandler(onBack = onDismiss)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
    ) {
        val blurRadius = if (events.size > 5) 5.dp else 2.dp

        //  Background image sized to the dialog
        AsyncImage(
            model = type.drawableRes,
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
                )
        )

        Column(
            modifier = Modifier
                .matchParentSize()
                .padding(12.dp)               // uniform padding inside edges
        ) {
            EventTypeHeaderPanel(
                type = type,
                summary = summary,
                eventCount = events.size,
                onDismiss = onDismiss,
                overlay = overlay
            )
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (events.isEmpty()) {
                    Text("No ${type.displayName.lowercase()} events yet")
                } else {
                    Timeline(
                        events = events,
                        onEdit = onEdit,
                        onDelete = { event ->
                            selectedBaby?.let {
                                eventViewModel.deleteEvent(event)
                            }
                        },
                        onLoadMore = {
                            eventViewModel.loadMoreHistoricalEvents()
                        },
                        isLoadingMore = isLoadingMore,
                        hasMoreHistory = hasMoreHistory,
                        modifier = Modifier.fillMaxSize()
                    )
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
                    Text("Add New Event", color = backgroundColor)
                }

                TextButton(onClick = onDismiss) {
                    Text("Close", color = backgroundColor)
                }
            }
        }
    }
}

/**
 * Reusable header panel showing event type title and summary information.
 * Keeps related UI logic together and improves code organization.
 */
@Composable
private fun EventTypeHeaderPanel(
    type: EventType,
    summary: String,
    eventCount: Int,
    onDismiss: () -> Unit,
    overlay: EventOverlayInfo = EventOverlayInfo(),
    modifier: Modifier = Modifier
) {

    val backgroundColor = BackgroundColor
    val contentColor = DarkGrey
    val tint = DarkBlue
    val cornerShape = MaterialTheme.shapes.extraLarge

    Column(modifier = modifier) {
        Spacer(Modifier.height(10.dp))
        // Title with icon
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        CircleShape
                    )
                    .size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back"
                )
            }

            Icon(
                imageVector = type.icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = type.displayName,
                style = MaterialTheme.typography.titleLarge,
                color = contentColor
            )
        }

        Spacer(Modifier.height(12.dp))
        // ============================================
        // UNIFIED OVERLAY SECTION
        // Description and content together in one cohesive block
        // ============================================
        if (overlay.hasContent()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(contentColor.copy(alpha = 0.12f))
                    .border(1.dp, contentColor.copy(alpha = 0.3f), MaterialTheme.shapes.medium)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Description and content side by side when both exist
                if (!overlay.description.isNullOrBlank() && overlay.content != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        // Description on left
                        Text(
                            text = overlay.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = contentColor.copy(alpha = 0.8f),
                            modifier = Modifier
                                .weight(1f)
                                .align(Alignment.Top)
                        )

                        // Content on right
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .align(Alignment.CenterVertically)
                        ) {
                            overlay.content!!()
                        }
                    }
                }
                // Description only
                else if (!overlay.description.isNullOrBlank()) {
                    Text(
                        text = overlay.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.8f),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                // Content only
                else if (overlay.content != null) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        overlay.content!!()
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
        }

        // ============================================
        // SUMMARY CARD
        // ============================================
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium),
            colors = CardDefaults.cardColors(
                containerColor = contentColor.copy(alpha = 0.15f)
            ),
            border = BorderStroke(1.dp, contentColor.copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = "Today's Summary",
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.8f)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = summary,
                    style = MaterialTheme.typography.titleMedium,
                    color = contentColor
                )
                if (eventCount > 0) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "$eventCount event${if (eventCount > 1) "s" else ""} total",
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

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
                    "Loading older events…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
/**
 * Data class to hold overlay content and description together.
 * Simplifies parameter passing and makes the API cleaner.
 */
/**
 * Data class to hold overlay content and description together.
 * Simplifies parameter passing and makes the API cleaner.
 */
data class EventOverlayInfo(
    val description: String? = null,
    val content: (@Composable BoxScope.() -> Unit)? = null
) {
    companion object {
        fun description(text: String) = EventOverlayInfo(description = text)
        fun content(block: @Composable BoxScope.() -> Unit) = EventOverlayInfo(content = block)
        fun full(
            description: String,
            block: @Composable BoxScope.() -> Unit
        ) = EventOverlayInfo(description = description, content = block)

        fun empty() = EventOverlayInfo()
    }

    fun hasContent(): Boolean = !description.isNullOrBlank() || content != null
}