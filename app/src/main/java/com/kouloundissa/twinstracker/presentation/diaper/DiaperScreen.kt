package com.kouloundissa.twinstracker.presentation.diaper

import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kouloundissa.twinstracker.data.DiaperType
import com.kouloundissa.twinstracker.data.PoopColor
import com.kouloundissa.twinstracker.data.PoopConsistency
import com.kouloundissa.twinstracker.presentation.feeding.capitalizeWords
// Import other necessary enums if you have a more detailed model like PoopContent, PoopColor
import com.kouloundissa.twinstracker.presentation.viewmodel.BabyViewModel
import com.kouloundissa.twinstracker.presentation.viewmodel.DiaperViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaperScreen(
    viewModel: DiaperViewModel = hiltViewModel(),
    babyViewModel: BabyViewModel = hiltViewModel()
    // onSaveSuccess: () -> Unit // Optional: For navigation
) {
    val diaperType by viewModel.diaperType.collectAsState()
    val poopColor by viewModel.poopColor.collectAsState()
    val poopConsistency by viewModel.poopConsistency.collectAsState()
    val notes by viewModel.notes.collectAsState()
    val timestamp by viewModel.timestamp.collectAsState()

    val isSaving by viewModel.isSaving.collectAsState()
    val saveSuccess by viewModel.saveSuccess.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    val selectedBaby by babyViewModel.selectedBaby.collectAsState()
    val currentBabyId = selectedBaby?.id

    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope() // For launching coroutines for snackbar

    // Time picker
    val calendar = remember { Calendar.getInstance().apply { time = timestamp } }
    val context = LocalContext.current
    val timePickerDialog = remember {
        TimePickerDialog(
            context,
            { _, hour, minute ->
                calendar.set(Calendar.HOUR_OF_DAY, hour)
                calendar.set(Calendar.MINUTE, minute)
                viewModel.onTimestampChanged(calendar.time)
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true
        )
    }

    LaunchedEffect(saveSuccess) {
        if (saveSuccess) {
            snackbarHostState.showSnackbar("Diaper event saved!", duration = SnackbarDuration.Short)
            // Consider what to reset in ViewModel or if ViewModel handles it
            viewModel.resetInputFields() // Clear the form
            viewModel.resetSaveSuccess() // Reset the flag
            // onSaveSuccess() // Call lambda to navigate or perform other actions
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Nouvelle couche", style = MaterialTheme.typography.headlineSmall)

            Spacer(modifier = Modifier.height(8.dp))

            // Diaper Content Selection
            DiaperTypeDropdown(
                selectedDiaperType = diaperType,
                onDiaperTypeSelected = { viewModel.onDiaperTypeChanged(it) }
            )

            if (diaperType == DiaperType.DIRTY || diaperType == DiaperType.MIXED) {
                PoopColorDropdown(poopColor, viewModel::onPoopColorChanged)
                PoopConsistencyDropdown(poopConsistency, viewModel::onPoopConsistencyChanged)
            }

            OutlinedTextField(
                value = notes,
                onValueChange = viewModel::onNotesChanged,
                label = { Text("Notes (optionnel)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Heure : ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(timestamp)}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = { timePickerDialog.show() }) {
                    Text("Choisir l'heure")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (currentBabyId != null) viewModel.saveDiaperEvent(currentBabyId)
                    else scope.launch {
                        snackbarHostState.showSnackbar("Sélectionnez un bébé d'abord")
                    }
                },
                enabled = !isSaving && currentBabyId != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Enregistrement…")
                } else {
                    Text("Enregistrer")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaperTypeDropdown(
    selectedDiaperType: DiaperType,
    onDiaperTypeSelected: (DiaperType) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val diaperTypes = DiaperType.entries

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selectedDiaperType.name.replace("_", " ")
                .capitalizeWords(), // Format for display
            onValueChange = {}, // Not directly changeable, selection via dropdown
            label = { Text("Type de couche") },
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth() // Important for dropdown behavior
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            diaperTypes.forEach { type ->
                DropdownMenuItem(
                    text = { Text(type.name.replace("_", " ").capitalizeWords()) },
                    onClick = {
                        onDiaperTypeSelected(type)
                        expanded = false
                    }
                )
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PoopColorDropdown(
    selectedColor: PoopColor?,
    onColorSelected: (PoopColor) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val poopColorTypes = PoopColor.entries
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selectedColor?.displayName.orEmpty(),
            onValueChange = {},
            label = { Text("Couleur de la selle") },
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            poopColorTypes.forEach { color ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(16.dp)
                                    .background(color.colorValue, CircleShape)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(color.displayName)
                        }
                    },
                    onClick = {
                        onColorSelected(color)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PoopConsistencyDropdown(
    selectedConsistency: PoopConsistency?,
    onConsistencySelected: (PoopConsistency) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selectedConsistency?.displayName.orEmpty(),
            onValueChange = {},
            label = { Text("Consistance de la selle") },
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            PoopConsistency.values().forEach { consistency ->
                DropdownMenuItem(
                    text = { Text(consistency.displayName) },
                    onClick = {
                        onConsistencySelected(consistency)
                        expanded = false
                    }
                )
            }
        }
    }
}