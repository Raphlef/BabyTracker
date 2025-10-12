package com.kouloundissa.twinstracker.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kouloundissa.twinstracker.data.Family
import com.kouloundissa.twinstracker.data.FamilySettings
import com.kouloundissa.twinstracker.data.PrivacyLevel
import com.kouloundissa.twinstracker.presentation.Family.CreateFamilyDialog
import com.kouloundissa.twinstracker.presentation.dashboard.BabySelectorRow
import com.kouloundissa.twinstracker.presentation.event.IconSelector
import com.kouloundissa.twinstracker.presentation.settings.GlassCard
import com.kouloundissa.twinstracker.presentation.viewmodel.BabyViewModel
import com.kouloundissa.twinstracker.presentation.viewmodel.FamilyViewModel
import com.kouloundissa.twinstracker.ui.theme.*
import kotlinx.coroutines.flow.map
import kotlin.collections.forEach


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyManagementCard(
    families: List<Family>,
    familyViewModel: FamilyViewModel,
    isLoading: Boolean,
    babyViewModel: BabyViewModel = hiltViewModel()
) {
    // Selected family from ViewModel
    val selectedFamily by familyViewModel.selectedFamily.collectAsState()
    val babies by babyViewModel.babies.collectAsState()
    val selectedBaby by babyViewModel.selectedBaby.collectAsState()

    val inviteResult by familyViewModel.inviteResult.collectAsState(initial = null)

    // Local editable state mirroring Family properties
    var name by remember(selectedFamily) { mutableStateOf(selectedFamily?.name.orEmpty()) }
    var description by remember(selectedFamily) { mutableStateOf(selectedFamily?.description.orEmpty()) }
    var inviteCode by remember(selectedFamily) { mutableStateOf(selectedFamily?.inviteCode.orEmpty()) }
    var allowInvites by remember(selectedFamily) { mutableStateOf(selectedFamily?.settings?.allowMemberInvites == true) }
    var requireApproval by remember(selectedFamily) { mutableStateOf(selectedFamily?.settings?.requireApprovalForNewMembers == true) }
    var sharedNotifications by remember(selectedFamily) { mutableStateOf(selectedFamily?.settings?.sharedNotifications == true) }
    var privacyLevel by remember(selectedFamily) { mutableStateOf(selectedFamily?.settings?.defaultPrivacy?.name.orEmpty()) }
    var showJoinDialog by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }

    // **Automatically select the first family when the list loads (or changes)**
    LaunchedEffect(families) {
        if (selectedFamily == null && families.isNotEmpty()) {
            familyViewModel.selectFamily(families.first())
        }
    }
    // When family changes, load its babies
    val familyBabies = remember(babies, selectedFamily) {
        selectedFamily?.let { fam ->
            babies.filter { it.id in fam.babyIds }
        } ?: emptyList()
    }

    // Handle successful join
    LaunchedEffect(inviteResult) {
        inviteResult?.onSuccess {
            showJoinDialog = false
            // TODO: show Snackbar("Rejoint avec succès !")
        }
    }
    val baseColor = BackgroundColor
    val tintColor = DarkBlue
    val contentColor = DarkGrey
    val cornerShape = MaterialTheme.shapes.extraLarge

    GlassCard(
        loading = isLoading
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Vos familles", style = MaterialTheme.typography.titleSmall, color = contentColor)

            FamilyList(
                families = families,
                selectedFamily = selectedFamily,
                onSelect = { familyViewModel.selectFamily(it) }
            )
            Button(
                onClick = { showCreateDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = !isLoading,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text("Créer une nouvelle famille")
                }
            }
            HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)
            // Baby selector row
            if (selectedFamily != null) {
                Text("Bébés", style = MaterialTheme.typography.titleSmall, color = contentColor)
                BabySelectorRow(
                    babies = babies,
                    selectedBaby = selectedBaby,
                    onSelectBaby = { babyViewModel.selectBaby(it) },
                    onAddBaby = { /* navigate to baby creation screen */ }
                )
            }
            HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)

            // Editable Form
            Text(
                if (selectedFamily == null) "Créer une nouvelle famille"
                else "Modifier la famille",
                style = MaterialTheme.typography.titleMedium,
                color = contentColor
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nom de la famille", color = contentColor) },
                enabled = !isLoading,
                textStyle = LocalTextStyle.current.copy(color = contentColor),
                singleLine = true,
                shape = cornerShape,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                textStyle = LocalTextStyle.current.copy(color = contentColor),
                label = { Text("Description (optionnel)", color = contentColor) },
                enabled = !isLoading,
                shape = cornerShape,
                modifier = Modifier.fillMaxWidth()
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = inviteCode,
                    onValueChange = {},
                    textStyle = LocalTextStyle.current.copy(color = contentColor),
                    label = { Text("Code d'invitation", color = contentColor) },
                    readOnly = true,
                    shape = cornerShape,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { familyViewModel.regenerateCode() },
                    enabled = !isLoading
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Régénérer le code",
                        tint = tintColor
                    )
                }
            }

            // Settings toggles
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    allowInvites,
                    onCheckedChange = { allowInvites = it },
                    enabled = !isLoading
                )
                Spacer(Modifier.width(8.dp))
                Text("Tous peuvent inviter", color = contentColor)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    requireApproval,
                    onCheckedChange = { requireApproval = it },
                    enabled = !isLoading
                )
                Spacer(Modifier.width(8.dp))
                Text("Validation admin nécessaire", color = contentColor)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    sharedNotifications,
                    onCheckedChange = { sharedNotifications = it },
                    enabled = !isLoading
                )
                Spacer(Modifier.width(8.dp))
                Text("Notifications partagées", color = contentColor)
            }

            // Privacy
            IconSelector(
                title = "Niveau de confidentialité",
                options = PrivacyLevel.entries.toList(),
                selected = PrivacyLevel.entries.find { it.name == privacyLevel },
                onSelect = { selected -> privacyLevel = selected.name },
                getIcon = { level ->
                    when (level) {
                        PrivacyLevel.PRIVATE -> Icons.Default.Lock
                        PrivacyLevel.FAMILY_ONLY -> Icons.Default.Group
                        PrivacyLevel.PUBLIC -> Icons.Default.Public
                    }
                },
                getLabel = { it.name.replace('_', ' ').lowercase() }
            )


            // Save / leave buttons
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                selectedFamily?.let {
                    FamilyLeaveButton(
                        selectedFamily = selectedFamily,
                        familyViewModel = familyViewModel,
                        isLoading = isLoading
                    )
                }
            }
            // Inline join button
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    onClick = { showJoinDialog = true },
                    modifier = Modifier.weight(1f),
                    enabled = !isLoading
                ) {
                    Icon(Icons.Default.QrCode, contentDescription = null, tint = tintColor)
                    Text("Rejoindre", color = tintColor)
                }

                val createLabel = if (selectedFamily == null) "Créer" else "Enregistrer"

                Button(
                    onClick = {
                        val base = selectedFamily?.copy() ?: Family()
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
                    Text(createLabel)
                }
            }

            // Display any error
            familyViewModel.state.collectAsState().value.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
        }
    }
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
            isLoading = isLoading
        )
    }
    if (showJoinDialog) {
        JoinFamilyDialog(
            show = showJoinDialog,
            onDismiss = { showJoinDialog = false },
            onJoin = { code ->
                familyViewModel.joinByCode(code)
            },
            inviteResult = inviteResult,
            isLoading = isLoading
        )
    }


}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyList(
    families: List<Family>,
    selectedFamily: Family?,
    onSelect: (Family) -> Unit
) {
    val colorContent = DarkBlue
    Column {
        families.forEach { family ->
            val isSelected = family.id == selectedFamily?.id
            Surface(
                tonalElevation = if (isSelected) 4.dp else 0.dp,
                shape = MaterialTheme.shapes.medium,
                color = if (isSelected) colorContent.copy(alpha = 0.1f)
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
                        tint = if (isSelected) colorContent
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = family.name,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = if (isSelected) colorContent
                            else MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Selected",
                            tint = colorContent
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinFamilyDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onJoin: (String) -> Unit,
    inviteResult: Result<Unit>?,
    isLoading: Boolean
) {
    if (!show) return

    var code by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rejoindre une famille") },
        text = {
            Column {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase().trim() },
                    label = { Text("Code d’invitation") },
                    singleLine = true,
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth()
                )
                inviteResult?.onFailure { ex ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = ex.message ?: "Erreur inconnue",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onJoin(code) },
                enabled = code.length == 6 && !isLoading
            ) {
                Text("Rejoindre")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        }
    )
}

@Composable
fun FamilyLeaveButton(
    selectedFamily: Family?,
    familyViewModel: FamilyViewModel,
    isLoading: Boolean
) {
    var showConfirmDialog by remember { mutableStateOf(false) }
    val nonNullUserIdFlow = familyViewModel.currentUserId.map { it.orEmpty() }
    val currentUserId: String by nonNullUserIdFlow
        .collectAsState(initial = "")
    selectedFamily?.let { family ->
        val isOnlyAdmin =
            family.adminIds.contains(currentUserId) && family.adminIds.size == 1


        TextButton(
            onClick = { showConfirmDialog = true },
            enabled = !isLoading,
            colors = ButtonDefaults.textButtonColors(
                contentColor = if (isOnlyAdmin) MaterialTheme.colorScheme.outline
                else MaterialTheme.colorScheme.error
            )
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    if (isOnlyAdmin) "Impossible de quitter" else "Quitter",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }


        // Confirmation Dialog
        if (showConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showConfirmDialog = false },
                title = { Text("Quitter la famille") },
                text = {
                    Text(
                        if (isOnlyAdmin) {
                            "Vous ne pouvez pas quitter cette famille car vous êtes le seul administrateur. Nommez d'abord un autre membre comme administrateur."
                        } else {
                            "Êtes-vous sûr de vouloir quitter \"${family.name}\" ? Vous perdrez l'accès à tous les bébés et données de cette famille."
                        }
                    )
                },
                confirmButton = {
                    if (!isOnlyAdmin) {
                        TextButton(
                            onClick = {
                                showConfirmDialog = false
                                familyViewModel.removeMember(
                                    family.id,
                                    currentUserId
                                )

                            }
                        ) {
                            Text("Quitter", color = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirmDialog = false }) {
                        Text(if (isOnlyAdmin) "Compris" else "Annuler")
                    }
                }
            )
        }
    }
}