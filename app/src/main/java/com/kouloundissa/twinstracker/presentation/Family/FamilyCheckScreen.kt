package com.kouloundissa.twinstracker.presentation.Family

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.kouloundissa.twinstracker.data.Family
import com.kouloundissa.twinstracker.data.FamilySettings
import com.kouloundissa.twinstracker.presentation.settings.GlassCard
import com.kouloundissa.twinstracker.presentation.viewmodel.BabyViewModel
import com.kouloundissa.twinstracker.presentation.viewmodel.FamilyViewModel
import com.kouloundissa.twinstracker.ui.components.JoinFamilyDialog
import com.kouloundissa.twinstracker.ui.theme.DarkBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyCheckScreen(
    familyViewModel: FamilyViewModel,
    onNavigateToDashboard: (String?) -> Unit
) {
    val babyViewModel: BabyViewModel = hiltViewModel()
    val babies by babyViewModel.babies.collectAsState()
    val selectedFamily by familyViewModel.selectedFamily.collectAsState()
    val families by familyViewModel.families.collectAsState()
    // Check if user has families and navigate accordingly
    LaunchedEffect(families) {
        if (families.isNotEmpty()) {
            // User has families, select first one and navigate to dashboard
            val firstFamily = families.first()
            familyViewModel.selectFamily(firstFamily)

            // Get first baby from the family
            val familyBabies = babies.filter { it.id in firstFamily.babyIds }
            val firstBabyId = familyBabies.firstOrNull()?.id

            onNavigateToDashboard(firstBabyId)
        }
    }

    // Only show the screen if user has no families
    if (families.isEmpty()) {
        FamilyOnboardingContent(
            familyViewModel = familyViewModel,
            onFamilyCreatedOrJoined = { family ->
                // After creating or joining a family, navigate to dashboard
                val familyBabies = babies.filter { it.id in family.babyIds }
                val firstBabyId = familyBabies.firstOrNull()?.id
                onNavigateToDashboard(firstBabyId)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyOnboardingContent(
    familyViewModel: FamilyViewModel,
    onFamilyCreatedOrJoined: (Family) -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var showJoinDialog by remember { mutableStateOf(false) }
    val families by familyViewModel.families.collectAsState()
    val familyState by familyViewModel.state.collectAsState()
    val inviteResult by familyViewModel.inviteResult.collectAsState(initial = null)

    // Monitor family creation/join success
    LaunchedEffect(familyViewModel.families) {
        if (families.isNotEmpty()) {
            onFamilyCreatedOrJoined(families.first())
        }
    }

    // Monitor join success
    LaunchedEffect(inviteResult) {
        inviteResult?.onSuccess {
            // Family was successfully joined, the families list will update
            // which will trigger the LaunchedEffect above
        }
    }

    GlassCard {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(0.5f))

            Icon(
                imageVector = Icons.Default.FamilyRestroom,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = DarkBlue
            )

            Text(
                text = "Bienvenue dans Baby Tracker",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Pour commencer, vous devez créer une nouvelle famille ou rejoindre une famille existante.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.weight(1f))

            // Create Family Button
            Button(
                onClick = { showCreateDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = !familyState.isLoading
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text("Créer une nouvelle famille")
                }
            }

            // Join Family Button
            OutlinedButton(
                onClick = { showJoinDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = !familyState.isLoading
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.QrCode, contentDescription = null)
                    Text("Rejoindre avec un code")
                }
            }

            Spacer(modifier = Modifier.weight(0.5f))

            // Display any error
            familyState.error?.let { error ->
                Text(
                    text = error,
                    color = Color.Red,// MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    // Create Family Dialog
    if (showCreateDialog) {
        CreateFamilyDialog(
            show = showCreateDialog,
            onDismiss = { showCreateDialog = false },
            onCreateFamily = { name, description ->
                val newFamily = Family(
                    name = name,
                    description = description.ifBlank { null },
                    settings = FamilySettings()
                )
                familyViewModel.createOrUpdateFamily(newFamily)
                showCreateDialog = false
            },
            isLoading = familyState.isLoading
        )
    }

    // Join Family Dialog - Reuse from FamilyManagementCard
    if (showJoinDialog) {
        JoinFamilyDialog(
            show = showJoinDialog,
            onDismiss = { showJoinDialog = false },
            onJoin = { code -> familyViewModel.joinByCode(code) },
            inviteResult = inviteResult,
            isLoading = familyState.isLoading
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateFamilyDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onCreateFamily: (name: String, description: String) -> Unit,
    isLoading: Boolean
) {
    if (!show) return

    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Créer une famille") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nom de la famille") },
                    singleLine = true,
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optionnel)") },
                    maxLines = 3,
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreateFamily(name, description) },
                enabled = name.isNotBlank() && !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Créer")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isLoading
            ) {
                Text("Annuler")
            }
        }
    )
}


