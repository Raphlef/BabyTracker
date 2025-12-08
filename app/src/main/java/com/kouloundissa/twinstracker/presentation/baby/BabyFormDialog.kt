package com.kouloundissa.twinstracker.presentation.baby

import android.annotation.SuppressLint
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import com.kouloundissa.twinstracker.R
import com.kouloundissa.twinstracker.data.Baby
import com.kouloundissa.twinstracker.data.BloodType
import com.kouloundissa.twinstracker.data.Gender
import com.kouloundissa.twinstracker.data.getDisplayName
import com.kouloundissa.twinstracker.presentation.viewmodel.BabyViewModel
import com.kouloundissa.twinstracker.presentation.viewmodel.EventViewModel
import com.kouloundissa.twinstracker.presentation.viewmodel.FamilyViewModel
import com.kouloundissa.twinstracker.ui.components.IconSelector
import com.kouloundissa.twinstracker.ui.components.ModernDateSelector
import com.kouloundissa.twinstracker.ui.components.PhotoPicker
import com.kouloundissa.twinstracker.ui.theme.BackgroundColor
import com.kouloundissa.twinstracker.ui.theme.DarkBlue
import com.kouloundissa.twinstracker.ui.theme.DarkGrey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BabyCreateDialog(
    onBabyCreated: (Baby) -> Unit,
    onCancel: () -> Unit,
) {
    BabyFormDialogInternal(
        babyToEdit = null,
        // Creation only: no delete
        onCompleted = { baby ->
            baby?.let(onBabyCreated)
        },
        onCancel = onCancel,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BabyEditDialog(
    babyToEdit: Baby,
    onBabyUpdated: (Baby?) -> Unit,
    onCancel: () -> Unit,
) {
    BabyFormDialogInternal(
        babyToEdit = babyToEdit,
        onCompleted = onBabyUpdated, // Handle update or null for delete
        onCancel = onCancel,
    )
}

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BabyFormDialogInternal(
    babyToEdit: Baby? = null,
    onCompleted: (Baby?) -> Unit,
    onCancel: () -> Unit,
    babyViewModel: BabyViewModel = hiltViewModel(),
    eventViewModel: EventViewModel = hiltViewModel(),
    familyViewModel: FamilyViewModel = hiltViewModel(),
) {
    val isEditMode = babyToEdit != null

    // VM states
    val isLoading by babyViewModel.isLoading.collectAsState()
    val babyError by babyViewModel.errorMessage.collectAsState()
    val babies by babyViewModel.babies.collectAsState()
    val selectedBaby by babyViewModel.selectedBaby.collectAsState()

    val selectedFamily by familyViewModel.selectedFamily.collectAsState()

    // Resolve latest baby reference when editing
    val currentBaby = remember(babies, babyToEdit) {
        babyToEdit?.let { b -> babies.find { it.id == b.id } ?: babyToEdit }
    }
    var saveRequested by remember { mutableStateOf(false) }
    var deleteRequested by remember { mutableStateOf(false) }
    var wasLoading by remember { mutableStateOf(false) }

    val openDeleteDialog = remember { mutableStateOf(false) }
    var savedBabyLocal by remember { mutableStateOf<Baby?>(null) }

    // Form State
    val formState = rememberBabyFormState(currentBaby)

    LaunchedEffect(isLoading, selectedBaby, babyError) {
        if (saveRequested && wasLoading && !isLoading) {
            if (babyError == null && selectedBaby != null) {
                // Save successful
                onCompleted(selectedBaby)
                saveRequested = false
            } else if (babyError != null) {
                // Error occurred, keep dialog open
                saveRequested = false
            }
        }
        wasLoading = isLoading
    }

    // ✅ Listen for delete completion
    LaunchedEffect(deleteRequested, isLoading, babies) {
        if (deleteRequested && !isLoading) {
            // Check if baby was actually deleted
            val babyStillExists = currentBaby?.let { b ->
                babies.any { it.id == b.id }
            } ?: false

            if (!babyStillExists) {
                // Delete successful
                onCompleted(null)
                deleteRequested = false
            }
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = Modifier.fillMaxSize()
    ) {
        BabyFormBottomSheetContent(
            state = formState,
            isEditMode = isEditMode,
            currentBaby = currentBaby,
            babyError = babyError,
            onOpenDeleteDialog = { openDeleteDialog.value = true },
            onSave = { babyData, newPhotoUri, photoRemoved ->
                saveRequested = true
                babyViewModel.saveBaby(
                    selectedFamily,
                    id = babyData.id,
                    name = babyData.name,
                    birthDate = babyData.birthDate,
                    gender = babyData.gender,
                    birthWeightKg = babyData.birthWeightKg,
                    birthLengthCm = babyData.birthLengthCm,
                    birthHeadCircumferenceCm = babyData.birthHeadCircumferenceCm,
                    birthTime = babyData.birthTime,
                    bloodType = babyData.bloodType,
                    allergies = babyData.allergies,
                    medicalConditions = babyData.medicalConditions,
                    pediatricianContact = babyData.pediatricianContact,
                    notes = babyData.notes,
                    existingPhotoUrl = currentBaby?.photoUrl,
                    newPhotoUri = newPhotoUri,
                    photoRemoved = photoRemoved
                )
                selectedBaby?.let { it1 ->
                    savedBabyLocal = it1
                    onCompleted(savedBabyLocal)
                }
            }
        )
    }

    // Delete confirmation dialog remains as AlertDialog for critical action
    if (isEditMode && openDeleteDialog.value && currentBaby != null) {
        DeleteConfirmationDialog(
            babyName = currentBaby.name,
            totalEventsProvider = {
                val counts = eventViewModel.getEventCounts()
                counts.values.sum()
            },
            onConfirm = {
                deleteRequested = true
                openDeleteDialog.value = false
                babyViewModel.deleteBaby(currentBaby.id)
            },
            onDismiss = { openDeleteDialog.value = false }
        )
    }
}
/* -----------------------
   State holder and mapping
   ----------------------- */

@Stable
class BabyFormState(
    initial: Baby?
) {
    // Core fields
    var name by mutableStateOf(initial?.name.orEmpty())
    var birthDateTimeMillis by mutableStateOf(initial?.birthDate ?: System.currentTimeMillis())

    // Photo
    var newPhotoUrl by mutableStateOf<Uri?>(null)
    var photoRemoved by mutableStateOf(false)

    // Selectors
    var gender by mutableStateOf(initial?.gender ?: Gender.UNKNOWN)
    var bloodType by mutableStateOf(
        initial?.bloodType?.let { bt -> BloodType.entries.firstOrNull { it == bt } }
            ?: BloodType.UNKNOWN
    )

    // Numeric fields
    var weight by mutableStateOf(initial?.birthWeightKg?.toString().orEmpty())
    var lengthCm by mutableStateOf(initial?.birthLengthCm?.toString().orEmpty())
    var headCirc by mutableStateOf(initial?.birthHeadCircumferenceCm?.toString().orEmpty())


    // Free text fields
    var allergies by mutableStateOf(initial?.allergies?.joinToString(", ").orEmpty())
    var conditions by mutableStateOf(initial?.medicalConditions?.joinToString(", ").orEmpty())
    var pediatricianContact by mutableStateOf(initial?.pediatricianContact.orEmpty())
    var notes by mutableStateOf(initial?.notes.orEmpty())

    // Errors
    var nameError by mutableStateOf(false)
    var weightError: String? by mutableStateOf(null)
    var lengthError: String? by mutableStateOf(null)
    var headCircError: String? by mutableStateOf(null)
    var timeError: String? by mutableStateOf(null)

    val birthDateDisplay: String
        get() = SimpleDateFormat("EEE, MMM dd, yyyy • HH:mm", Locale.getDefault())
            .format(Date(birthDateTimeMillis))

    fun validate(): Boolean {
        nameError = name.isBlank()
        // Numeric simple validation hooks remain extensible
        // Let NumericField composables set their own error messages via lambdas as in original
        return !nameError
    }

    fun toBabyTriple(isEditMode: Boolean, original: Baby?): Triple<Baby, Uri?, Boolean> {
        val now = System.currentTimeMillis()
        val baby = Baby(
            id = if (isEditMode) (original?.id ?: "") else "",
            name = name.trim(),
            birthDate = birthDateTimeMillis,
            birthTime = birthDateDisplay,
            gender = gender,
            birthWeightKg = weight.toDoubleOrNull(),
            birthLengthCm = lengthCm.toDoubleOrNull(),
            birthHeadCircumferenceCm = headCirc.toDoubleOrNull(),
            bloodType = bloodType,
            allergies = allergies.split(",").map { it.trim() }.filter { it.isNotEmpty() },
            medicalConditions = conditions.split(",").map { it.trim() }
                .filter { it.isNotEmpty() },
            pediatricianContact = pediatricianContact.ifBlank { null },
            notes = notes.ifBlank { null },
            createdAt = original?.createdAt ?: now,
            updatedAt = now,
            photoUrl = original?.photoUrl // placeholder as before
        )
        return Triple(baby, newPhotoUrl, photoRemoved)
    }
}

@Composable
private fun BabyFormBottomSheetContent(
    state: BabyFormState,
    isEditMode: Boolean,
    currentBaby: Baby?,
    babyError: String?,
    onOpenDeleteDialog: () -> Unit,
    onSave: (Baby, Uri?, Boolean) -> Unit,
    babyViewModel: BabyViewModel = hiltViewModel(),
    familyViewModel: FamilyViewModel = hiltViewModel(),
) {
    val backgroundColor = BackgroundColor
    val contentColor = DarkGrey
    val tint = DarkBlue
    val cornerShape = MaterialTheme.shapes.extraLarge

    val selectedFamily by familyViewModel.selectedFamily.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .navigationBarsPadding()
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = if (isEditMode)
                    stringResource(id = R.string.baby_form_title_edit)
                else
                    stringResource(id = R.string.baby_form_title_create),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = backgroundColor,
            )
        }
        Spacer(Modifier.height(8.dp))
        // Form content with scroll
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {

            Column {
                BabyFormContent(
                    state = state,
                    isEditMode = isEditMode,
                    existingPhotoUrl = currentBaby?.photoUrl,
                    onRequestDeletePhoto = {
                        if (isEditMode && currentBaby != null) {
                            babyViewModel.deleteBabyPhoto(currentBaby.id, selectedFamily)
                        }
                    }
                )

                babyError?.let {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        it,
                        color = Color.Red, // MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        // Action buttons - fixed at bottom
        BabyFormActionButtons(
            isEditMode = isEditMode,
            onDelete = onOpenDeleteDialog,
            onSave = {
                Log.d("BabyForm", "request to save baby")
                // Validate required fields
                val valid = state.validate()
                if (!valid) return@BabyFormActionButtons

                val (babyData, newPhotoUri, photoRemoved) = state.toBabyTriple(
                    isEditMode,
                    currentBaby
                )
                onSave(babyData, newPhotoUri, photoRemoved)
            }
        )
    }
}

@Composable
private fun BabyFormActionButtons(
    isEditMode: Boolean,
    onDelete: () -> Unit,
    onSave: () -> Unit,
    babyViewModel: BabyViewModel = hiltViewModel(),
) {
    val backgroundColor = BackgroundColor
    val contentColor = DarkGrey
    val tint = DarkBlue
    val cornerShape = MaterialTheme.shapes.extraLarge

    val isLoading by babyViewModel.isLoading.collectAsState()

    val haptic = LocalHapticFeedback.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 64.dp)
            .background(
                color = backgroundColor.copy(alpha = 0.7f),
                shape = cornerShape
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            // Delete button - only in edit mode
            if (isEditMode) {
                OutlinedButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onDelete()
                    },
                    enabled = !isLoading,
                    shape = cornerShape,
                    border = BorderStroke(
                        1.dp,
                        if (isLoading) Color.Gray else Color.Red
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.Red,
                        disabledContentColor = Color.Gray
                    )
                ) {
                    Text(stringResource(id = R.string.delete_button_confirm))
                }
            }

            Spacer(Modifier.weight(1f))

            // Save/Create button
            Button(
                onClick = onSave,
                enabled = !isLoading,
                shape = cornerShape,
                modifier = Modifier
                    .height(56.dp)
                    .pointerInput(Unit) {
                        // ✅ Force la capture des clics
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (isEditMode)
                            stringResource(id = R.string.saving_button)
                        else
                            stringResource(id = R.string.baby_form_creating_button)
                    )
                } else {
                    Text(
                        if (isEditMode)
                            stringResource(id = R.string.baby_form_save_button)
                        else
                            stringResource(id = R.string.baby_form_create_button)
                    )
                }
            }
        }
    }
}

