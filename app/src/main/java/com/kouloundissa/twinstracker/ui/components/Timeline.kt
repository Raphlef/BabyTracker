package com.kouloundissa.twinstracker.ui.components

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.kouloundissa.twinstracker.data.Baby
import com.kouloundissa.twinstracker.data.DiaperEvent
import com.kouloundissa.twinstracker.data.DiaperType
import com.kouloundissa.twinstracker.data.DrugType
import com.kouloundissa.twinstracker.data.DrugsEvent
import com.kouloundissa.twinstracker.data.Event
import com.kouloundissa.twinstracker.data.EventType
import com.kouloundissa.twinstracker.data.FeedingEvent
import com.kouloundissa.twinstracker.data.GrowthEvent
import com.kouloundissa.twinstracker.data.PumpingEvent
import com.kouloundissa.twinstracker.data.SleepEvent
import com.kouloundissa.twinstracker.presentation.viewmodel.EventViewModel
import java.text.SimpleDateFormat
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.collections.forEach
import kotlin.math.roundToInt


@Composable
fun TimelineList(
    events: List<Event>,
    modifier: Modifier = Modifier,
    onEdit: (Event) -> Unit,
    eventViewModel: EventViewModel = hiltViewModel(),
    baby: Baby,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        events.forEach { event ->
            EventCard(
                event = event,
                onEdit = { onEdit(event) },
                onDelete = {
                    eventViewModel.deleteEvent(event.id, baby.id)
                }
            )
        }
    }
}

@Composable
fun EventCard(
    event: Event,
    onEdit: () -> Unit,
    onDelete: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val eventType = EventType.forClass(event::class)

    val contentColor = Color.White
    val backColor = Color.DarkGray
    val cornerShape = MaterialTheme.shapes.extraLarge

    val context = LocalContext.current
    val deleteAction: () -> Unit = onDelete ?: {
        Toast.makeText(context, "Deleted ${event.id}", Toast.LENGTH_SHORT).show()
    }

    // Swipe & confirmation states
    var offsetX by remember { mutableFloatStateOf(0f) }
    var showConfirm by remember { mutableStateOf(false) }
    val animatedOffset by animateFloatAsState(offsetX, tween(300))

    val density = LocalDensity.current
    val maxSwipePx = with(density) { 120.dp.toPx() }
    val thresholdPx = with(density) { 50.dp.toPx() }

    Box(modifier = modifier.fillMaxWidth()) {

        // Foreground card
        Surface(
            color = backColor.copy(alpha = 0.3f),
            shape = cornerShape,
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(animatedOffset.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (!showConfirm && offsetX <= -thresholdPx) {
                                showConfirm = true
                                offsetX = -maxSwipePx
                            } else {
                                showConfirm = false
                                offsetX = 0f
                            }
                        }
                    ) { change, delta ->
                        change.consume()
                        when {
                            // If confirmation is showing and user drags right, cancel it
                            showConfirm && delta > 0 -> {
                                showConfirm = false
                                offsetX = 0f
                            }
                            // Only allow leftward drag to reveal confirm
                            !showConfirm && delta < 0 -> {
                                offsetX = (offsetX + delta).coerceIn(-maxSwipePx, 0f)
                            }
                            // Ignore other drags
                            else -> { /* no-op */
                            }
                        }
                    }
                }
                .clickable {
                    if (showConfirm) {
                        // Tapped outside bin: cancel
                        showConfirm = false
                        offsetX = 0f
                    } else {
                        onEdit()
                    }
                }
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                eventType.color.copy(alpha = 0.15f),
                                eventType.color.copy(alpha = 0.85f)
                            )
                        ),
                        shape = cornerShape
                    )
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                EventTypeIndicator(eventType)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        buildEventTitle(event, eventType),
                        style = MaterialTheme.typography.titleMedium,
                        color = contentColor
                    )
                    TimeDisplay(event)
                    event.notes?.takeIf(String::isNotBlank)?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = contentColor.copy(alpha = 0.85f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (event is SleepEvent && event.endTime != null) DurationBadge(event)
                event.photoUrl?.takeIf(String::isNotBlank)?.let {
                    Icon(
                        Icons.Default.PhotoCamera,
                        contentDescription = "Photo attached",
                        tint = contentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Edit",
                    tint = contentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Confirm-delete bin overlay
        if (showConfirm) {
            val confirmWidth = with(LocalDensity.current) { (-animatedOffset).toDp() }
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(confirmWidth)
                    .align(Alignment.CenterEnd),
                color = Color.White.copy(alpha = 0.2f),
                shape = cornerShape
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clickable {
                            deleteAction()
                            showConfirm = false
                            offsetX = 0f
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Confirm delete",
                        tint = Color.Red,// MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .size(48.dp)
                            .clickable {
                                deleteAction()
                                showConfirm = false
                                offsetX = 0f
                            }
                    )
                }
            }
        }
    }
}


