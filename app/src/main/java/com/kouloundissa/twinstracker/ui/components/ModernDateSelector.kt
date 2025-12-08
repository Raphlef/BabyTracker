package com.kouloundissa.twinstracker.ui.components

import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerState
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kouloundissa.twinstracker.R
import com.kouloundissa.twinstracker.ui.theme.BackgroundColor
import com.kouloundissa.twinstracker.ui.theme.DarkBlue
import com.kouloundissa.twinstracker.ui.theme.DarkGrey
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModernDateSelector(
    label: String = stringResource(id = R.string.date_time_label),
    selectedDate: Date?,
    onDateSelected: (Date) -> Unit,
    modifier: Modifier = Modifier
) {
    // State control for dialogs
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    // Interim selection state
    val calendar = remember(selectedDate) {
        Calendar.getInstance().apply {
            if (selectedDate != null) {
                time = selectedDate
            }
        }
    }
    val displayDate = calendar.time

    // UI Colors
    val backgroundcolor = BackgroundColor.copy(alpha = 0.5f)
    val contentcolor = DarkGrey
    val tint = DarkBlue

    // --- UI surface with 2 chips/buttons ---
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = backgroundcolor,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = contentcolor,
                modifier = Modifier
                    .padding(start = 4.dp)
            )
            Spacer(Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.height(IntrinsicSize.Min)
            ) {
                // Date chip
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    border = BorderStroke(1.dp, contentcolor.copy(alpha = 0.5f)),
                    contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Default.DateRange,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = tint
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = SimpleDateFormat("EEE, MMM dd, yyyy", Locale.getDefault()).format(
                            displayDate
                        ),
                        color = contentcolor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(12.dp))

                // Time chip
                OutlinedButton(
                    onClick = { showTimePicker = true },
                    border = BorderStroke(1.dp, contentcolor.copy(alpha = 0.5f)),
                    contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp),
                    modifier = Modifier.weight(0.5f)
                ) {
                    Icon(
                        Icons.Default.AccessTime,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = tint
                    )
                    Spacer(Modifier.width(8.dp))
                    if (selectedDate != null) {
                        Text(
                            text = SimpleDateFormat(
                                "HH:mm",
                                Locale.getDefault()
                            ).format(displayDate),
                            style = MaterialTheme.typography.labelMedium,
                            color = contentcolor
                        )
                    }
                }
            }
        }
    }

    // --- Date picker ---
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = calendar.timeInMillis
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    showDatePicker = false
                    datePickerState.selectedDateMillis?.let { ms ->
                        // Save previous time
                        val previousHour = calendar.get(Calendar.HOUR_OF_DAY)
                        val previousMinute = calendar.get(Calendar.MINUTE)
                        // Update only the date part
                        calendar.timeInMillis = ms
                        // Restore time
                        calendar.set(Calendar.HOUR_OF_DAY, previousHour)
                        calendar.set(Calendar.MINUTE, previousMinute)
                        calendar.set(Calendar.SECOND, 0)
                        calendar.set(Calendar.MILLISECOND, 0)
                        onDateSelected(calendar.time)
                    }
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // --- Time picker ---
    if (showTimePicker) {
        ShowTimePickerDialog(
            label = "Event Time",
            initialDate = displayDate,
            onTimeSelected = { timePart ->
                // Set only hour/minute/second on existing date
                with(calendar) {
                    set(Calendar.HOUR_OF_DAY, timePart.hours)
                    set(Calendar.MINUTE, timePart.minutes)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                onDateSelected(calendar.time)
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false }
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