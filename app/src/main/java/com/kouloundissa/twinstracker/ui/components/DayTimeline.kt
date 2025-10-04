import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import androidx.compose.ui.zIndex
import com.kouloundissa.twinstracker.data.Event
import com.kouloundissa.twinstracker.data.EventType
import com.kouloundissa.twinstracker.data.SleepEvent
import com.kouloundissa.twinstracker.ui.theme.*
import java.time.Instant
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

    val eventTypes = EventType.entries
    // Break multi-day events into per-day spans
    val spans = remember(date, events) {
        events.flatMap { evt ->
            val startZ = evt.timestamp.toInstant().atZone(systemZone)
            val endInstant = (evt as? SleepEvent)?.endTime
                ?.toInstant() ?: startZ.toInstant().plusSeconds(30 * 60)
            val endZ = endInstant.atZone(systemZone)

            // If spans into next day, split
            if (endZ.toLocalDate().isAfter(startZ.toLocalDate())) {
                listOf(
                    // First-day span
                    DaySpan(evt, startZ, startZ.toLocalDate().atTime(23,59).atZone(systemZone)),
                    // Next-day span
                    DaySpan(evt, endZ.toLocalDate().atStartOfDay(systemZone), endZ)
                )
            } else {
                listOf(DaySpan(evt, startZ, endZ))
            }
        }
            .filter { it.start.toLocalDate() == date }  // keep only spans for this day
    }

    Column() {
        repeat(24) { hour ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(hourRowHeight),
                verticalAlignment = Alignment.Top
            ) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(hourRowHeight - 4.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    backgroundColor.copy(alpha = 0.85f),
                                    backgroundColor.copy(alpha = 0.55f)
                                )
                            ),
                            shape = cornerShape,
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .width(84.dp)
                            .fillMaxHeight()
                            .align(Alignment.Center)
                            .zIndex(1f),
                    ) {
                        Text(
                            text = "%02d:00".format(hour),
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold        // Bold text
                            ),
                            color = contentColor
                        )
                    }
                    val covering = spans.filter { it.coversHour(hour) }
                    covering
                        .sortedByDescending { it.evt is SleepEvent }
                        .forEach { span ->
                            EventSegment(
                                span = span,
                                onEdit = onEdit,
                                parentWidth = maxWidth,
                                currentHour = hour,
                                hourRowHeight = hourRowHeight - 4.dp
                            )
                        }
                }
            }
        }
    }
}

@Composable
fun EventSegment(
    span: DaySpan,
    onEdit: (Event) -> Unit,
    parentWidth: Dp,
    currentHour: Int,
    hourRowHeight: Dp
) {
    val cornerShape = MaterialTheme.shapes.large
    val type = EventType.forClass(span.evt::class)

    // Compute fraction of hour before start
    val startFrac = if (currentHour == span.startHour)
        span.start.minute / 60f
    else 0f

    // Compute fraction of hour after end
    val endFrac = if (currentHour == span.end.hour)
        span.end.minute / 60f
    else 1f

    // Duration fraction within this hour
    val durationFrac = (endFrac - startFrac).coerceAtLeast(MIN_INSTANT_FRAC)

    // Layout calculations
    val (slotWidth, xOffset) = computeLayoutParams(span.evt, EventType.entries, parentWidth)
    val yOffset = hourRowHeight * startFrac
    val heightDp = hourRowHeight * durationFrac

    Box(
        modifier = Modifier
            .offset(x = xOffset, y = yOffset)
            .width(slotWidth)
            .height(heightDp)
            .clip(cornerShape)
            .background(type.color.copy(alpha = 0.85f))
            .clickable { onEdit(span.evt) }
            .padding(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = type.icon,
                contentDescription = type.displayName,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = span.evt.notes.takeIf { !it.isNullOrBlank() } ?: type.displayName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
public data class DaySpan(
    val evt: Event,
    val start: ZonedDateTime,
    val end: ZonedDateTime
) {
    val startHour = start.hour
    fun coversHour(hour: Int): Boolean =
        when {
            hour == startHour -> true
            hour in (startHour + 1) until end.hour -> true
            hour == end.hour && end.minute > 0 -> true
            else -> false
        }
}
data class Span(val evt: Event, val startHour: Int, val endHour: Int)

private val systemZone get() = ZoneId.systemDefault()

private fun Date.toZoned() =
    Instant.ofEpochMilli(time).atZone(systemZone)

fun Event.toSpan(): Span? {
    val startDate = (this as? SleepEvent)?.beginTime ?: timestamp
    val endDate = when (this) {
        is SleepEvent -> endTime ?: startDate
        else -> Date(startDate.time + 30 * 60_000)
    }
    val zs = startDate.toZoned()
    val ze = endDate.toZoned()
    val startH = zs.hour
    val endH = ze.hour + if (ze.minute > 0) 1 else 0
    return Span(this, startH, endH)
}

val ZonedDateTime.minuteFraction: Float
    get() = minute / 60f

fun Span.coversHour(hour: Int): Boolean =
    if (evt.isSleep()) {
        startHour <= hour && hour < endHour
    } else {
        hour == startHour
    }

// Minimum visible fraction (e.g. 10% of an hour row)
private val MIN_INSTANT_FRAC = 0.1f

fun Event.isSleep() = this is SleepEvent

data class LayoutParams(val slotWidth: Dp, val xOffset: Dp)

fun computeLayoutParams(
    evt: Event,
    eventTypes: List<EventType>,
    parentWidth: Dp
): LayoutParams {
    if (evt is SleepEvent) {
        return LayoutParams(parentWidth, 0.dp)
    }
    val nonSleep = eventTypes.filterNot { it.eventClass == SleepEvent::class }
    val columns = nonSleep.size.coerceAtLeast(1)
    val index = nonSleep.indexOf(EventType.forClass(evt::class))
        .coerceIn(0, columns - 1)
    val w = parentWidth / columns
    return LayoutParams(w, w * index)
}