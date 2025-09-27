import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import com.kouloundissa.twinstracker.data.Event
import com.kouloundissa.twinstracker.data.EventType
import com.kouloundissa.twinstracker.data.SleepEvent
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Date

@Composable
fun DayTimeline(
    events: List<Event>,
    onEdit: (Event) -> Unit,
    modifier: Modifier = Modifier,
    hourRowHeight: Dp = 60.dp
) {
    val eventTypes = EventType.entries
    val spans = remember(events) {
        events.mapNotNull(Event::toSpan)
    }

    Column(modifier.fillMaxSize()) {
        repeat(24) { hour ->
            val covering = spans.filter { it.coversHour(hour) }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(hourRowHeight)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = "%02d:00".format(hour),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.width(48.dp)
                )
                Spacer(Modifier.width(8.dp))
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(hourRowHeight - 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    covering
                        .sortedByDescending  { it.evt is SleepEvent }
                        .forEach { span ->
                            EventSegment(
                                evt = span.evt,
                                onEdit = onEdit,
                                eventTypes = eventTypes,
                                parentWidth = maxWidth,
                                currentHour = hour,
                                startHour = span.startHour,
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
    evt: Event,
    onEdit: (Event) -> Unit,
    eventTypes: List<EventType>,
    parentWidth: Dp,
    currentHour: Int,
    startHour: Int,
    hourRowHeight: Dp = 56.dp
) {
    val type = EventType.forClass(evt::class)
    val startZ = ((evt as? SleepEvent)?.beginTime ?: evt.timestamp).toZoned()
    val endZ = when (evt) {
        is SleepEvent -> (evt.endTime ?: startZ.toInstant()
            .let { Date(it.toEpochMilli()) }).toZoned()

        else -> ZonedDateTime.ofInstant(
            Instant.ofEpochMilli(startZ.toInstant().toEpochMilli() + 30 * 60_000),
            systemZone
        )
    }

    val yOffsetFrac = when {
        !evt.isSleep() -> startZ.minuteFraction
        currentHour == startHour -> startZ.minuteFraction
        else -> 0f
    }

    val durationFrac = when {
        !evt.isSleep() -> ((endZ.toLocalTime().toSecondOfDay() - startZ.toLocalTime()
            .toSecondOfDay()) / 3600f)

        endZ.hour == currentHour -> endZ.minuteFraction
        currentHour == startHour -> 1f - startZ.minuteFraction
        currentHour in (startHour + 1) until endZ.hour -> 1f
        else -> 0f
    }.coerceAtLeast(0f)

    val (slotWidth, xOffset) = computeLayoutParams(evt, eventTypes, parentWidth)

    val rawHeight = hourRowHeight * durationFrac
    val heightDp = rawHeight.coerceAtLeast(MinEventHeight)
    val yOffset = hourRowHeight * yOffsetFrac

    Box(
        modifier = Modifier
            .offset(x = xOffset, y = yOffset)
            .width(slotWidth)
            .height(heightDp)
            .clip(RoundedCornerShape(6.dp))
            .background(type.color.copy(alpha = 0.85f))
            .clickable { onEdit(evt) }
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
                text = evt.notes.takeIf { !it.isNullOrBlank() } ?: type.displayName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
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

private val MinEventHeight = 8.dp

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
    val index = eventTypes.indexOf(EventType.forClass(evt::class))
        .coerceIn(0, columns - 1)
    val w = parentWidth / columns
    return LayoutParams(w, w * index)
}