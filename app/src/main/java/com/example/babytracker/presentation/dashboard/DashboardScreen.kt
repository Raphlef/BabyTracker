package com.example.babytracker.presentation.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
@Composable
fun DashboardScreen(
    navController: NavController,
    babyId: String,
    viewModel: BabyViewModel = hiltViewModel()
) {

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
            // Display content based on the selectedScreen object
            when (selectedScreen) {
                is Screen.Feeding -> FeedingScreen(/* Pass data from viewModel */)
                is Screen.Diaper -> DiaperScreen(/* ... */)
                is Screen.Sleep -> SleepScreen(/* ... */)
                is Screen.Growth -> GrowthScreen(/* ... */)
                // Add else or handle other screen types if selectedScreen could be something else
                // This 'else' should ideally not be hit if selectedScreen is always one of the bottomNavScreens
                else -> { /* Optional: Handle unexpected screen state */ }
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
        listOf(Screen.Feeding, Screen.Diaper, Screen.Sleep, Screen.Growth, Screen.Dashboard, Screen.Auth, Screen.BabySelection)
            .find { it.route == route } ?: Screen.Feeding // Default if not found
    }
)