@Composable
private fun EventTypeIndicator(eventType: EventType) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Color.DarkGray.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = eventType.icon,
            contentDescription = null,
            tint = eventType.color,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun TimeDisplay(event: Event) {
    val formatter = DateTimeFormatter.ofPattern("MMM dd, HH:mm")  // e.g. "Sep 30, 14:45"
    val contentColor = Color.White
    val timeText = when (event) {
        is SleepEvent -> {
            val startTime = event.beginTime?.toInstant()
                ?.atZone(ZoneId.systemDefault())
                ?.format(formatter) ?: "Unknown"

            if (event.endTime != null) {
                val endTime = event.endTime.toInstant()
                    .atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("HH:mm"))  // only show time for end
                "$startTime - $endTime"
            } else {
                "Started at $startTime"
            }
        }

        else -> {
            event.timestamp.toInstant()
                .atZone(ZoneId.systemDefault())
                .format(formatter)
        }
    }

    Text(
        text = timeText,
        style = MaterialTheme.typography.labelSmall,  // small font size
        color = contentColor
    )
}


@Composable
private fun DurationBadge(event: SleepEvent) {
    if (event.endTime == null) return

    val duration = Duration.between(
        event.beginTime?.toInstant() ?: event.timestamp.toInstant(),
        event.endTime.toInstant()
    )

    val hours = duration.toHours()
    val minutes = duration.toMinutes() % 60

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.padding(4.dp)
    ) {
        Text(
            text = "${hours}h ${minutes}m",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

// Build event-specific titles using your existing data classes
private fun buildEventTitle(event: Event, eventType: EventType): String {
    return when (event) {
        is DiaperEvent -> {
            val typeInfo = when (event.diaperType) {
                DiaperType.WET -> "Wet"
                DiaperType.DIRTY -> "Dirty"
                DiaperType.MIXED -> "Mixed"
                DiaperType.DRY -> "Dry"
                else -> ""
            }
            "${eventType.displayName} - $typeInfo"
        }

        is FeedingEvent -> {
            val details = buildString {
                append(eventType.displayName)
                event.amountMl?.let { append(" - ${it.toInt()}ml") }
                event.durationMinutes?.let { append(" (${it}min)") }
                event.breastSide?.let {
                    append(
                        " - ${
                            it.name.lowercase().replaceFirstChar { char -> char.uppercase() }
                        }"
                    )
                }
            }
            details
        }

        is SleepEvent -> {
            if (event.isSleeping) {
                "${eventType.displayName} - Ongoing"
            } else {
                eventType.displayName
            }
        }

        is GrowthEvent -> {
            val measurements = buildString {
                append(eventType.displayName)
                val parts = mutableListOf<String>()
                event.weightKg?.let { parts.add("${String.format("%.1f", it)}kg") }
                event.heightCm?.let { parts.add("${it.toInt()}cm") }
                event.headCircumferenceCm?.let { parts.add("Head: ${String.format("%.1f", it)}cm") }
                if (parts.isNotEmpty()) {
                    append(" - ${parts.joinToString(", ")}")
                }
            }
            measurements
        }

        is PumpingEvent -> {
            val details = buildString {
                append(eventType.displayName)
                event.amountMl?.let { append(" - ${it.toInt()}ml") }
                event.durationMinutes?.let { append(" (${it}min)") }
                event.breastSide?.let {
                    append(
                        " - ${
                            it.name.lowercase().replaceFirstChar { char -> char.uppercase() }
                        }"
                    )
                }
            }
            details
        }

        is DrugsEvent -> {
            val details = buildString {
                append(eventType.displayName)
                val drugName = if (event.drugType == DrugType.OTHER) {
                    event.otherDrugName ?: "Unknown"
                } else {
                    event.drugType.displayName
                }
                append(" - $drugName")
                event.dosage?.let { append(" ${it.toInt()}${event.unit}") }
            }
            details
        }

        else -> eventType.displayName
    }
}
