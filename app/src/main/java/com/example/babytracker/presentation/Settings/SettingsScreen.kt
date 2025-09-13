package com.example.babytracker.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.babytracker.data.Theme
import com.example.babytracker.ui.components.TopAppBar
import com.example.babytracker.presentation.viewmodel.AuthViewModel
import com.example.babytracker.presentation.viewmodel.BabyViewModel
import kotlinx.coroutines.flow.map
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.SemanticsPropertyKey


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    authViewModel: AuthViewModel = hiltViewModel(),
    babyViewModel: BabyViewModel = hiltViewModel()
) {
    val state by authViewModel.state.collectAsState()
    val profile = state.userProfile
    val authLoading by authViewModel.state.map { it.isLoading }.collectAsState(initial = false)
    val babyLoading by babyViewModel.isLoading.collectAsState()
    val isLoading = remember(authLoading, babyLoading) { authLoading || babyLoading }


    // Charger le profil si nécessaire
    LaunchedEffect(state.isAuthenticated) {
        if (state.isAuthenticated && profile == null) {
            authViewModel.loadUserProfile()
        }
    }
    LaunchedEffect(profile?.defaultBabyId) {
        babyViewModel.loadDefaultBaby(profile?.defaultBabyId)
    }

    val babies by babyViewModel.babies.collectAsState()
    val defaultBaby by babyViewModel.defaultBaby.collectAsState()
    var expandedBabies by remember { mutableStateOf(false) }
    var expandedTheme by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "Paramètres",
                navController = navController,
                showBackButton = true
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.Top
        ) {
            // Email et nom
            Text("Email :", style = MaterialTheme.typography.labelLarge)
            Text(profile?.email.orEmpty(), style = MaterialTheme.typography.bodyLarge)

            Text("Nom affiché :", style = MaterialTheme.typography.labelLarge)
            val focusManager = LocalFocusManager.current
            // 1. État local du champ
            var localName by remember(profile?.displayName) {
                mutableStateOf(profile?.displayName.orEmpty())
            }
            // 2. Indicateur de focus pour déclencher la mise à jour à la perte de focus
            var nameFieldFocused by remember { mutableStateOf(false) }
            OutlinedTextField(
                value = localName,
                onValueChange = { newValue ->
                    localName = newValue
                },
                enabled = !isLoading,
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focusState ->
                        // À la perte de focus, déclencher la mise à jour si modifié
                        if (nameFieldFocused && !focusState.isFocused) {
                            if (localName != profile?.displayName.orEmpty()) {
                                authViewModel.updateUserProfile(mapOf("displayName" to localName))
                            }
                        }
                        nameFieldFocused = focusState.isFocused
                    },
                keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        // Enregistrer et masquer le clavier
                        if (localName != profile?.displayName.orEmpty()) {
                            authViewModel.updateUserProfile(mapOf("displayName" to localName))
                        }
                        focusManager.clearFocus()
                    }
                )
            )

            HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)

            // Thème
            Text("Thème :", style = MaterialTheme.typography.labelLarge)
            ExposedDropdownMenuBox(
                expanded = expandedTheme,
                onExpandedChange = { expandedTheme = !expandedTheme }
            ) {
                TextField(
                    value = profile?.theme?.name.orEmpty(),
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedTheme) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                )
                ExposedDropdownMenu(
                    expanded = expandedTheme,
                    onDismissRequest = { expandedTheme = false }
                ) {
                    Theme.entries.forEach { theme ->
                        DropdownMenuItem(
                            text = { Text(theme.name) },
                            onClick = {
                                expandedTheme = false
                                authViewModel.updateUserProfile(mapOf("theme" to theme.name))
                            }
                        )
                    }
                }
            }

            // Notifications
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = profile?.notificationsEnabled == true,
                    onCheckedChange = { enabled ->
                        authViewModel.updateUserProfile(mapOf("notificationsEnabled" to enabled))
                    },
                    enabled = !state.isLoading
                )
                Spacer(Modifier.width(8.dp))
                Text("Activer les notifications")
            }

            // Locale
            Text("Langue :", style = MaterialTheme.typography.labelLarge)

            var expandedLocale by remember { mutableStateOf(false) }
            val locales = listOf("fr", "en", "es", "de") // Vos locales supportées
            val currentLocale = profile?.locale ?: locales.first()

            ExposedDropdownMenuBox(
                expanded = expandedLocale,
                onExpandedChange = { expandedLocale = !expandedLocale }
            ) {
                TextField(
                    value = currentLocale,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedLocale) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                )
                ExposedDropdownMenu(
                    expanded = expandedLocale,
                    onDismissRequest = { expandedLocale = false }
                ) {
                    locales.forEach { localeCode ->
                        DropdownMenuItem(
                            text = { Text(localeCode) },
                            onClick = {
                                expandedLocale = false
                                authViewModel.updateUserProfile(mapOf("locale" to localeCode))
                            }
                        )
                    }
                }
            }

            HorizontalDivider(
                Modifier,
                DividerDefaults.Thickness,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
            )

            // Default Baby
            Text("Bébé par défaut :", style = MaterialTheme.typography.labelLarge)
            ExposedDropdownMenuBox(
                expanded = expandedBabies,
                onExpandedChange = { expandedBabies = !expandedBabies }
            ) {
                TextField(
                    value = defaultBaby?.name.orEmpty(),
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = {
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isLoading) { expandedBabies = true },
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
                                    expandedBabies = false
                                    babyViewModel.setDefaultBaby(baby)
                                    authViewModel.updateUserProfile(
                                        mapOf("defaultBabyId" to baby.id)
                                    )
                                }
                            )
                        }
                    }
                }
            }


            // Déconnexion
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

            // Erreur éventuelle
            state.error?.let { err ->
                Text(
                    text = err,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (isLoading) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

