package com.example.babytracker.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.babytracker.data.Family
import com.example.babytracker.data.PrivacyLevel
import com.example.babytracker.data.Theme
import com.example.babytracker.presentation.event.IconSelector
import com.example.babytracker.presentation.viewmodel.AuthViewModel
import com.example.babytracker.presentation.viewmodel.BabyViewModel
import com.example.babytracker.presentation.viewmodel.FamilyViewModel
import com.example.babytracker.ui.components.FamilyManagementCard
import kotlinx.coroutines.flow.map
import java.util.Locale


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    contentPadding: PaddingValues = PaddingValues(),
    authViewModel: AuthViewModel = hiltViewModel(),
    babyViewModel: BabyViewModel = hiltViewModel(),
    familyViewModel: FamilyViewModel = hiltViewModel()
) {
    // Collect state
    val authState by authViewModel.state.collectAsState()
    val profile = authState.userProfile
    val babies by babyViewModel.babies.collectAsState()
    val families by familyViewModel.families.collectAsState()

    val isAuthLoading by authViewModel.state.map { it.isLoading }.collectAsState(false)
    val isBabyLoading by babyViewModel.isLoading.collectAsState(false)
    val isFamilyLoading by familyViewModel.state.map { it.isLoading }.collectAsState(false)
    // val isLoading = isAuthLoading || isBabyLoading || isFamilyLoading

    // Local editable values
    var displayName by remember { mutableStateOf(profile?.displayName.orEmpty()) }
    var themeChoice by remember { mutableStateOf(profile?.theme?.name.orEmpty()) }
    var localeChoice by remember { mutableStateOf(profile?.locale.orEmpty()) }
    var notificationsEnabled by remember { mutableStateOf(profile?.notificationsEnabled == true) }

    val themeOptions = Theme.entries.toList()
    val localeOptions = listOf("fr", "en", "es", "de")

    Scaffold { padding ->
        LazyColumn(
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // — Profile Section —
            item {
                SectionCard(
                    "Mon profil",
                    icon = Icons.Default.Person
                ) {
                    GlassCard(
                        loading = isAuthLoading
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            ReadOnlyField("Email", profile?.email.orEmpty())
                            EditableField("Nom affiché", displayName, !isAuthLoading) {
                                displayName = it
                            }

                            // Save button for profile edits
                            Button(
                                onClick = {
                                    authViewModel.updateUserProfile(
                                        mapOf("displayName" to displayName)
                                    )
                                },
                                enabled = !isAuthLoading,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Enregistrer le nom")
                            }
                        }
                    }
                }
            }

            // — Appearance & Language Section —
            item {
                SectionCard(
                    "Apparence & Langue",
                    icon = Icons.Default.Palette
                ) {
                    GlassCard(
                        loading = isAuthLoading
                    ) {
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
                                enabled = !isAuthLoading,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Enregistrer les préférences")
                            }
                        }
                    }
                }
            }

            // — Notifications & Default Baby Section —
            item {
                SectionCard(
                    "Notifications",
                    icon = Icons.Default.Notifications
                )
                {
                    GlassCard(
                        loading = isAuthLoading
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            ToggleSetting(
                                label = "Notifications activées",
                                checked = notificationsEnabled,
                                enabled = !isAuthLoading
                            ) { notificationsEnabled = it }

                            Button(
                                onClick = {
                                    authViewModel.updateUserProfile(
                                        mapOf(
                                            "notificationsEnabled" to notificationsEnabled,
                                        )
                                    )
                                },
                                enabled = !isAuthLoading,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Enregistrer les réglages")
                            }
                        }
                    }
                }
            }

            // — Family Management Section —
            item {
                SectionCard(
                    "Gestion de la famille",
                    icon = Icons.Default.FamilyRestroom
                )
                {
                    FamilyManagementCard(families, familyViewModel, isFamilyLoading)
                }
            }
        }


    }
}
//————————————————————————————————————————————————————————————————————————————————
// Reusable Components
//————————————————————————————————————————————————————————————————————————————————

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onClickHeader: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column {
            // Header row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = onClickHeader != null) {
                        onClickHeader?.invoke()
                    }
                    .padding(16.dp)
            ) {
                icon?.let {
                    Icon(
                        imageVector = it,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }

            // Divider between header and content
            Divider(color = MaterialTheme.colorScheme.outline)

            // Subcard content area
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    shape: Shape = RoundedCornerShape(16.dp),
    backgroundAlpha: Float = 0.6f,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = backgroundAlpha),
                    shape = shape
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content
        )

        if (loading) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = backgroundAlpha)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.dp
                )
            }
        }
    }
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

