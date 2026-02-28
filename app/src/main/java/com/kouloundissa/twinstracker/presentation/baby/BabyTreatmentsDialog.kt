package com.kouloundissa.twinstracker.presentation.baby

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.kouloundissa.twinstracker.R
import com.kouloundissa.twinstracker.data.BabyTreatment
import com.kouloundissa.twinstracker.data.CustomDrugType
import com.kouloundissa.twinstracker.data.DrugType
import com.kouloundissa.twinstracker.data.TreatmentFrequencyType
import com.kouloundissa.twinstracker.data.buildTreatmentSummary
import com.kouloundissa.twinstracker.data.getDisplayName
import com.kouloundissa.twinstracker.data.toUiModel
import com.kouloundissa.twinstracker.presentation.viewmodel.FamilyViewModel
import com.kouloundissa.twinstracker.ui.components.CustomDrugTypeDialog
import com.kouloundissa.twinstracker.ui.components.IconSelector
import com.kouloundissa.twinstracker.ui.theme.BackgroundColor
import com.kouloundissa.twinstracker.ui.theme.DarkBlue
import com.kouloundissa.twinstracker.ui.theme.DarkGrey
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BabyTreatmentsDialog(
    treatments: List<BabyTreatment>,
    onDismiss: () -> Unit,
    onSave: (List<BabyTreatment>) -> Unit,
    modifier: Modifier = Modifier,
    familyViewModel: FamilyViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    var treatments by remember { mutableStateOf(treatments) }
    var editingTreatment by remember { mutableStateOf<BabyTreatment?>(null) }
    var showTreatmentFormDialog by remember { mutableStateOf(false) }

    var treatmentToDelete by remember { mutableStateOf<BabyTreatment?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val selectedFamily by familyViewModel.selectedFamily.collectAsState()
    val customOptions = selectedFamily?.settings?.customDrugTypes.orEmpty()

    AlertDialog(
        containerColor = BackgroundColor,
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = { Text(stringResource(R.string.treatments_label)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
            ) {
                if (treatments.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.no_treatment_configured),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(
                            items = treatments,
                            key = { it.id }
                        ) { treatment ->
                            TreatmentItem(
                                treatment = treatment,
                                onEdit = {
                                    editingTreatment = treatment
                                    showTreatmentFormDialog = true
                                },
                                onDelete = {
                                    treatmentToDelete = treatment
                                    showDeleteConfirm = true
                                }
                            )
                        }
                    }
                }
                // Add button at bottom of list, above the actions
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start
                ) {
                    TextButton(
                        onClick = {
                            editingTreatment = null
                            showTreatmentFormDialog = true
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.add_treatment))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(treatments) }) {
                Text(stringResource(R.string.save_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel_button))
            }
        }
    )

    if (showTreatmentFormDialog) {
        TreatmentFormDialog(
            existing = editingTreatment,
            onDismiss = {
                showTreatmentFormDialog = false
                editingTreatment = null
            },
            onSave = { newTreatment ->
                treatments = if (editingTreatment == null) {
                    treatments + newTreatment
                } else {
                    treatments.map {
                        if (it.id == newTreatment.id) newTreatment else it
                    }
                }
                showTreatmentFormDialog = false
                editingTreatment = null
            },
            onDelete = {
                treatmentToDelete = editingTreatment
                showDeleteConfirm = true
            }
        )
    }
    if (showDeleteConfirm && treatmentToDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            icon = {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text(stringResource(R.string.delete_treatment) + "?") },
            text = {
                Text(
                    stringResource(
                        R.string.confirm_delete_treatment,
                        buildTreatmentSummary(
                            treatmentToDelete!!,
                            LocalContext.current,
                            customOptions
                        )
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        treatments = treatments.filterNot { it.id == treatmentToDelete!!.id }
                        showDeleteConfirm = false
                        treatmentToDelete = null
                    }
                ) {
                    Text(stringResource(R.string.delete_button))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    treatmentToDelete = null
                }) {
                    Text(stringResource(R.string.cancel_button))
                }
            }
        )
    }
}

