package com.kouloundissa.twinstracker.presentation.feeding // [1]

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kouloundissa.twinstracker.data.BreastSide
import com.kouloundissa.twinstracker.data.FeedType
// Assuming BabyViewModel provides the selected baby's ID
import com.kouloundissa.twinstracker.presentation.viewmodel.BabyViewModel // Or however you get babyId
import kotlinx.coroutines.launch



@OptIn(ExperimentalMaterial3Api::class) // For ExposedDropdownMenuBox
@Composable
fun FeedingScreen(
    viewModel: FeedingViewModel = hiltViewModel(),
    babyViewModel: BabyViewModel = hiltViewModel() // To get the current babyId
    // onSaveSuccess: () -> Unit // Optional: Lambda to call when save is successful (e.g., to navigate back)
) {

    val feedType by viewModel.feedType.collectAsState()
    val amountMl by viewModel.amountMl.collectAsState()
    val durationMinutes by viewModel.durationMinutes.collectAsState()
    val breastSide by viewModel.breastSide.collectAsState()
    val notes by viewModel.notes.collectAsState()

    val isSaving by viewModel.isSaving.collectAsState()
    val saveSuccess by viewModel.saveSuccess.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    val selectedBaby by babyViewModel.selectedBaby.collectAsState()
    val currentBabyId = selectedBaby?.id

    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(saveSuccess) {
        if (saveSuccess) {
            snackbarHostState.showSnackbar("Feeding event saved!", duration = SnackbarDuration.Short)
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
                .verticalScroll(scrollState), // Make content scrollable
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            Text("Log Feeding", style = MaterialTheme.typography.headlineSmall)

            Spacer(modifier = Modifier.height(8.dp))

            // Feed Type Selection (Dropdown Example)
            FeedTypeDropdown(
                selectedFeedType = feedType,
                onFeedTypeSelected = { viewModel.onFeedTypeChanged(it) }
            )

            // Conditional Fields based on FeedType
            when (feedType) {
                FeedType.BREAST_MILK -> {
                    // Option for Pumped Milk (Amount) or Breastfeeding (Duration/Side)
                    Text("For pumped milk, enter amount. For breastfeeding, enter duration & side.")
                    OutlinedTextField(
                        value = amountMl,
                        onValueChange = viewModel::onAmountMlChanged,
                        label = { Text("Amount (ml) - Optional for Breastfeeding") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = durationMinutes,
                        onValueChange = viewModel::onDurationMinutesChanged,
                        label = { Text("Duration (minutes)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    BreastSideSelection(
                        selectedSide = breastSide,
                        onSideSelected = { viewModel.onBreastSideChanged(it) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                FeedType.FORMULA -> {
                    OutlinedTextField(
                        value = amountMl,
                        onValueChange = viewModel::onAmountMlChanged,
                        label = { Text("Amount (ml)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        isError = amountMl.toDoubleOrNull() == null && amountMl.isNotEmpty() // Basic validation
                    )
                }
                FeedType.SOLID -> {
                    OutlinedTextField(
                        value = amountMl, // Could be used for grams
                        onValueChange = viewModel::onAmountMlChanged,
                        label = { Text("Amount (e.g., grams, pieces) - Optional") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            OutlinedTextField(
                value = notes,
                onValueChange = viewModel::onNotesChanged,
                label = { Text("Notes (Optional)") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                singleLine = false
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (currentBabyId != null) {
                        viewModel.saveFeedingEvent(currentBabyId)
                    } else {
                        // Show error: No baby selected
                        scope.launch { // Requires a CoroutineScope, e.g. from rememberCoroutineScope()
                            snackbarHostState.showSnackbar("Please select a baby first.", duration = SnackbarDuration.Short)
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
                    Text("Save Feeding Event")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedTypeDropdown(
    selectedFeedType: FeedType,
    onFeedTypeSelected: (FeedType) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val feedTypes = FeedType.entries // Get all enum values

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selectedFeedType.name.replace("_", " ").capitalizeWords(), // Format for display
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
            feedTypes.forEach { type ->
                DropdownMenuItem(
                    text = { Text(type.name.replace("_", " ").capitalizeWords()) },
                    onClick = {
                        onFeedTypeSelected(type)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun BreastSideSelection(
    selectedSide: BreastSide?,
    onSideSelected: (BreastSide?) -> Unit,
    modifier: Modifier = Modifier
) {
    val breastSides = BreastSide.values()
    var showNullOption = true // Decide if "None" or clearing selection is an option

    Column(modifier = modifier) {
        Text("Breast Side:", style = MaterialTheme.typography.bodyLarge)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            breastSides.forEach { side ->
                Button(
                    onClick = { onSideSelected(side) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedSide == side) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(side.name.capitalizeWords())
                }
            }
            if (showNullOption && selectedSide != null) { // Show a clear button if a side is selected
                Button(
                    onClick = { onSideSelected(null) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Text("Clear")
                }
            }
        }
    }
}

// Helper to capitalize words for display (e.g., BREAST_MILK -> Breast Milk)
fun String.capitalizeWords(): String = split('_').joinToString(" ") { it.lowercase().replaceFirstChar(Char::titlecase) }

