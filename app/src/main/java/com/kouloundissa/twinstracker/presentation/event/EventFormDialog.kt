package com.kouloundissa.twinstracker.presentation.event

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Timer
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.kouloundissa.twinstracker.R
import com.kouloundissa.twinstracker.data.BreastSide
import com.kouloundissa.twinstracker.data.DiaperType
import com.kouloundissa.twinstracker.data.DrugType
import com.kouloundissa.twinstracker.data.EventFormState
import com.kouloundissa.twinstracker.data.EventFormState.Diaper
import com.kouloundissa.twinstracker.data.EventFormState.Drugs
import com.kouloundissa.twinstracker.data.EventFormState.Feeding
import com.kouloundissa.twinstracker.data.EventFormState.Growth
import com.kouloundissa.twinstracker.data.EventFormState.Pumping
import com.kouloundissa.twinstracker.data.EventFormState.Sleep
import com.kouloundissa.twinstracker.data.EventType
import com.kouloundissa.twinstracker.data.EventType.Companion.getDisplayName
import com.kouloundissa.twinstracker.data.FeedType
import com.kouloundissa.twinstracker.data.FeedingEvent
import com.kouloundissa.twinstracker.data.PoopColor
import com.kouloundissa.twinstracker.data.PoopConsistency
import com.kouloundissa.twinstracker.data.PumpingEvent
import com.kouloundissa.twinstracker.data.getDisplayName
import com.kouloundissa.twinstracker.presentation.viewmodel.BabyViewModel
import com.kouloundissa.twinstracker.presentation.viewmodel.EventViewModel
import com.kouloundissa.twinstracker.presentation.viewmodel.FamilyViewModel
import com.kouloundissa.twinstracker.ui.components.AmountInput
import com.kouloundissa.twinstracker.ui.components.BabySelectorRow
import com.kouloundissa.twinstracker.ui.components.IconSelector
import com.kouloundissa.twinstracker.ui.components.MinutesInput
import com.kouloundissa.twinstracker.ui.components.ModernDateSelector
import com.kouloundissa.twinstracker.ui.components.PhotoPicker
import com.kouloundissa.twinstracker.ui.theme.BackgroundColor
import com.kouloundissa.twinstracker.ui.theme.DarkBlue
import com.kouloundissa.twinstracker.ui.theme.DarkGrey
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.launch
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class, DelicateCoroutinesApi::class)
@Composable
fun EventFormDialog(
    initialBabyId: String,
    onDismiss: () -> Unit,
    initialEventType: EventType? = null,
    familyViewModel: FamilyViewModel = hiltViewModel(),
    eventViewModel: EventViewModel = hiltViewModel(),
) {
    // Animated visibility state
    var isVisible by remember { mutableStateOf(false) }

    val formState by eventViewModel.formState.collectAsState()
    val isCreateMode = formState.eventId == null
    val currentUserIsViewer = familyViewModel.isCurrentUserViewer()
    LaunchedEffect(Unit) {
        isVisible = true
    }

    // If viewer is trying to create event, show error instead
    if (currentUserIsViewer && isCreateMode) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.restricted_access)) },
            text = { Text(stringResource(R.string.viewer_cannot_create_event)) },
            confirmButton = {
                Button(onClick = onDismiss) {
                    Text("OK")
                }
            }
        )
        return
    }
    // Wrap everything in a Dialog to make it truly full-screen
    Dialog(
        onDismissRequest = {
            isVisible = false
            kotlinx.coroutines.GlobalScope.launch {
                kotlinx.coroutines.delay(300)
                onDismiss()
            }
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        // Animated content with slide and fade
        AnimatedVisibility(
            visible = isVisible,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ) + fadeIn(
                animationSpec = tween(300)
            ),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(250)
            ) + fadeOut(
                animationSpec = tween(250)
            )
        ) {
            EventFormDialogContent(
                initialBabyId = initialBabyId,
                onDismiss = {
                    isVisible = false
                    kotlinx.coroutines.GlobalScope.launch {
                        kotlinx.coroutines.delay(300)
                        onDismiss()
                    }
                },
                initialEventType = initialEventType
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventFormDialogContent(
    initialBabyId: String,
    onDismiss: () -> Unit,
    initialEventType: EventType? = null,
    familyViewModel: FamilyViewModel = hiltViewModel(),
    eventViewModel: EventViewModel = hiltViewModel(),
    babyViewModel: BabyViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    val currentUserIsViewer = familyViewModel.isCurrentUserViewer()

    val formState by eventViewModel.formState.collectAsState()
    val lastGrowthEvent by eventViewModel.lastGrowthEvent.collectAsState()
    val isSaving by eventViewModel.isSaving.collectAsState()
    val saveSuccess by eventViewModel.saveSuccess.collectAsState()
    val errorMessage by eventViewModel.errorMessage.collectAsState()

    val isDeleting by eventViewModel.isDeleting.collectAsState()
    val deleteSuccess by eventViewModel.deleteSuccess.collectAsState()
    val deleteError by eventViewModel.deleteError.collectAsState()
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val currentType = formState.eventType

    val initialPhotoUri = formState.photoUrl?.let { Uri.parse(it) }
    var selectedUri by rememberSaveable { mutableStateOf<Uri?>(initialPhotoUri) }

    val headerHeight = 64.dp
    val footerHeight = 80.dp

    var selectedDate by remember(formState.eventTimestamp) {
        mutableStateOf(formState.eventTimestamp)
    }
    val backgroundColor = BackgroundColor
    val contentcolor = DarkGrey
    val tint = DarkBlue
    val cornerShape = MaterialTheme.shapes.extraLarge

    val babies by babyViewModel.babies.collectAsState()
    val selectedBaby by babyViewModel.selectedBaby.collectAsState()

    val haptic = LocalHapticFeedback.current
    var showViewerWarning by remember { mutableStateOf(false) }
    if (showViewerWarning) {
        ViewerCannotModifyDialog(onDismiss = {
            showViewerWarning = false
            onDismiss()
        })
    }
    LaunchedEffect(formState.eventType) {
        // Reset focus when event type changes
        focusManager.clearFocus()
    }
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
            eventViewModel.updateForm { newState }
        }
    }
    LaunchedEffect(selectedBaby, currentType) {
        if (formState.eventId == null && currentType == EventType.GROWTH) {
            selectedBaby?.let { baby ->
                eventViewModel.loadLastGrowth(baby.id)
                lastGrowthEvent?.let { event ->
                    // Only run this once—guarded by LaunchedEffect
                    eventViewModel.updateForm {
                        (this as EventFormState.Growth).copy(
                            weightKg = event.weightKg?.toString().orEmpty(),
                            heightCm = event.heightCm?.toString().orEmpty(),
                            headCircumferenceCm = event.headCircumferenceCm?.toString().orEmpty()
                            // notes and other fields remain untouched
                        )
                    }
                }
            };
        }
    }

    LaunchedEffect(saveSuccess, deleteSuccess) {
        if (saveSuccess || deleteSuccess) {
            onDismiss()
            eventViewModel.resetSaveSuccess()
            eventViewModel.resetDeleteState()
        }
    }
    // Confirmation dialog
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(id = R.string.delete_event_title)) },
            text = { Text(stringResource(id = R.string.delete_event_message)) },
            confirmButton = {
                TextButton(onClick = {
                    formState.event?.let { eventViewModel.deleteEvent(it, familyViewModel) }
                    showDeleteConfirm = false
                }) {
                    Text(
                        stringResource(id = R.string.delete_button),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(id = R.string.cancel_button))
                }
            }
        )
    }

    BackHandler(onBack = onDismiss)

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        AsyncImage(
            model = R.drawable.background,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .blur(8.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            currentType.color.copy(alpha = 0.35f),
                            currentType.color.copy(alpha = 0.15f)
                        )
                    ),
                )
                .systemBarsPadding()
                .navigationBarsPadding()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 12.dp)
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .background(
                            contentcolor.copy(alpha = 0.1f),
                            CircleShape
                        )
                        .size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(id = R.string.back_button_description),
                        tint = tint
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(
                        id = if (formState.eventId == null) R.string.event_form_add_event else R.string.event_form_edit_event
                    ),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = tint,
                )

            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = headerHeight, bottom = footerHeight)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
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
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = backgroundColor.copy(0.5f),
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(12.dp)
                                .fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(id = R.string.event_type_label),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(bottom = 12.dp),
                                color = contentcolor,
                            )
                            Row {
                                currentType.let { type ->
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = type.color.copy(alpha = 0.5f),
                                        border = BorderStroke(2.dp, type.color),
                                        modifier = Modifier
                                            .size(80.dp, 88.dp)
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center,
                                            modifier = Modifier.padding(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = type.icon,
                                                contentDescription = type.getDisplayName(
                                                    LocalContext.current
                                                ),
                                                tint = BackgroundColor,
                                                modifier = Modifier.size(32.dp)
                                            )
                                            Spacer(Modifier.height(6.dp))
                                            Text(
                                                text = type.getDisplayName(LocalContext.current),
                                                style = MaterialTheme.typography.bodySmall,
                                                textAlign = TextAlign.Center,
                                                color = BackgroundColor,
                                                maxLines = 2
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    IconSelector(
                        title = stringResource(id = R.string.event_type_label),
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
                            eventViewModel.updateForm { newState }
                        },
                        getIcon = { it.icon },
                        getLabel = { it.getDisplayName(context) },
                        getColor = { it.color }
                    )
                }

                // Date Selector
                // Then render the specific form
                when (val s = formState) {
                    is Sleep -> {
                        // No top-level date selector for Sleep
                        SleepForm(s, eventViewModel)
                    }

                    else -> {
                        // For all other events, render the date selector
                        ModernDateSelector(
                            selectedDate = selectedDate,
                            onDateSelected = {
                                selectedDate = it
                                eventViewModel.updateEventTimestamp(it)
                            }
                        )
                        when (s) {
                            is Diaper -> DiaperForm(s, eventViewModel)
                            is Feeding -> FeedingForm(s, eventViewModel)
                            is Growth -> GrowthForm(s, eventViewModel)
                            is Pumping -> PumpingForm(s, eventViewModel)
                            is Drugs -> DrugsForm(s, eventViewModel)
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
                            eventViewModel.deleteEventPhoto(formState.eventId!!, familyViewModel)
                        }
                        formState.photoUrl = null
                        formState.photoRemoved = true
                    })
                Spacer(Modifier.height(12.dp))
            }
        }
        // Footer: Cancel / Delete (if edit) / Save
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Cancel button
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.height(40.dp)
            ) {
                Text(
                    stringResource(id = R.string.cancel_button),
                    color = DarkBlue,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            // Delete button - only in edit mode
            if (formState.eventId != null && !currentUserIsViewer) {
                OutlinedButton(
                    onClick = {
                        showDeleteConfirm = true
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    enabled = !isDeleting,
                    shape = cornerShape,
                    border = BorderStroke(1.dp, if (isDeleting) Color.Gray else Color.Red),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.Red,
                        disabledContentColor = Color.Gray
                    ),
                    modifier = Modifier.height(40.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    if (isDeleting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = Color.Gray
                        )
                    } else {
                        Text(
                            stringResource(id = R.string.delete_button),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // Save/Update button
            Button(
                onClick = {
                    selectedBaby?.let {
                        val isEditMode = formState.eventId != null
                        if (currentUserIsViewer && isEditMode) {
                            showViewerWarning = true
                            haptic.performHapticFeedback(HapticFeedbackType.Reject)
                            return@Button  // Stop execution
                        }
                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        eventViewModel.SaveEvent(it.id, familyViewModel)
                    }
                },
                enabled = !isSaving,
                shape = cornerShape,
                modifier = Modifier
                    .height(56.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(id = R.string.saving_button))
                } else {
                    Text(
                        text = stringResource(
                            id = if (formState.eventId == null) R.string.create_event_button else R.string.update_event_button
                        ),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

@Composable
fun ViewerCannotModifyDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cannot_modify_event)) },
        text = { Text(stringResource(R.string.viewer_cannot_modify)) },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(R.string.ok_button))
            }
        }
    )
}

