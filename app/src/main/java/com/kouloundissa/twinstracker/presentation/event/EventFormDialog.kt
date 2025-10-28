package com.kouloundissa.twinstracker.presentation.event

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kouloundissa.twinstracker.data.*
import com.kouloundissa.twinstracker.presentation.viewmodel.EventViewModel
import androidx.compose.foundation.Image
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import com.kouloundissa.twinstracker.R
import com.kouloundissa.twinstracker.data.EventFormState.*
import com.kouloundissa.twinstracker.presentation.viewmodel.BabyViewModel
import com.kouloundissa.twinstracker.ui.components.BabySelectorRow
import com.kouloundissa.twinstracker.ui.components.IconSelector
import com.kouloundissa.twinstracker.ui.components.ModernDateSelector
import com.kouloundissa.twinstracker.ui.components.PhotoPicker
import com.kouloundissa.twinstracker.ui.theme.*
import java.util.*


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventFormDialog(
    initialBabyId: String,
    onDismiss: () -> Unit,
    initialEventType: EventType? = null,
    viewModel: EventViewModel = hiltViewModel(),
    babyViewModel: BabyViewModel = hiltViewModel(),
) {
    val formState by viewModel.formState.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val saveSuccess by viewModel.saveSuccess.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    val isDeleting by viewModel.isDeleting.collectAsState()
    val deleteSuccess by viewModel.deleteSuccess.collectAsState()
    val deleteError by viewModel.deleteError.collectAsState()
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val currentType = formState.eventType

    val initialPhotoUri = formState.photoUrl?.let { Uri.parse(it) }
    var selectedUri by rememberSaveable { mutableStateOf<Uri?>(initialPhotoUri) }

    val footerHeight = 72.dp

    var selectedDate by remember(formState.eventTimestamp) {
        mutableStateOf(formState.eventTimestamp)
    }
    val contentColor = Color.White
    val cornerShape = MaterialTheme.shapes.extraLarge

    val babies by babyViewModel.babies.collectAsState()
    val selectedBaby by babyViewModel.selectedBaby.collectAsState()

    // Initialize selectedBaby on first composition
    LaunchedEffect(babies, initialBabyId) {
        if (babies.isNotEmpty() && selectedBaby == null) {
            val toSelect = initialBabyId?.let { id ->
                babies.find { it.id == id }
            } ?: babies.first()
            babyViewModel.selectBaby(toSelect)
        }
    }

    LaunchedEffect(initialEventType) {
        initialEventType?.let {
            val newState = when (it) {
                EventType.DIAPER -> Diaper()
                EventType.FEEDING -> Feeding()
                EventType.SLEEP -> Sleep()
                EventType.GROWTH -> Growth()
                EventType.PUMPING -> Pumping()
                EventType.DRUGS -> Drugs()
            }
            viewModel.updateForm { newState }
        }
    }
    LaunchedEffect(selectedBaby, currentType) {
        if (formState.eventId == null && currentType == EventType.GROWTH) {
            selectedBaby?.let { viewModel.loadLastGrowth(it.id) };
        }
    }

    LaunchedEffect(saveSuccess, deleteSuccess) {
        if (saveSuccess || deleteSuccess) {
            onDismiss()
            viewModel.resetSaveSuccess()
            viewModel.resetDeleteState()
        }
    }
    // Confirmation dialog
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Event") },
            text = { Text("Are you sure you want to delete this event?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteEvent(formState.eventId!!, initialBabyId)
                    showDeleteConfirm = false
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,  // skips intermediate state to start fully expanded
    )
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Transparent,
    ) {
        Surface(
            shape = cornerShape,
            tonalElevation = 8.dp,
            color = Color.Transparent,// MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()          // use full width
                .fillMaxHeight(0.75f)
        ) {

            Image(
                painter = painterResource(id = R.drawable.background),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(8.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                currentType.color.copy(alpha = 0.35f),
                                currentType.color.copy(alpha = 0.15f)
                            )
                        ),
                        shape = cornerShape,
                    )
                    .padding(horizontal = 12.dp, vertical = 12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 20.dp)
                        .padding(bottom = footerHeight)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Header
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (formState.eventId == null) "Add Event" else "Edit Event",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = contentColor,
                        )
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    CircleShape
                                )
                                .size(40.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                    val displayMessage = errorMessage ?: deleteError
                    if (displayMessage != null) {
                        Surface(
                            color = Color.Transparent,
                            shape = cornerShape
                        ) {
                            Text(
                                errorMessage!!,
                                color = Color.Red,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    BabySelectorRow(
                        babies = babies,
                        selectedBaby = selectedBaby,
                        onSelectBaby = {
                            babyViewModel.selectBaby(it)
                        },
                        onAddBaby = null
                    )
                    // Event Type Selector (only for new events)
                    val isEditMode = formState.eventId != null
                    if (isEditMode) {
                        // Edit mode: show only the selected icon (no list)
                        Column {
                            Text(
                                text = "Event Type",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(bottom = 12.dp),
                                color = contentColor,
                            )
                            Row {
                                currentType?.let { type ->
                                    Icon(
                                        imageVector = type.icon,
                                        contentDescription = type.displayName,
                                        tint = type.color,
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = type.displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = contentColor
                                    )
                                }
                            }
                        }
                    } else {
                        IconSelector(
                            title = "Event Type",
                            options = EventType.entries,
                            selected = currentType,
                            onSelect = { type ->
                                val newState = when (type) {
                                    EventType.DIAPER -> Diaper()
                                    EventType.FEEDING -> Feeding()
                                    EventType.SLEEP -> Sleep()
                                    EventType.GROWTH -> Growth()
                                    EventType.PUMPING -> Pumping()
                                    EventType.DRUGS -> Drugs()
                                }
                                viewModel.updateForm { newState }
                            },
                            getIcon = { it.icon },
                            getLabel = { it.displayName },
                            getColor = { it.color }
                        )
                    }

                    // Date Selector
                    when (val s = formState) {
                        is EventFormState.Sleep -> {
                            // No top-level date selector for Sleep
                            SleepForm(s, viewModel)
                        }

                        else -> {
                            // For all other events, render the date selector
                            ModernDateSelector(
                                selectedDate = selectedDate,
                                onDateSelected = {
                                    selectedDate = it
                                    viewModel.updateEventTimestamp(it)
                                }
                            )
                            // Then render the specific form
                            when (s) {
                                is EventFormState.Diaper -> DiaperForm(s, viewModel)
                                is EventFormState.Feeding -> FeedingForm(s, viewModel)
                                is EventFormState.Growth -> GrowthForm(s, viewModel)
                                is EventFormState.Pumping -> PumpingForm(s, viewModel)
                                is EventFormState.Drugs -> DrugsForm(s, viewModel)
                                else -> {}
                            }
                        }
                    }
                    PhotoPicker(
                        photoUrl = selectedUri,
                        onPhotoSelected = { uri ->
                            // update both our local preview state AND the ViewModel form state
                            selectedUri = uri
                            formState.newPhotoUrl = uri
                            formState.photoRemoved = false
                        },
                        onPhotoRemoved = {
                            // Only remove from storage if this event already exists:
                            if (isEditMode) {
                                viewModel.deleteEventPhoto(formState.eventId!!)
                            }
                            formState.photoUrl = null
                            formState.photoRemoved = true
                        })
                    Spacer(Modifier.height(12.dp))
                }
                // Footer: Cancel / Delete (if edit) / Save
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .background(color = contentColor.copy(alpha = 0.2f), shape = cornerShape)
                        .padding(12.dp)
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = DarkBlue)
                    }
                    // Show "Delete" only in edit mode
                    if (formState.eventId != null) {
                        TextButton(
                            onClick = { showDeleteConfirm = true },
                            enabled = !isDeleting
                        ) {
                            if (isDeleting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Delete", color = Color.Red)
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = { selectedBaby?.let { viewModel.SaveEvent(it.id) } },
                        enabled = !isSaving,
                        shape = cornerShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Saving…")
                        } else {
                            Text(
                                if (formState.eventId == null) "Create Event" else "Update Event",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiaperForm(state: EventFormState.Diaper, viewModel: EventViewModel) {
    val contentColor = Color.White
    val cornerShape = MaterialTheme.shapes.extraLarge
    // Diaper Type
    IconSelector(
        title = "Diaper Type",
        options = DiaperType.entries,
        selected = state.diaperType,
        onSelect = {
            viewModel.updateForm {
                (this as EventFormState.Diaper).copy(diaperType = it)
            }
        },
        getIcon = { type ->
            when (type) {
                DiaperType.WET -> Icons.Default.Opacity
                DiaperType.DIRTY -> Icons.Default.Circle
                DiaperType.MIXED -> Icons.Default.Merge
                DiaperType.DRY -> Icons.Default.InvertColorsOff
            }
        },
        getLabel = { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }
    )

    // Conditional poop details
    if (state.diaperType == DiaperType.DIRTY || state.diaperType == DiaperType.MIXED) {
        IconSelector(
            title = "Poop Color",
            options = PoopColor.entries,
            selected = state.poopColor,
            onSelect = {
                viewModel.updateForm {
                    (this as EventFormState.Diaper).copy(poopColor = it)
                }
            },
            getIcon = { Icons.Default.Palette },
            getLabel = { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
            getColor = { it.colorValue }
        )

        IconSelector(
            title = "Consistency",
            options = PoopConsistency.entries,
            selected = state.poopConsistency,
            onSelect = {
                viewModel.updateForm {
                    (this as EventFormState.Diaper).copy(poopConsistency = it)
                }
            },
            getIcon = { it.icon },           // use enum's icon
            getLabel = { it.displayName }    // use enum's displayName
        )
    }

    // Notes
    OutlinedTextField(
        value = state.notes,
        onValueChange = {
            viewModel.updateForm { (this as EventFormState.Diaper).copy(notes = it) }
        },
        textStyle = LocalTextStyle.current.copy(color = contentColor),
        label = {
            Text(
                "Notes (optional)",
                color = contentColor,
            )
        },
        shape = cornerShape,
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
    )
}

@Composable
private fun SleepForm(state: EventFormState.Sleep, viewModel: EventViewModel) {
    fun computeDuration(begin: Date?, end: Date?): Long? =
        if (begin != null && end != null)
            ((end.time - begin.time) / 60000).coerceAtLeast(0L)
        else null

    LaunchedEffect(state.beginTime) {
        if (state.beginTime == null) {
            val now = Date()
            viewModel.updateEventTimestamp(now)
            viewModel.updateForm {
                val s = this as EventFormState.Sleep
                s.copy(
                    beginTime = now,
                    durationMinutes = computeDuration(now, s.endTime)
                )
            }
        }
    }

    val cornerShape = MaterialTheme.shapes.extraLarge
    val contentColor = Color.White
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        ModernDateSelector(
            label = "Begin sleep",
            selectedDate = state.beginTime ?: Date(),
            onDateSelected = { newBegin ->
                viewModel.updateEventTimestamp(newBegin)
                viewModel.updateForm {
                    val s = this as EventFormState.Sleep
                    s.copy(
                        beginTime = newBegin,
                        durationMinutes = computeDuration(newBegin, s.endTime)
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        // End Date & Time Picker
        ModernDateSelector(
            label = "End sleep",
            selectedDate = state.endTime,
            onDateSelected = { newEnd ->
                viewModel.updateForm {
                    val s = this as EventFormState.Sleep
                    s.copy(
                        endTime = newEnd,
                        durationMinutes = computeDuration(s.beginTime, newEnd)
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        )


        // Duration display
        state.durationMinutes?.let { minutes ->
            Surface(
                shape = cornerShape,
                color = DarkGrey.copy(alpha = 0.3f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                ) {
                    Icon(Icons.Default.Timer, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Duration: ${minutes / 60}h ${minutes % 60}min",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        OutlinedTextField(
            value = state.notes,
            onValueChange = { viewModel.updateForm { (this as EventFormState.Sleep).copy(notes = it) } },
            label = { Text("Notes (optional)", color = contentColor) },
            textStyle = LocalTextStyle.current.copy(color = contentColor),
            shape = cornerShape,
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
        )
    }
}

@Composable
private fun FeedingForm(state: EventFormState.Feeding, viewModel: EventViewModel) {
    val contentColor = Color.White
    val cornerShape = MaterialTheme.shapes.extraLarge
    // Feed Type
    IconSelector(
        title = "Feed Type",
        options = FeedType.entries,
        selected = state.feedType,
        onSelect = {
            viewModel.updateForm {
                (this as EventFormState.Feeding).copy(feedType = it)
            }
        },
        getIcon = { type ->
            type.icon
        },
        getLabel = {
            it.name.replace("_", " ").lowercase()
                .split(" ")
                .joinToString(" ") { word -> word.replaceFirstChar { c -> c.uppercase() } }
        }
    )

    // Amount (hidden for breast milk)
    if (state.feedType != FeedType.BREAST_MILK) {
        OutlinedTextField(
            value = state.amountMl,
            onValueChange = {
                viewModel.updateForm { (this as EventFormState.Feeding).copy(amountMl = it) }
            },
            label = { Text("Amount (ml)", color = Color.White) },
            textStyle = LocalTextStyle.current.copy(color = contentColor),
            shape = cornerShape,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
    }

    // Duration
    OutlinedTextField(
        value = state.durationMin,
        onValueChange = {
            viewModel.updateForm { (this as EventFormState.Feeding).copy(durationMin = it) }
        },
        label = { Text("Duration (minutes)", color = contentColor) },
        textStyle = LocalTextStyle.current.copy(color = contentColor),
        shape = cornerShape,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )

    // Breast Side (for breast milk)
    if (state.feedType == FeedType.BREAST_MILK) {
        IconSelector(
            title = "Breast Side",
            options = BreastSide.entries,
            selected = state.breastSide,
            onSelect = {
                viewModel.updateForm {
                    (this as EventFormState.Feeding).copy(breastSide = it)
                }
            },
            getIcon = { side ->
                side.icon
            },
            getLabel = {
                it.name.lowercase().replaceFirstChar { c -> c.uppercase() }
            }
        )
    }

    OutlinedTextField(
        value = state.notes,
        onValueChange = { viewModel.updateForm { (this as EventFormState.Feeding).copy(notes = it) } },
        label = { Text("Notes (optional)", color = contentColor) },
        textStyle = LocalTextStyle.current.copy(color = contentColor),
        shape = cornerShape,
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
    )
}

@Composable
private fun GrowthForm(state: EventFormState.Growth, viewModel: EventViewModel) {
    val contentColor = Color.White
    val cornerShape = MaterialTheme.shapes.extraLarge

    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = state.weightKg,
            onValueChange = { viewModel.updateForm { (this as EventFormState.Growth).copy(weightKg = it) } },
            label = { Text("Weight (kg)", color = contentColor) },
            textStyle = LocalTextStyle.current.copy(color = contentColor),
            shape = cornerShape,
            modifier = Modifier.weight(1f),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )

        OutlinedTextField(
            value = state.heightCm,
            onValueChange = { viewModel.updateForm { (this as EventFormState.Growth).copy(heightCm = it) } },
            label = { Text("Height (cm)", color = contentColor) },
            textStyle = LocalTextStyle.current.copy(color = contentColor),
            shape = cornerShape,
            modifier = Modifier.weight(1f),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
    }

    OutlinedTextField(
        value = state.headCircumferenceCm,
        onValueChange = {
            viewModel.updateForm {
                (this as EventFormState.Growth).copy(
                    headCircumferenceCm = it
                )
            }
        },
        label = { Text("Head Circumference (cm)", color = contentColor) },
        textStyle = LocalTextStyle.current.copy(color = contentColor),
        shape = cornerShape,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    )

    OutlinedTextField(
        value = state.notes,
        onValueChange = { viewModel.updateForm { (this as EventFormState.Growth).copy(notes = it) } },
        label = { Text("Notes (optional)", color = contentColor) },
        textStyle = LocalTextStyle.current.copy(color = contentColor),
        shape = cornerShape,
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
    )
}

@Composable
private fun PumpingForm(state: EventFormState.Pumping, viewModel: EventViewModel) {
    val contentColor = Color.White
    val cornerShape = MaterialTheme.shapes.extraLarge
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // Amount Pumped (ml)
        OutlinedTextField(
            value = state.amountMl,
            onValueChange = {
                viewModel.updateForm {
                    (this as EventFormState.Pumping).copy(amountMl = it)
                }
            },
            label = { Text("Amount (ml)", color = contentColor) },
            textStyle = LocalTextStyle.current.copy(color = contentColor),
            shape = cornerShape,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )

        // Duration of Pumping (minutes)
        OutlinedTextField(
            value = state.durationMin,
            onValueChange = {
                viewModel.updateForm {
                    (this as EventFormState.Pumping).copy(durationMin = it)
                }
            },
            label = { Text("Duration (minutes)", color = contentColor) },
            textStyle = LocalTextStyle.current.copy(color = contentColor),
            shape = cornerShape,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )

        // Breast Side
        IconSelector(
            title = "Breast Side",
            options = BreastSide.entries,
            selected = state.breastSide,
            onSelect = {
                viewModel.updateForm {
                    (this as EventFormState.Pumping).copy(breastSide = it)
                }
            },
            getIcon = { side -> side.icon },
            getLabel = { side ->
                side.name.lowercase().replaceFirstChar { it.uppercase() }
            }
        )

        // Notes (optional)
        OutlinedTextField(
            value = state.notes,
            onValueChange = {
                viewModel.updateForm {
                    (this as EventFormState.Pumping).copy(notes = it)
                }
            },
            label = { Text("Notes (optional)", color = contentColor) },
            textStyle = LocalTextStyle.current.copy(color = contentColor),
            shape = cornerShape,
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
        )
    }
}

@Composable
private fun DrugsForm(state: EventFormState.Drugs, viewModel: EventViewModel) {
    val contentColor = Color.White
    val cornerShape = MaterialTheme.shapes.extraLarge
    Column(modifier = Modifier.fillMaxWidth()) {
        // Drug Type Picker
        IconSelector(
            title = "Drug Type",
            options = DrugType.entries,
            selected = state.drugType,
            onSelect = { selected ->
                viewModel.updateForm {
                    (this as EventFormState.Drugs).copy(drugType = selected)
                }
            },
            getIcon = { it.icon },
            getLabel = { it.displayName }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Other drug name (conditional)
        if (state.drugType == DrugType.OTHER) {
            OutlinedTextField(
                value = state.otherDrugName,
                onValueChange = { newName ->
                    viewModel.updateForm {
                        (this as EventFormState.Drugs).copy(otherDrugName = newName)
                    }
                },
                label = { Text("Specify Drug Name", color = contentColor) },
                textStyle = LocalTextStyle.current.copy(color = contentColor),
                placeholder = { Text("e.g., Ibuprofen") },
                shape = cornerShape,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
            )

            Spacer(modifier = Modifier.height(8.dp))
        }
        // Dosage Input
        OutlinedTextField(
            value = state.dosage,
            onValueChange = { newValue ->
                viewModel.updateForm {
                    (this as EventFormState.Drugs).copy(dosage = newValue)
                }
            },
            label = { Text("Dosage", color = contentColor) },
            textStyle = LocalTextStyle.current.copy(color = contentColor),
            placeholder = { Text("e.g., 250") },
            shape = cornerShape,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Unit Input
        OutlinedTextField(
            value = state.unit,
            onValueChange = { newValue ->
                viewModel.updateForm {
                    (this as EventFormState.Drugs).copy(unit = newValue)
                }
            },
            label = { Text("Unit", color = contentColor) },
            textStyle = LocalTextStyle.current.copy(color = contentColor),
            shape = cornerShape,
            placeholder = { Text("mg, IU, etc.") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
        )

        Spacer(modifier = Modifier.height(8.dp))


        // Notes
        OutlinedTextField(
            value = state.notes,
            onValueChange = { newNotes ->
                viewModel.updateForm {
                    (this as EventFormState.Drugs).copy(notes = newNotes)
                }
            },
            label = { Text("Notes (optional)", color = contentColor) },
            textStyle = LocalTextStyle.current.copy(color = contentColor),
            shape = cornerShape,
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
        )
    }
}