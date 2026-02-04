package com.kouloundissa.twinstracker.presentation.home

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.kouloundissa.twinstracker.R
import com.kouloundissa.twinstracker.data.AnalysisRange
import com.kouloundissa.twinstracker.data.Event
import com.kouloundissa.twinstracker.data.EventType
import com.kouloundissa.twinstracker.data.EventTypeOverlayContext
import com.kouloundissa.twinstracker.data.SleepEvent
import com.kouloundissa.twinstracker.data.toEventOverlayInfo
import com.kouloundissa.twinstracker.presentation.baby.BabyCreateDialog
import com.kouloundissa.twinstracker.presentation.event.EventFormDialog
import com.kouloundissa.twinstracker.presentation.viewmodel.BabyViewModel
import com.kouloundissa.twinstracker.presentation.viewmodel.EventViewModel
import com.kouloundissa.twinstracker.presentation.viewmodel.FamilyViewModel
import com.kouloundissa.twinstracker.ui.components.AnalysisFilter
import com.kouloundissa.twinstracker.ui.components.AnalysisFilters
import com.kouloundissa.twinstracker.ui.components.EventOverlayInfo
import com.kouloundissa.twinstracker.ui.components.EventTypeCard
import com.kouloundissa.twinstracker.ui.components.EventTypeDialog
import com.kouloundissa.twinstracker.ui.components.calculateCardDimensions
import com.kouloundissa.twinstracker.ui.theme.BackgroundColor
import com.kouloundissa.twinstracker.ui.theme.DarkBlue
import com.kouloundissa.twinstracker.ui.theme.DarkGrey
import kotlinx.coroutines.FlowPreview
import java.time.LocalDate

