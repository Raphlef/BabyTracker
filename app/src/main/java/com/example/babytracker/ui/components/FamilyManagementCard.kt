package com.example.babytracker.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import com.example.babytracker.data.Family
import com.example.babytracker.data.PrivacyLevel
import com.example.babytracker.presentation.event.IconSelector
import com.example.babytracker.presentation.settings.GlassCard
import com.example.babytracker.presentation.viewmodel.FamilyViewModel
import kotlin.collections.forEach


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyManagementCard(
    families: List<Family>,
    familyViewModel: FamilyViewModel,
    isLoading: Boolean
) {
    // Selected family from ViewModel
    val selected by familyViewModel.selectedFamily.collectAsState()
    // **Automatically select the first family when the list loads (or changes)**
    LaunchedEffect(families) {
        if (selected == null && families.isNotEmpty()) {
            familyViewModel.selectFamily(families.first())
        }
    }
    // Local editable state mirroring Family properties
    var name by remember(selected) { mutableStateOf(selected?.name.orEmpty()) }
    var description by remember(selected) { mutableStateOf(selected?.description.orEmpty()) }
    var inviteCode by remember(selected) { mutableStateOf(selected?.inviteCode.orEmpty()) }
    var allowInvites by remember(selected) { mutableStateOf(selected?.settings?.allowMemberInvites == true) }
    var requireApproval by remember(selected) { mutableStateOf(selected?.settings?.requireApprovalForNewMembers == true) }
    var sharedNotifications by remember(selected) { mutableStateOf(selected?.settings?.sharedNotifications == true) }
    var privacyLevel by remember(selected) { mutableStateOf(selected?.settings?.defaultPrivacy?.name.orEmpty()) }

    GlassCard(
        loading = isLoading
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Vos familles", style = MaterialTheme.typography.titleSmall)
            if (families.isEmpty()) {
                Text("Aucune famille enregistrée", color = Color.Gray)
            } else {
                // "Créer nouvelle famille" option always visible
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { familyViewModel.selectFamily(null) }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "➕ Créer une nouvelle famille",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (selected == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }

                Divider()
                FamilyList(
                    families = families,
                    selectedFamily = selected,
                    onSelect = { familyViewModel.selectFamily(it) }
                )
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
            IconSelector(
                title = "Niveau de confidentialité",
                options = PrivacyLevel.entries.toList(),
                selected = PrivacyLevel.values().find { it.name == privacyLevel },
                onSelect = { selected -> privacyLevel = selected.name },
                getIcon = { level ->
                    when (level) {
                        PrivacyLevel.PRIVATE     -> Icons.Default.Lock
                        PrivacyLevel.FAMILY_ONLY -> Icons.Default.Group
                        PrivacyLevel.PUBLIC      -> Icons.Default.Public
                    }
                },
                getLabel = { it.name.replace('_', ' ').lowercase() }
            )


            // Save / Delete buttons
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                selected?.let {
                    TextButton(
                        onClick = { familyViewModel.deleteFamily(it.id) },
                        enabled = !isLoading,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Supprimer", color = MaterialTheme.colorScheme.error)
                    }
                }
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
                                defaultPrivacy = PrivacyLevel.valueOf(privacyLevel)
                            )
                        )
                        familyViewModel.createOrUpdateFamily(updated)
                    },
                    enabled = name.isNotBlank() && !isLoading,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (selected == null) "Créer" else "Enregistrer")
                }
            }

            // Display any error
            familyViewModel.state.collectAsState().value.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyList(
    families: List<Family>,
    selectedFamily: Family?,
    onSelect: (Family) -> Unit
) {
    Column  {
        families.forEach{ family ->
            val isSelected = family.id == selectedFamily?.id
            Surface(
                tonalElevation = if (isSelected) 4.dp else 0.dp,
                shape = MaterialTheme.shapes.medium,
                color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                else MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .clickable { onSelect(family) }
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.FamilyRestroom,
                        contentDescription = null,
                        tint = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = family.name,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}