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
import com.example.babytracker.data.Theme
import com.example.babytracker.presentation.viewmodel.AuthViewModel
import com.example.babytracker.presentation.viewmodel.BabyViewModel
import kotlinx.coroutines.flow.map


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    listState: LazyListState,
    authViewModel: AuthViewModel = hiltViewModel(),
    babyViewModel: BabyViewModel = hiltViewModel()
) {

    val errorMessage by babyViewModel.errorMessage.collectAsState()
    // ViewModel states
    val state by authViewModel.state.collectAsState()
    val profile = state.userProfile
    val authLoading by authViewModel.state.map { it.isLoading }.collectAsState(false)
    val babyLoading by babyViewModel.isLoading.collectAsState()
    val isLoading = remember(authLoading, babyLoading) { authLoading || babyLoading }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
        }
    }
    // Load profile & default baby
    LaunchedEffect(state.isAuthenticated) {
        if (state.isAuthenticated && profile == null) authViewModel.loadUserProfile()
    }
    LaunchedEffect(profile?.defaultBabyId) {
        babyViewModel.loadDefaultBaby(profile?.defaultBabyId)
    }
    val babies by babyViewModel.babies.collectAsState()
    val defaultBaby by babyViewModel.defaultBaby.collectAsState()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = Modifier.fillMaxSize()
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            //—— User Settings Section —————————————————————————————————————————————
            item {
                SectionTitle("Mon profil")
                GlassCard {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ReadOnlyField(label = "Email", value = profile?.email.orEmpty())
                        EditableField(
                            label = "Nom affiché",
                            text = profile?.displayName.orEmpty(),
                            enabled = !isLoading,
                            onValueConfirmed = { newName ->
                                authViewModel.updateUserProfile(mapOf("displayName" to newName))
                            }
                        )
                        DropdownSetting(
                            label = "Thème",
                            options = Theme.entries.map { it.name },
                            selected = profile?.theme?.name.orEmpty(),
                            enabled = !isLoading
                        ) { choice ->
                            authViewModel.updateUserProfile(mapOf("theme" to choice))
                        }
                        ToggleSetting(
                            label = "Notifications",
                            checked = profile?.notificationsEnabled == true,
                            enabled = !isLoading
                        ) { enabled ->
                            authViewModel.updateUserProfile(mapOf("notificationsEnabled" to enabled))
                        }
                        DropdownSetting(
                            label = "Langue",
                            options = listOf("fr", "en", "es", "de"),
                            selected = profile?.locale.orEmpty(),
                            enabled = !isLoading
                        ) { choice ->
                            authViewModel.updateUserProfile(mapOf("locale" to choice))
                        }
                        DropdownSetting(
                            label = "Bébé par défaut",
                            options = babies.map { it.name }.ifEmpty { listOf("Aucun bébé") },
                            selected = defaultBaby?.name.orEmpty(),
                            enabled = !isLoading
                        ) { name ->
                            babies.find { it.name == name }?.let { baby ->
                                babyViewModel.setDefaultBaby(baby)
                                authViewModel.updateUserProfile(mapOf("defaultBabyId" to baby.id))
                            }
                        }
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
                        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                }
            }

            //—— Family Settings Section ———————————————————————————————————————————
            item {
                SectionTitle("Gestion de la famille")
                ParentsCard(babyViewModel = babyViewModel)
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
