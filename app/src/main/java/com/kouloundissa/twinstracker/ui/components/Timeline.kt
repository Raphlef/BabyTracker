package com.kouloundissa.twinstracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import java.text.SimpleDateFormat
import java.time.Duration
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.collections.forEach


@Composable
fun TimelineList(
    events: List<Event>,
    modifier: Modifier = Modifier,
    onEdit: (Event) -> Unit
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        events.forEach { event ->
            EventCard(
                event = event,
                onEdit = { onEdit(event) }
            )
        }
    }
}

@Composable
fun EventCard(
    event: Event,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val eventType = EventType.forClass(event::class)
    val baseColor = MaterialTheme.colorScheme.primary
    val contentColor = MaterialTheme.colorScheme.onPrimary
    val cornerShape = MaterialTheme.shapes.extraLarge

    Surface(
        color = Color.Transparent,
        modifier = modifier
            .fillMaxWidth()
            .clickable { onEdit() },
        tonalElevation = 0.dp,
        shape = cornerShape
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            baseColor.copy(alpha = 0.85f),
                            baseColor.copy(alpha = 0.55f)
                        )
                    ),
                    shape = cornerShape
                )
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Event Type Indicator
            EventTypeIndicator(eventType = eventType)

            // Event Content
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Event Title
                Text(
                    text = buildEventTitle(event, eventType),
                    style = MaterialTheme.typography.titleMedium,
                    color = contentColor
                )

                // Time Display
                TimeDisplay(event = event)

                // Notes (if any)
                if (!event.notes.isNullOrBlank()) {
                    Text(
                        text = event.notes!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.85f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,

                        )
                }
            }

            // Duration Badge (for timed events)
            if (event is SleepEvent && event.endTime != null) {
                DurationBadge(event = event)
            }

            // ➊ Photo Badge
            event.photoUrl?.takeIf { it.isNotBlank() }?.let {
                Icon(
                    imageVector     = Icons.Default.PhotoCamera,
                    contentDescription = "Photo attached",
                    tint            = contentColor,
                    modifier        = Modifier
                        .size(20.dp)
                        .padding(end = 4.dp)
                )
            }

            // Edit Icon
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "Edit event",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun EventTypeIndicator(eventType: EventType) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(eventType.color.copy(alpha = 0.15f)),
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
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    val timeText = when (event) {
        is SleepEvent -> {
            val startTime = event.beginTime?.toInstant()
                ?.atZone(ZoneId.systemDefault())
                ?.format(timeFormatter) ?: "Unknown"

            if (event.endTime != null) {
                val endTime = event.endTime.toInstant()
                    .atZone(ZoneId.systemDefault())
                    .format(timeFormatter)
                "$startTime - $endTime"
            } else {
                "Started at $startTime"
            }
        }

        else -> {
            event.timestamp.toInstant()
                .atZone(ZoneId.systemDefault())
                .format(timeFormatter)
        }
    }

    Text(
        text = timeText,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onPrimary
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
