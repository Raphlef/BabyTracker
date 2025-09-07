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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.babytracker.presentation.auth.AuthScreen
import com.example.babytracker.presentation.dashboard.AddBabyScreen
import com.example.babytracker.presentation.dashboard.BabySelectionScreen
import com.example.babytracker.presentation.dashboard.DashboardScreen
import com.example.babytracker.presentation.settings.SettingsScreen
import com.example.babytracker.presentation.viewmodel.AuthViewModel
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
        startDestination = if (state.isAuthenticated) "baby_selection" else "auth"
    ) {
        composable("auth") {
            AuthScreen(
                onLoginSuccess = { navController.navigate("baby_selection") }
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
                babyId = babyId
            )
        }
        composable("settings") {
            SettingsScreen(
                navController = navController
            )
        }
    }
}
@HiltAndroidApp
class BabyTrackerApplication : Application(){

}