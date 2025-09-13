package com.example.babytracker.presentation.dashboard

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.unit.dp
import com.example.babytracker.data.Baby
import com.example.babytracker.presentation.viewmodel.AuthViewModel

@Composable
fun DashboardScreen(
    navController: NavController,
    babyId: String,
    viewModel: BabyViewModel = hiltViewModel(),
    authViewModel: AuthViewModel = hiltViewModel()
) {

    // Load all babies – ensure BabyViewModel manages this
    val babies by viewModel.babies.collectAsState()
    var selectedBaby by remember { mutableStateOf<Baby?>(null) }

    // Sélectionne le bébé initial quand la liste est chargée
    LaunchedEffect(babies, babyId) {
        if (selectedBaby == null && babies.isNotEmpty()) {
            selectedBaby = babies.find { it.id == babyId } ?: babies.first()
        }
    }

    LaunchedEffect(selectedBaby) {
        selectedBaby?.let { viewModel.selectBaby(it) }
    }
    // Define which screens appear in the bottom nav bar
    val bottomNavScreens = listOf(
        Screen.Feeding,
        Screen.Diaper,
        Screen.Sleep,
        Screen.Growth
    )

    // Manage selectedScreen state here, defaulting to the first item in bottomNavScreens
    var selectedScreen by rememberSaveable(stateSaver = ScreenSaver) { // Use custom saver for Screen
        mutableStateOf<Screen>(bottomNavScreens.firstOrNull() ?: Screen.Feeding) // Default to Feeding or first
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
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            // *** IMPORTANT: Afficher les composants baby DANS le Scaffold ***
            if (babies.isNotEmpty()) {
                BabySelector(
                    babies = babies,
                    selectedBaby = selectedBaby,
                    onBabySelected = { selectedBaby = it }
                )

                selectedBaby?.let { baby ->
                    BabyInfoBar(baby = baby)
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            // Afficher l'écran sélectionné
            selectedBaby?.let { baby ->
                when (selectedScreen) {
                    is Screen.Feeding -> FeedingScreen(/* babyId = baby.id */)
                    is Screen.Diaper -> DiaperScreen(/* babyId = baby.id */)
                    is Screen.Sleep -> SleepScreen(/* babyId = baby.id */)
                    is Screen.Growth -> GrowthScreen(/* babyId = baby.id */)
                    else -> {}
                }
            }
        }
    }
}

// BabySelector: simple horizontal scroll selection
@Composable
fun BabySelector(
    babies: List<Baby>,
    selectedBaby: Baby?,
    onBabySelected: (Baby) -> Unit
) {
    Row(modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState())
        .padding(8.dp)) {
        babies.forEach { baby ->
            OutlinedButton(
                onClick = { onBabySelected(baby) },
                modifier = Modifier
                    .padding(end = 8.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (baby == selectedBaby) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                )
            ) {
                Text(baby.name)
            }
        }
    }
}

// BabyInfoBar: display selected baby details
@Composable
fun BabyInfoBar(baby: Baby) {
    Surface(
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Nom: ${baby.name}", style = MaterialTheme.typography.titleMedium)
            Text("Naissance: ${java.text.SimpleDateFormat("dd/MM/yyyy").format(java.util.Date(baby.birthDate))}", style = MaterialTheme.typography.bodyMedium)
            Text("Genre: ${baby.gender.name}")
            // Add more info if needed (photo, notes, etc.)
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
        listOf(Screen.Feeding, Screen.Diaper, Screen.Sleep, Screen.Growth, Screen.Dashboard, Screen.Auth, Screen.BabySelection)
            .find { it.route == route } ?: Screen.Feeding // Default if not found
    }
)