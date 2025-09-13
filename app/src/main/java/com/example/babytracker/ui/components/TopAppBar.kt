package com.example.babytracker.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.example.babytracker.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopAppBar(
    title: String,
    navController: NavController? = null,
    showBackButton: Boolean = false,
    onLogout: (() -> Unit)? = null,          // Callback déconnexion
    onNavigateSettings: (() -> Unit)? = null, // Callback navigation settings
    onNavigateParents: (() -> Unit)? = null,// Callback navigation parents
    actions: @Composable () -> Unit = {}
) {

    var showMenu by remember { mutableStateOf(false) }

    CenterAlignedTopAppBar(
        title = { Text(title) },
        navigationIcon = {
            if (showBackButton && navController != null) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = stringResource(R.string.back_button_description)
                    )
                }
            }
        },
        actions = {
            IconButton(onClick = { showMenu = true }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.more_options)
                )
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.export_data)) },
                    onClick = { /* TODO: Implémenter l'export */ }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.parents)) },
                    onClick = {
                        showMenu = false
                        onNavigateParents?.invoke()
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.settings)) },
                    onClick = {
                        showMenu = false
                        onNavigateSettings?.invoke()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Déconnexion") },
                    onClick = {
                        showMenu = false
                        onLogout?.invoke()
                    }
                )
            }

            actions()
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.primary,
        ),
        modifier = Modifier
    )
}