package com.example.babytracker.presentation.event

import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.babytracker.data.BreastSide
import com.example.babytracker.data.DiaperType
import com.example.babytracker.data.FeedType
import com.example.babytracker.data.PoopColor
import com.example.babytracker.data.PoopConsistency
import com.example.babytracker.data.event.EventFormState
import com.example.babytracker.data.event.EventType
import com.example.babytracker.presentation.diaper.DiaperTypeDropdown
import com.example.babytracker.presentation.diaper.PoopColorDropdown
import com.example.babytracker.presentation.diaper.PoopConsistencyDropdown
import com.example.babytracker.presentation.feeding.FeedTypeDropdown
import com.example.babytracker.presentation.feeding.capitalizeWords
import com.example.babytracker.presentation.viewmodel.EventViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventFormScreen(
    babyId: String,
    navController: NavController,
    viewModel: EventViewModel = hiltViewModel()
) {
    val formState by viewModel.formState.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val saveSuccess by viewModel.saveSuccess.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    val currentType = formState.eventType

    LaunchedEffect(saveSuccess) {
        if (saveSuccess) navController.popBackStack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add / Edit Event") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            // 1️⃣ Event Type Selector
            var typeDropdownExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = typeDropdownExpanded,
                onExpandedChange = { typeDropdownExpanded = !typeDropdownExpanded }
            ) {
                OutlinedTextField(
                    value = currentType.displayName,
                    onValueChange = {},
                    label = { Text("Event Type") },
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeDropdownExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = typeDropdownExpanded,
                    onDismissRequest = { typeDropdownExpanded = false }
                ) {
                    EventType.entries.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type.displayName) },
                            onClick = {
                                // Switch formState to the matching sealed subclass
                                viewModel.updateForm {
                                    when (type) {
                                        EventType.DIAPER  -> EventFormState.Diaper()
                                        EventType.FEEDING -> EventFormState.Feeding()
                                        EventType.SLEEP   -> EventFormState.Sleep()
                                        EventType.GROWTH  -> EventFormState.Growth()
                                    }
                                }
                                typeDropdownExpanded = false
                            }
                        )
                    }
                }
            }
            // 2️⃣ Then render the specific sub-form
            when (formState) {
                is EventFormState.Diaper -> {
                    val s = formState as EventFormState.Diaper
                    Text("Diaper Event", style = MaterialTheme.typography.headlineSmall)
                    DiaperTypeDropdown(
                        selectedDiaperType = s.diaperType,
                        onDiaperTypeSelected = { viewModel.updateForm { (this as EventFormState.Diaper).copy(diaperType = it) } }
                    )
                    if (s.diaperType == DiaperType.DIRTY || s.diaperType == DiaperType.MIXED) {
                        PoopColorDropdown(
                            selectedColor = s.poopColor,
                            onColorSelected = { viewModel.updateForm { (this as EventFormState.Diaper).copy(poopColor = it) } }
                        )
                        PoopConsistencyDropdown(
                            selectedConsistency = s.poopConsistency,
                            onConsistencySelected = { viewModel.updateForm { (this as EventFormState.Diaper).copy(poopConsistency = it) } }
                        )
                    }
                    OutlinedTextField(
                        value = s.notes,
                        onValueChange = { viewModel.updateForm { (this as EventFormState.Diaper).copy(notes = it) } },
                        label = { Text("Notes") },
                        modifier = Modifier.fillMaxWidth().height(100.dp)
                    )
                }
                is EventFormState.Sleep -> {
                    val s = formState as EventFormState.Sleep
                    Text("Sleep Event", style = MaterialTheme.typography.headlineSmall)

                    val context = LocalContext.current
                    val calStart = remember { Calendar.getInstance() }
                    val calEnd   = remember { Calendar.getInstance() }

                    LaunchedEffect(s.beginTime) { s.beginTime?.let { calStart.time = it } }
                    LaunchedEffect(s.endTime)   { s.endTime?.let   { calEnd.time   = it } }

                    fun computeDuration(begin: Date?, end: Date?): Long? =
                        if (begin != null && end != null)
                            ((end.time - begin.time) / 60000).coerceAtLeast(0L)
                        else null

                    // Start picker
                    val tpStart = remember {
                        TimePickerDialog(
                            context,
                            { _, h, m ->
                                calStart.set(Calendar.HOUR_OF_DAY, h)
                                calStart.set(Calendar.MINUTE, m)
                                val newBegin = calStart.time
                                viewModel.updateForm {
                                    val currentEnd = (this as EventFormState.Sleep).endTime
                                    copy(
                                        beginTime = newBegin,
                                        durationMinutes = computeDuration(newBegin, currentEnd)
                                    )
                                }
                            },
                            calStart.get(Calendar.HOUR_OF_DAY),
                            calStart.get(Calendar.MINUTE),
                            true
                        )
                    }

                    // End picker
                    val tpEnd = remember {
                        TimePickerDialog(
                            context,
                            { _, h, m ->
                                calEnd.set(Calendar.HOUR_OF_DAY, h)
                                calEnd.set(Calendar.MINUTE, m)
                                val newEnd = calEnd.time
                                viewModel.updateForm {
                                    val currentBegin = (this as EventFormState.Sleep).beginTime
                                    copy(
                                        endTime = newEnd,
                                        durationMinutes = computeDuration(currentBegin, newEnd)
                                    )
                                }
                            },
                            calEnd.get(Calendar.HOUR_OF_DAY),
                            calEnd.get(Calendar.MINUTE),
                            true
                        )
                    }

                    OutlinedTextField(
                        value = s.beginTime?.let { SimpleDateFormat("HH:mm", Locale.getDefault()).format(it) }.orEmpty(),
                        onValueChange = {}, readOnly = true,
                        label = { Text("Start Time") }, modifier = Modifier.fillMaxWidth()
                    )
                    Button(onClick = { tpStart.show() }) { Text("Choose Start") }

                    OutlinedTextField(
                        value = s.endTime?.let { SimpleDateFormat("HH:mm", Locale.getDefault()).format(it) }.orEmpty(),
                        onValueChange = {}, readOnly = true,
                        label = { Text("End Time") }, modifier = Modifier.fillMaxWidth()
                    )
                    Button(onClick = { tpEnd.show() }) { Text("Choose End") }

                    OutlinedTextField(
                        value = s.durationMinutes?.let { "$it min" }.orEmpty(),
                        onValueChange = {}, readOnly = true,
                        label = { Text("Duration") }, modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = s.notes,
                        onValueChange = {
                            viewModel.updateForm { (this as EventFormState.Sleep).copy(notes = it) }
                        },
                        label = { Text("Notes") },
                        modifier = Modifier.fillMaxWidth().height(100.dp)
                    )
                }
                is EventFormState.Feeding -> {
                    val s = formState as EventFormState.Feeding
                    Text("Feeding Event", style = MaterialTheme.typography.headlineSmall)

                    // Feed Type
                    FeedTypeDropdown(
                        selectedFeedType = s.feedType,
                        onFeedTypeSelected = {
                            viewModel.updateForm { (this as EventFormState.Feeding).copy(feedType = it) }
                        }
                    )

                    // Amount and Duration
                    OutlinedTextField(
                        value = s.amountMl,
                        onValueChange = {
                            viewModel.updateForm { (this as EventFormState.Feeding).copy(amountMl = it) }
                        },
                        label = { Text("Amount (ml)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = s.durationMin,
                        onValueChange = {
                            viewModel.updateForm { (this as EventFormState.Feeding).copy(durationMin = it) }
                        },
                        label = { Text("Duration (min)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Breast Side Selection
                    Text("Breast Side:", style = MaterialTheme.typography.bodyLarge)
                    val breastSides = BreastSide.entries
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        breastSides.forEach { side ->
                            Button(
                                onClick = {
                                    viewModel.updateForm { (this as EventFormState.Feeding).copy(breastSide = side) }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (s.breastSide == side)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else
                                        MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Text(side.name.replace("_", " ").capitalizeWords())
                            }
                        }
                        if (s.breastSide != null) {
                            Button(
                                onClick = {
                                    viewModel.updateForm { (this as EventFormState.Feeding).copy(breastSide = null) }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                                )
                            ) {
                                Text("Clear")
                            }
                        }
                    }

                    // Notes
                    OutlinedTextField(
                        value = s.notes,
                        onValueChange = {
                            viewModel.updateForm { (this as EventFormState.Feeding).copy(notes = it) }
                        },
                        label = { Text("Notes") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                    )
                }
                is EventFormState.Growth -> {
                    val s = formState as EventFormState.Growth
                    Text("Growth Event", style = MaterialTheme.typography.headlineSmall)
                    OutlinedTextField(
                        value = s.weightKg,
                        onValueChange = { viewModel.updateForm { (this as EventFormState.Growth).copy(weightKg = it) } },
                        label = { Text("Weight (kg)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = s.heightCm,
                        onValueChange = { viewModel.updateForm { (this as EventFormState.Growth).copy(heightCm = it) } },
                        label = { Text("Height (cm)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = s.headCircumferenceCm,
                        onValueChange = { viewModel.updateForm { (this as EventFormState.Growth).copy(headCircumferenceCm = it) } },
                        label = { Text("Head Circumference (cm)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = s.notes,
                        onValueChange = { viewModel.updateForm { (this as EventFormState.Growth).copy(notes = it) } },
                        label = { Text("Notes") },
                        modifier = Modifier.fillMaxWidth().height(100.dp)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = { viewModel.validateAndSave(babyId) },
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Saving…")
                } else {
                    Text("Save")
                }
            }
        }
    }
}
