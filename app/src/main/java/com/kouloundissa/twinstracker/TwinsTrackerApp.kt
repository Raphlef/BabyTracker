package com.kouloundissa.twinstracker

import android.Manifest
import android.app.AlertDialog
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.android.gms.ads.MobileAds
import com.google.firebase.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.kouloundissa.twinstracker.presentation.Family.FamilyCheckScreen
import com.kouloundissa.twinstracker.presentation.auth.AuthScreen
import com.kouloundissa.twinstracker.presentation.dashboard.DashboardScreen
import com.kouloundissa.twinstracker.presentation.event.NotificationEventScreen
import com.kouloundissa.twinstracker.presentation.viewmodel.EventViewModel
import com.kouloundissa.twinstracker.presentation.viewmodel.FamilyViewModel
import com.kouloundissa.twinstracker.ui.theme.BabyTrackerTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val eventViewModel: EventViewModel by viewModels()

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            // Handle case where user denied permission
            showNotificationPermissionRationale()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        CoroutineScope(Dispatchers.IO).launch {
            // Initialize the Google Mobile Ads SDK on a background thread.
            MobileAds.initialize(this@MainActivity) {}
        }
        setContent {
            BabyTrackerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    TwinsTrackerApp()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent) {
        val target = intent.getStringExtra("navigation_target") ?: return
        if (target == "editEvent") {
            intent.getStringExtra("event_id")?.let { eventId ->
                eventViewModel.onNotificationClicked(eventId)
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun showNotificationPermissionRationale() {
        // Show explanation to user about why notifications are useful
        AlertDialog.Builder(this)
            .setTitle("Enable Notifications")
            .setMessage("Get notified when other caregivers add events for your baby")
            .setPositiveButton("Enable") { _, _ ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            .setNegativeButton("Not Now", null)
            .show()
    }
}

@Composable
fun TwinsTrackerApp() {
    val navController = rememberNavController()
    val familyViewModel: FamilyViewModel = hiltViewModel()
    val eventViewModel: EventViewModel = hiltViewModel()

    val notificationEvent by eventViewModel.notificationEvent.collectAsState()


    LaunchedEffect(notificationEvent) {
        notificationEvent?.let { event ->
            eventViewModel.loadEventIntoForm(notificationEvent!!)
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
        composable(
            route = "editEvent/{eventId}",
            arguments = listOf(navArgument("eventId") { type = NavType.StringType })
        ) { backStackEntry ->
            val eventId = backStackEntry.arguments?.getString("eventId")!!
            NotificationEventScreen(
                eventId = eventId,
                onDone = { navController.popBackStack() }
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