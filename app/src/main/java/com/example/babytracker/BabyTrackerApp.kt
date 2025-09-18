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
import com.example.babytracker.presentation.dashboard.DashboardScreen
import com.example.babytracker.presentation.settings.ParentsScreen
import com.example.babytracker.presentation.settings.SettingsScreen
import com.example.babytracker.presentation.viewmodel.AuthViewModel
import com.example.babytracker.ui.theme.BabyTrackerTheme
import com.google.firebase.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
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
                    // Always navigate to dashboard with babyId or empty string if none
                    val babyIdToNavigate = firstBabyId ?: ""
                    navController.navigate("dashboard/$babyIdToNavigate") {
                        popUpTo("auth") { inclusive = true }
                    }
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
    override fun onCreate() {
        super.onCreate()

        // Initialize Firebase first
        FirebaseApp.initializeApp(this)

        // Initialize App Check
        initializeAppCheck()
    }

    private fun initializeAppCheck() {
        val firebaseAppCheck = FirebaseAppCheck.getInstance()

        // Use different providers for debug vs release builds
        if (BuildConfig.DEBUG) {
            // For development - use debug provider
            firebaseAppCheck.installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
        } else {
            // For production - use Play Integrity provider
            firebaseAppCheck.installAppCheckProviderFactory(
                PlayIntegrityAppCheckProviderFactory.getInstance()
            )
        }
    }
}