@Composable
fun TreatmentItem(
    treatment: BabyTreatment,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    familyViewModel: FamilyViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    val selectedFamily by familyViewModel.selectedFamily.collectAsState()
    val customOptions = selectedFamily?.settings?.customDrugTypes.orEmpty()
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        onClick = onEdit,
        colors = CardDefaults.cardColors(containerColor = DarkGrey.copy(alpha = 0.1f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            val drugName =
                if (treatment.drugType == DrugType.CUSTOM) {
                    customOptions.find { it.id == treatment.customDrugTypeId }?.name
                        ?: stringResource(treatment.drugType.displayNameRes)
                } else {
                    stringResource(treatment.drugType.displayNameRes)
                }
            Text(
                text = drugName,
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = buildTreatmentSummary(treatment, LocalContext.current, customOptions),
                style = MaterialTheme.typography.bodyMedium
            )

            treatment.dosage?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text(  text = stringResource(R.string.dosage_label, it))
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth()
            ) {
                TextButton(onClick = onEdit) {
                    Text(stringResource(R.string.edit_treatment))
                }
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error  // Rouge simple
                    )
                ) {
                    Text(stringResource(R.string.delete_treatment))
                }
            }
        }
    }
}

@Composable
fun TreatmentFormDialog(
    existing: BabyTreatment? = null,
    onDismiss: () -> Unit,
    onSave: (BabyTreatment) -> Unit,
    onDelete: (() -> Unit)? = null,
    familyViewModel: FamilyViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    var showCustomDrugTypeDialog by remember { mutableStateOf(false) }
    var editingDrug by remember { mutableStateOf<CustomDrugType?>(null) }

    val selectedFamily by familyViewModel.selectedFamily.collectAsState()
    val builtInOptions = remember {
        DrugType.entries.filter { it != DrugType.CUSTOM }.map { it.toUiModel(context) }
    }
    val customOptions = remember(selectedFamily?.settings?.customDrugTypes) {
        selectedFamily?.settings?.customDrugTypes.orEmpty().map { it.toUiModel() }
    }
    val allOptions = remember(builtInOptions, customOptions, DrugType.CUSTOM) {
        builtInOptions + customOptions
    }

    var drugType by remember { mutableStateOf(existing?.drugType ?: DrugType.PARACETAMOL) }
    var dosage by remember { mutableStateOf(existing?.dosage ?: "") }
    var frequencyType by remember {
        mutableStateOf(existing?.frequencyType ?: TreatmentFrequencyType.DAILY)
    }
    var customDrugTypeId by remember {
        mutableStateOf(existing?.customDrugTypeId)
    }
    var interval by remember {
        mutableStateOf(existing?.frequencyInterval?.toString() ?: "1")
    }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }

    val selectedOption = remember(drugType, customDrugTypeId) {
        when {
            drugType == DrugType.CUSTOM && customDrugTypeId != null ->
                allOptions.firstOrNull { it.isCustom && it.backingCustomId == customDrugTypeId }

            else -> allOptions.firstOrNull { !it.isCustom && it.backingEnum == drugType }
        }
    }
    AlertDialog(
        containerColor = BackgroundColor,
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        confirmButton = {
            TextButton(
                onClick = {
                    val newTreatment = BabyTreatment(
                        id = existing?.id ?: UUID.randomUUID().toString(),
                        drugType = drugType,
                        customDrugTypeId = customDrugTypeId,
                        dosage = dosage.ifBlank { null },
                        frequencyType = frequencyType,
                        frequencyInterval = interval.toIntOrNull() ?: 1,
                        notes = notes.ifBlank { null },
                        updatedAt = System.currentTimeMillis()
                    )
                    onSave(newTreatment)
                }
            ) {
                Text(stringResource(R.string.save_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel_button))
            }
        },
        title = {
            Text(if (existing == null) stringResource(R.string.add_treatment) else stringResource(R.string.edit_treatment))
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Drug selector
                IconSelector(
                    title = stringResource(id = R.string.drug_type_label),
                    options = allOptions,
                    selected = selectedOption,
                    onSelect = { selected ->
                        drugType = selected.backingEnum ?: DrugType.CUSTOM
                        customDrugTypeId = selected.backingCustomId
                    },
                    getColor = { it.color },
                    getIcon = { it.icon },
                    getLabel = { it.label },
                    onAddCustom = { showCustomDrugTypeDialog = true },
                    onLongPress = { selectedOption ->
                        if (selectedOption.backingCustomId != null) {
                            editingDrug =
                                selectedFamily?.settings?.customDrugTypes?.find { it.id == selectedOption.backingCustomId }
                            showCustomDrugTypeDialog = true
                        }
                    }
                )
                OutlinedTextField(
                    value = dosage,
                    onValueChange = { dosage = it },
                    label = { Text(stringResource(R.string.dosage_information_title)) },
                    modifier = Modifier.fillMaxWidth()
                )

                IconSelector(
                    title = stringResource(id = R.string.frequency_label),
                    options = TreatmentFrequencyType.entries,
                    selected = frequencyType,
                    onSelect = { frequencyType = it },
                    getIcon = { it.icon },
                    getLabel = { it.getDisplayName(context) },
                    getColor = { it.color }
                )

                OutlinedTextField(
                    value = interval,
                    onValueChange = { interval = it.filter { c -> c.isDigit() } },
                    label = { Text(stringResource(R.string.interval)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.notes_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Spacer(Modifier.weight(1f))

                    // Delete button only when editing and onDelete is provided
                    if (existing != null && onDelete != null) {
                        TextButton(
                            onClick = onDelete,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text(stringResource(R.string.delete_button))
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                }
            }
        }
    )
    if (showCustomDrugTypeDialog) {
        CustomDrugTypeDialog(
            existingDrug = editingDrug,
            onAdd = { newCustom ->
                selectedFamily?.let { family ->
                    val currentCustomTypes = family.settings.customDrugTypes

                    val updatedCustomTypes = if (editingDrug != null) {
                        // Mode édition : REMPLACER l'existant par le modifié
                        currentCustomTypes.map {
                            if (it.id == editingDrug!!.id) newCustom else it
                        }
                    } else {
                        // Mode création : AJOUTER le nouveau
                        currentCustomTypes + newCustom
                    }

                    val updated = family.copy(
                        settings = family.settings.copy(customDrugTypes = updatedCustomTypes)
                    )
                    familyViewModel.createOrUpdateFamily(updated)
                }
                showCustomDrugTypeDialog = false
            },
            onDelete = {
                editingDrug?.let { drugToDelete ->
                    selectedFamily?.let { family ->
                        val currentCustomTypes = family.settings.customDrugTypes
                        val updatedCustomTypes =
                            currentCustomTypes.filter { it.id != drugToDelete.id }

                        val updated = family.copy(
                            settings = family.settings.copy(customDrugTypes = updatedCustomTypes)
                        )
                        familyViewModel.createOrUpdateFamily(updated)
                    }
                }
                showCustomDrugTypeDialog = false
            },
            onDismiss = { showCustomDrugTypeDialog = false }
        )
    }
}

@Composable
fun TreatmentSummaryCard(
    treatments: List<BabyTreatment>,
    onTreatmentsClick: () -> Unit,
    onAddNewTreatment: () -> Unit,
    onTreatmentEdit: (BabyTreatment) -> Unit,
    familyViewModel: FamilyViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    val backgroundcolor = BackgroundColor.copy(alpha = 0.5f)
    val contentColor = DarkGrey
    val tint = DarkBlue

    val selectedFamily by familyViewModel.selectedFamily.collectAsState()
    val customOptions = selectedFamily?.settings?.customDrugTypes.orEmpty()

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = backgroundcolor,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onTreatmentsClick() }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.treatments_label),
                style = MaterialTheme.typography.titleSmall,
                color = contentColor
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (treatments.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_treatment_configured),
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor
                )
            } else {
                treatments.take(3).forEach { treatment ->
                    TreatmentItem(
                        treatment = treatment,
                        customOptions = customOptions,
                        onEdit = { onTreatmentEdit(treatment) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (treatments.size > 3) {
                    Text(
                        "… +${treatments.size - 3}",
                        color = contentColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            TextButton(
                onClick = onAddNewTreatment,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = tint
                ),
                modifier = Modifier.align(Alignment.Start)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.add_treatment))
            }
        }
    }
}

@Composable
private fun TreatmentItem(
    treatment: BabyTreatment,
    customOptions: List<CustomDrugType>,
    onEdit: () -> Unit
) {
    val context = LocalContext.current

    val contentColor = DarkGrey
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Nom + bouton edit inline
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = buildTreatmentSummary(treatment, context, customOptions),
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
                modifier = Modifier.weight(1f),
                maxLines = 1
            )
            IconButton(onClick = onEdit, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Edit, null, tint = DarkBlue)
            }
        }
    }
}