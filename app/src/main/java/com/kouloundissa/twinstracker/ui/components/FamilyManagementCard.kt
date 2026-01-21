package com.kouloundissa.twinstracker.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kouloundissa.twinstracker.R
import com.kouloundissa.twinstracker.data.Family
import com.kouloundissa.twinstracker.data.FamilyRole
import com.kouloundissa.twinstracker.data.FamilySettings
import com.kouloundissa.twinstracker.data.FamilyUser
import com.kouloundissa.twinstracker.data.PrivacyLevel
import com.kouloundissa.twinstracker.presentation.Family.CreateFamilyDialog
import com.kouloundissa.twinstracker.presentation.settings.GlassCard
import com.kouloundissa.twinstracker.presentation.viewmodel.BabyViewModel
import com.kouloundissa.twinstracker.presentation.viewmodel.FamilyViewModel
import com.kouloundissa.twinstracker.ui.theme.BackgroundColor
import com.kouloundissa.twinstracker.ui.theme.DarkBlue
import com.kouloundissa.twinstracker.ui.theme.DarkGrey
import kotlinx.coroutines.flow.map


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyManagementCard(
    families: List<Family>,
    isLoading: Boolean,
    familyViewModel: FamilyViewModel = hiltViewModel(),
    babyViewModel: BabyViewModel = hiltViewModel()
) {
    // Selected family from ViewModel
    val selectedFamily by familyViewModel.selectedFamily.collectAsState()
    val familyUsers by familyViewModel.familyUsers.collectAsState(emptyList())
    val inviteResult by familyViewModel.inviteResult.collectAsState(initial = null)
    val isCurrentAdmin = familyViewModel.isCurrentUserAdmin()
    val errorMessage = familyViewModel.state.collectAsState().value.error
    val isEditFamily = selectedFamily != null

    val babies by babyViewModel.babies.collectAsState()
    val selectedBaby by babyViewModel.selectedBaby.collectAsState()

    // Extract copy logic into a reusable function
    fun copyToClipboard(context: Context, text: String, label: String = "text") {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(
            context,
            context.getString(R.string.family_management_code_copied),
            Toast.LENGTH_SHORT
        ).show()
    }

    val context = LocalContext.current

    // Local editable state mirroring Family properties
    var name by remember(selectedFamily) { mutableStateOf(selectedFamily?.name.orEmpty()) }
    var description by remember(selectedFamily) { mutableStateOf(selectedFamily?.description.orEmpty()) }
    var inviteCode by remember(selectedFamily) { mutableStateOf(selectedFamily?.inviteCode.orEmpty()) }
    var requireApproval by remember(selectedFamily) { mutableStateOf(selectedFamily?.settings?.requireApprovalForNewMembers == true) }
    //var sharedNotifications by remember(selectedFamily) { mutableStateOf(selectedFamily?.settings?.sharedNotifications == true) }
    var privacyLevel by remember(selectedFamily) { mutableStateOf(selectedFamily?.settings?.defaultPrivacy?.name.orEmpty()) }
    var showJoinDialog by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    // **Automatically select the first family when the list loads (or changes)**
    LaunchedEffect(families) {
        if (selectedFamily == null && families.isNotEmpty()) {
            familyViewModel.selectFamily(families.first())
        }
    }

    // Handle successful join
    LaunchedEffect(inviteResult) {
        inviteResult?.onSuccess {
            snackbarHostState.showSnackbar(context.getString(R.string.family_management_joined_success))
        }?.onFailure { ex ->
            snackbarHostState.showSnackbar(context.getString(R.string.family_management_join_failed) + " " + ex.localizedMessage)
        }
    }
    val baseColor = BackgroundColor
    val tintColor = DarkBlue
    val contentColor = DarkGrey
    val cornerShape = MaterialTheme.shapes.extraLarge

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(
                message = it
            )
        }
    }
    GlassCard(
        loading = isLoading
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.family_management_your_families),
                style = MaterialTheme.typography.titleSmall,
                color = contentColor
            )

            FamilyList(
                families = families,
                selectedFamily = selectedFamily,
                onSelect = { familyViewModel.selectFamily(it) }
            )
            Row(
                Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    onClick = { showJoinDialog = true },
                    enabled = !isLoading
                ) {
                    Icon(Icons.Default.QrCode, contentDescription = null, tint = tintColor)
                    Text(stringResource(R.string.family_management_join_button), color = tintColor)
                }
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
                        Text(stringResource(R.string.family_management_create_new_family))
                    }
                }
            }

            HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)
            selectedFamily?.let { family ->
                FamilyMemberSection(
                    family = family,
                    familyUsers = familyUsers,
                    isLoading = isLoading,
                    onRoleChange = { userId, newRole ->
                        familyViewModel.updateUserRole(family, userId, newRole, context)
                    },
                    onRemoveUser = { userId ->
                        familyViewModel.removeMember(family.id, userId, context)
                    },
                    familyViewModel = familyViewModel
                )

                HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)
            }
            // Baby selector row
            if (selectedFamily != null) {
                Text(
                    stringResource(R.string.family_management_babies_section),
                    style = MaterialTheme.typography.titleSmall,
                    color = contentColor
                )
                BabySelectorRow(
                    babies = babies,
                    selectedBaby = selectedBaby,
                    onSelectBaby = { babyViewModel.selectBaby(it) },
                    onAddBaby = null
                )
            }
            HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)

            // Editable Form
            Text(
                stringResource(
                    if (isEditFamily) R.string.family_management_edit_title
                    else R.string.family_management_create_title
                ),
                style = MaterialTheme.typography.titleMedium,
                color = contentColor
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = {
                    Text(
                        stringResource(R.string.family_management_family_name_label),
                        color = contentColor
                    )
                },
                enabled = !isLoading && (!isEditFamily || isCurrentAdmin),
                textStyle = LocalTextStyle.current.copy(color = contentColor),
                singleLine = true,
                shape = cornerShape,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                textStyle = LocalTextStyle.current.copy(color = contentColor),
                label = {
                    Text(
                        stringResource(R.string.family_management_description_label),
                        color = contentColor
                    )
                },
                enabled = !isLoading && (!isEditFamily || isCurrentAdmin),
                shape = cornerShape,
                modifier = Modifier.fillMaxWidth()
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = inviteCode,
                    onValueChange = {},
                    textStyle = LocalTextStyle.current.copy(color = contentColor),
                    label = {
                        Text(
                            stringResource(R.string.family_management_invite_code_label),
                            color = contentColor
                        )
                    },
                    readOnly = true,
                    shape = cornerShape,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { copyToClipboard(context, inviteCode, "invite_code") },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copier le code",
                            modifier = Modifier
                                .size(20.dp)
                                .clickable { copyToClipboard(context, inviteCode, "invite_code") },
                            tint = contentColor
                        )
                    }
                )
                IconButton(
                    onClick = { familyViewModel.regenerateCode(context) },
                    enabled = !isLoading && (!isEditFamily || isCurrentAdmin)
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.family_management_regenerate_code),
                        tint = tintColor
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    requireApproval,
                    onCheckedChange = { requireApproval = it },
                    enabled = !isLoading && (!isEditFamily || isCurrentAdmin)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.family_management_require_approval),
                    color = contentColor
                )
            }


            Row(
                Modifier
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            )
            {
                // Privacy
                IconSelector(
                    title = stringResource(R.string.family_management_privacy_level),
                    options = PrivacyLevel.entries.toList(),
                    selected = PrivacyLevel.entries.find { it.name == privacyLevel },
                    onSelect = { selected -> privacyLevel = selected.name },
                    getIcon = { level ->
                        when (level) {
                            PrivacyLevel.PRIVATE -> Icons.Default.Lock
                            PrivacyLevel.FAMILY_ONLY -> Icons.Default.Group
                        }
                    },
                    getLabel = { it.name.replace('_', ' ').lowercase() },
                    enabled = !isLoading && (!isEditFamily || isCurrentAdmin)
                )
            }

            // Save / leave buttons
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                selectedFamily?.let {
                    FamilyLeaveButton(
                        selectedFamily = selectedFamily,
                        familyViewModel = familyViewModel,
                        isLoading = isLoading
                    )
                }

                val createLabel =
                    if (isEditFamily) stringResource(R.string.family_management_save_button)
                    else
                        stringResource(R.string.family_management_create_button)


                Button(
                    onClick = {
                        val base = selectedFamily?.copy() ?: Family()
                        val updated = base.copy(
                            name = name,
                            description = description.ifBlank { null },
                            settings = base.settings.copy(
                                requireApprovalForNewMembers = requireApproval,
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
                Log.e("FamilyViewModel", it)
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
                showJoinDialog = false
                familyViewModel.joinByCode(code, context)
            },
            inviteResult = inviteResult,
            isLoading = isLoading
        )
    }
}

