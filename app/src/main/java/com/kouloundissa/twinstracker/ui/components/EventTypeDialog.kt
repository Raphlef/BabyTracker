package com.kouloundissa.twinstracker.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil.compose.AsyncImage
import com.kouloundissa.twinstracker.R
import com.kouloundissa.twinstracker.data.AnalysisRange
import com.kouloundissa.twinstracker.data.Baby
import com.kouloundissa.twinstracker.data.DailyAnalysis
import com.kouloundissa.twinstracker.data.Event
import com.kouloundissa.twinstracker.data.EventType
import com.kouloundissa.twinstracker.data.EventType.Companion.getDisplayName
import com.kouloundissa.twinstracker.data.GrowthMeasurement
import com.kouloundissa.twinstracker.presentation.viewmodel.BabyViewModel
import com.kouloundissa.twinstracker.presentation.viewmodel.EventViewModel
import com.kouloundissa.twinstracker.presentation.viewmodel.FamilyViewModel
import com.kouloundissa.twinstracker.ui.theme.BackgroundColor
import com.kouloundissa.twinstracker.ui.theme.DarkBlue
import com.kouloundissa.twinstracker.ui.theme.DarkGrey
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    // Animated visibility state
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isVisible = true
    }

    // Wrap in Dialog for full-screen behavior
    Dialog(
        onDismissRequest = {
            isVisible = false
            // Delay dismiss to allow exit animation
            GlobalScope.launch {
                delay(300)
                onDismiss()
            }
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        // Animated content with slide and fade
        AnimatedVisibility(
            visible = isVisible,
            enter = slideInVertically(
                initialOffsetY = { it / 2 },
                animationSpec = tween(200)
            ) + fadeIn(
                animationSpec = tween(150)
            ),
            exit = slideOutVertically(
                targetOffsetY = { it / 2 },
                animationSpec = tween(100)
            ) + fadeOut(
                animationSpec = tween(150)
            )
        ) {
            EventTypeDialogContent(
                type = type,
                events = events,
                onDismiss = {
                    isVisible = false
                    GlobalScope.launch {
                        delay(300)
                        onDismiss()
                    }
                },
                onEdit = onEdit,
                onAdd = onAdd,
                eventViewModel = eventViewModel,
                selectedBaby = selectedBaby,
                overlay = overlay
            )
        }
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
    familyViewModel: FamilyViewModel = hiltViewModel(),
    selectedBaby: Baby?,
    overlay: EventOverlayInfo = EventOverlayInfo()
) {
    val backgroundColor = BackgroundColor
    val contentColor = DarkGrey
    val tint = DarkBlue
    val cornerShape = MaterialTheme.shapes.extraLarge

    val isLoadingMore by eventViewModel.isLoadingMore.collectAsState()
    val hasMoreHistory by eventViewModel.hasMoreHistory.collectAsState()
    val lastGrowthEvent by eventViewModel.lastGrowthEvent.collectAsState()

    val today = LocalDate.now()

    val analysisSnapshot by eventViewModel.analysisSnapshot.collectAsState()

    val lazyListState = rememberLazyListState()
    val context = LocalContext.current
    val growthMeasurement = lastGrowthEvent?.let { event ->
        GrowthMeasurement(
            weightKg = event.weightKg?.toFloat() ?: Float.NaN,
            heightCm = event.heightCm?.toFloat() ?: Float.NaN,
            headCircumferenceCm = event.headCircumferenceCm?.toFloat() ?: Float.NaN,
            timestamp = event.timestamp.time
        )
    }
    val todayDailyAnalysis = analysisSnapshot.dailyAnalysis.find { it.date == today }
        ?.copy(  // Immutable update, preserves all other fields
            growthMeasurements = lastGrowthEvent?.let { event ->
                GrowthMeasurement(
                    weightKg = event.weightKg?.toFloat() ?: Float.NaN,
                    heightCm = event.heightCm?.toFloat() ?: Float.NaN,
                    headCircumferenceCm = event.headCircumferenceCm?.toFloat() ?: Float.NaN,
                    timestamp = event.timestamp.time
                )
            }
        )?: DailyAnalysis(date = today)
    val summary = remember(todayDailyAnalysis, lastGrowthEvent) {
        type.generateSummary( listOf(todayDailyAnalysis), context)
    }
    LaunchedEffect(selectedBaby)
    {
        selectedBaby?.let { eventViewModel.loadLastGrowth(it.id) }
    }

    if (type == EventType.GROWTH) {
        LaunchedEffect(selectedBaby) {
            selectedBaby?.let { baby ->
                val babyFilter = AnalysisFilter.BabyFilter(selectedBabies = setOf(baby))

                val analysisFilters = AnalysisFilters(
                    babyFilter = babyFilter,
                    dateRange = AnalysisFilter.DateRange(AnalysisRange.ONE_MONTH)
                )

                eventViewModel.refreshWithFilters(analysisFilters)
            }
        }
    }

    InfiniteScrollEffect(
        lazyListState = lazyListState,
        isLoading = isLoadingMore,
        hasMore = hasMoreHistory,
        onLoadMore = { eventViewModel.loadMoreEvents() },
        itemsBeforeEndToLoad = 3,
        maxConsecutiveEmptyLoads = if (type == EventType.GROWTH) 20 else 6,
    )
    BackHandler(onBack = onDismiss)

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        val blurRadius = if (events.size > 5) 15.dp else 6.dp

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
                .systemBarsPadding()
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
                    Text(
                        "No ${
                            type.getDisplayName(context = LocalContext.current).lowercase()
                        } events yet"
                    )
                } else {
                    Timeline(
                        events = events,
                        onEdit = onEdit,
                        onDelete = { event ->
                            selectedBaby?.let {
                                eventViewModel.deleteEvent(event, familyViewModel)
                            }
                        },
                        isLoadingMore = isLoadingMore,
                        hasMoreHistory = hasMoreHistory,
                        modifier = Modifier.fillMaxSize(),
                        lazyListState = lazyListState
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {

                TextButton(
                    onClick = { onAdd(type) }
                ) {
                    Text(
                        stringResource(id = R.string.event_type_add_event),
                        color = contentColor,
                        modifier = Modifier.background(
                            backgroundColor.copy(alpha = 0.9f),
                            cornerShape
                        ).padding(12.dp)
                    )
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
    modifier: Modifier = Modifier,
    babyViewModel: BabyViewModel = hiltViewModel(),
) {

    val backgroundColor = BackgroundColor
    val contentColor = DarkGrey
    val tint = DarkBlue
    val cornerShape = MaterialTheme.shapes.extraLarge

    val babies by babyViewModel.babies.collectAsState()
    val selectedBaby by babyViewModel.selectedBaby.collectAsState()

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
                        backgroundColor.copy(alpha = 0.3f),
                        CircleShape
                    )
                    .size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = contentColor
                )
            }

            Icon(
                imageVector = type.icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = type.getDisplayName(context = LocalContext.current),
                style = MaterialTheme.typography.titleLarge,
                color = tint
            )
        }
        Spacer(Modifier.height(12.dp))

        BabySelectorRow(
            babies = babies,
            selectedBaby = selectedBaby,
            onSelectBaby = { baby ->
                babyViewModel.selectBaby(baby)
            }
        )

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
                    .background(backgroundColor.copy(alpha = 0.3f))
                    .border(1.dp, backgroundColor.copy(alpha = 0.4f), MaterialTheme.shapes.medium)
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
                            color = tint.copy(alpha = 0.9f),
                            modifier = Modifier
                                .weight(1f)
                                .align(Alignment.CenterVertically)
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
                        color = tint.copy(alpha = 0.9f),
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
                containerColor = backgroundColor.copy(alpha = 0.3f)
            ),
            border = BorderStroke(1.dp, backgroundColor.copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.event_type_today_summary),
                    style = MaterialTheme.typography.labelSmall,
                    color = tint.copy(alpha = 0.9f)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = summary,
                    style = MaterialTheme.typography.titleMedium,
                    color = tint
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

    fun hasContent(): Boolean = (!description.isNullOrBlank() && content != null)
}