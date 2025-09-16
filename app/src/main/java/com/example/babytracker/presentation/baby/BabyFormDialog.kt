package com.example.babytracker.presentation.baby

import android.app.DatePickerDialog
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.babytracker.data.Baby
import com.example.babytracker.data.BloodType
import com.example.babytracker.data.Gender
import com.example.babytracker.presentation.event.IconSelector
import com.example.babytracker.presentation.viewmodel.BabyViewModel
import com.example.babytracker.presentation.viewmodel.EventViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun BabyFormDialog(
    babyToEdit: Baby? = null,
    onBabyUpdated: () -> Unit,
    onCancel: () -> Unit,
    viewModel: BabyViewModel = hiltViewModel(),
    eventViewModel: EventViewModel = hiltViewModel(),
) {
    val isEditMode = babyToEdit != null

    var nameError by remember { mutableStateOf(false) }
    var weightError by remember { mutableStateOf<String?>(null) }
    var lengthError by remember { mutableStateOf<String?>(null) }
    var headCircError by remember { mutableStateOf<String?>(null) }
    var timeError by remember { mutableStateOf<String?>(null) }

    val isLoading by viewModel.isLoading.collectAsState()
    val repoError by viewModel.errorMessage.collectAsState()

    var saveClicked by remember { mutableStateOf(false) }
    var deleteClicked by remember { mutableStateOf(false) }
    var wasLoading by remember { mutableStateOf(false) }

    val openDeleteDialog = remember { mutableStateOf(false) }

    LaunchedEffect(isLoading, repoError) {
        if ((saveClicked || deleteClicked) && wasLoading && !isLoading && repoError == null) {
            onBabyUpdated()
        }
        wasLoading = isLoading
    }
    // Load current baby
    val babies by viewModel.babies.collectAsState()
    val baby = remember(babies) { babyToEdit?.let { b -> babies.find { it.id == b.id } } }

    // Local editable state
    var name by remember { mutableStateOf(baby?.name.orEmpty()) }
    var birthDate by remember {
        mutableStateOf(Calendar.getInstance().apply {
            baby?.let { timeInMillis = it.birthDate }
        })
    }
    var gender by remember { mutableStateOf(baby?.gender ?: Gender.UNKNOWN) }
    var weight by remember { mutableStateOf(baby?.birthWeightKg?.toString().orEmpty()) }
    var lengthCm by remember { mutableStateOf(baby?.birthLengthCm?.toString().orEmpty()) }
    var headCirc by remember {
        mutableStateOf(
            baby?.birthHeadCircumferenceCm?.toString().orEmpty()
        )
    }
    var birthTime by remember { mutableStateOf(baby?.birthTime.orEmpty()) }
    var bloodType by remember(baby) {
        // Map the baby's bloodType string to the BloodType enum, or default to UNKNOWN
        val initial: BloodType = baby?.bloodType?.let { btString ->
            BloodType.entries.firstOrNull { enum -> enum == btString }
        } ?: BloodType.UNKNOWN

        mutableStateOf(initial)
    }
    var allergies by remember { mutableStateOf(baby?.allergies?.joinToString(", ").orEmpty()) }
    var conditions by remember {
        mutableStateOf(
            baby?.medicalConditions?.joinToString(", ").orEmpty()
        )
    }
    var pediatricianContact by remember { mutableStateOf(baby?.pediatricianContact.orEmpty()) }
    var notes by remember { mutableStateOf(baby?.notes.orEmpty()) }

    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    val birthDateStr = dateFormat.format(birthDate.time)

    val datePicker = DatePickerDialog(
        context,
        { _, year, month, day ->
            birthDate.set(year, month, day)
        },
        birthDate.get(Calendar.YEAR),
        birthDate.get(Calendar.MONTH),
        birthDate.get(Calendar.DAY_OF_MONTH)
    )
    val contactLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickContact()
    ) { uri ->
        uri?.let { it ->
            val cursor = context.contentResolver.query(it, arrayOf(ContactsContract.Contacts.DISPLAY_NAME), null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    pediatricianContact = it.getString(0)
                }
            }
        }
    }
    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text(
                text = if (isEditMode) "Edit Baby" else "Create Baby",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Box(
                Modifier
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Column {
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            if (nameError) nameError = false
                        },
                        label = { Text("Baby Name*") },
                        isError = nameError,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (nameError) Text("Name is required", color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(12.dp))

                    Button(onClick = { datePicker.show() }) {
                        Text("Birth Date: $birthDateStr")
                    }
                    Spacer(Modifier.height(12.dp))

                    IconSelector(
                        title = "Gender",
                        options = Gender.entries,
                        selected = gender,
                        onSelect = { gender = it },
                        getIcon = { it.icon },
                        getLabel = { it.displayName }
                    )
                    Spacer(Modifier.height(12.dp))

                    NumericField("Weight (kg)", weight, { weight = it }, weightError) { weightError = it }
                    Spacer(Modifier.height(8.dp))
                    NumericField("Length (cm)", lengthCm, { lengthCm = it }, lengthError) { lengthError = it }
                    Spacer(Modifier.height(8.dp))
                    NumericField("Head Circumference (cm)", headCirc, { headCirc = it }, headCircError) { headCircError = it }
                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = birthTime,
                        onValueChange = { input ->
                            val filtered = input.filter { it.isDigit() || it == ':' }.take(5)
                            birthTime = filtered
                            timeError = when {
                                filtered.isBlank() -> null
                                !filtered.matches(Regex("^([01]?\\d|2[0-3]):[0-5]\\d$")) -> "Invalid time (HH:mm)"
                                else -> null
                            }
                        },
                        label = { Text("Birth Time (HH:mm)") },
                        isError = timeError != null,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    timeError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Spacer(Modifier.height(8.dp))

                    IconSelector(
                        title = "Blood Type",
                        options = BloodType.entries.toList(),
                        selected = bloodType,
                        onSelect = { bloodType = it },
                        getIcon = { it.icon },
                        getLabel = { it.name }
                    )
                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = allergies,
                        onValueChange = { allergies = it },
                        label = { Text("Allergies (comma separated)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = conditions,
                        onValueChange = { conditions = it },
                        label = { Text("Medical Conditions (comma separated)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = pediatricianContact,
                        onValueChange = { },
                        label = { Text("Pediatrician Contact") },
                        readOnly = true,
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = { contactLauncher.launch(null) }) {
                                Icon(Icons.Default.Person, contentDescription = "Pick Contact")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Notes") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                    )

                    repoError?.let {
                        Spacer(Modifier.height(16.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isBlank()) {
                        nameError = true
                        return@TextButton
                    }
                    saveClicked = true
                    if (isEditMode) {
                        viewModel.updateBaby(
                            id = babyToEdit!!.id,
                            name = name.trim(),
                            birthDate = birthDate.timeInMillis,
                            gender = gender,
                            birthWeightKg = weight.toDoubleOrNull(),
                            birthLengthCm = lengthCm.toDoubleOrNull(),
                            birthHeadCircumferenceCm = headCirc.toDoubleOrNull(),
                            birthTime = birthTime.ifBlank { null },
                            bloodType = bloodType,
                            allergies = allergies.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                            medicalConditions = conditions.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                            pediatricianContact = pediatricianContact.ifBlank { null },
                            notes = notes.ifBlank { null }
                        )
                    } else {
                        viewModel.addBaby(
                            name = name.trim(),
                            birthDate = birthDate.timeInMillis,
                            gender = gender
                        )
                    }
                },
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (isEditMode) "Saving..." else "Creating...")
                } else {
                    Text(if (isEditMode) "Save" else "Create")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("Cancel")
            }
        }
    )

    // Delete confirmation dialog only in edit mode
    if (isEditMode && openDeleteDialog.value) {
        val eventCounts = remember { eventViewModel.getEventCounts() }
        val totalEvents = eventCounts.values.sum()
        AlertDialog(
            onDismissRequest = { openDeleteDialog.value = false },
            title = { Text("Delete ${baby?.name.orEmpty()}") },
            text = {
                Column {
                    Text("This action is irreversible.")
                    if (totalEvents > 0) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "⚠️ $totalEvents associated event(s) will also be deleted.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteBaby(babyToEdit!!.id)
                    openDeleteDialog.value = false
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { openDeleteDialog.value = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}



@Composable
fun NumericField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    errorMsg: String?,
    setError: (String?) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = { new ->
            // Allow only digits and at most one dot
            val filtered = new.filter { it.isDigit() || it == '.' }
            val cleaned = if (filtered.count { it == '.' } > 1) value else filtered
            onValueChange(cleaned)
            setError(
                if (cleaned.isNotBlank() && cleaned.toDoubleOrNull() == null)
                    "Nombre invalide" else null
            )
        },
        label = { Text(label) },
        isError = errorMsg != null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth()
    )
    errorMsg?.let { Text(it, color = MaterialTheme.colorScheme.error) }
}