package com.example.babytracker.presentation.growth

import android.app.DatePickerDialog
import android.os.Build
import android.widget.DatePicker
import androidx.activity.result.launch
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.babytracker.presentation.viewmodel.BabyViewModel
import com.example.babytracker.presentation.viewmodel.GrowthViewModel
import com.example.babytracker.presentation.viewmodel.SleepViewModel
import com.github.mikephil.charting.charts.LineChart
import kotlinx.coroutines.launch
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalContext
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Date
import java.util.Locale

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun GrowthScreen(
    viewModel: GrowthViewModel = hiltViewModel(),
    babyViewModel: BabyViewModel = hiltViewModel()
) {
    val heightCm by viewModel.heightCm.collectAsState()
    val weightKg by viewModel.weightKg.collectAsState()
    val headCircumferenceCm by viewModel.headCircumferenceCm.collectAsState()
    val notes by viewModel.notes.collectAsState()


    val isSaving by viewModel.isSaving.collectAsState()
    val saveSuccess by viewModel.saveSuccess.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    val selectedBaby by babyViewModel.selectedBaby.collectAsState()
    val currentBabyId = selectedBaby?.id

    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    val measurementTs by viewModel.measurementTimestamp.collectAsState()
    var measurementCal by remember { mutableStateOf(Calendar.getInstance().apply { timeInMillis = measurementTs }) }
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    val measurementDateString = dateFormat.format(measurementCal.time)

    val context = LocalContext.current
    val datePickerDialog = DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            measurementCal.set(year, month, dayOfMonth)
            viewModel.setMeasurementTimestamp(measurementCal.timeInMillis)
        },
        measurementCal.get(Calendar.YEAR),
        measurementCal.get(Calendar.MONTH),
        measurementCal.get(Calendar.DAY_OF_MONTH)
    )

    LaunchedEffect(saveSuccess) {
        if (saveSuccess) {
            snackbarHostState.showSnackbar("Growth event saved!", duration = SnackbarDuration.Short)
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

    Scaffold( // Added Scaffold for Snackbar support
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues) // Apply padding from Scaffold
                .padding(16.dp)
                .verticalScroll(scrollState), // Make the column scrollable
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Growth Tracking", style = MaterialTheme.typography.headlineMedium)

            Spacer(modifier = Modifier.height(16.dp))

            // Graphique de croissance (Placeholder - needs data from ViewModel)
            AndroidView(
                factory = { context ->
                    com.github.mikephil.charting.charts.LineChart(context).apply {
                        description.isEnabled = false
                        setTouchEnabled(true)
                        setScaleEnabled(true)
                        setPinchZoom(true)
                        // Basic styling if needed
                    }
                },
                update = { chart ->
                    // Example:
                    // state.chartData?.let { chart.data = it }
                    // chart.invalidate()
                    // You'll need to load/prepare LineData in your ViewModel
                    // and pass it to the chart here.
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text("Log New Measurement", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(16.dp))

            // Bouton Date (ouvre DatePickerDialog)
            Button(onClick = { datePickerDialog.show() }) {
                Text("Date de mesure : $measurementDateString")
            }
            Spacer(Modifier.height(16.dp))

            // Formulaire d'ajout
            OutlinedTextField(
                value = weightKg.toString(), // Use state.weightInput
                onValueChange = { input ->
                    val value = input.toDoubleOrNull() ?: 0.0
                    viewModel.setWeight(value)
                }, // Call ViewModel's handler
                label = { Text("Weight (kg)") }, // Be more generic or specify unit
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = heightCm.toString(), // Use state.heightInput
                onValueChange = { input ->
                    val value = input.toDoubleOrNull() ?: 0.0
                    viewModel.setHeight(value)
                }, // Call ViewModel's handler
                label = { Text("Height (cm)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = headCircumferenceCm.toString(),
                onValueChange = { input ->
                    val value = input.toDoubleOrNull() ?: 0.0
                    viewModel.setHeadCircumferenceCm(value)
                }, // Call ViewModel's handler
                label = { Text("Head Circumference (cm)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true
            )
            // TODO: Add a DatePicker for measurementDate if needed

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = notes, // Use state.notes
                onValueChange = { viewModel.onNotesChanged(it) }, // Call ViewModel's handler
                label = { Text("Notes (Optional)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 80.dp),
                singleLine = false
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (currentBabyId != null) {
                        viewModel.saveGrowthEvent(currentBabyId)
                    } else {
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                "Please select a baby first.",
                                duration = SnackbarDuration.Short
                            )
                        }
                    }
                },
                enabled = !isSaving && currentBabyId != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text("Saving...")
                } else {
                    Text("Save Measurement")
                }
            }

            // Error message display from the button click (now handled by LaunchedEffect and Snackbar)
            // state.error?.let { // This specific error text can be removed if snackbar is sufficient
            //     Spacer(modifier = Modifier.height(16.dp))
            //     Text(
            //         text = it,
            //         color = MaterialTheme.colorScheme.error,
            //         style = MaterialTheme.typography.bodySmall
            //     )
            // }

            // TODO: Ajouter des courbes de percentile (OMS) - This involves more complex chart setup
            // TODO: Implémenter un suivi du périmètre crânien - Covered by the head circumference field
            // TODO: Ajouter des rappels pour les prochaines mesures - This is a separate feature
        }
    }
}