package com.kouloundissa.twinstracker

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.kouloundissa.twinstracker.presentation.auth.AuthScreen
import com.kouloundissa.twinstracker.presentation.dashboard.DashboardScreen
import com.kouloundissa.twinstracker.presentation.viewmodel.AuthViewModel
import com.kouloundissa.twinstracker.ui.theme.BabyTrackerTheme
import com.google.firebase.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.kouloundissa.twinstracker.presentation.Family.FamilyCheckScreen
import com.kouloundissa.twinstracker.presentation.viewmodel.AuthEvent
import com.kouloundissa.twinstracker.presentation.viewmodel.FamilyViewModel
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
    val authViewModel: AuthViewModel = hiltViewModel()
    val familyViewModel: FamilyViewModel = hiltViewModel()


    LaunchedEffect(Unit) {
        authViewModel.oneTimeEventFlow.collect { event ->
            when (event) {
                AuthEvent.Logout -> {
                    navController.navigate("auth") {
                        popUpTo(navController.graph.startDestinationId) {
                            inclusive = true
                        }
                    }
                }
                else -> { /* ignore other events */ }
            }
        }
    }
    NavHost(
        navController = navController,
        startDestination = "auth"
    ) {
        composable("auth") {
            AuthScreen(
                onLoginSuccess = {
                    navController.navigate("family_check") {
                        popUpTo("auth") { inclusive = true }
                    }
                }
            )
        }
        composable("family_check") {
            FamilyCheckScreen(
                familyViewModel = familyViewModel,
                onNavigateToDashboard = { babyId ->
                    navController.navigate("dashboard/${babyId.orEmpty()}") {
                        popUpTo("family_check") { inclusive = true }
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

        // Optional: enable or disable Crashlytics collection
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)
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