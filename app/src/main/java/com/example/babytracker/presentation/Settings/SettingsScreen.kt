package com.example.babytracker.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.babytracker.data.Baby
import com.example.babytracker.data.Family
import com.example.babytracker.data.Theme
import com.example.babytracker.presentation.viewmodel.AuthViewModel
import com.example.babytracker.presentation.viewmodel.BabyViewModel
import com.example.babytracker.presentation.viewmodel.FamilyViewModel
import kotlinx.coroutines.flow.map


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    listState: LazyListState,
    authViewModel: AuthViewModel = hiltViewModel(),
    babyViewModel: BabyViewModel = hiltViewModel(),
    familyViewModel: FamilyViewModel = hiltViewModel()
) {
    val state by authViewModel.state.collectAsState()
    val profile = state.userProfile
    val babies by babyViewModel.babies.collectAsState()
    val defaultBaby by babyViewModel.defaultBaby.collectAsState()
    val families by familyViewModel.families.collectAsState()
    val isAuthLoading by authViewModel.state.map { it.isLoading }.collectAsState(false)
    val isBabyLoading by babyViewModel.isLoading.collectAsState()
    val isFamilyLoading by familyViewModel.state.map { it.isLoading }.collectAsState(false)
    val isLoading = remember(isAuthLoading, isBabyLoading, isFamilyLoading) {
        isAuthLoading || isBabyLoading || isFamilyLoading
    }

    // Local editable state
    var displayName by remember(profile) { mutableStateOf(profile?.displayName.orEmpty()) }
    var themeChoice by remember(profile) { mutableStateOf(profile?.theme?.name.orEmpty()) }
    var notificationsEnabled by remember(profile) {
        mutableStateOf(profile?.notificationsEnabled == true)
    }
    var localeChoice by remember(profile) { mutableStateOf(profile?.locale.orEmpty()) }
    var defaultBabyName by remember(defaultBaby) { mutableStateOf(defaultBaby?.name.orEmpty()) }

    Scaffold(
        snackbarHost = { SnackbarHost(remember { SnackbarHostState() }) },
        modifier = Modifier.fillMaxSize()
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // — User Profile Section —
            item {
                SectionTitle("Mon profil")
                GlassCard {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        ReadOnlyField("Email", profile?.email.orEmpty())

                        EditableField("Nom affiché", displayName, !isLoading) {
                            displayName = it
                        }
                        DropdownSetting("Thème", Theme.values().map { it.name }, themeChoice, !isLoading) {
                            themeChoice = it
                        }
                        ToggleSetting("Notifications", notificationsEnabled, !isLoading) {
                            notificationsEnabled = it
                        }
                        DropdownSetting("Langue", listOf("fr","en","es","de"), localeChoice, !isLoading) {
                            localeChoice = it
                        }
                        DropdownSetting(
                            "Bébé par défaut",
                            babies.map { it.name }.ifEmpty { listOf("Aucun bébé") },
                            defaultBabyName,
                            !isLoading && babies.isNotEmpty()
                        ) {
                            defaultBabyName = it
                        }

                        Button(
                            onClick = {
                                val updates = mapOf(
                                    "displayName" to displayName,
                                    "theme" to themeChoice,
                                    "notificationsEnabled" to notificationsEnabled,
                                    "locale" to localeChoice,
                                    "defaultBabyId" to babies.find { it.name == defaultBabyName }?.id
                                )
                                authViewModel.updateUserProfile(updates)
                            },
                            enabled = !isLoading,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Enregistrer les modifications")
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                authViewModel.logout()
                                navController.navigate("auth") {
                                    popUpTo("settings") { inclusive = true }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Déconnexion")
                        }
                    }
                }
            }

            // — Baby Settings Section (unchanged) —
            item {
                SectionTitle("Mon bébé")
                ParentsCard(babyViewModel)
            }

            // — Family Management Section —
            item {
                SectionTitle("Gestion de la famille")
                GlassCard {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        // List existing families
                        Text("Vos familles", style = MaterialTheme.typography.titleSmall)
                        if (families.isEmpty()) {
                            Text("Aucune famille trouvée", color = Color.Gray)
                        } else {
                            families.forEach { family ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { familyViewModel.selectFamily(family) }
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(family.name, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }

                        // Create / Edit family
                        val selectedFamily by familyViewModel.selectedFamily.collectAsState()
                        var familyName by remember(selectedFamily) { mutableStateOf(selectedFamily?.name.orEmpty()) }

                        OutlinedTextField(
                            value = familyName,
                            onValueChange = { familyName = it },
                            label = { Text("Nom de la famille") },
                            singleLine = true,
                            enabled = !isLoading,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Button(
                            onClick = {
                                val fam = selectedFamily?.copy(name = familyName)
                                    ?: Family(name = familyName)
                                familyViewModel.createOrUpdateFamily(fam)
                            },
                            enabled = familyName.isNotBlank() && !isLoading,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (selectedFamily == null) "Créer la famille" else "Mettre à jour la famille")
                        }
                        selectedFamily?.let {
                            TextButton(
                                onClick = { familyViewModel.deleteFamily(it.id) },
                                enabled = !isLoading
                            ) {
                                Text("Supprimer la famille", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }

        if (isLoading) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }
}
//————————————————————————————————————————————————————————————————————————————————
// Reusable Components
//————————————————————————————————————————————————————————————————————————————————

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun GlassCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(16.dp),
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReadOnlyField(label: String, value: String) {
    Text(label, style = MaterialTheme.typography.labelLarge)
    Text(value, style = MaterialTheme.typography.bodyLarge)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditableField(
    label: String,
    text: String,
    enabled: Boolean,
    onValueConfirmed: (String) -> Unit
) {
    val focusManager = LocalFocusManager.current
    var localText by remember(text) { mutableStateOf(text) }
    var hasFocus by remember { mutableStateOf(false) }

    Text(label, style = MaterialTheme.typography.labelLarge)
    OutlinedTextField(
        value = localText,
        onValueChange = { localText = it },
        singleLine = true,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { state ->
                if (hasFocus && !state.isFocused && localText != text) {
                    onValueConfirmed(localText)
                }
                hasFocus = state.isFocused
            },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = {
            if (localText != text) onValueConfirmed(localText)
            focusManager.clearFocus()
        })
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownSetting(
    label: String,
    options: List<String>,
    selected: String,
    enabled: Boolean,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Text(label, style = MaterialTheme.typography.labelLarge)
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        TextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun ToggleSetting(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, onCheckedChange = onToggle, enabled = enabled)
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

@Composable
private fun FullScreenLoader() {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ParentsCard(babyViewModel: BabyViewModel) {
    val babies by babyViewModel.babies.collectAsState()
    val parents by babyViewModel.parents.collectAsState()
    val isLoading by babyViewModel.isLoading.collectAsState()
    val error by babyViewModel.errorMessage.collectAsState()
    var selectedBaby by remember { mutableStateOf<Baby?>(null) }
    var expanded by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var emailError by remember { mutableStateOf<String?>(null) }
    var previousLoading by remember { mutableStateOf(false) }

    fun validateEmail(input: String) =
        android.util.Patterns.EMAIL_ADDRESS.matcher(input).matches()

    // Clear email when loading finishes successfully
    LaunchedEffect(isLoading, error) {
        if (previousLoading && !isLoading && error == null) {
            email = ""
            emailError = null
        }
        previousLoading = isLoading
    }

    GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Baby selector
            DropdownSetting(
                label = "Bébé",
                options = babies.map { it.name }.ifEmpty { listOf("Aucun bébé disponible") },
                selected = selectedBaby?.name ?: "Sélectionner un bébé",
                enabled = !isLoading && babies.isNotEmpty()
            ) { name ->
                selectedBaby = babies.find { it.name == name }
                selectedBaby?.id?.let { babyViewModel.loadParents(it) }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                thickness = DividerDefaults.Thickness,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
            )

            // Invite co-parent section
            Text("Inviter un coparent", style = MaterialTheme.typography.titleSmall)

            OutlinedTextField(
                value = email,
                onValueChange = {
                    email = it
                    emailError = if (it.isBlank() || validateEmail(it)) null else "Email invalide"
                },
                label = { Text("Email coparent") },
                isError = emailError != null,
                singleLine = true,
                enabled = !isLoading,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    if (email.isNotBlank() && emailError == null) {
                        babyViewModel.inviteParent(selectedBaby!!.id, email.trim())
                    }
                }),
                modifier = Modifier.fillMaxWidth()
            )

            emailError?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 16.dp)
                )
            }

            Button(
                onClick = {
                    babyViewModel.inviteParent(selectedBaby!!.id, email.trim())
                },
                enabled = email.isNotBlank() && emailError == null && !isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Inviter")
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Parents list
            Text("Parents actuels", style = MaterialTheme.typography.titleSmall)

            if (isLoading && parents.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (parents.isEmpty()) {
                Text(
                    text = "Aucun parent pour ce bébé",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    parents.forEach { user ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp)
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
                        }
                    }
                }
            }

            error?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
