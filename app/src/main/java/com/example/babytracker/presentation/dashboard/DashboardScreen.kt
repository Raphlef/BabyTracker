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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.example.babytracker.ui.components.BottomNavBar
import com.example.babytracker.ui.components.TopAppBar
import com.example.babytracker.presentation.viewmodel.BabyViewModel
import com.example.babytracker.presentation.feeding.FeedingScreen
import com.example.babytracker.presentation.growth.GrowthScreen
import com.example.babytracker.presentation.navigation.Screen
import com.example.babytracker.presentation.sleep.SleepScreen
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Alignment
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
import com.example.babytracker.presentation.baby.EditBabyFormDialog


@Composable
fun DashboardScreen(
    navController: NavController,
    initialBabyId: String,
    viewModel: BabyViewModel = hiltViewModel(),
    authViewModel: AuthViewModel = hiltViewModel()
) {

    // Load all babies – ensure BabyViewModel manages this
    val babies by viewModel.babies.collectAsState()
    val selectedBaby by viewModel.selectedBaby.collectAsState()
    var editingBabyId by remember { mutableStateOf<String?>(null) }
    var showEventForm by remember { mutableStateOf(false) }

    // Initialize selectedBaby on first composition
    LaunchedEffect(babies, initialBabyId) {
        if (babies.isNotEmpty() && selectedBaby == null) {
            val toSelect = initialBabyId?.let { id ->
                babies.find { it.id == id }
            } ?: babies.first()
            viewModel.selectBaby(toSelect)
        }
    }

    // Define which screens appear in the bottom nav bar
    val bottomNavScreens = listOf(
        Screen.Feeding,
        Screen.Diaper,
        Screen.Sleep,
        Screen.Growth,
        Screen.Calendar
    )

    // Manage selectedScreen state here, defaulting to the first item in bottomNavScreens
    var selectedScreen by rememberSaveable(stateSaver = ScreenSaver) { // Use custom saver for Screen
        mutableStateOf<Screen>(
            bottomNavScreens.firstOrNull() ?: Screen.Feeding
        ) // Default to Feeding or first
    }

    val listState = rememberLazyListState()
    val isAtTop by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "BabyTracking",
                navController = navController,
                onLogout = {
                    authViewModel.logout()
                    navController.navigate("auth") {
                        popUpTo("dashboard/{babyId}") { inclusive = true }
                    }
                },
                onNavigateSettings = {
                    navController.navigate("settings")
                },
                onNavigateParents = {
                    navController.navigate("parents/{babyId}")
                }
            )
        },
        bottomBar = {
            BottomNavBar(
                navController = navController,
                screensForBottomNav = bottomNavScreens,
                selectedScreen = selectedScreen,
                onScreenSelected = { newScreen -> selectedScreen = newScreen }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (selectedBaby != null) {
                        showEventForm = true
                    }
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Event")
            }
        }
    ) { paddingValues ->
        Column(Modifier.padding(paddingValues)) {
            Spacer(modifier = Modifier.height(6.dp))
            // Always visible baby selector row (not scrollable)
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(babies) { baby ->
                    OutlinedButton(
                        onClick = { viewModel.selectBaby(baby) },
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (baby == selectedBaby)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surface,
                        )
                    ) {
                        Text(baby.name.ifEmpty { "Sans nom" }, maxLines = 1)
                    }
                }
                item {
                    IconButton(
                        onClick = { navController.navigate("add_baby") },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Baby")
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))

            // Scrollable content area with baby info and selected screen content
            LazyColumn(state = listState) {
                item {
                    // Smooth visibility of baby info
                    AnimatedVisibility(
                        visible = isAtTop,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        selectedBaby?.let { baby ->
                            BabyInfoBar(
                                baby = baby,
                                onEditClick = { editingBabyId = baby.id }
                            )
                        }
                    }
                }
                item {
                    selectedBaby?.let { baby ->
                        val screenHeight = LocalConfiguration.current.screenHeightDp.dp
                        val reservedHeight = 120.dp
                        val contentHeight = (screenHeight - reservedHeight).coerceAtLeast(200.dp)
                        when (selectedScreen) {
                            Screen.Feeding -> Box(modifier = Modifier.heightIn(max = contentHeight)) { FeedingScreen() }
                            Screen.Diaper -> Box(modifier = Modifier.heightIn(max = contentHeight)) { DiaperScreen() }
                            Screen.Sleep -> Box(modifier = Modifier.heightIn(max = contentHeight)) { SleepScreen() }
                            Screen.Growth -> Box(modifier = Modifier.heightIn(max = contentHeight)) { GrowthScreen() }
                            Screen.Calendar -> Box(modifier = Modifier.heightIn(max = contentHeight)) { CalendarScreen() }
                            else -> {}
                        }
                    }
                }
            }

            // Event form dialog shown conditionally
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
                    onCancel      = { editingBabyId = null }
                )
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
                            Gender.MALE               -> "♂" to "Male"
                            Gender.FEMALE             -> "♀" to "Female"
                            Gender.OTHER              -> "⚧" to "Other"
                            Gender.PREFER_NOT_TO_SAY  -> "❔" to "Prefer not to say"
                            Gender.UNKNOWN            -> "—" to "Unknown"
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


// Custom Saver for Screen (if Screen is a sealed class with objects)
// Put this in a relevant place, maybe near your Screen definition or in a utils file

val ScreenSaver = Saver<Screen, String>(
    save = { it.route }, // Save the route string
    restore = { route -> // Restore based on the route string
        // Find the Screen object that matches the saved route
        // This requires you to have a way to get all possible Screen objects
        // or to check against each known object.
        // For example:
        listOf(
            Screen.Feeding,
            Screen.Diaper,
            Screen.Sleep,
            Screen.Growth,
            Screen.Dashboard,
            Screen.Auth,
            Screen.BabySelection
        )
            .find { it.route == route } ?: Screen.Feeding // Default if not found
    }
)