@Composable
fun rememberBabyFormState(initial: Baby?): BabyFormState {
    // Using rememberSaveable selectively for UX persistence if dialog recomposes.
    // Avoid serializing Calendar directly; persist millis.
    val initialMillis = initial?.birthDate
    val state = rememberSaveable(
        inputs = arrayOf(initial?.id, initialMillis),
        saver = listSaver(
            save = { s ->
                listOf(
                    s.name,
                    s.birthDateTimeMillis,
                    s.newPhotoUrl?.toString(),
                    s.photoRemoved,
                    s.gender.name,
                    s.bloodType.name,
                    s.weight,
                    s.lengthCm,
                    s.headCirc,
                    s.allergies,
                    s.conditions,
                    s.pediatricianContact,
                    s.notes
                )
            },
            restore = { list ->
                val dummy = BabyFormState(initial)
                dummy.name = list[0] as String
                dummy.birthDateTimeMillis = list[1] as Long
                dummy.newPhotoUrl = (list[2] as String?)?.let { Uri.parse(it) }
                dummy.photoRemoved = list[3] as Boolean
                dummy.gender = Gender.valueOf(list[4] as String)
                dummy.bloodType = BloodType.valueOf(list[5] as String)
                dummy.weight = list[6] as String
                dummy.lengthCm = list[7] as String
                dummy.headCirc = list[8] as String
                dummy.allergies = list[9] as String
                dummy.conditions = list[10] as String
                dummy.pediatricianContact = list[11] as String
                dummy.notes = list[12] as String
                dummy
            }
        )
    ) { BabyFormState(initial) }
    return state
}

