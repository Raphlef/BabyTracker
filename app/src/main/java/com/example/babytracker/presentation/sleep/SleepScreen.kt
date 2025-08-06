package com.example.babytracker.presentation.sleep

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.babytracker.presentation.viewmodel.BabyViewModel
import com.example.babytracker.presentation.viewmodel.SleepViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepScreen(
    viewModel: SleepViewModel = hiltViewModel(),
    babyViewModel: BabyViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    val isSaving by viewModel.isSaving.collectAsState()
    val saveSuccess by viewModel.saveSuccess.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    val selectedBaby by babyViewModel.selectedBaby.collectAsState()
    val currentBabyId = selectedBaby?.id

    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    val dateFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    LaunchedEffect(state.saveSuccess) {
        if (state.saveSuccess) {
            snackbarHostState.showSnackbar("Sleep event saved!", duration = SnackbarDuration.Short)
            viewModel.resetState()
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Long)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Log Sleep", style = MaterialTheme.typography.headlineSmall)

            if (state.isSleeping) {
                Text("Sleep started at: ${dateFormat.format(state.startTime)}")
                Text("Duration so far: ${state.duration} minutes")

                Button(
                    onClick = { viewModel.stopSleep() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Stop Sleep")
                }
            } else {
                Text("Last sleep duration: ${state.duration} minutes")
                state.startTime?.let {
                    Text("Started at: ${dateFormat.format(it)}")
                }
                state.endTime?.let {
                    Text("Ended at: ${dateFormat.format(it)}")
                }

                Button(
                    onClick = { viewModel.startSleep() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Start Sleep")
                }
            }

            OutlinedTextField(
                value = state.notes ?: "",
                onValueChange = { viewModel.onNotesChanged(it) },
                label = { Text("Notes (optional)") },
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    if (currentBabyId != null) {
                        viewModel.saveSleepEvent(currentBabyId)
                    } else {
                        scope.launch {
                            snackbarHostState.showSnackbar("Please select a baby first.", SnackbarDuration.Short)
                        }
                    }
                },
                enabled = !state.isSleeping && state.duration > 0 && !state.isSaving,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Saving...")
                } else {
                    Text("Save Sleep Event")
                }
            }
        }
    }
}