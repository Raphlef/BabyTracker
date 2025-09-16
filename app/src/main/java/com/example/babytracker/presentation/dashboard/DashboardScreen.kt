package com.example.babytracker.presentation.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.babytracker.presentation.diaper.DiaperScreen
import com.example.babytracker.ui.components.TopAppBar
import com.example.babytracker.presentation.viewmodel.BabyViewModel
import com.example.babytracker.presentation.feeding.FeedingScreen
import com.example.babytracker.presentation.growth.GrowthScreen
import com.example.babytracker.presentation.navigation.Screen
import com.example.babytracker.presentation.sleep.SleepScreen
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.babytracker.data.Baby
import com.example.babytracker.data.Gender
import com.example.babytracker.presentation.calendar.CalendarScreen
import com.example.babytracker.presentation.event.EventFormDialog
import com.example.babytracker.presentation.viewmodel.AuthViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.zIndex
import com.example.babytracker.presentation.analysis.AnalysisScreen
import com.example.babytracker.presentation.baby.EditBabyFormDialog
import com.example.babytracker.presentation.home.HomeScreen
import com.example.babytracker.presentation.viewmodel.EventViewModel
import com.example.babytracker.ui.components.BottomNavBar
import dev.chrisbanes.haze.HazeState


enum class DashboardTab(val label: String, val icon: @Composable () -> Unit) {
    Home("Home", { Icon(Icons.Default.Home, contentDescription = "Home") }),
    Calendar("Calendar", { Icon(Icons.Default.CalendarToday, contentDescription = "Calendar") }),
    Analysis(
        "Analysis",
        { Icon(Icons.AutoMirrored.Filled.ShowChart, contentDescription = "Analysis") }),
    Settings("Settings", { Icon(Icons.Default.Settings, contentDescription = "Settings") }),
}

@Composable
fun DashboardScreen(
    navController: NavController,
    initialBabyId: String,
    viewModel: BabyViewModel = hiltViewModel(),
    authViewModel: AuthViewModel = hiltViewModel()
) {
    // HazeState for glassmorphic blur
    val hazeState = remember { HazeState() }

    // Load all babies – ensure BabyViewModel manages this
    val babies by viewModel.babies.collectAsState()
    val selectedBaby by viewModel.selectedBaby.collectAsState()
    var editingBabyId by remember { mutableStateOf<String?>(null) }
    var showEventForm by remember { mutableStateOf(false) }

    // Tabs state
    var selectedTab by remember { mutableStateOf(DashboardTab.Home) }

    // Initialize selectedBaby on first composition
    LaunchedEffect(babies, initialBabyId) {
        if (babies.isNotEmpty() && selectedBaby == null) {
            val toSelect = initialBabyId?.let { id ->
                babies.find { it.id == id }
            } ?: babies.first()
            viewModel.selectBaby(toSelect)
        }
    }

    // Track scroll for BabyInfoBar visibility
    val listState = rememberLazyListState()
    val showInfoBar by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 20
        }
    }

    Scaffold(
        // Turn off the automatic bottom padding inset
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = Color.Transparent, // remove Scaffold’s own background
        bottomBar = {
            BottomNavBar(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
                onAddClicked = { showEventForm = true },
                navItems = listOf(
                    DashboardTab.Home,
                    DashboardTab.Calendar,
                    DashboardTab.Analysis,
                    DashboardTab.Settings
                ),
                hazeState = hazeState
            )
        }
    ) { paddingValues ->
        Column(Modifier.padding(paddingValues)) {
            Spacer(modifier = Modifier.height(4.dp))
            // --- BABY SELECTOR + INFO ---
            BabySelectorRow(
                babies = babies,
                selectedBaby = selectedBaby,
                onSelectBaby = { viewModel.selectBaby(it) },
                onAddBaby = { navController.navigate("add_baby") }
            )

            AnimatedVisibility(
                visible = showInfoBar && selectedBaby != null,
                enter = fadeIn(), exit = fadeOut()
            ) {
                selectedBaby?.let {
                    BabyInfoBar(it) { editingBabyId = it.id }
                }
            }

            Spacer(Modifier.height(8.dp))

            // --- MAIN CONTENT for selected tab ---
            Box(Modifier.weight(1f)) {
                when (selectedTab) {
                    DashboardTab.Home -> HomeScreen(listState)
                    DashboardTab.Calendar -> CalendarScreen(listState)
                    DashboardTab.Analysis -> AnalysisScreen()
                    DashboardTab.Settings -> SettingsScreen()
                }
            }
            // --- Dialogs ---
            selectedBaby?.takeIf { showEventForm }?.let { baby ->
                EventFormDialog(
                    babyId = baby.id,
                    onDismiss = { showEventForm = false }
                )
            }
            editingBabyId?.let { babyId ->
                EditBabyFormDialog(
                    babyId = babyId,
                    onBabyUpdated = { editingBabyId = null },
                    onCancel = { editingBabyId = null }
                )
            }
        }
    }
}

@Composable
fun BabySelectorRow(
    babies: List<Baby>,
    selectedBaby: Baby?,
    onSelectBaby: (Baby) -> Unit,
    onAddBaby: () -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(babies) { baby ->
            OutlinedButton(
                onClick = { onSelectBaby(baby) },
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (baby == selectedBaby)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surface,
                ),
                shape = CircleShape
            ) {
                Text(baby.name.ifEmpty { "Sans nom" }, maxLines = 1)
            }
        }
        item {
            IconButton(
                onClick = onAddBaby,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Baby")
            }
        }
    }
}

@Composable
fun BabyInfoBar(
    baby: Baby,
    onEditClick: () -> Unit = {}
) {
    Surface(
        tonalElevation = 4.dp,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Box(modifier = Modifier.padding(20.dp)) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "👶 ${baby.name.ifEmpty { "Unnamed Baby" }}",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(
                        onClick = onEditClick,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Baby"
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "📅 Born: ",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(
                            Date(
                                baby.birthDate
                            )
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(Modifier.height(6.dp))

                if (baby.gender != Gender.UNKNOWN) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "⚧ Genre : ",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val (icon, label) = when (baby.gender) {
                            Gender.MALE -> "♂" to "Male"
                            Gender.FEMALE -> "♀" to "Female"
                            Gender.OTHER -> "⚧" to "Other"
                            Gender.PREFER_NOT_TO_SAY -> "❔" to "Prefer not to say"
                            Gender.UNKNOWN -> "—" to "Unknown"
                        }
                        Text(
                            text = "$icon $label",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Text(
            "Settings & Parents (dev in progress…)",
            style = MaterialTheme.typography.headlineSmall
        )
    }
}