@Composable
fun DiaperForm(state: EventFormState.Diaper, viewModel: EventViewModel) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Type selector
        IconSelector(
            title = stringResource(id = R.string.diaper_type_label),
            options = DiaperType.entries,
            selected = state.diaperType,
            onSelect = { viewModel.updateForm { (this as EventFormState.Diaper).copy(diaperType = it) } },
            getIcon = { type -> type.icon },
            getColor = { it.color },
            getLabel = { it.getDisplayName(LocalContext.current) }
        )

        // Conditionally show poop details with smooth animation
        FormFieldVisibility(
            visible = state.diaperType in listOf(
                DiaperType.DIRTY,
                DiaperType.MIXED
            )
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                IconSelector(
                    title = stringResource(id = R.string.poop_color_label),
                    options = PoopColor.entries,
                    selected = state.poopColor,
                    onSelect = {
                        viewModel.updateForm {
                            (this as EventFormState.Diaper).copy(
                                poopColor = it
                            )
                        }
                    },
                    getIcon = { Icons.Default.Palette },
                    getLabel = { it.getDisplayName(LocalContext.current) },
                    getColor = { it.color }
                )

                IconSelector(
                    title = stringResource(id = R.string.consistency_label),
                    options = PoopConsistency.entries,
                    selected = state.poopConsistency,
                    onSelect = {
                        viewModel.updateForm {
                            (this as EventFormState.Diaper).copy(
                                poopConsistency = it
                            )
                        }
                    },
                    getColor = { it.color },
                    getIcon = { it.icon },
                    getLabel = { it.getDisplayName(LocalContext.current) }
                )
            }
        }

        // Notes field
        FormTextInput(
            value = state.notes,
            onValueChange = { newNotes ->
                viewModel.updateForm { (this as EventFormState.Diaper).copy(notes = newNotes) }
            },
            label = stringResource(id = R.string.notes_hint),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 80.dp),
            minLines = 4
        )
    }
}

