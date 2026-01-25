import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import com.kouloundissa.twinstracker.data.Event
import com.kouloundissa.twinstracker.data.EventType
import com.kouloundissa.twinstracker.data.EventType.Companion.getDisplayName
import com.kouloundissa.twinstracker.data.FeedingEvent
import com.kouloundissa.twinstracker.data.PumpingEvent
import com.kouloundissa.twinstracker.data.SleepEvent
import com.kouloundissa.twinstracker.ui.theme.BackgroundColor
import com.kouloundissa.twinstracker.ui.theme.DarkGrey
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Date

@Composable
fun DayTimeline(
    date: LocalDate,
    events: List<Event>,
    onEdit: (Event) -> Unit,
    hourRowHeight: Dp = 60.dp
) {
    val contentColor = DarkGrey.copy(alpha = 0.5f)
    val backgroundColor = BackgroundColor.copy(alpha = 0.2f)
    val cornerShape = MaterialTheme.shapes.large

    // Process events into day spans
    val daySpans = remember(date, events) {
        computeDaySpans(date, events)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        backgroundColor.copy(alpha = 0.9f),
                        backgroundColor.copy(alpha = 0.6f)
                    )
                ),
                shape = cornerShape
            )
            .padding(8.dp)
    ) {
        // LAYER 1: Hour labels and structure
        Column(modifier = Modifier.fillMaxWidth()) {
            repeat(24) { hour ->
                HourRowLabel(
                    hour = hour,
                    contentColor = contentColor,
                    hourRowHeight = hourRowHeight
                )
            }
        }

        // LAYER 2: Events overlay (drawn on top, with absolute positioning)
        Box(modifier = Modifier.fillMaxWidth()) {
            repeat(24) { hour ->
                DrawEventsForHour(
                    hour = hour,
                    daySpans = daySpans,
                    onEdit = onEdit,
                    hourRowHeight = hourRowHeight
                )
            }
        }
    }
}
@Composable
private fun HourRowLabel(
    hour: Int,
    contentColor: Color,
    hourRowHeight: Dp
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(hourRowHeight)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .width(50.dp)
                .fillMaxHeight(),
            contentAlignment = Alignment.TopCenter
        ) {
            Text(
                text = "%02d:00".format(hour),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                ),
                color = contentColor,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
@Composable
private fun DrawEventsForHour(
    hour: Int,
    daySpans: List<DaySpan>,
    onEdit: (Event) -> Unit,
    hourRowHeight: Dp
) {
    // Only get events that START in this hour
    val eventsStartingInHour = daySpans.filter { it.startHour == hour }

    if (eventsStartingInHour.isEmpty()) return

    // Absolute positioning at the hour's vertical position
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .offset(y = hourRowHeight * hour)  // Position at start of this hour
            //.height(hourRowHeight)  // Only anchors the starting point
            .padding(start = 60.dp, end = 8.dp, top = 2.dp)
    ) {
        val sleepEvents = eventsStartingInHour.filter { it.evt is SleepEvent }
        val otherEvents = eventsStartingInHour.filter { it.evt !is SleepEvent }

        // Render sleep events (full width, stacked)
        sleepEvents.forEachIndexed { index, span ->
            EventOverlay(
                span = span,
                onEdit = onEdit,
                widthFraction = 1f,
                xOffsetFraction = 0f,
                stackIndex = index,
                hourRowHeight = hourRowHeight
            )
        }

        // Render other events (side-by-side)
        val numOtherEvents = otherEvents.size
        otherEvents.forEachIndexed { index, span ->
            EventOverlay(
                span = span,
                onEdit = onEdit,
                widthFraction = 1f / numOtherEvents,
                xOffsetFraction = index.toFloat() / numOtherEvents,
                stackIndex = sleepEvents.size,
                hourRowHeight = hourRowHeight
            )
        }
    }
}
@Composable
private fun EventOverlay(
    span: DaySpan,
    onEdit: (Event) -> Unit,
    widthFraction: Float,
    xOffsetFraction: Float,
    stackIndex: Int,
    hourRowHeight: Dp
) {
    val cornerShape = MaterialTheme.shapes.medium
    val type = EventType.forClass(span.evt::class)

    // Calculate total height for entire event duration
    val totalHeightFraction = calculateTotalHeightFraction(span, hourRowHeight)
    // ✅ NEW: Differentiate between punctual and duration events
    val hasDuration = when (span.evt) {
        is SleepEvent -> span.evt.endTime != null
        is FeedingEvent -> (span.evt.durationMinutes ?: 0) > 0
        is PumpingEvent -> (span.evt.durationMinutes ?: 0) > 0
        else -> false  // Punctual events: diaper, growth, drugs
    }

    val minFraction = if (hasDuration) 0.5f else MIN_INSTANT_FRAC  // 30 min or 6 min

    val heightFraction = totalHeightFraction.coerceAtLeast(minFraction)

    // Offset from start minute within first hour
    val topOffsetFraction = span.startMinute / 60f

    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth()
    ) {
        val parentWidth = maxWidth
        val eventWidth = parentWidth * widthFraction - 2.dp
        val eventOffset = parentWidth * xOffsetFraction
        val eventHeight = hourRowHeight * heightFraction  // Can be 2.0, 3.5, etc.
        val topOffset = hourRowHeight * topOffsetFraction
        val verticalSpacing = stackIndex * 2.dp

        // This Box is NOT constrained by parent - it can overflow
        Box(
            modifier = Modifier
                .offset(x = eventOffset, y = topOffset + verticalSpacing)
                .width(eventWidth)
                .height(eventHeight)  // e.g., 120.dp for 2-hour event
                .clip(cornerShape)
                .background(type.color.copy(alpha = 0.85f))
                .clickable { onEdit(span.evt) }
                .padding(4.dp)
        ) {
            EventContent(span = span, type = type)
        }
    }
}



