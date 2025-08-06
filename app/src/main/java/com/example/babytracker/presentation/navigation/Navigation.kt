package com.example.babytracker.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.babytracker.presentation.auth.AuthScreen
import com.example.babytracker.presentation.dashboard.*

@Composable
fun BabyTrackerNavHost(
    navController: NavHostController,
    startDestination: String = Screen.Auth.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Auth.route) {
            AuthScreen(
                onLoginSuccess = { navController.navigate(Screen.BabySelection.route) }
            )
        }

        composable(Screen.BabySelection.route) {
            BabySelectionScreen(
                onBabySelected = { navController.navigate(Screen.Dashboard.route) }
            )
        }

        composable(Screen.AddBaby.route) {
            AddBabyScreen(
                onComplete = { navController.popBackStack() }
            )
        }

        composable(Screen.Dashboard.route) {
            DashboardScreen(navController = navController)
        }

        // TODO: Ajouter les autres écrans (Feeding, Diaper, etc.)
    }
}