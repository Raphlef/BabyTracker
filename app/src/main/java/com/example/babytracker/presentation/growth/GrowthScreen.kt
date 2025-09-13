package com.example.babytracker.presentation.growth

import android.app.DatePickerDialog
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import com.github.mikephil.charting.charts.LineChart
import kotlinx.coroutines.launch
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun GrowthScreen(
    viewModel: GrowthViewModel = hiltViewModel(),
    babyViewModel: BabyViewModel = hiltViewModel()
) {
    val heightCm by viewModel.heightCm.collectAsState()
    val weightKg by viewModel.weightKg.collectAsState()
    val headCircumferenceCm by viewModel.headCircumferenceCm.collectAsState()
    val notes by viewModel.notes.collectAsState()

    val isLoadingInitial by viewModel.isLoading.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val saveSuccess by viewModel.saveSuccess.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    val selectedBaby by babyViewModel.selectedBaby.collectAsState()
    val babyId = selectedBaby?.id
    val startDate by viewModel.startDate.collectAsState()
    val endDate by viewModel.endDate.collectAsState()

    LaunchedEffect(babyId) {
        babyId?.let {
            viewModel.loadGrowthEventsInRange(it)
            viewModel.loadLastGrowth(it)    // Pour préremplissage du form
        }
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    val isLoading by viewModel.isLoading.collectAsState()
    val growthEvents by viewModel.growthEvents.collectAsState()
    val (lineData, xFormatter) = remember(growthEvents) { viewModel.getMultiLineData() }

    val measurementTs by viewModel.measurementTimestamp.collectAsState()
    var measurementCal by remember {
        mutableStateOf(
            Calendar.getInstance().apply { timeInMillis = measurementTs })
    }
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
            viewModel.resetSaveSuccess() // Reset the flag
        }
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Long)
            viewModel.clearErrorMessage() // Clear the error message after showing
        }
    }

    // Mettre à jour measurementCal si measurementTs change
    LaunchedEffect(measurementTs) {
        measurementCal.timeInMillis = measurementTs
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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Button(onClick = {
                    DatePickerDialog(
                        context,
                        { _, y, m, d ->
                            Calendar.getInstance().apply {
                                set(y, m, d, 0, 0, 0); set(Calendar.MILLISECOND, 0)
                                viewModel.setStartDate(time, babyId!!)
                            }
                        },
                        Calendar.getInstance().apply { time = startDate }.get(Calendar.YEAR),
                        Calendar.getInstance().apply { time = startDate }.get(Calendar.MONTH),
                        Calendar.getInstance().apply { time = startDate }.get(Calendar.DAY_OF_MONTH)
                    ).show()
                }) {
                    Text("Début : ${dateFormat.format(startDate)}")
                }

                Button(onClick = {
                    DatePickerDialog(
                        context,
                        { _, y, m, d ->
                            Calendar.getInstance().apply {
                                set(y, m, d, 23, 59, 59); set(Calendar.MILLISECOND, 999)
                                viewModel.setEndDate(time, babyId!!)
                            }
                        },
                        Calendar.getInstance().apply { time = endDate }.get(Calendar.YEAR),
                        Calendar.getInstance().apply { time = endDate }.get(Calendar.MONTH),
                        Calendar.getInstance().apply { time = endDate }.get(Calendar.DAY_OF_MONTH)
                    ).show()
                }) {
                    Text("Fin : ${dateFormat.format(endDate)}")
                }
            }

            Spacer(Modifier.height(16.dp))

            // Graphique de croissance (Placeholder - needs data from ViewModel)
            AndroidView(
                factory = { context ->
                    LineChart(context).apply {
                        description.isEnabled = false
                        setTouchEnabled(true); setPinchZoom(true)
                        axisRight.isEnabled = false
                        legend.isWordWrapEnabled = true
                        xAxis.granularity = 1f
                    }
                },
                update = { chart ->
                    chart.data = lineData
                    chart.xAxis.valueFormatter = xFormatter
                    chart.invalidate()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
            )
            // Overlay de chargement lors du changement de bébé
            if (isLoading) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            Spacer(Modifier.height(24.dp))

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
                    if (babyId != null) {
                        viewModel.saveGrowthEvent(babyId)
                    } else {
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                "Please select a baby first.",
                                duration = SnackbarDuration.Short
                            )
                        }
                    }
                },
                enabled = !isSaving && babyId != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Saving...")
                } else {
                    Text("Save Measurement")
                }
            }
        }
    }
    // Overlay de chargement initial
    if (isLoadingInitial) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }
}