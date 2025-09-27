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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date

@Composable
fun DayTimeline(
    events: List<Event>,
    onEdit: (Event) -> Unit,
    modifier: Modifier = Modifier,
    hourRowHeight: Dp = 60.dp
) {
    val eventTypes = EventType.entries

    // Precompute events grouped by hour index (0–23)
    data class Span(val evt: Event, val startH: Int, val endH: Int)

    val spans = remember(events) {
        events.mapNotNull { evt ->
            val start = (evt as? SleepEvent)?.beginTime ?: evt.timestamp
            val end = when (evt) {
                is SleepEvent -> evt.endTime ?: start
                else -> Date(start.time + 30 * 60 * 1000)
            }
            val zs = Instant.ofEpochMilli(start.time).atZone(ZoneId.systemDefault())
            val ze = Instant.ofEpochMilli(end.time).atZone(ZoneId.systemDefault())
            Span(evt, zs.hour, ze.hour + if (ze.minute > 0) 1 else 0)
        }
    }


    Column(
        modifier = modifier
            .fillMaxSize()
    ) {
        repeat(24) { hour ->
            // All spans covering this hour
            val covering = spans.filter { it.startH <= hour && it.endH > hour }

            // Count only *instant* events for splitting
            val instants = covering.filter { it.evt !is SleepEvent }
            val splitCount = if (instants.size > 1) instants.size else 1

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(hourRowHeight)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Hour label
                Text(
                    text = "%02d:00".format(hour),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.width(48.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Event container
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(hourRowHeight - 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    val parentWidth = maxWidth
                    covering.forEach { span ->
                        EventChip(
                            evt = span.evt,
                            onEdit = onEdit,
                            hourRowHeight = hourRowHeight - 4.dp,
                            eventTypes = eventTypes,
                            parentWidth = parentWidth
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EventChip(
    evt: Event,
    onEdit: (Event) -> Unit,
    hourRowHeight: Dp = 56.dp,
    eventTypes: List<EventType>,
    parentWidth: Dp
) {
    val type = EventType.forClass(evt::class)

    // 1. Convert Date → Instant
    val startDate = (evt as? SleepEvent)?.beginTime ?: evt.timestamp
    val endDate = when (evt) {
        is SleepEvent -> evt.endTime ?: startDate
        else -> Date(startDate.time + 30 * 60 * 1000)
    }

    val zone = ZoneId.systemDefault()
    val startZ = Instant.ofEpochMilli(startDate.time).atZone(zone)
    val endZ = Instant.ofEpochMilli(endDate.time).atZone(zone)

  

    val offsetFrac = startZ.minute / 60f
    // If SleepEvent spans multiple hours, make height = full card height
    val durationFrac = if (evt is SleepEvent) {
        1f
    } else {
        ((endZ.hour * 60 + endZ.minute) -
                (startZ.hour * 60 + startZ.minute)) / 60f
    }

    val (slotWidth, xOffset) = if (evt is SleepEvent) {
        parentWidth to 0.dp
    } else {
        val nonSleepTypes = eventTypes.filterNot { it.eventClass == SleepEvent::class }
        val total = nonSleepTypes.size.coerceAtLeast(1)
        val rawIndex = eventTypes.indexOf(type).coerceAtLeast(0)
        val idx = rawIndex.coerceIn(0, total - 1)
        val w = parentWidth / total
        w to (w * idx)
    }


    Box(
        modifier = Modifier
            .offset(
                x = xOffset,
                y = if (evt is SleepEvent) 0.dp else hourRowHeight * offsetFrac
            )
            .width(slotWidth)
            .height(hourRowHeight * durationFrac.coerceAtLeast(1f.coerceAtLeast(durationFrac)))
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
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = evt.notes.takeIf { !it.isNullOrBlank() }
                    ?: type.displayName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