@Composable
fun SleepForm(state: EventFormState.Sleep, viewModel: EventViewModel) {

    val cornerShape = MaterialTheme.shapes.extraLarge
    val backgroundColor = BackgroundColor
    val tint = DarkBlue

    fun computeDuration(begin: Date?, end: Date?): Long? =
        if (begin != null && end != null)
            ((end.time - begin.time) / 60000).coerceAtLeast(0L)
        else null

    LaunchedEffect(Unit) {
        if (state.beginTime == null) {
            val now = Date()
            viewModel.updateForm {
                val s = this as EventFormState.Sleep
                s.copy(
                    beginTime = now,
                    durationMinutes = computeDuration(now, s.endTime)
                )
            }
            viewModel.updateEventTimestamp(now)
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        FormSection(title = stringResource(id = R.string.sleep_period_title)) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ModernDateSelector(
                    label = stringResource(id = R.string.begin_sleep_label),
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
                ModernDateSelector(
                    label = stringResource(id = R.string.end_sleep_label),
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
            }
        }


        // Duration display
        state.durationMinutes?.let { minutes ->
            Surface(
                shape = cornerShape,
                color = tint.copy(alpha = 0.4f),
                border = BorderStroke(1.dp, tint.copy(alpha = 0.6f))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.Timer,
                        contentDescription = null,
                        tint = backgroundColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            stringResource(id = R.string.duration),
                            style = MaterialTheme.typography.labelSmall,
                            color = backgroundColor.copy(alpha = 0.7f)
                        )
                        Text(
                            "${minutes / 60}h ${minutes % 60}min",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = backgroundColor
                        )
                    }
                }
            }
        }

        FormTextInput(
            value = state.notes,
            onValueChange = { viewModel.updateForm { (this as EventFormState.Sleep).copy(notes = it) } },
            label = stringResource(id = R.string.notes_hint),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 80.dp),
            minLines = 4
        )
    }
}