/* -----------------------
   Form content
   ----------------------- */

@Composable
private fun BabyFormContent(
    state: BabyFormState,
    isEditMode: Boolean,
    existingPhotoUrl: String?,
    onRequestDeletePhoto: () -> Unit,
) {
    val backgroundColor = BackgroundColor
    val contentColor = DarkGrey
    val tint = DarkBlue
    val cornerShape = MaterialTheme.shapes.extraLarge
    val context = LocalContext.current

    val contactLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickContact()
    ) { uri ->
        uri?.let {
            val cursor = context.contentResolver.query(
                it,
                arrayOf(ContactsContract.Contacts.DISPLAY_NAME),
                null, null, null
            )
            cursor?.use { c ->
                if (c.moveToFirst()) state.pediatricianContact = c.getString(0)
            }
        }
    }

    OutlinedTextField(
        value = state.name,
        onValueChange = {
            state.name = it
            if (state.nameError) state.nameError = false
        },
        textStyle = LocalTextStyle.current.copy(color = backgroundColor),
        label = { Text(stringResource(id = R.string.baby_name_label), color = backgroundColor) },
        isError = state.nameError,
        modifier = Modifier.fillMaxWidth(),
        shape = cornerShape,
    )
    if (state.nameError) Text(stringResource(id = R.string.baby_name_error), color = Color.Red)

    Spacer(Modifier.height(16.dp))

    PhotoPicker(
        photoUrl = state.newPhotoUrl ?: existingPhotoUrl?.toUri(),
        onPhotoSelected = {
            state.newPhotoUrl = it
            state.photoRemoved = false
        },
        onPhotoRemoved = {
            if (isEditMode) onRequestDeletePhoto()
            state.newPhotoUrl = null
            state.photoRemoved = true
        }
    )
    Spacer(Modifier.height(16.dp))

    ModernDateSelector(
        label = stringResource(id = R.string.date_of_birth_label),
        selectedDate = Date(state.birthDateTimeMillis),
        onDateSelected = { dt -> state.birthDateTimeMillis = dt.time },
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(Modifier.height(16.dp))

    IconSelector(
        title = stringResource(id = R.string.gender_label),
        options = Gender.entries,
        selected = state.gender,
        onSelect = { state.gender = it },
        getIcon = { it.icon },
        getColor = { it.color },
        getLabel = { it.getDisplayName(context) }
    )
    Spacer(Modifier.height(16.dp))

    NumericFieldSection(
        label = stringResource(id = R.string.weight_form_label),
        value = state.weight,
        onChange = { state.weight = it },
        error = state.weightError,
        onErrorChange = { state.weightError = it }
    )

    Spacer(Modifier.height(12.dp))

    NumericFieldSection(
        label = stringResource(id = R.string.length_form_label),
        value = state.lengthCm,
        onChange = { state.lengthCm = it },
        error = state.lengthError,
        onErrorChange = { state.lengthError = it }
    )

    Spacer(Modifier.height(12.dp))

    NumericFieldSection(
        label = stringResource(id = R.string.head_circumference),
        value = state.headCirc,
        onChange = { state.headCirc = it },
        error = state.headCircError,
        onErrorChange = { state.headCircError = it }
    )

    Spacer(Modifier.height(12.dp))


    IconSelector(
        title = stringResource(id = R.string.blood_type_label),
        options = BloodType.entries.toList(),
        selected = state.bloodType,
        onSelect = { state.bloodType = it },
        getIcon = { it.icon },
        getColor = { it.color },
        getLabel = { it.name }
    )
    Spacer(Modifier.height(12.dp))

    OutlinedTextField(
        value = state.allergies,
        onValueChange = { state.allergies = it },
        textStyle = LocalTextStyle.current.copy(color = backgroundColor),
        label = { Text(stringResource(id = R.string.allergies_label), color = backgroundColor) },
        shape = cornerShape,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(12.dp))

    OutlinedTextField(
        value = state.conditions,
        onValueChange = { state.conditions = it },
        textStyle = LocalTextStyle.current.copy(color = backgroundColor),
        label = {
            Text(
                stringResource(id = R.string.medical_conditions_label),
                color = backgroundColor
            )
        },
        modifier = Modifier.fillMaxWidth(),
        shape = cornerShape,
    )
    Spacer(Modifier.height(12.dp))

    OutlinedTextField(
        value = state.pediatricianContact,
        onValueChange = { /* read-only */ },
        textStyle = LocalTextStyle.current.copy(color = backgroundColor),
        label = {
            Text(
                stringResource(id = R.string.pediatrician_contact_label),
                color = backgroundColor
            )
        },
        readOnly = true,
        singleLine = true,
        trailingIcon = {
            IconButton(onClick = { contactLauncher.launch(null) }) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = stringResource(id = R.string.pick_contact_description),
                    tint = backgroundColor
                )
            }
        },
        shape = cornerShape,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = state.notes,
        onValueChange = { state.notes = it },
        textStyle = LocalTextStyle.current.copy(color = backgroundColor),
        label = { Text(stringResource(id = R.string.notes_label), color = backgroundColor) },
        shape = cornerShape,
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
    )
}

