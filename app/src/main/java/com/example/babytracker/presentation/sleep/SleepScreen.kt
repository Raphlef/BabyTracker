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

    val isSleeping by viewModel.isSleeping.collectAsState()
    val beginTime by viewModel.beginTime.collectAsState()
    val endTime by viewModel.endTime.collectAsState()
    val durationMinutes by viewModel.durationMinutes.collectAsState()
    val notes by viewModel.notes.collectAsState()

    val isSaving by viewModel.isSaving.collectAsState()
    val saveSuccess by viewModel.saveSuccess.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    val selectedBaby by babyViewModel.selectedBaby.collectAsState()
    val currentBabyId = selectedBaby?.id

    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    val dateFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    LaunchedEffect(saveSuccess) {
        if (saveSuccess) {
            snackbarHostState.showSnackbar("Sleep event saved!", duration = SnackbarDuration.Short)
            viewModel.resetInputFields() // Clear the form
            viewModel.resetSaveSuccess() // Reset the flag
        }
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Long)
            viewModel.clearErrorMessage() // Clear the error message after showing
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

            if (isSleeping) {
                Text("Sleep started at: ${dateFormat.format(beginTime)}")
                Text("Duration so far: ${durationMinutes} minutes")

                Button(
                    onClick = { viewModel.stopSleep() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Stop Sleep")
                }
            } else {
                Text("Last sleep duration: ${durationMinutes} minutes")
                beginTime?.let {
                    Text("Started at: ${dateFormat.format(it)}")
                }
                endTime?.let {
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
                value = notes ?: "",
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
                            snackbarHostState.showSnackbar("Please select a baby first.", duration = SnackbarDuration.Short)
                        }
                    }
                },
                enabled = !isSleeping && durationMinutes > 0 && !isSaving,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isSaving) {
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