@Composable
fun FeedingForm(state: EventFormState.Feeding, viewModel: EventViewModel) {
    val contentColor = BackgroundColor
    val cornerShape = MaterialTheme.shapes.extraLarge

    // Feed Type
    IconSelector(
        title = stringResource(id = R.string.feeding_type_label),
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
        getColor = { it.color },
        getLabel = {
            it.getDisplayName(LocalContext.current)
        }
    )

    val isEditMode = state.eventId != null
    val allEvents by viewModel.events.collectAsState()

    val amountPreset = if (isEditMode && state.event != null) {
        listOf(state.event as FeedingEvent).calculatePresets()
    } else {
        allEvents
            .filterIsInstance<FeedingEvent>()
            .filter { it.amountMl != null && it.amountMl > 0 }
            .take(10)
            .calculatePresets()
    }

    val durationPreset = allEvents
        .filterIsInstance<FeedingEvent>()
        .filter { it.durationMinutes != null && it.durationMinutes > 0 }
        .mapNotNull { it.durationMinutes }
        .take(10)
        .calculatePresetsFromNumbers(listOf(5, 10, 15, 20))

    LaunchedEffect(state.feedType, amountPreset, durationPreset) {
        if (isEditMode)
            return@LaunchedEffect
        if (state.feedType != FeedType.BREAST_MILK && amountPreset.size > 1) {
            viewModel.updateForm {
                (this as EventFormState.Feeding).copy(
                    durationMin = "",
                    amountMl = amountPreset[1].toString(),
                    breastSide = null
                )
            }
        }
        if (state.feedType == FeedType.BREAST_MILK && durationPreset.size > 1) {
            viewModel.updateForm {
                (this as EventFormState.Feeding).copy(
                    durationMin = durationPreset[1].toString(),
                    amountMl = "",
                    breastSide = BreastSide.BOTH
                )
            }
        }
    }

    // Amount (hidden for breast milk)
    FormFieldVisibility(visible = state.feedType != FeedType.BREAST_MILK) {
        AmountInput(
            value = state.amountMl,
            onValueChange = { newAmount ->
                viewModel.updateForm {
                    (this as EventFormState.Feeding).copy(amountMl = newAmount)
                }
            },
            min = 0,
            max = 9999,
            step = 5,
            presets = amountPreset
        )
    }

    // Breast Side (for breast milk)
    FormFieldVisibility(visible = state.feedType == FeedType.BREAST_MILK) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // Duration Input
            MinutesInput(
                value = state.durationMin,
                onValueChange = { newDuration ->
                    viewModel.updateForm {
                        (this as EventFormState.Feeding).copy(durationMin = newDuration)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                presets = durationPreset
            )

            // Breast Side Selection
            IconSelector(
                title = stringResource(id = R.string.breast_side_label),
                options = BreastSide.entries,
                selected = state.breastSide,
                onSelect = {
                    viewModel.updateForm {
                        (this as EventFormState.Feeding).copy(breastSide = it)
                    }
                },
                getIcon = { side -> side.icon },
                getColor = { it.color },
                getLabel = { side -> side.getDisplayName(LocalContext.current) }
            )
        }
    }

    FormTextInput(
        value = state.notes,
        onValueChange = { viewModel.updateForm { (this as EventFormState.Feeding).copy(notes = it) } },
        label = stringResource(id = R.string.notes_hint),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 80.dp),
        minLines = 4
    )
}

