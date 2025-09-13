package com.example.babytracker.presentation.baby

import android.app.DatePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.babytracker.data.Gender
import com.example.babytracker.presentation.viewmodel.BabyViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun AddBabyScreen(
    onBabyAdded: () -> Unit,
    onCancel: () -> Unit,
    viewModel: BabyViewModel = hiltViewModel()
) {
    var name by remember { mutableStateOf("") }
    var birthDate by remember { mutableStateOf(Calendar.getInstance()) }
    var gender by remember { mutableStateOf(Gender.UNKNOWN) }

    var showError by remember { mutableStateOf(false) }
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    var saveClicked by remember { mutableStateOf(false) }
    var wasLoading by remember { mutableStateOf(false) }

    LaunchedEffect(isLoading, errorMessage) {
        if (saveClicked && wasLoading && !isLoading && errorMessage == null) {
            onBabyAdded()
        }
        wasLoading = isLoading
    }
    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    val birthDateString = dateFormat.format(birthDate.time)

    val datePickerDialog = DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            birthDate.set(year, month, dayOfMonth)
        },
        birthDate.get(Calendar.YEAR),
        birthDate.get(Calendar.MONTH),
        birthDate.get(Calendar.DAY_OF_MONTH)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Ajouter un bébé", style = MaterialTheme.typography.headlineMedium)

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = name,
            onValueChange = {
                name = it
                if (showError) showError = false
            },
            label = { Text("Nom du bébé") },
            isError = showError,
            modifier = Modifier.fillMaxWidth()
        )
        if (showError) {
            Text("Le nom est obligatoire", color = MaterialTheme.colorScheme.error)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = { datePickerDialog.show() }) {
            Text("Date de naissance : $birthDateString")
        }

        Spacer(modifier = Modifier.height(16.dp))

        GenderDropdown(
            gender = gender,
            onGenderSelected = { gender = it },
            genders = Gender.values()
        )

        Spacer(modifier = Modifier.height(32.dp))

        if (errorMessage != null) {
            Text(
                errorMessage!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            OutlinedButton(onClick = onCancel) {
                Text("Annuler")
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    if (name.isBlank()) {
                        showError = true
                        return@Button
                    }
                    saveClicked = true
                    // Crée le bébé et sélectionne-le
                    viewModel.addBaby(name.trim(), birthDate.timeInMillis, gender)

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
                    Text("Enregistrement…")
                } else {
                    Text("Enregistrer")
                }
            }
        }
    }
    val babies by viewModel.babies.collectAsState()
    val selectedBaby by viewModel.selectedBaby.collectAsState()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenderDropdown(
    gender: Gender,
    onGenderSelected: (Gender) -> Unit,
    genders: Array<Gender>
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = gender.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() },
            onValueChange = {},
            label = { Text("Genre") },
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true } // << rendre le champ cliquable sur toute la zone
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth() // ← important
        ) {
            genders.forEach { g ->
                DropdownMenuItem(
                    text = {
                        Text(g.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() })
                    },
                    onClick = {
                        onGenderSelected(g)
                        expanded = false
                    }
                )
            }
        }
    }
}
