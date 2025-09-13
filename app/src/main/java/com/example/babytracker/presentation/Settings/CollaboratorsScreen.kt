package com.example.babytracker.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.babytracker.data.Baby
import com.example.babytracker.data.User
import com.example.babytracker.presentation.viewmodel.BabyViewModel
import com.example.babytracker.ui.components.TopAppBar
import kotlin.text.ifEmpty


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollaboratorsScreen(
    navController: NavController,
    babyViewModel: BabyViewModel = hiltViewModel()
) {
    val babies by babyViewModel.babies.collectAsState()
    val collaborators by babyViewModel.collaborators.collectAsState()
    val isLoading by babyViewModel.isLoading.collectAsState()
    val errorMessage by babyViewModel.errorMessage.collectAsState()

    var selectedBaby by remember { mutableStateOf<Baby?>(null) }
    var expandedBabies by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var emailError by remember { mutableStateOf<String?>(null) }
    var previousLoading by remember { mutableStateOf(false) }


    fun isValidEmail(input: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(input).matches()
    }

    LaunchedEffect(selectedBaby) {
        selectedBaby?.let { baby ->
            babyViewModel.loadCollaborators(baby.id)
        }
    }

    LaunchedEffect(isLoading, errorMessage) {
        // When loading finishes and there is no error, clear the email
        if (previousLoading && !isLoading && errorMessage == null) {
            email = ""
            emailError = null
        }
        previousLoading = isLoading
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "Collaborateurs",
                navController = navController,
                showBackButton = true
            )
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Sélecteur de bébé
                Text("Bébé :", style = MaterialTheme.typography.labelLarge)
                ExposedDropdownMenuBox(
                    expanded = expandedBabies,
                    onExpandedChange = { expandedBabies = !expandedBabies }
                ) {
                    TextField(
                        value = selectedBaby?.name ?: "Sélectionner un bébé",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedBabies)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        enabled = !isLoading
                    )
                    ExposedDropdownMenu(
                        expanded = expandedBabies,
                        onDismissRequest = { expandedBabies = false }
                    ) {
                        if (babies.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("Aucun bébé disponible") },
                                onClick = {}
                            )
                        } else {
                            babies.forEach { baby ->
                                DropdownMenuItem(
                                    text = { Text(baby.name) },
                                    onClick = {
                                        selectedBaby = baby
                                        expandedBabies = false
                                    }
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)

                // Affichage conditionnel du reste si un bébé est sélectionné
                if (selectedBaby != null) {
                    // Invitation
                    OutlinedTextField(
                        value = email,
                        onValueChange = {
                            email = it
                            emailError =
                                if (it.isEmpty() || isValidEmail(it)) null else "Email invalide"
                        },
                        label = { Text("Email collaborateur") },
                        isError = emailError != null,
                        singleLine = true,
                        enabled = !isLoading,
                        keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            if (email.isNotBlank() && emailError == null && selectedBaby != null) {
                                babyViewModel.inviteCollaborator(selectedBaby!!.id, email.trim())
                            }
                        }),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (emailError != null) {
                        Text(
                            text = emailError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }
                    Button(
                        onClick = {
                            if (selectedBaby != null) {
                                babyViewModel.inviteCollaborator(selectedBaby!!.id, email.trim())
                            }
                        },
                        enabled = email.isNotBlank() && !isLoading && selectedBaby != null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Inviter")
                    }

                    // Liste des collaborateurs
                    Text("Collaborateurs", style = MaterialTheme.typography.titleMedium)
                    if (isLoading && collaborators.isEmpty()) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else if (collaborators.isEmpty()) {
                        Text("Aucun collaborateur", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(collaborators) { user ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp)
                                ) {
                                    Text(
                                        text = user.displayName.ifBlank { user.email },
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = user.email,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Divider()
                            }
                        }
                    }
                } else {
                    Text(
                        "Veuillez sélectionner un bébé pour gérer ses collaborateurs",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                // Message d'erreur
                errorMessage?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // Overlay global de chargement
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
        }
    }
}