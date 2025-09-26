package com.kouloundissa.twinstracker.presentation.event

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.kouloundissa.twinstracker.data.*
import com.kouloundissa.twinstracker.presentation.viewmodel.EventViewModel
import android.text.format.DateFormat
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.net.toUri
import com.kouloundissa.twinstracker.ui.components.PhotoPicker
import java.text.SimpleDateFormat
import java.util.*
import java.util.Collections.copy


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventFormDialog(
    babyId: String,
    onDismiss: () -> Unit,
    initialEventType: EventType? = null,
    viewModel: EventViewModel = hiltViewModel()
) {
    val formState by viewModel.formState.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val saveSuccess by viewModel.saveSuccess.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val currentType = formState.eventType

    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val verticalPadding = WindowInsets.systemBars.asPaddingValues().calculateTopPadding() +
            WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()

    val initialPhotoUri = formState.photoUrl?.let { Uri.parse(it) }
    var selectedUri by rememberSaveable { mutableStateOf<Uri?>(initialPhotoUri) }

    val footerHeight = 72.dp
    val maxDialogHeight = screenHeight - verticalPadding - footerHeight // extra buffer

    var selectedDate by remember(formState.eventTimestamp) {
        mutableStateOf(formState.eventTimestamp)
    }

    LaunchedEffect(initialEventType) {
        initialEventType?.let {
            val newState = when (it) {
                EventType.DIAPER -> EventFormState.Diaper()
                EventType.FEEDING -> EventFormState.Feeding()
                EventType.SLEEP -> EventFormState.Sleep()
                EventType.GROWTH -> EventFormState.Growth()
                EventType.PUMPING -> EventFormState.Pumping()
            }
            viewModel.updateForm { newState }
        }
    }


    LaunchedEffect(saveSuccess) {
        if (saveSuccess) {
            onDismiss()
            viewModel.resetSaveSuccess()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 8.dp,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()          // use full width
                .padding(horizontal = 16.dp)
                .wrapContentHeight()
        ) {

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxDialogHeight)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 20.dp)
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
                            fontWeight = FontWeight.Bold
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

                    if (errorMessage != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                errorMessage!!,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    // Event Type Selector (only for new events)
                    val isEditMode = formState.eventId != null
                    if (isEditMode) {
                        // Edit mode: show only the selected icon (no list)
                        Column {
                            Text(
                                text = "Event Type",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(bottom = 12.dp)
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
                                        color = MaterialTheme.colorScheme.onSurface
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
                                    EventType.DIAPER -> EventFormState.Diaper()
                                    EventType.FEEDING -> EventFormState.Feeding()
                                    EventType.SLEEP -> EventFormState.Sleep()
                                    EventType.GROWTH -> EventFormState.Growth()
                                    EventType.PUMPING -> EventFormState.Pumping()
                                }
                                viewModel.updateForm { newState }
                            },
                            getIcon = { it.icon },
                            getLabel = { it.displayName },
                            getColor = { it.color }
                        )
                    }

                    // Date Selector
                    ModernDateSelector(
                        selectedDate = selectedDate,
                        onDateSelected = {
                            selectedDate = it
                            viewModel.updateEventTimestamp(it)
                        }
                    )

                    // Event-specific form content
                    when (val s = formState) {
                        is EventFormState.Diaper -> DiaperForm(s, viewModel)
                        is EventFormState.Sleep -> SleepForm(s, viewModel)
                        is EventFormState.Feeding -> FeedingForm(s, viewModel)
                        is EventFormState.Growth -> GrowthForm(s, viewModel)
                        is EventFormState.Pumping -> PumpingForm()
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
                // Save button
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = { viewModel.SaveEvent(babyId) },
                        enabled = !isSaving,
                        shape = RoundedCornerShape(16.dp),
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

// Reusable Icon Selector Component
@Composable
fun <T> IconSelector(
    title: String,
    options: List<T>,
    selected: T?,
    onSelect: (T) -> Unit,
    getIcon: (T) -> ImageVector,
    getLabel: (T) -> String,
    getColor: ((T) -> Color)? = null,
    modifier: Modifier = Modifier
) {
    val defaultColor = MaterialTheme.colorScheme.primary
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(options) { option ->
                val itemColor = getColor?.invoke(option) ?: defaultColor
                val isSelected = selected == option
                Surface(
                    onClick = { onSelect(option) },
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) itemColor.copy(alpha = 0.2f) else Color.Transparent,
                    border = if (isSelected)
                        BorderStroke(2.dp, itemColor)
                    else
                        BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                    modifier = Modifier.size(80.dp, 88.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Icon(
                            imageVector = getIcon(option),
                            contentDescription = getLabel(option),
                            tint = if (isSelected) itemColor else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = getLabel(option),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = if (isSelected) itemColor else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModernDateSelector(
    selectedDate: Date,
    onDateSelected: (Date) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    // Hold interim date before time selection
    var interimDateMillis by remember { mutableStateOf(selectedDate.time) }

    // Date picker state
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = selectedDate.time
    )

    // Trigger surface
    Surface(
        onClick = { showDatePicker = true },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(20.dp)
        ) {
            Icon(
                Icons.Default.DateRange,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Event Date & Time",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    SimpleDateFormat("EEE, MMM dd, yyyy • hh:mm a", Locale.getDefault())
                        .format(selectedDate),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
            }
            Icon(
                Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    // Date picker dialog
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    showDatePicker = false
                    datePickerState.selectedDateMillis?.let { ms ->
                        interimDateMillis = ms
                        showTimePicker = true
                    }
                }) {
                    Text("Next")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Time picker dialog (reuses ModernTimeSelector internals)
    if (showTimePicker) {
        ShowTimePickerDialog(
            label = "Event Time",
            initialDate = Date(interimDateMillis),
            onTimeSelected = { timeDate ->
                // Merge date + time
                val cal = Calendar.getInstance().apply {
                    timeInMillis = interimDateMillis
                    set(Calendar.HOUR_OF_DAY, timeDate.hours)
                    set(Calendar.MINUTE, timeDate.minutes)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                onDateSelected(cal.time)
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false }
        )
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModernTimeSelector(
    label: String,
    time: Date?,
    onTimeSelected: (Date) -> Unit,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable { showDialog = true },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp)
        ) {
            Icon(
                Icons.Default.Schedule,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = time?.let {
                        SimpleDateFormat("HH:mm", Locale.getDefault()).format(it)
                    } ?: "Select time",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }

    if (showDialog) {
        ShowTimePickerDialog(
            label = label,
            initialDate = time ?: Date().also { onDismiss() },
            onTimeSelected = {
                onTimeSelected(it)
                showDialog = false
            },
            onDismiss = {
                onDismiss()
                showDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShowTimePickerDialog(
    label: String,
    initialDate: Date,
    onTimeSelected: (Date) -> Unit,
    onDismiss: () -> Unit
) {
    // Extract initial hour/minute
    val (initialHour, initialMinute) = remember(initialDate) {
        Calendar.getInstance().apply { time = initialDate }
            .let { it.get(Calendar.HOUR_OF_DAY) to it.get(Calendar.MINUTE) }
    }
    val context = LocalContext.current
    val is24Hour = DateFormat.is24HourFormat(context)
    val timePickerState = remember {
        TimePickerState(
            initialHour = initialHour,
            initialMinute = initialMinute,
            is24Hour = is24Hour
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(label) },
        text = { TimePicker(state = timePickerState) },
        confirmButton = {
            TextButton(onClick = {
                val cal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                    set(Calendar.MINUTE, timePickerState.minute)
                }
                onTimeSelected(cal.time)
            }) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun DiaperForm(state: EventFormState.Diaper, viewModel: EventViewModel) {
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
        label = { Text("Notes (optional)") },
        shape = RoundedCornerShape(12.dp),
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

    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        ModernTimeSelector(
            label = "Start Time",
            time = state.beginTime,
            onTimeSelected = { newBegin ->
                viewModel.updateForm {
                    val s = this as EventFormState.Sleep
                    s.copy(
                        beginTime = newBegin,
                        durationMinutes = computeDuration(newBegin, s.endTime)
                    )
                }
            },
            modifier = Modifier.weight(1f),
            onDismiss = { }
        )

        ModernTimeSelector(
            label = "End Time",
            time = state.endTime,
            onTimeSelected = { newEnd ->
                viewModel.updateForm {
                    val s = this as EventFormState.Sleep
                    s.copy(
                        endTime = newEnd,
                        durationMinutes = computeDuration(s.beginTime, newEnd)
                    )
                }
            },
            modifier = Modifier.weight(1f),
            onDismiss = { }
        )
    }

    // Duration display
    state.durationMinutes?.let { minutes ->
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
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
        label = { Text("Notes (optional)") },
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
    )
}

@Composable
private fun FeedingForm(state: EventFormState.Feeding, viewModel: EventViewModel) {
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
            when (type) {
                FeedType.BREAST_MILK -> Icons.Default.Favorite
                FeedType.FORMULA -> Icons.Default.LocalDrink
                FeedType.SOLID -> Icons.Default.Restaurant
            }
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
            label = { Text("Amount (ml)") },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )
    }

    // Duration
    OutlinedTextField(
        value = state.durationMin,
        onValueChange = {
            viewModel.updateForm { (this as EventFormState.Feeding).copy(durationMin = it) }
        },
        label = { Text("Duration (minutes)") },
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
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
                when (side) {
                    BreastSide.LEFT -> Icons.Default.ChevronLeft
                    BreastSide.RIGHT -> Icons.Default.ChevronRight
                    BreastSide.BOTH -> Icons.Default.SwapHoriz
                }
            },
            getLabel = {
                it.name.lowercase().replaceFirstChar { c -> c.uppercase() }
            }
        )
    }

    OutlinedTextField(
        value = state.notes,
        onValueChange = { viewModel.updateForm { (this as EventFormState.Feeding).copy(notes = it) } },
        label = { Text("Notes (optional)") },
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
    )
}

@Composable
private fun GrowthForm(state: EventFormState.Growth, viewModel: EventViewModel) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = state.weightKg,
            onValueChange = { viewModel.updateForm { (this as EventFormState.Growth).copy(weightKg = it) } },
            label = { Text("Weight (kg)") },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.weight(1f)
        )

        OutlinedTextField(
            value = state.heightCm,
            onValueChange = { viewModel.updateForm { (this as EventFormState.Growth).copy(heightCm = it) } },
            label = { Text("Height (cm)") },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.weight(1f)
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
        label = { Text("Head Circumference (cm)") },
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    )

    OutlinedTextField(
        value = state.notes,
        onValueChange = { viewModel.updateForm { (this as EventFormState.Growth).copy(notes = it) } },
        label = { Text("Notes (optional)") },
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
    )
}

@Composable
private fun PumpingForm() {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text(
                "Pumping form coming soon…",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
        }
    }
}