@Composable
private fun EventContent(span: DaySpan, type: EventType) {
    val context = LocalContext.current
    Row(
        modifier = Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Icon(
            imageVector = type.icon,
            contentDescription = type.getDisplayName(context),
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = span.evt.notes.takeIf { !it.isNullOrBlank() } ?: type.getDisplayName(context),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}
data class DaySpan(
    val evt: Event,
    val start: ZonedDateTime,
    val end: ZonedDateTime
) {
    val startHour = start.hour
    val endHour = end.hour
    val startMinute = start.minute
    val endMinute = end.minute
    val spansMultipleHours = endHour > startHour

    fun coversHour(hour: Int): Boolean =
        when {
            hour == startHour && hour == endHour -> true
            hour == startHour -> true
            hour in (startHour + 1) until endHour -> true
            hour == endHour && endMinute > 0 -> true
            else -> false
        }

    fun getHeightFraction(hour: Int): Float {
        return when {
            hour == startHour && hour == endHour -> {
                // Event is completely within this hour
                (endMinute - startMinute) / 60f
            }
            hour == startHour -> {
                // Event starts in this hour and continues
                (60 - startMinute) / 60f
            }
            hour == endHour -> {
                // Event ends in this hour
                endMinute / 60f
            }
            else -> {
                // Event completely fills this hour
                1f
            }
        }
    }
}
fun computeDaySpans(date: LocalDate, events: List<Event>): List<DaySpan> {
    val systemZone = ZoneId.systemDefault()

    return events.flatMap { evt ->
        val startInstant = evt.timestamp.toInstant()
        val startZ = startInstant.atZone(systemZone)

        // Calculate end time based on event type
        val endInstant = when (evt) {
            is SleepEvent -> evt.endTime?.toInstant() ?: startInstant.plusSeconds(30 * 60)
            is FeedingEvent -> if ((evt.durationMinutes ?: 0) > 0) {
                startInstant.plusSeconds((evt.durationMinutes!! * 60).toLong())
            } else {
                startInstant.plusSeconds(30 * 60)
            }
            is PumpingEvent -> if ((evt.durationMinutes ?: 0) > 0) {
                startInstant.plusSeconds((evt.durationMinutes!! * 60).toLong())
            } else {
                startInstant.plusSeconds(30 * 60)
            }
            else -> startInstant.plusSeconds(30 * 60)
        }
        val endZ = endInstant.atZone(systemZone)

        // Split multi-day events
        if (endZ.toLocalDate().isAfter(startZ.toLocalDate())) {
            // Multi-day event - split into spans for each day
            val spans = mutableListOf<DaySpan>()
            var currentDate = startZ.toLocalDate()
            var currentStart = startZ

            while (currentDate < endZ.toLocalDate()) {
                // Span until end of current day
                val currentEnd = currentDate.atTime(23, 59, 59).atZone(systemZone)
                spans.add(DaySpan(evt, currentStart, currentEnd))

                // Move to next day
                currentDate = currentDate.plusDays(1)
                currentStart = currentDate.atStartOfDay(systemZone)
            }

            // Add final day span
            spans.add(DaySpan(evt, currentStart, endZ))
            spans
        } else {
            // Single day event
            listOf(DaySpan(evt, startZ, endZ))
        }
    }
        .filter { it.start.toLocalDate() == date }  // Keep only spans for requested day
}
private fun calculateTotalHeightFraction(span: DaySpan, hourRowHeight: Dp): Float {
    val startHour = span.startHour
    val endHour = span.endHour
    val startMinute = span.startMinute
    val endMinute = span.endMinute

    return when {
        startHour == endHour -> {
            (endMinute - startMinute) / 60f
        }
        else -> {
            var totalMinutes = 0
            totalMinutes += (60 - startMinute)

            for (h in (startHour + 1) until endHour) {
                totalMinutes += 60
            }

            totalMinutes += endMinute
            totalMinutes / 60f
        }
    }
}
private val systemZone: ZoneId
    get() = ZoneId.systemDefault()

private fun Date.toInstant() = java.time.Instant.ofEpochMilli(time)

private fun Date.toZoned() = toInstant().atZone(systemZone)

// Minimum visible height for punctual events (10% of hour)
private val MIN_INSTANT_FRAC = 0.1f

