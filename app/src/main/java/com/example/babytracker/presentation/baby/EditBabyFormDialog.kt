package com.example.babytracker.presentation.baby

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.babytracker.data.Event
import com.example.babytracker.data.Gender
import com.example.babytracker.presentation.feeding.capitalizeWords
import com.example.babytracker.presentation.viewmodel.BabyViewModel
import com.example.babytracker.presentation.viewmodel.EventViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun EditBabyFormDialog(
    babyId: String,
    onBabyUpdated: () -> Unit,
    onCancel: () -> Unit,
    viewModel: BabyViewModel = hiltViewModel(),
    eventViewModel: EventViewModel = hiltViewModel(),
) {

    var showError by remember { mutableStateOf(false) }
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    var saveClicked by remember { mutableStateOf(false) }
    var deleteClicked by remember { mutableStateOf(false) }
    var wasLoading by remember { mutableStateOf(false) }

    val openDeleteDialog = remember { mutableStateOf(false) }

    LaunchedEffect(isLoading, errorMessage) {
        if ((saveClicked || deleteClicked) && wasLoading && !isLoading && errorMessage == null) {
            onBabyUpdated()
        }
        wasLoading = isLoading
    }
    // Load current baby
    val babies by viewModel.babies.collectAsState()
    val baby = remember(babies) { babies.find { it.id == babyId } }

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
    var bloodType by remember { mutableStateOf(baby?.bloodType.orEmpty()) }
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

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Modifier un bébé", style = MaterialTheme.typography.headlineSmall) },
        text = {
            // Constrain height and enable vertical scrolling
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
                            if (showError) showError = false
                        },
                        label = { Text("Nom du bébé*") },
                        isError = showError,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (showError) {
                        Text("Le nom est obligatoire", color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.height(12.dp))

                    Button(onClick = { datePicker.show() }) {
                        Text("Date de naissance : $birthDateStr")
                    }
                    Spacer(Modifier.height(12.dp))

                    GenderDropdown(
                        selectedGender = gender,
                        onGenderSelected = { gender = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = weight,
                        onValueChange = { weight = it },
                        label = { Text("Poids à la naissance (kg)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = lengthCm,
                        onValueChange = { lengthCm = it },
                        label = { Text("Taille à la naissance (cm)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = headCirc,
                        onValueChange = { headCirc = it },
                        label = { Text("Circonférence tête (cm)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = birthTime,
                        onValueChange = { birthTime = it },
                        label = { Text("Heure de naissance") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = bloodType,
                        onValueChange = { bloodType = it },
                        label = { Text("Groupe sanguin") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = allergies,
                        onValueChange = { allergies = it },
                        label = { Text("Allergies (séparées par ,)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = conditions,
                        onValueChange = { conditions = it },
                        label = { Text("Conditions médicales (séparées par ,)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = pediatricianContact,
                        onValueChange = { pediatricianContact = it },
                        label = { Text("Contact pédiatre") },
                        singleLine = true,
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

                    errorMessage?.let {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isBlank()) {
                        showError = true
                        return@TextButton
                    }
                    saveClicked = true
                    viewModel.updateBaby(
                        id = babyId,
                        name = name.trim(),
                        birthDate = birthDate.timeInMillis,
                        gender = gender,
                        birthWeightKg = weight.toDoubleOrNull(),
                        birthLengthCm = lengthCm.toDoubleOrNull(),
                        birthHeadCircumferenceCm = headCirc.toDoubleOrNull(),
                        birthTime = birthTime.ifBlank { null },
                        bloodType = bloodType.ifBlank { null },
                        allergies = allergies.split(",").map { it.trim() }
                            .filter { it.isNotEmpty() },
                        medicalConditions = conditions.split(",").map { it.trim() }
                            .filter { it.isNotEmpty() },
                        pediatricianContact = pediatricianContact.ifBlank { null },
                        notes = notes.ifBlank { null }
                    )
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
                    Text("Mise à jour…")
                } else {
                    Text("Enregistrer")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("Annuler")
            }
        }
    )

    val eventCounts = remember { eventViewModel.getEventCounts() }
    val totalEvents = eventCounts.values.sum()
    // Dialogue de confirmation de suppression
    if (openDeleteDialog.value) {
        AlertDialog(
            onDismissRequest = { openDeleteDialog.value = false },
            title = { Text("Supprimer ${baby?.name.orEmpty()}") },
            text = {
                Column {
                    Text("Cette action est irréversible.")
                    if (totalEvents > 0) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "⚠️ $totalEvents événement(s) lié(s) seront également supprimés.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    deleteClicked = true
                    saveClicked = false
                    openDeleteDialog.value = false
                    viewModel.deleteBaby(babyId)
                }) {
                    Text("Supprimer", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { openDeleteDialog.value = false }) {
                    Text("Annuler")
                }
            }
        )
    }
}