@Composable
fun GrowthForm(state: EventFormState.Growth, viewModel: EventViewModel) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        FormSection(title = stringResource(id = R.string.body_measurements_title)) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FormNumericInput(
                        value = state.weightKg,
                        onValueChange = {
                            viewModel.updateForm {
                                (this as EventFormState.Growth).copy(
                                    weightKg = it
                                )
                            }
                        },
                        label = stringResource(id = R.string.weight_label),
                        modifier = Modifier.weight(1f),
                        max = 50f // reasonable max for baby
                    )

                    FormNumericInput(
                        value = state.heightCm,
                        onValueChange = {
                            viewModel.updateForm {
                                (this as EventFormState.Growth).copy(
                                    heightCm = it
                                )
                            }
                        },
                        label = stringResource(id = R.string.height_label),
                        modifier = Modifier.weight(1f),
                        max = 150f
                    )
                }

                FormNumericInput(
                    value = state.headCircumferenceCm,
                    onValueChange = {
                        viewModel.updateForm {
                            (this as EventFormState.Growth).copy(
                                headCircumferenceCm = it
                            )
                        }
                    },
                    label = stringResource(id = R.string.head_circumference),
                    modifier = Modifier.fillMaxWidth(),
                    max = 60f
                )
            }
        }

        FormTextInput(
            value = state.notes,
            onValueChange = { viewModel.updateForm { (this as EventFormState.Growth).copy(notes = it) } },
            label = stringResource(id = R.string.notes_hint),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 80.dp),
            minLines = 4
        )
    }
}

