package com.example.babytracker

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.babytracker.presentation.auth.AuthScreen
import com.example.babytracker.presentation.baby.AddBabyScreen
import com.example.babytracker.presentation.baby.BabySelectionScreen
import com.example.babytracker.presentation.baby.EditBabyScreen
import com.example.babytracker.presentation.calendar.CalendarScreen
import com.example.babytracker.presentation.dashboard.DashboardScreen
import com.example.babytracker.presentation.event.EventFormScreen
import com.example.babytracker.presentation.navigation.Screen
import com.example.babytracker.presentation.settings.ParentsScreen
import com.example.babytracker.presentation.settings.SettingsScreen
import com.example.babytracker.presentation.viewmodel.AuthViewModel
import com.example.babytracker.presentation.viewmodel.EventViewModel
import com.example.babytracker.ui.theme.BabyTrackerTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.HiltAndroidApp

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BabyTrackerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BabyTrackerApp()
                }
            }
        }
    }
}

@Composable
fun BabyTrackerApp() {
    val navController = rememberNavController()
    val viewModel: AuthViewModel = hiltViewModel()

    // Collect state from StateFlow
    val state by viewModel.state.collectAsState()

    NavHost(
        navController = navController,
        startDestination = "auth"
    ) {
        composable("auth") {
            AuthScreen(
                onLoginSuccess = { firstBabyId ->
                    if (firstBabyId != null) {
                        navController.navigate("dashboard/$firstBabyId") {
                            popUpTo("auth") { inclusive = true }
                        }
                    } else {
                        navController.navigate("baby_selection") {
                            popUpTo("auth") { inclusive = true }
                        }
                    }
                }
            )
        }
        composable("baby_selection") {
            BabySelectionScreen(
                onAddBaby = { navController.navigate("add_baby") },
                onContinue = { selectedBabyId ->
                    navController.navigate("dashboard/$selectedBabyId")
                }
            )
        }
        composable("add_baby") {
            AddBabyScreen(
                onBabyAdded = {
                    navController.popBackStack() // Retour à baby_selection
                },
                onCancel = {
                    navController.popBackStack()
                }
            )
        }
        composable("dashboard/{babyId}") { backStackEntry ->
            val babyId = backStackEntry.arguments?.getString("babyId") ?: ""
            // Passer babyId au DashboardScreen pour afficher données du bébé sélectionné
            DashboardScreen(
                navController = navController,
                initialBabyId = babyId
            )
        }
        composable(
            "event_form/{babyId}",
            arguments = listOf(navArgument("babyId") { type = NavType.StringType })
        ) { backStackEntry ->
            val babyId = backStackEntry.arguments!!.getString("babyId")!!
            EventFormScreen(
                babyId = babyId,
                navController,
                viewModel = hiltViewModel<EventViewModel>()
            )
        }
        composable(
            route = "edit_baby/{babyId}",
            arguments = listOf(navArgument("babyId") { type = NavType.StringType })
        ) { backStackEntry ->
            val babyId = backStackEntry.arguments?.getString("babyId") ?: ""
            EditBabyScreen(
                babyId = babyId,
                onBabyUpdated = { navController.popBackStack() },
                onCancel = { navController.popBackStack() }
            )
        }
        composable("settings") {
            SettingsScreen(
                navController = navController
            )
        }
        composable(
            route = "parents/{babyId}",
            arguments = listOf(navArgument("babyId") {
                type = NavType.StringType
            })
        ) { backStackEntry ->
            ParentsScreen(
                navController = navController,
                babyViewModel = hiltViewModel()       // pass same VM if needed
            )
        }
    }
}

@HiltAndroidApp
class BabyTrackerApplication : Application() {

}