/* -----------------------
   Subcomponents
   ----------------------- */


@Composable
private fun NumericFieldSection(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    error: String?,
    onErrorChange: (String?) -> Unit
) {
    val context = LocalContext.current
    val contentColor = Color.White
    val cornerShape = MaterialTheme.shapes.extraLarge

    OutlinedTextField(
        value = value,
        onValueChange = { new ->
            // Allow only digits and at most one dot
            val filtered = new.filter { it.isDigit() || it == '.' }
            val cleaned = if (filtered.count { it == '.' } > 1) value else filtered
            onChange(cleaned)
            onErrorChange(
                if (cleaned.isNotBlank() && cleaned.toDoubleOrNull() == null)
                    context.getString(R.string.invalid_number) else null
            )
        },
        textStyle = LocalTextStyle.current.copy(color = contentColor),
        label = { Text(label, color = contentColor) },
        isError = error != null,
        singleLine = true,
        shape = cornerShape,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),

        )
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
}
/* -----------------------
   Delete dialog
   ----------------------- */

@Composable
private fun DeleteConfirmationDialog(
    babyName: String,
    totalEventsProvider: () -> Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val totalEvents = remember { totalEventsProvider() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id = R.string.delete_baby_title, babyName)) },
        text = {
            Column {
                Text(stringResource(id = R.string.delete_baby_irreversible))
                if (totalEvents > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(id = R.string.delete_baby_events_warning, totalEvents),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(id = R.string.delete_button_confirm), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(id = R.string.cancel_button_confirm))
            }
        }
    )
}

