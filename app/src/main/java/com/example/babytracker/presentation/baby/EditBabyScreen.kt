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
fun EditBabyScreen(
    babyId: String,
    onBabyUpdated: () -> Unit,
    onCancel: () -> Unit,
    viewModel: BabyViewModel = hiltViewModel()
) {

    var showError by remember { mutableStateOf(false) }
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    var saveClicked by remember { mutableStateOf(false) }
    var deleteClicked by remember { mutableStateOf(false) }
    var wasLoading by remember { mutableStateOf(false) }

    val openDeleteDialog = remember { mutableStateOf(false) }

    LaunchedEffect(isLoading, errorMessage) {
        if (saveClicked && wasLoading && !isLoading && errorMessage == null) {
            onBabyUpdated()
        }
        if (deleteClicked && wasLoading && !isLoading && errorMessage == null) {
            onBabyUpdated()
        }
        wasLoading = isLoading
    }

    // Charger le baby courant
    val babies by viewModel.babies.collectAsState()
    val baby = remember(babies) { babies.find { it.id == babyId } }

    // États locaux initialisés avec les valeurs existantes
    var name by remember { mutableStateOf(baby?.name.orEmpty()) }
    var birthDate by remember {
        mutableStateOf(Calendar.getInstance().apply {
            baby?.let { timeInMillis = it.birthDate }
        })
    }
    var gender by remember { mutableStateOf(baby?.gender ?: Gender.UNKNOWN) }

    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    val birthDateString = dateFormat.format(birthDate.time)

    val datePickerDialog = DatePickerDialog(
        context,
        { _, year, month, day ->
            birthDate.set(year, month, day)
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
        Text("Modifier un bébé", style = MaterialTheme.typography.headlineMedium)

        Spacer(Modifier.height(24.dp))

        // Champ Nom
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

        Spacer(Modifier.height(16.dp))

        // Sélecteur de date
        Button(onClick = { datePickerDialog.show() }) {
            Text("Date de naissance : $birthDateString")
        }

        Spacer(Modifier.height(16.dp))

        // Dropdown Genre
        GenderDropdown(
            gender = gender,
            onGenderSelected = { gender = it },
            genders = Gender.entries.toTypedArray()
        )

        Spacer(Modifier.height(32.dp))

        // Message d’erreur éventuel
        errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
        }

        // Boutons Annuler / Enregistrer
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
                    // 3. Appel à l’update
                    viewModel.updateBaby(
                        id = babyId,
                        name = name.trim(),
                        birthDate = birthDate.timeInMillis,
                        gender = gender
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

            Spacer(Modifier.width(8.dp))
            OutlinedButton(
                onClick = { openDeleteDialog.value = true },
                enabled = !isLoading
            ) {
                Text("Supprimer", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    // Dialogue de confirmation de suppression
    if (openDeleteDialog.value) {
        AlertDialog(
            onDismissRequest = { openDeleteDialog.value = false },
            title = { Text("Supprimer le bébé") },
            text = { Text("Êtes-vous sûr de vouloir supprimer ce bébé ? Cette action est irréversible.") },
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
