package com.kouloundissa.twinstracker.presentation.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
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
import com.kouloundissa.twinstracker.data.Theme
import com.kouloundissa.twinstracker.presentation.event.IconSelector
import com.kouloundissa.twinstracker.presentation.viewmodel.AuthViewModel
import com.kouloundissa.twinstracker.presentation.viewmodel.BabyViewModel
import com.kouloundissa.twinstracker.presentation.viewmodel.FamilyViewModel
import com.kouloundissa.twinstracker.ui.components.FamilyManagementCard
import com.kouloundissa.twinstracker.ui.theme.BackgroundColor
import com.kouloundissa.twinstracker.ui.theme.DarkBlue
import com.kouloundissa.twinstracker.ui.theme.DarkGrey
import kotlinx.coroutines.flow.map


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
    val snackbarHostState = remember { SnackbarHostState() }
    var showLogoutDialog by remember { mutableStateOf(false) }
    // Local editable values
    var displayName by remember { mutableStateOf(profile?.displayName.orEmpty()) }
    var themeChoice by remember { mutableStateOf(profile?.theme?.name.orEmpty()) }
    var localeChoice by remember { mutableStateOf(profile?.locale.orEmpty()) }
    var notificationsEnabled by remember { mutableStateOf(profile?.notificationsEnabled == true) }


    val baseColor = Color.White
    val contentColor = DarkBlue
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = Modifier.fillMaxSize()
    ) { padding ->
        LazyColumn(
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
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
                            EditableField(
                                "Nom affiché",
                                displayName,
                                !isAuthLoading
                            ) { confirmedValue ->
                                displayName = confirmedValue
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

                            // Logout button with confirmation
                            OutlinedButton(
                                onClick = {
                                    showLogoutDialog = true
                                },
                                enabled = !isAuthLoading,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor =Color.Red
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    Color.Red
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Se déconnecter")
                            }
                        }
                    }
                }

                // Logout confirmation dialog
                if (showLogoutDialog) {
                    AlertDialog(
                        onDismissRequest = { showLogoutDialog = false },
                        title = {
                            Text("Confirmation de déconnexion")
                        },
                        text = {
                            Text("Êtes-vous sûr de vouloir vous déconnecter ?")
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    authViewModel.logout()
                                    showLogoutDialog = false
                                }
                            ) {
                                Text(
                                    "Se déconnecter",
                                    color = Color.Red// MaterialTheme.colorScheme.error
                                )
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = { showLogoutDialog = false }
                            ) {
                                Text("Annuler")
                            }
                        }
                    )
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

    val baseColor = BackgroundColor
    val tintColor = DarkBlue
    val contentColor = DarkGrey
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = baseColor.copy(alpha = 0.4f)
        ),
    ) {
        Column(modifier = Modifier) {
            // Header row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = onClickHeader != null) {
                        onClickHeader?.invoke()
                    }.background(color = baseColor.copy(alpha = 0.6f))
                    .padding(16.dp)
            ) {
                icon?.let {
                    Icon(
                        imageVector = it,
                        contentDescription = null,
                        tint = tintColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = contentColor,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }

            // Divider between header and content
            HorizontalDivider(
                Modifier,
                DividerDefaults.Thickness,
                color = MaterialTheme.colorScheme.outline
            )

            // Subcard content area
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
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
    content: @Composable ColumnScope.() -> Unit
) {

    val baseColor = Color.White
    val contentColor = DarkBlue

    Box(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content
        )

        if (loading) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(baseColor.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = contentColor,
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
    onValueChange: (String) -> Unit
) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = text,
        onValueChange = { newValue ->
            onValueChange(newValue)
        },
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(
            onDone = { focusManager.clearFocus() }
        )
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

