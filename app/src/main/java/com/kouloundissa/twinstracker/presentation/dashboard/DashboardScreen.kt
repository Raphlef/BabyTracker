package com.kouloundissa.twinstracker.presentation.dashboard

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.kouloundissa.twinstracker.presentation.viewmodel.BabyViewModel
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kouloundissa.twinstracker.R
import com.kouloundissa.twinstracker.data.Baby
import com.kouloundissa.twinstracker.data.DashboardTab
import com.kouloundissa.twinstracker.data.EventFormState
import com.kouloundissa.twinstracker.data.EventFormState.*
import com.kouloundissa.twinstracker.data.EventType
import com.kouloundissa.twinstracker.presentation.calendar.CalendarScreen
import com.kouloundissa.twinstracker.presentation.event.EventFormDialog
import com.kouloundissa.twinstracker.presentation.viewmodel.AuthViewModel
import com.kouloundissa.twinstracker.presentation.analysis.AnalysisScreen
import com.kouloundissa.twinstracker.presentation.baby.BabyFormDialog
import com.kouloundissa.twinstracker.presentation.home.HomeScreen
import com.kouloundissa.twinstracker.presentation.settings.SettingsScreen
import com.kouloundissa.twinstracker.presentation.viewmodel.EventViewModel
import com.kouloundissa.twinstracker.ui.components.BabyInfoBar
import com.kouloundissa.twinstracker.ui.components.BabySelectorRow
import com.kouloundissa.twinstracker.ui.components.BackgroundContainer
import com.kouloundissa.twinstracker.ui.components.BottomNavBar
import com.kouloundissa.twinstracker.ui.theme.*
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun DashboardScreen(
    navController: NavController,
    initialBabyId: String,
    babyViewModel: BabyViewModel = hiltViewModel(),
    authViewModel: AuthViewModel = hiltViewModel(),
    eventViewModel: EventViewModel = androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel()
) {
    // HazeState for glassmorphic blur
    val hazeState = remember { HazeState() }

    val density = LocalDensity.current

    // State for measured height in Dp
    var bottomBarHeightDp by remember { mutableStateOf(0.dp) }
    val contentPadding = PaddingValues(bottom = bottomBarHeightDp)
    // Load all babies – ensure BabyViewModel manages this
    val babies by babyViewModel.babies.collectAsState()
    val selectedBaby by babyViewModel.selectedBaby.collectAsState()
    var editingBaby by remember { mutableStateOf<Baby?>(null) }
    var showBabyDialog by remember { mutableStateOf(false) }
    var showEventForm by remember { mutableStateOf(false) }
    var selectedEventFormState by remember { mutableStateOf<EventFormState?>(null) }
    val babyError by babyViewModel.errorMessage.collectAsState()


    // Initialize selectedBaby on first composition
    LaunchedEffect(babies, initialBabyId) {
        if (babies.isNotEmpty() && selectedBaby == null) {
            val toSelect = initialBabyId?.let { id ->
                babies.find { it.id == id }
            } ?: babies.first()
            babyViewModel.selectBaby(toSelect)
        }
    }

    val tabs = listOf(
        DashboardTab.Home,
        DashboardTab.Calendar,
        DashboardTab.Analysis,
        DashboardTab.Settings
    )
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { tabs.size }
    )
    val currentTab = tabs[pagerState.currentPage]

    LaunchedEffect(pagerState.currentPage) {
        Log.d("Dashboard", "Page changed to: $currentTab (index: ${pagerState.currentPage})")

        // Arrêter tous les streams actifs quand on change de page
        eventViewModel.stopStreaming()
    }
    val coroutineScope = rememberCoroutineScope()

    // Synchronisation entre l'onglet sélectionné et le pager
    val selectedTab by remember {
        derivedStateOf { tabs[pagerState.currentPage] }
    }

    BackgroundContainer(backgroundRes = R.drawable.background) {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets.systemBars
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // Apply the Scaffold’s insets (status bar + nav bar):
                    .padding(paddingValues)
            ) {
                Column() {
                    Spacer(Modifier.height(8.dp))

                    BabyInfoBar(
                        babies = babies,
                        selectedBaby = selectedBaby,
                        onSelectBaby = {
                            babyViewModel.selectBaby(it)
                        },
                        onEditBaby = {
                            editingBaby = selectedBaby
                        },
                        onAddBaby = {
                            // Open create baby dialog
                            editingBaby = null
                            showBabyDialog = true
                        }
                    )


                    Spacer(Modifier.height(8.dp))
                    // --- MAIN CONTENT for selected tab ---
                    Box(Modifier.weight(1f)) {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize()
                        ) { page ->
                            val isCurrentPage = page == pagerState.currentPage
                            when (tabs[page]) {
                                DashboardTab.Home -> HomeScreen(
                                    contentPadding = contentPadding,
                                    isVisible = isCurrentPage
                                )

                                DashboardTab.Calendar -> CalendarScreen(
                                    contentPadding = contentPadding,
                                    isVisible = isCurrentPage
                                )

                                DashboardTab.Analysis -> AnalysisScreen(
                                    contentPadding = contentPadding,
                                    isVisible = isCurrentPage
                                )

                                DashboardTab.Settings -> SettingsScreen(
                                    navController,
                                    contentPadding = contentPadding
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    // --- Dialogs ---
                    // Dialog display condition
                    selectedBaby?.takeIf { showEventForm }?.let { baby ->
                        val formState =
                            selectedEventFormState ?: EventFormState.Diaper() // fallback default
                        EventFormDialog(
                            initialBabyId = baby.id,
                            initialEventType = formState.eventType,
                            onDismiss = {
                                showEventForm = false
                                selectedEventFormState = null
                            }
                        )
                    }
                    editingBaby?.let { baby ->
                        BabyFormDialog(
                            babyToEdit = baby,
                            onBabyUpdated = { editingBaby = null },
                            onCancel = { editingBaby = null }
                        )
                    }
                    if (showBabyDialog && editingBaby == null) {
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
                    babyError?.let {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                // Floating nav sits on bot, aligned bottom center
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .onGloballyPositioned { coords ->
                            // Measure height of this Box (nav + offsets)
                            bottomBarHeightDp = with(density) { coords.size.height.toDp() }
                        }
                ) {
                    val eventTypes = EventType.entries
                    BottomNavBar(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth(),
                        selectedTab = selectedTab,
                        onTabSelected = { tab ->
                            // Animation vers la page correspondante
                            val index = tabs.indexOf(tab)
                            if (index != -1) {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            }
                        },
                        onAddClicked = { showEventForm = true },
                        navItems = tabs,
                        hazeState = hazeState,
                        eventTypes = eventTypes.map { et ->
                            et.displayName to @Composable {
                                Icon(
                                    et.icon,
                                    et.displayName,
                                    tint = et.color
                                )
                            }
                        },
                        onEventTypeSelected = { eventTypeName ->
                            val eventType = try {
                                EventType.valueOf(eventTypeName.uppercase(Locale.getDefault()))
                            } catch (e: IllegalArgumentException) {
                                Log.w("EventForm", "Unknown eventTypeString: $eventTypeName")
                                null
                            }

                            eventType?.let {
                                selectedEventFormState = when (it) {
                                    EventType.DIAPER -> Diaper()
                                    EventType.FEEDING -> Feeding()
                                    EventType.SLEEP -> Sleep()
                                    EventType.GROWTH -> Growth()
                                    EventType.PUMPING -> Pumping()
                                    EventType.DRUGS -> Drugs()
                                }
                                showEventForm = true
                            }
                        }
                    )
                }
            }
        }
    }
}