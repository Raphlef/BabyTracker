package com.kouloundissa.twinstracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kouloundissa.twinstracker.R
import com.kouloundissa.twinstracker.data.CustomDrugType
import com.kouloundissa.twinstracker.data.drugIconOptions
import java.util.UUID

@Composable
fun CustomDrugTypeDialog(
    existingDrug: CustomDrugType? = null,
    onAdd: (CustomDrugType) -> Unit,
    onDelete: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    var name by remember(existingDrug) {
        mutableStateOf(existingDrug?.name ?: "")
    }
    var nameError by remember { mutableStateOf<String?>(null) }

    var selectedColor by remember(existingDrug) {
        mutableStateOf((existingDrug?.color ?: 0xFF9E9E9E))
    }
    var selectedIcon by remember(existingDrug) {
        mutableStateOf(existingDrug?.iconName ?: drugIconOptions.first().key)
    }
    val isEditMode = existingDrug != null
    val error = stringResource(R.string.error_drug_type_name_required)
    val buttonText =
        stringResource(if (isEditMode) R.string.drug_edit_label else R.string.drug_add_label)

    var showDeleteConfirm by remember { mutableStateOf(false) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .widthIn(min = 280.dp, max = 400.dp)
                .wrapContentHeight()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Text(
                    text = stringResource(
                        if (isEditMode) R.string.drug_edit_label else R.string.create_specify_drug_label
                    ),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Name field
                OutlinedTextField(
                    value = name,
                    onValueChange = { newName ->
                        name = newName.trim()
                        nameError = null
                    },
                    label = { Text(stringResource(R.string.specify_drug_name_title)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (nameError != null) {
                    Text(
                        text = nameError!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .align(Alignment.Start)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Color picker row (small palette)
                Text(
                    text = stringResource(R.string.drug_color_label),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(4.dp))

                AdvancedColorPicker(
                    selectedColor = selectedColor,
                    onColorSelected = { selectedColor = it }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Icon picker row
                Text(
                    text = stringResource(R.string.drug_icon_label),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(4.dp))
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 72.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.height(240.dp),
                ) {
                    items(drugIconOptions) { option ->
                        val isSelected = selectedIcon == option.key

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.medium)
                                .background(
                                    if (isSelected)
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                    else
                                        Color.Transparent
                                )
                                .clickable { selectedIcon = option.key }
                                .padding(12.dp)
                        ) {
                            Icon(
                                imageVector = option.icon,
                                contentDescription = null,
                                tint = if (isSelected)
                                    MaterialTheme.colorScheme.primary
                                else
                                    LocalContentColor.current.copy(alpha = 0.6f),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                }
                Spacer(modifier = Modifier.height(24.dp))

                //action
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Bouton Supprimer (seulement en mode édition)
                    if (isEditMode && onDelete != null) {
                        TextButton(
                            onClick = {
                                showDeleteConfirm = true
                            },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text(stringResource(R.string.delete_button))
                        }
                    }

                    // Reste des boutons aligné à droite
                    Spacer(modifier = Modifier.weight(1f))

                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel_button))
                    }

                    Button(
                        onClick = {
                            if (name.isBlank()) {
                                nameError = error
                                return@Button
                            }
                            focusManager.clearFocus()
                            onAdd(
                                CustomDrugType(
                                    id = existingDrug?.id ?: UUID.randomUUID().toString(),
                                    name = name,
                                    color = selectedColor.toLong(),
                                    iconName = selectedIcon
                                )
                            )
                        }
                    ) {
                        Text(
                            stringResource(
                                if (isEditMode) R.string.save_button
                                else R.string.add_label
                            )
                        )
                    }
                }
            }
        }
    }
    if (showDeleteConfirm && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            icon = {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text(stringResource(R.string.delete_drug) + "?") },
            text = {
                Text(
                    stringResource(
                        R.string.confirm_delete_treatment,
                        name
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                    }
                ) {
                    Text(stringResource(R.string.delete_button))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                }) {
                    Text(stringResource(R.string.cancel_button))
                }
            }
        )
    }
}