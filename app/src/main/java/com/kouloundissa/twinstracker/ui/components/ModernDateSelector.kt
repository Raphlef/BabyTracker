package com.kouloundissa.twinstracker.ui.components

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    label: String = "Event Date & Time",
    selectedDate: Date?,
    onDateSelected: (Date) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    val initialMillis = selectedDate?.time ?: System.currentTimeMillis()
    var interimDateMillis by remember { mutableStateOf(initialMillis) }
    // Hold interim date before time selection
    val interimCalendar = remember { Calendar.getInstance().apply { timeInMillis = initialMillis } }

    val backgroundcolor = BackgroundColor.copy(alpha = 0.5f)
    val contentcolor = DarkGrey
    val tint = DarkBlue

    // Date picker state
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = interimDateMillis
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
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = contentcolor
                )
                Text(
                    text = selectedDate
                        ?.let {
                            SimpleDateFormat(
                                "EEE, MMM dd, yyyy • HH:mm",
                                Locale.getDefault()
                            ).format(it)
                        }
                        ?: "Select date & time",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = contentcolor
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
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
            },
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
private fun ShowTimePickerDialog(
    label: String,
    initialDate: Date,
    onTimeSelected: (Date) -> Unit,
    onDismiss: () -> Unit
) {
    val contentColor = Color.White

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