@Composable
fun PumpingForm(state: EventFormState.Pumping, viewModel: EventViewModel) {
    val contentColor = BackgroundColor
    val cornerShape = MaterialTheme.shapes.extraLarge

    val allEvents by viewModel.events.collectAsState()
    val isEditMode = state.eventId != null

    val amountPreset = if (isEditMode && state.event != null) {
        listOf(state.event as PumpingEvent).calculatePresets()
    } else {
        allEvents
            .filterIsInstance<PumpingEvent>()
            .filter { it.amountMl != null && it.amountMl > 0 }
            .take(10)
            .calculatePresets()
    }

    val presets2 = allEvents
        .filterIsInstance<PumpingEvent>()
        .filter { it.durationMinutes != null && it.durationMinutes > 0 }
        .mapNotNull { it.durationMinutes }
        .take(10)
        .calculatePresetsFromNumbers(listOf(5, 10, 15, 20))

    LaunchedEffect(Unit) {
        if (state.amountMl.isEmpty()) {
            viewModel.updateForm {
                (this as EventFormState.Pumping).copy(amountMl = amountPreset[1].toString())
            }
        }
        if (state.durationMin.isEmpty()) {
            viewModel.updateForm {
                (this as EventFormState.Pumping).copy(durationMin = presets2[1].toString())
            }
        }
    }

    AmountInput(
        value = state.amountMl,
        onValueChange = { newAmount ->
            viewModel.updateForm {
                (this as EventFormState.Pumping).copy(amountMl = newAmount)
            }
        },
        min = 0,
        max = 999,
        step = 5,
        presets = amountPreset
    )

    MinutesInput(
        value = state.durationMin,
        onValueChange = { newDuration ->
            viewModel.updateForm {
                (this as EventFormState.Pumping).copy(durationMin = newDuration)
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        presets = listOf(15, 20, 25, 30)
    )

    // Breast Side
    IconSelector(
        title = stringResource(id = R.string.breast_side_label),
        options = BreastSide.entries,
        selected = state.breastSide,
        onSelect = {
            viewModel.updateForm {
                (this as EventFormState.Pumping).copy(breastSide = it)
            }
        },
        getColor = { it.color },
        getIcon = { side -> side.icon },
        getLabel = { side ->
            side.name.lowercase().replaceFirstChar { it.uppercase() }
        }
    )

    // Notes
    FormTextInput(
        value = state.notes,
        onValueChange = { newNotes ->
            viewModel.updateForm {
                (this as EventFormState.Pumping).copy(notes = newNotes)
            }
        },
        label = stringResource(id = R.string.notes_hint),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 80.dp),
        minLines = 4
    )
}

@Composable
fun DrugsForm(state: EventFormState.Drugs, viewModel: EventViewModel) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Drug Type Picker
        IconSelector(
            title = stringResource(id = R.string.drug_type_label),
            options = DrugType.entries,
            selected = state.drugType,
            onSelect = { selected ->
                viewModel.updateForm {
                    (this as EventFormState.Drugs).copy(drugType = selected)
                }
            },
            getColor = { it.color },
            getIcon = { it.icon },
            getLabel = { it.getDisplayName(LocalContext.current) }
        )

        // Conditionally show drug name input
        FormFieldVisibility(visible = state.drugType == DrugType.OTHER) {
            FormTextInput(
                value = state.otherDrugName,
                onValueChange = { newName ->
                    viewModel.updateForm {
                        (this as EventFormState.Drugs).copy(otherDrugName = newName)
                    }
                },
                label = stringResource(id = R.string.specify_drug_name_label),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Dosage information section
        FormFieldVisibility(visible = state.drugType != DrugType.CREAM) {
            FormSection(title = stringResource(id = R.string.dosage_information_title)) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    androidx.compose.foundation.layout.Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FormNumericInput(
                            value = state.dosage,
                            onValueChange = { newValue ->
                                viewModel.updateForm {
                                    (this as EventFormState.Drugs).copy(dosage = newValue)
                                }
                            },
                            label = stringResource(id = R.string.dosage_amount_label),
                            modifier = Modifier.weight(2f),
                            max = 1000f
                        )

                        FormTextInput(
                            value = state.unit,
                            onValueChange = { newValue ->
                                viewModel.updateForm {
                                    (this as EventFormState.Drugs).copy(unit = newValue)
                                }
                            },
                            label = stringResource(id = R.string.dosage_unit_label),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Notes
        FormTextInput(
            value = state.notes,
            onValueChange = { newNotes ->
                viewModel.updateForm {
                    (this as EventFormState.Drugs).copy(notes = newNotes)
                }
            },
            label = stringResource(id = R.string.notes_hint),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 80.dp),
            minLines = 4
        )
    }
}

@Composable
fun FormFieldVisibility(
    visible: Boolean,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(animationSpec = tween(300)) + fadeIn(),
        exit = shrinkVertically(animationSpec = tween(300)) + fadeOut()
    ) {
        content()
    }
}

@Composable
fun FormSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = BackgroundColor.copy(alpha = 0.5f),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth()
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 12.dp),
                color = DarkGrey,
            )
            content()
        }
    }
}

@Composable
fun FormTextInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    isError: Boolean = false,
    errorMessage: String? = null,
) {
    val cornerShape = MaterialTheme.shapes.extraLarge
    val contentColor = BackgroundColor

    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label, color = contentColor) },
            textStyle = LocalTextStyle.current.copy(color = contentColor),
            shape = cornerShape,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            singleLine = singleLine,
            minLines = minLines,
            isError = isError,
            supportingText = if (isError && errorMessage != null) {
                { Text(errorMessage, color = MaterialTheme.colorScheme.error) }
            } else null,
        )
    }
}

@Composable
fun FormNumericInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    min: Float = 0f,
    max: Float = Float.MAX_VALUE,
) {
    FormTextInput(
        value = value,
        onValueChange = { newValue ->
            val floatValue = newValue.toFloatOrNull() ?: 0f
            if (floatValue in min..max) {
                onValueChange(newValue)
            }
        },
        label = label,
        modifier = modifier,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true
    )
}