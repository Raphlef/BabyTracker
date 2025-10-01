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
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.SheetValue.Hidden
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.net.toUri
import com.kouloundissa.twinstracker.R
import com.kouloundissa.twinstracker.data.EventFormState.*
import com.kouloundissa.twinstracker.ui.components.PhotoPicker
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
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
    val contentColor = MaterialTheme.colorScheme.onSecondary
    val cornerShape = MaterialTheme.shapes.extraLarge
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
                    viewModel.deleteEvent(formState.eventId!!, babyId)
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
                    .blur(radiusX = 4.dp, radiusY = 4.dp)
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

                    if (errorMessage != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = cornerShape
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
                        is EventFormState.Pumping -> PumpingForm(s, viewModel)
                        is EventFormState.Drugs -> DrugsForm(s, viewModel)
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
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = MaterialTheme.colorScheme.primary)
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
                                Text("Delete", color = MaterialTheme.colorScheme.onError)
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = { viewModel.SaveEvent(babyId) },
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

    val backgroundcolor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val contentcolor= MaterialTheme.colorScheme.onSurfaceVariant
    val tint = MaterialTheme.colorScheme.primary

    //val contentColor = MaterialTheme.colorScheme.onPrimary
    val defaultColor = MaterialTheme.colorScheme.primary
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 12.dp),
            color = tint
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
                    color = if (isSelected) itemColor.copy(alpha = 0.2f) else backgroundcolor,
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
                            tint = contentcolor.copy(alpha = 0.8f),
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = getLabel(option),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = if (isSelected) itemColor else contentcolor,
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
    var interimDateMillis by remember { mutableStateOf(selectedDate.time) }
    // Hold interim date before time selection
    val interimCalendar = remember { Calendar.getInstance().apply { time = selectedDate } }

    val backgroundcolor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val contentcolor= MaterialTheme.colorScheme.onSurfaceVariant
    val tint = MaterialTheme.colorScheme.primary

    // Date picker state
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = selectedDate.time
    )

    // Trigger surface
    Surface(
        onClick = { showDatePicker = true },
        shape = RoundedCornerShape(16.dp),
        color = backgroundcolor,
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(20.dp)
        ) {
            Icon(
                Icons.Default.DateRange,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Event Date & Time",
                    style = MaterialTheme.typography.labelMedium,
                    color = contentcolor
                )
                Text(
                    SimpleDateFormat("EEE, MMM dd, yyyy • hh:mm a", Locale.getDefault())
                        .format(selectedDate),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = contentcolor
                )
            }
            Icon(
                Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = contentcolor,
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
            initialDate = interimCalendar.time,
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
private fun PumpingForm(
    state: EventFormState.Pumping,
    viewModel: EventViewModel
) {
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
            label = { Text("Amount (ml)") },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        // Duration of Pumping (minutes)
        OutlinedTextField(
            value = state.durationMin,
            onValueChange = {
                viewModel.updateForm {
                    (this as EventFormState.Pumping).copy(durationMin = it)
                }
            },
            label = { Text("Duration (minutes)") },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
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
            label = { Text("Notes (optional)") },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
        )
    }
}


@Composable
private fun DrugsForm(state: EventFormState.Drugs, viewModel: EventViewModel) {
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
                label = { Text("Specify Drug Name") },
                placeholder = { Text("e.g., Ibuprofen") },
                modifier = Modifier.fillMaxWidth()
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
            label = { Text("Dosage") },
            placeholder = { Text("e.g., 250") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
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
            label = { Text("Unit") },
            placeholder = { Text("mg, IU, etc.") },
            modifier = Modifier.fillMaxWidth()
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
            label = { Text("Notes (optional)") },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
        )
    }
}