@OptIn(FlowPreview::class)
@SuppressLint(
    "DefaultLocale", "UnusedMaterial3ScaffoldPaddingParameter",
    "UnusedBoxWithConstraintsScope"
)
@Composable
fun HomeScreen(
    contentPadding: PaddingValues = PaddingValues(),
    isVisible: Boolean,
    babyViewModel: BabyViewModel = hiltViewModel(),
    eventViewModel: EventViewModel = hiltViewModel(),
    familyViewModel: FamilyViewModel = hiltViewModel()
) {
    val backgroundColor = BackgroundColor
    val contentColor = DarkGrey
    val tint = DarkBlue
    val cornerShape = MaterialTheme.shapes.extraLarge

    val spacing = 8.dp

    val selectedBaby by babyViewModel.selectedBaby.collectAsState()
    val babies by babyViewModel.babies.collectAsState()

    var editingEvent by remember { mutableStateOf<Event?>(null) }
    var showEventDialog by remember { mutableStateOf(false) }
    var showBabyDialog by remember { mutableStateOf(false) }

    val analysisSnapshot by eventViewModel.analysisSnapshot.collectAsState()

    val errorMessage by eventViewModel.errorMessage.collectAsState()
    val favoriteEventTypes by eventViewModel.favoriteEventTypes.collectAsState()

    val sortedEventTypes = remember(favoriteEventTypes) {
        eventViewModel.getSortedEventTypes(favoriteEventTypes)
    }

    val snackbarHostState = remember { SnackbarHostState() }

    var selectedType by remember { mutableStateOf<EventType?>(null) }
    var showTypeDialog by remember { mutableStateOf(false) }

    LaunchedEffect(editingEvent) {
        editingEvent?.let {
            eventViewModel.loadEventIntoForm(it)
        }
    }
    LaunchedEffect(isVisible, selectedBaby?.id) {
        if (isVisible) {
            selectedBaby?.let { baby ->
                val babyFilter =
                    AnalysisFilter.BabyFilter(selectedBabies = setOf(baby))

                val analysisFilters = AnalysisFilters(
                    babyFilter = babyFilter,
                    dateRange = AnalysisFilter.DateRange(AnalysisRange.THREE_DAYS)
                )

                eventViewModel.refreshWithFilters(analysisFilters)
                eventViewModel.loadLastGrowth(baby.id)
            }
        }
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            eventViewModel.clearErrorMessage()
        }
    }

    val today = LocalDate.now()
    val yesterday = today.minusDays(1)

    val activeSleepEvent: SleepEvent? = remember(analysisSnapshot) {
        (listOfNotNull(
            analysisSnapshot.eventsByDay[today],
            analysisSnapshot.eventsByDay[yesterday]
        ).flatten())
            .filterIsInstance<SleepEvent>()
            .sortedByDescending { it.timestamp }
            .firstOrNull { it.endTime == null && it.beginTime != null }
    }

    val filteredEvents = analysisSnapshot.events
        .filter { EventType.forClass(it::class) == selectedType }

    val currentOverlayData by remember(selectedType, filteredEvents) {
        derivedStateOf {
            selectedType?.createOverlayData(
                EventTypeOverlayContext(
                    onClick = {
                        editingEvent =
                            if (selectedType == EventType.SLEEP) activeSleepEvent else null
                        editingEvent?.let { event ->
                            eventViewModel.loadEventIntoForm(event)
                        }
                        showEventDialog = true
                    },
                    lastEvents = filteredEvents
                )
            )
        }
    }

    // Résoudre les ressources dans le contexte Composable
    val currentOverlay = currentOverlayData?.toEventOverlayInfo() ?: EventOverlayInfo()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = Modifier.fillMaxSize()
    ) { scaffoldPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding)
        ) {
            if (selectedBaby == null) {
                // Empty state when no baby selected
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (babies.isEmpty() && !showBabyDialog) {
                        Button(
                            onClick = {
                                showBabyDialog = true
                            },
                            modifier = Modifier
                                .height(56.dp)
                                .width(180.dp)
                        ) {
                            Text(
                                stringResource(id = R.string.baby_form_title_add))
                        }
                    } else if (babies.isNotEmpty()) {
                        Text(
                            stringResource(id = R.string.select_baby_to_see_data),
                            style = MaterialTheme.typography.bodyLarge,
                            color = backgroundColor
                        )
                    }
                }
            } else {

                val cardDimensions by remember(maxWidth, maxHeight, sortedEventTypes.size) {
                    derivedStateOf {
                        calculateCardDimensions(
                            this@BoxWithConstraints,
                            contentPadding = contentPadding,
                            itemCount = sortedEventTypes.size,
                            spacing
                        )
                    }
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(cardDimensions.columns),
                    contentPadding = PaddingValues(
                        start = spacing * 2,
                        end = spacing * 2,
                        top = spacing,
                        bottom = spacing
                    ),
                    userScrollEnabled = cardDimensions.needsScrolling,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = cardDimensions.gridHeight),
                    horizontalArrangement = Arrangement.spacedBy(spacing),
                    verticalArrangement = Arrangement.spacedBy(spacing)
                ) {
                    items(sortedEventTypes) { type ->
                        val filterEvents = analysisSnapshot.events
                            .filter { EventType.forClass(it::class) == type }
                        EventTypeCard(
                            type = type,
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
                            width = cardDimensions.cardWidth,
                            height = cardDimensions.cardHeight,
                            overlayContent = type.overlayBuilder(
                                EventTypeOverlayContext(
                                    onClick = {
                                        selectedType = type
                                        editingEvent =
                                            if (type == EventType.SLEEP) activeSleepEvent else null
                                        editingEvent?.let { event ->
                                            eventViewModel.loadEventIntoForm(
                                                event
                                            )
                                        }
                                        showEventDialog = true
                                    },
                                    lastEvents = filterEvents
                                )
                            )
                        )
                    }
                }
            }

            // dialog handling:
            if (showEventDialog && editingEvent != null) {
                EventFormDialog(
                    initialBabyId = selectedBaby?.id ?: return@BoxWithConstraints,
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
                    initialBabyId = selectedBaby?.id ?: return@BoxWithConstraints,
                    initialEventType = selectedType,
                    onDismiss = {
                        showEventDialog = false
                        eventViewModel.resetFormState()
                    }
                )
            }
            if (showTypeDialog && selectedType != null) {
                // Filter events for this type and selected baby
                EventTypeDialog(
                    type = selectedType!!,
                    selectedBaby = selectedBaby,
                    events = filteredEvents,
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
                BabyCreateDialog(
                    onBabyCreated = { savedOrDeletedBaby ->
                        showBabyDialog = false
                        savedOrDeletedBaby.let { babyViewModel.selectBaby(it) }
                    },
                    onCancel = { showBabyDialog = false },
                )
            }

        }
    }
}