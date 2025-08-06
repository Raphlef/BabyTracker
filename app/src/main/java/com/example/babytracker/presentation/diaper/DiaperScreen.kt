package com.example.babytracker.presentation.diaper

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.babytracker.data.DiaperType
import com.example.babytracker.presentation.feeding.capitalizeWords
// Import other necessary enums if you have a more detailed model like PoopContent, PoopColor
import com.example.babytracker.presentation.viewmodel.BabyViewModel
import com.example.babytracker.presentation.viewmodel.DiaperViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaperScreen(
    viewModel: DiaperViewModel = hiltViewModel(),
    babyViewModel: BabyViewModel = hiltViewModel()
    // onSaveSuccess: () -> Unit // Optional: For navigation
) {
    val diaperType by viewModel.diaperType.collectAsState()
    val notes by viewModel.notes.collectAsState()
    val color by viewModel.color.collectAsState()
    val consistency by viewModel.consistency.collectAsState()

    val isSaving by viewModel.isSaving.collectAsState()
    val saveSuccess by viewModel.saveSuccess.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    val selectedBaby by babyViewModel.selectedBaby.collectAsState()
    val currentBabyId = selectedBaby?.id

    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope() // For launching coroutines for snackbar

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
            Text("Log Diaper Change", style = MaterialTheme.typography.headlineSmall)

            Spacer(modifier = Modifier.height(8.dp))

            // Diaper Content Selection
            DiaperTypeDropdown(
                selectedDiaperType = diaperType,
                onDiaperTypeSelected ={ viewModel.onDiaperTypeChanged(it) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (currentBabyId != null) {
                            viewModel.saveDiaperEvent(currentBabyId)
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
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Saving...")
                } else {
                    Text("Save Diaper Event")
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
            value = selectedDiaperType.name.replace("_", " ").capitalizeWords(), // Format for display
            onValueChange = {}, // Not directly changeable, selection via dropdown
            label = { Text("Feed Type") },
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth() // Important for dropdown behavior
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