@Composable
private fun FamilyMemberSection(
    family: Family,
    familyUsers: List<FamilyUser>,
    isLoading: Boolean,
    onRoleChange: (String, FamilyRole) -> Unit,
    onRemoveUser: (String) -> Unit,
    familyViewModel: FamilyViewModel,
) {
    val tintColor = DarkGrey
    val isCurrentAdmin = familyViewModel.isCurrentUserAdmin()

    var userToRemove: FamilyUser? by remember { mutableStateOf(null) }
    val sortedUsers = familyUsers.sortedWith(
        compareBy<FamilyUser> { user ->
            when (user.role) {
                FamilyRole.ADMIN -> 0
                FamilyRole.MEMBER -> 1
                FamilyRole.VIEWER -> 2
            }
        }.thenBy { it.displayNameOrEmail }
    )
    Column {
        Text(
            stringResource(R.string.family_member_section_title),
            style = MaterialTheme.typography.titleSmall,
            color = tintColor
        )
        Spacer(Modifier.height(8.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            sortedUsers.forEach { user ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    data class RoleOption(
                        val role: FamilyRole?,
                        val icon: ImageVector,
                        val label: String,
                        val color: Color,
                        val isLeave: Boolean = false
                    )

                    val displayOptions = FamilyRole.entries.map { role ->
                        RoleOption(
                            role = role,
                            icon = role.icon,
                            label = role.label,
                            color = role.color,
                            isLeave = false
                        )
                    }.toMutableList().apply {
                        add(
                            RoleOption(
                                role = null,
                                icon = Icons.AutoMirrored.Filled.ExitToApp,
                                label = stringResource(R.string.delete_button),
                                color = Color.Red,
                                isLeave = true
                            )
                        )
                    }
                    IconSelector(
                        title = user.displayNameOrEmail,
                        options = displayOptions,
                        selected = displayOptions.first { !it.isLeave && it.role == user.role },
                        onSelect = { selectedOption ->
                            if (selectedOption.isLeave) {
                                // Trigger leave/remove flow
                                userToRemove = user
                            } else {
                                selectedOption.role?.let { role ->
                                    onRoleChange(user.userId, role)
                                }
                            }
                        },
                        getIcon = { it.icon },
                        getLabel = { it.label },
                        getColor = { it.color },
                        enabled = isCurrentAdmin && !isLoading
                    )
                }
            }
        }
    }
    // Confirmation Dialog
    userToRemove?.let { user ->
        val isOnlyAdmin = user.role == FamilyRole.ADMIN && family.adminIds.size == 1
        AlertDialog(
            onDismissRequest = { userToRemove = null },
            title = { Text(stringResource(R.string.family_leave_dialog_title)) },
            text = {
                Text(
                    stringResource(
                        if (isOnlyAdmin) {
                            R.string.family_user_leave_dialog_only_admin_message
                        } else {
                            R.string.family_user_leave_dialog_confirm_message
                        },

                        user.displayNameOrEmail,
                        family.name,
                    )
                )
            },
            confirmButton = {
                if (!isOnlyAdmin) {
                    TextButton(
                        onClick = {
                            onRemoveUser(user.userId)
                            userToRemove = null
                        }
                    ) {
                        Text(
                            stringResource(R.string.family_remove_dialog_confirm_button),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { userToRemove = null }) {
                    Text(
                        stringResource(
                            if (isOnlyAdmin) R.string.family_leave_dialog_dismiss_only_admin
                            else R.string.cancel_button
                        )
                    )
                }
            }
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
    val tintColor = DarkBlue
    Column {
        families.forEach { family ->
            val isSelected = family.id == selectedFamily?.id
            Surface(
                tonalElevation = if (isSelected) 4.dp else 0.dp,
                shape = MaterialTheme.shapes.medium,
                color = if (isSelected) tintColor.copy(alpha = 0.1f)
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
                        tint = if (isSelected) tintColor
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = family.name,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = if (isSelected) tintColor
                            else MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Selected",
                            tint = tintColor
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
        title = { Text(stringResource(R.string.join_family_dialog_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase().trim() },
                    label = { Text(stringResource(R.string.join_family_dialog_invite_code_label)) },
                    singleLine = true,
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth()
                )
                inviteResult?.onFailure { ex ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.join_family_dialog_unknown_error) + " " + ex.localizedMessage,
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
                Text(stringResource(R.string.join_family_dialog_join_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel_button))
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
    var context = LocalContext.current
    var showConfirmDialog by remember { mutableStateOf(false) }
    val nonNullUserIdFlow = familyViewModel.currentUserId.map { it.orEmpty() }
    val currentUserId: String by nonNullUserIdFlow
        .collectAsState(initial = "")
    selectedFamily?.let { family ->
        val isOnlyAdmin = familyViewModel.isCurrentUserAdmin() && family.adminIds.size == 1

        TextButton(
            onClick = { showConfirmDialog = true },
            enabled = !isLoading,
            colors = ButtonDefaults.textButtonColors(
                contentColor = if (isOnlyAdmin) Color.Red.copy(alpha = 0.5f)
                else Color.Red
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
                    stringResource(
                        if (isOnlyAdmin) R.string.family_leave_button_cannot_leave
                        else R.string.family_leave_button_leave
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }


        // Confirmation Dialog
        if (showConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showConfirmDialog = false },
                title = { Text(stringResource(R.string.family_leave_dialog_title)) },
                text = {
                    Text(
                        stringResource(
                            if (isOnlyAdmin) {
                                R.string.family_leave_dialog_only_admin_message
                            } else {
                                R.string.family_leave_dialog_confirm_message
                            },
                            family.name
                        )
                    )
                },
                confirmButton = {
                    if (!isOnlyAdmin) {
                        TextButton(
                            onClick = {
                                showConfirmDialog = false
                                familyViewModel.removeMember(
                                    family.id,
                                    currentUserId,
                                    context
                                )
                            }
                        ) {
                            Text(
                                stringResource(R.string.family_leave_dialog_confirm_button),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirmDialog = false }) {
                        Text(
                            stringResource(
                                if (isOnlyAdmin) R.string.family_leave_dialog_dismiss_only_admin
                                else R.string.cancel_button
                            )
                        )
                    }
                }
            )
        }
    }
}