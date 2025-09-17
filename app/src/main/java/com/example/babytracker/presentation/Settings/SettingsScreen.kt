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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import com.example.babytracker.data.PrivacyLevel
import com.example.babytracker.data.Theme
import com.example.babytracker.presentation.baby.BabyFormDialog
import com.example.babytracker.presentation.dashboard.BabySelectorRow
import com.example.babytracker.presentation.event.IconSelector
import com.example.babytracker.presentation.viewmodel.AuthViewModel
import com.example.babytracker.presentation.viewmodel.BabyViewModel
import com.example.babytracker.presentation.viewmodel.FamilyViewModel
import kotlinx.coroutines.flow.map


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    listState: LazyListState,
    contentPadding: PaddingValues = PaddingValues(),
    authViewModel: AuthViewModel = hiltViewModel(),
    babyViewModel: BabyViewModel = hiltViewModel(),
    familyViewModel: FamilyViewModel = hiltViewModel()
) {
    // Collect state
    val authState by authViewModel.state.collectAsState()
    val profile = authState.userProfile
    val babies by babyViewModel.babies.collectAsState()
    val defaultBaby by babyViewModel.defaultBaby.collectAsState()
    val families by familyViewModel.families.collectAsState()

    val isAuthLoading by authViewModel.state.map { it.isLoading }.collectAsState(false)
    val isBabyLoading by babyViewModel.isLoading.collectAsState(false)
    val isFamilyLoading by familyViewModel.state.map { it.isLoading }.collectAsState(false)
    val isLoading = isAuthLoading || isBabyLoading || isFamilyLoading

    // Local editable values
    var displayName by remember { mutableStateOf(profile?.displayName.orEmpty()) }
    var themeChoice by remember { mutableStateOf(profile?.theme?.name.orEmpty()) }
    var localeChoice by remember { mutableStateOf(profile?.locale.orEmpty()) }
    var notificationsEnabled by remember { mutableStateOf(profile?.notificationsEnabled == true) }

    val themeOptions = Theme.entries.toList()
    val localeOptions = listOf("fr", "en", "es", "de")

    Scaffold { padding ->
        LazyColumn(
            state = listState,
            contentPadding = contentPadding ,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // — Profile Section —
            item {
                SectionTitle("Mon profil")
                GlassCard {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        ReadOnlyField("Email", profile?.email.orEmpty())
                        EditableField("Nom affiché", displayName, !isLoading) { displayName = it }

                        // Save button for profile edits
                        Button(
                            onClick = {
                                authViewModel.updateUserProfile(
                                    mapOf("displayName" to displayName)
                                )
                            },
                            enabled = !isLoading,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Enregistrer le nom")
                        }
                    }
                }
            }

            // — Appearance & Language Section —
            item {
                SectionTitle("Apparence & Langue")
                GlassCard {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        IconSelector(
                            title = "Thème de l’application",
                            options = themeOptions,
                            selected = themeOptions.find { it.name == themeChoice },
                            onSelect = { selected -> themeChoice = selected.name },
                            getIcon = { theme ->
                                when (theme) {
                                    Theme.LIGHT -> Icons.Default.LightMode
                                    Theme.DARK -> Icons.Default.DarkMode
                                    Theme.SYSTEM -> Icons.Default.Settings  // or any icon you prefer
                                }
                            },
                            getLabel = { it.name }
                        )

                        IconSelector(
                            title = "Langue de l’interface",
                            options = localeOptions,
                            selected = localeOptions.find { it == localeChoice },
                            onSelect = { selected -> localeChoice = selected },
                            getIcon = {
                                // Example: use flags or generic language icons if available
                                Icons.Default.Language
                            },
                            getLabel = { it.uppercase() }
                        )

                        Button(
                            onClick = {
                                authViewModel.updateUserProfile(
                                    mapOf(
                                        "theme" to themeChoice,
                                        "locale" to localeChoice
                                    )
                                )
                            },
                            enabled = !isLoading,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Enregistrer les préférences")
                        }
                    }
                }
            }

            // — Notifications & Default Baby Section —
            item {
                SectionTitle("Notifications")
                GlassCard {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        ToggleSetting(
                            label = "Notifications activées",
                            checked = notificationsEnabled,
                            enabled = !isLoading
                        ) { notificationsEnabled = it }

                        Button(
                            onClick = {
                                authViewModel.updateUserProfile(
                                    mapOf(
                                        "notificationsEnabled" to notificationsEnabled,
                                    )
                                )
                            },
                            enabled = !isLoading,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Enregistrer les réglages")
                        }
                    }
                }
            }

            // — Baby Co-parents Section (unchanged) —
            item {
                SectionTitle("Mon bébé")
                ParentsCard(babyViewModel, navController)
            }

            // — Family Management Section —
            item {
                SectionTitle("Gestion de la famille")
                FamilyManagementCard(families, familyViewModel, isLoading)
            }
        }

        if (isLoading) {
            FullScreenLoader()
        }

    }
}
//————————————————————————————————————————————————————————————————————————————————
// Reusable Components
//————————————————————————————————————————————————————————————————————————————————

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyManagementCard(
    families: List<Family>,
    vm: FamilyViewModel,
    isLoading: Boolean
) {
    // Selected family from ViewModel
    val selected by vm.selectedFamily.collectAsState()
    // Local editable state mirroring Family properties
    var name by remember(selected) { mutableStateOf(selected?.name.orEmpty()) }
    var description by remember(selected) { mutableStateOf(selected?.description.orEmpty()) }
    var inviteCode by remember(selected) { mutableStateOf(selected?.inviteCode.orEmpty()) }
    var allowInvites by remember(selected) { mutableStateOf(selected?.settings?.allowMemberInvites == true) }
    var requireApproval by remember(selected) { mutableStateOf(selected?.settings?.requireApprovalForNewMembers == true) }
    var sharedNotifications by remember(selected) { mutableStateOf(selected?.settings?.sharedNotifications == true) }
    var privacyLevel by remember(selected) { mutableStateOf(selected?.settings?.defaultPrivacy?.name.orEmpty()) }
    var timezone by remember(selected) { mutableStateOf(selected?.settings?.timezone.orEmpty()) }

    GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Vos familles", style = MaterialTheme.typography.titleSmall)
            if (families.isEmpty()) {
                Text("Aucune famille enregistrée", color = Color.Gray)
            } else {
                families.forEach { family ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                vm.selectFamily(family)
                            }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(family.name, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            Divider()

            // Editable Form
            Text(
                if (selected == null) "Créer une nouvelle famille"
                else "Modifier la famille",
                style = MaterialTheme.typography.titleMedium
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nom de la famille") },
                enabled = !isLoading,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description (optionnel)") },
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = inviteCode,
                onValueChange = { inviteCode = it },
                label = { Text("Code d'invitation") },
                enabled = false, // read-only; generated by backend
                modifier = Modifier.fillMaxWidth()
            )

            // Settings toggles
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    allowInvites,
                    onCheckedChange = { allowInvites = it },
                    enabled = !isLoading
                )
                Spacer(Modifier.width(8.dp))
                Text("Tous peuvent inviter")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    requireApproval,
                    onCheckedChange = { requireApproval = it },
                    enabled = !isLoading
                )
                Spacer(Modifier.width(8.dp))
                Text("Validation admin nécessaire")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    sharedNotifications,
                    onCheckedChange = { sharedNotifications = it },
                    enabled = !isLoading
                )
                Spacer(Modifier.width(8.dp))
                Text("Notifications partagées")
            }

            // Privacy & Timezone
            DropdownSetting(
                label = "Niveau de confidentialité",
                options = PrivacyLevel.entries.map { it.name },
                selected = privacyLevel,
                enabled = !isLoading
            ) { privacyLevel = it }
            OutlinedTextField(
                value = timezone,
                onValueChange = { timezone = it },
                label = { Text("Fuseau horaire") },
                enabled = !isLoading,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Save / Delete buttons
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val base = selected?.copy() ?: Family()
                        val updated = base.copy(
                            name = name,
                            description = description.ifBlank { null },
                            settings = base.settings.copy(
                                allowMemberInvites = allowInvites,
                                requireApprovalForNewMembers = requireApproval,
                                sharedNotifications = sharedNotifications,
                                defaultPrivacy = PrivacyLevel.valueOf(privacyLevel),
                                timezone = timezone
                            )
                        )
                        vm.createOrUpdateFamily(updated)
                    },
                    enabled = name.isNotBlank() && !isLoading,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (selected == null) "Créer" else "Enregistrer")
                }
                if (selected != null) {
                    TextButton(
                        onClick = { vm.deleteFamily(selected!!.id) },
                        enabled = !isLoading,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Supprimer", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            // Display any error
            vm.state.collectAsState().value.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

        }
    }
}

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
private fun ParentsCard(
    babyViewModel: BabyViewModel,
    navController: NavController,
) {

    var showBabyDialog by remember { mutableStateOf(false) }
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
            BabySelectorRow(
                babies = babies,
                selectedBaby = selectedBaby,
                onSelectBaby = { baby ->
                    selectedBaby = baby
                    babyViewModel.loadParents(baby.id)
                },
                onAddBaby = { showBabyDialog = true }
            )

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
            if (showBabyDialog) {
                BabyFormDialog(
                    babyToEdit = null,
                    onBabyUpdated = { savedOrDeletedBaby ->
                        showBabyDialog = false
                        savedOrDeletedBaby?.let { babyViewModel.selectBaby(it) }
                    },
                    onCancel = { showBabyDialog = false },
                    viewModel = babyViewModel
                )
            }
        }
    }
}
