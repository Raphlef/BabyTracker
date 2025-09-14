package com.example.babytracker.presentation.calendar


import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.example.babytracker.data.Baby
import com.example.babytracker.data.Gender
import com.example.babytracker.presentation.viewmodel.AuthViewModel
import com.example.babytracker.presentation.viewmodel.EventViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CalendarScreen(
    viewModel: EventViewModel = hiltViewModel(),
    babyViewModel: BabyViewModel = hiltViewModel()
) {
    val eventsByType by viewModel.eventsByType.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val selectedBaby by babyViewModel.selectedBaby.collectAsState()
    val currentBabyId = selectedBaby?.id
    val snackbarHostState = remember { SnackbarHostState() }

    // Load events whenever babyId changes
    LaunchedEffect(currentBabyId) {
        currentBabyId?.let { viewModel.loadEventsInRange(it) }
    }

    // Show error via Snackbar
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Long)
            viewModel.clearErrorMessage()
        }
    }

    // Flatten and sort all events by timestamp
    val allEvents = remember(eventsByType) {
        eventsByType.values.flatten().sortedByDescending { it.timestamp }
    }

    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
            ) {
                when {
                    isLoading -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }

                    allEvents.isEmpty() -> {
                        Text(
                            text = "No events to display",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(allEvents) { event ->
                                val time = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
                                    .format(event.timestamp)
                                Text(
                                    text = "$time • ${event::class.simpleName}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp)
                                )
                                Divider()
                            }
                        }
                    }
                }
            }
        }
    }
}