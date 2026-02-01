import android.annotation.SuppressLint
import android.util.Log
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.with
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.times
import androidx.compose.ui.zIndex
import com.kouloundissa.twinstracker.data.AnalysisSnapshot
import com.kouloundissa.twinstracker.data.DiaperEvent
import com.kouloundissa.twinstracker.data.Event
import com.kouloundissa.twinstracker.data.EventType
import com.kouloundissa.twinstracker.data.EventType.Companion.getDisplayName
import com.kouloundissa.twinstracker.data.FeedingEvent
import com.kouloundissa.twinstracker.data.PumpingEvent
import com.kouloundissa.twinstracker.data.SleepEvent
import com.kouloundissa.twinstracker.ui.components.Calendar.DayHeader
import com.kouloundissa.twinstracker.ui.components.Calendar.VERTICAL_SPACING_BETWEEN_STACKED
import com.kouloundissa.twinstracker.ui.theme.BackgroundColor
import com.kouloundissa.twinstracker.ui.theme.DarkGrey
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Date


private val DAY_HOUR_ROW_HEIGHT = 60.dp
private val DAY_HOUR_LABEL_WIDTH = 50.dp

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun DayCalendar(
    currentDate: LocalDate,
    analysisSnapshot: AnalysisSnapshot,
    filterTypes: Set<EventType>,
    onEdit: (Event) -> Unit,
    onDayChange: (delta: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var dragOffset by remember { mutableStateOf(0f) }

    val swipeThreshold = with(LocalDensity.current) { 70.dp.toPx() }

    Box(
        modifier
            .background(BackgroundColor, MaterialTheme.shapes.large)
            .pointerInput(currentDate) {
                detectHorizontalDragGestures(
                    onDragStart = { dragOffset = 0f },
                    onHorizontalDrag = { change, deltaX ->
                        dragOffset += deltaX
                        val absDragOffset = kotlin.math.abs(dragOffset)
                        if (absDragOffset > swipeThreshold) {
                            change.consume()
                        }
                    },
                    onDragEnd = {
                        when {
                            dragOffset > swipeThreshold -> onDayChange(-1L)
                            dragOffset < -swipeThreshold -> onDayChange(1L)
                        }
                        dragOffset = 0f
                    }
                )
            }
            .padding(2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            DayHeader(
                currentDate = currentDate,
                onDayChange = onDayChange
            )

            Spacer(Modifier.height(8.dp))

            AnimatedContent(
                targetState = currentDate,
                transitionSpec = {
                    if (targetState > initialState) {
                        slideInHorizontally { width -> width } + fadeIn() with
                                slideOutHorizontally { width -> -width } + fadeOut()
                    } else {
                        slideInHorizontally { width -> -width } + fadeIn() with
                                slideOutHorizontally { width -> width } + fadeOut()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { date ->
                DayCalendarContent(
                    date = date,
                    analysisSnapshot = analysisSnapshot,
                    filterTypes = filterTypes,
                    onEdit = onEdit,
                )
            }
        }
    }
}

@Composable
private fun DayCalendarContent(
    analysisSnapshot: AnalysisSnapshot,
    date: LocalDate,
    filterTypes: Set<EventType>,
    onEdit: (Event) -> Unit,
    modifier: Modifier = Modifier
) {
    val contentColor = DarkGrey.copy(alpha = 0.5f)
    val backgroundColor = BackgroundColor
    val cornerShape = MaterialTheme.shapes.large

    // Process events into day spans
    val daySpans = remember(date, analysisSnapshot, filterTypes) {
        val filteredEvents = analysisSnapshot.events.filter { event ->
            filterTypes.contains(EventType.forClass(event::class))
        }
        computeDaySpans(filteredEvents)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor, cornerShape)
            .padding(8.dp)
    ) {

        // LAYER 1: Hour labels and structure
        Column(modifier = Modifier.fillMaxWidth()) {
            repeat(24) { hour ->
                HourRowLabel(
                    hour = hour,
                    hourRowHeight = DAY_HOUR_ROW_HEIGHT,
                    hourLabelWidth = DAY_HOUR_LABEL_WIDTH,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // LAYER 2: Events overlay
        DrawEventsForDay(
            day = date,
            daySpans = daySpans,
            hourRowHeight = DAY_HOUR_ROW_HEIGHT,
            onEdit = onEdit,
            modifier = modifier
                .fillMaxHeight()
                .padding(start = DAY_HOUR_ROW_HEIGHT)
        )
    }
}

@Composable
fun DrawEventsForDay(
    day: LocalDate,
    daySpans: List<DaySpan>,
    hourRowHeight: Dp,
    onEdit: (Event) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        if (daySpans.isEmpty()) return@Box
        val dayEventsForThisDay = daySpans.mapNotNull { span ->
            val spanStart = span.start.toLocalDate()
            val spanEnd = span.end.toLocalDate()

            when {
                spanStart == spanEnd && spanStart == day -> span
                spanStart < spanEnd && spanStart == day -> {
                    val endOfDay = span.start.toLocalDate().atTime(23, 59, 59)
                        .atZone(span.start.zone)
                    span.copy(end = endOfDay)
                }

                spanStart < spanEnd && spanEnd == day -> {
                    val startOfDay = day.atTime(0, 0, 0)
                        .atZone(span.start.zone)
                    span.copy(start = startOfDay)
                }

                else -> null
            }
        }

        val sleepEvents = dayEventsForThisDay.filter { it.evt is SleepEvent }
        val otherEventsByHour = dayEventsForThisDay
            .filter { it.evt !is SleepEvent }
            .groupBy { it.startHour }

        CurrentHourIndicator(
            day = day,
            hourRowHeight = hourRowHeight,
            modifier = Modifier.fillMaxWidth()
        )

        // SLEEP: full width
        sleepEvents.forEach  {  span ->
            EventBar(
                span = span,
                hourRowHeight = hourRowHeight,
                onEdit = onEdit
            )
        }

        otherEventsByHour.forEach { (_, eventsInSameHour) ->
            // Pour chaque groupe d'événements à la même heure
            val numConcurrent = eventsInSameHour.size

            eventsInSameHour.forEachIndexed { index, span ->
                EventBar(
                    span = span,
                    widthFraction = 1f / numConcurrent,
                    xOffsetFraction = index.toFloat() / numConcurrent,
                    stackIndex = sleepEvents.size,
                    hourRowHeight = hourRowHeight,
                    onEdit = onEdit
                )
            }
        }
    }
}

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun CurrentHourIndicator(
    day: LocalDate,
    hourRowHeight: Dp,
    modifier: Modifier = Modifier
) {
    val contentColor = DarkGrey.copy(alpha = 0.5f)
    val backgroundColor = BackgroundColor
    val cornerShape = MaterialTheme.shapes.large

    val today = remember { LocalDate.now() }
    var currentHourFraction by remember { mutableStateOf(0f) }

    // Mise à jour automatique chaque minute
    LaunchedEffect(day) {
        if (day == today) {
            while (day == LocalDate.now()) {
                val now = ZonedDateTime.now()
                currentHourFraction = now.hour + (now.minute / 60f)
                delay(60_000) // 1 minute
            }
        }
    }

    if (day != today) return

    BoxWithConstraints(modifier = modifier.fillMaxWidth().zIndex(Float.MAX_VALUE)) {
        val topOffset = hourRowHeight * currentHourFraction

        Box(
            modifier = Modifier
                .offset(y = topOffset)
                .fillMaxWidth()
                .height(2.dp)
                .background(Color(0xFFFF9800))
                .padding(vertical = VERTICAL_SPACING_BETWEEN_STACKED),
        )

        Text(
            text = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm")),
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFFFF9800),
            fontSize = 10.sp,
            modifier = Modifier
                .offset(y = topOffset - 10.dp, x = 2.dp)
                .background(
                    color = backgroundColor,
                    shape = RoundedCornerShape(2.dp)
                )
                .padding(vertical = VERTICAL_SPACING_BETWEEN_STACKED),
        )
    }
}


@Composable
fun HourRowLabel(
    hour: Int,
    hourRowHeight: Dp,
    hourLabelWidth: Dp,
    contentColumns: @Composable (RowScope.() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val contentColor = DarkGrey.copy(alpha = 0.5f)

    Row(modifier = modifier.height(hourRowHeight)) {
        Box(
            modifier = Modifier
                .width(hourLabelWidth)
                .fillMaxHeight(),
            contentAlignment = Alignment.TopCenter
        ) {
            Text(
                text = "%02d:00".format(hour),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = contentColor,
                modifier = Modifier
                    .offset(y = -4 * VERTICAL_SPACING_BETWEEN_STACKED)
            )
        }

        contentColumns?.invoke(this)
    }
}


@Composable
fun EventBar(
    span: DaySpan,
    widthFraction: Float = 1f,
    xOffsetFraction: Float = 0f,
    stackIndex: Int = 0,
    hourRowHeight: Dp,
    onEdit: ((Event) -> Unit)? = null,
    minHeightFraction: Float = 0.1f
) {
    val type = EventType.forClass(span.evt::class)

    // 🐛 DEBUG: Ajoutez ce log temporaire
    if (span.evt is SleepEvent) {
        Log.d("EventBar", """
            Sleep Event Debug:
            - start: ${span.start}
            - startHour: ${span.startHour}
            - startMinute: ${span.startMinute}
            - topOffsetFraction: ${span.startHour + (span.startMinute / 60f)}
        """.trimIndent())
    }

    // Hauteur TOTALE de l'event (start à end, peut être plusieurs heures)
    val totalMinutes = (span.endHour - span.startHour) * 60 +
            (span.endMinute - span.startMinute)
    val heightFraction = (totalMinutes / 60f).coerceAtLeast(minHeightFraction)

    // Position absolue depuis le top (hour de début + minutes)
    val topOffsetFraction = span.startHour + (span.startMinute / 60f)

    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth()
    ) {
        val eventWidth = maxWidth * widthFraction
        val eventOffset = maxWidth * xOffsetFraction
        val eventHeight = hourRowHeight * heightFraction
        val topOffset = hourRowHeight * topOffsetFraction
        val verticalSpacing = stackIndex * VERTICAL_SPACING_BETWEEN_STACKED

        Box(
            modifier = Modifier
                .offset(
                    x = eventOffset,
                    y = topOffset + verticalSpacing
                )
                .width(eventWidth)
                .height(eventHeight)
                .clip(MaterialTheme.shapes.medium)
                .background(type.color.copy(alpha = 0.8f))
                .clickable(enabled = onEdit != null) {
                    onEdit?.invoke(span.evt)
                }
                .padding(4.dp),
            contentAlignment = Alignment.TopStart
        ) {
            EventContent(span = span, type = type)
        }
    }
}


@Composable
fun EventContent(span: DaySpan, type: EventType) {
    val context = LocalContext.current

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        when {
            // ✅ LARGE: Icon + Text complet
            maxWidth > 80.dp -> {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start,
                    // Add horizontal padding to account for container padding
                ) {

                    Icon(
                        imageVector = type.icon,
                        contentDescription = null,
                        tint = BackgroundColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = if (span.evt is DiaperEvent) {
                            stringResource(span.evt.diaperType.displayNameRes) +
                                    (span.evt.notes?.takeIf { it.isNotBlank() }?.let { " • $it" }
                                        ?: "")
                        } else {
                            type.getDisplayName(context) +
                                    (span.evt.notes?.takeIf { it.isNotBlank() }?.let { " • $it" }
                                        ?: "")
                        },

                        style = MaterialTheme.typography.labelSmall,
                        color = BackgroundColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 12.sp
                    )
                }
            }

            // 🟡 MEDIUM: Icon + Text court (pas de spacer)
            maxWidth > 40.dp -> {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = type.icon,
                        contentDescription = null,
                        tint = BackgroundColor,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = span.evt.notes?.take(3) ?: type.getDisplayName(context).take(1),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 10.sp
                    )
                }
            }

            // 🔴 SMALL: Icon seul
            maxWidth > 20.dp -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = type.icon,
                        contentDescription = type.getDisplayName(context),
                        tint = BackgroundColor,
                        modifier = Modifier.size(10.dp)
                    )
                }
            }

            // ⚫ TINY: Juste un point/background
            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                )
            }
        }
    }
}


data class DaySpan(
    val evt: Event,
    val start: ZonedDateTime,
    val end: ZonedDateTime
) {
    val startHour: Int get() = start.hour
    val endHour: Int get() = end.hour
    val startMinute: Int get() = start.minute
    val endMinute: Int get() = end.minute
}

fun computeDaySpans(events: List<Event>): List<DaySpan> {
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
    //    .filter { it.start.toLocalDate() == date }  // Keep only spans for requested day
//    val systemZone = ZoneId.systemDefault()
//    return events.mapNotNull { event ->
//        val startZdt = event.timestamp.toInstant().atZone(systemZone)
//        val endZdt = when (event) {
//            is SleepEvent -> event.endTime?.toInstant()?.atZone(systemZone) ?: startZdt.plusHours(1)
//            is FeedingEvent -> startZdt.plusMinutes((event.durationMinutes ?: 0).toLong())
//            is PumpingEvent -> startZdt.plusMinutes((event.durationMinutes ?: 0).toLong())
//            else -> startZdt.plusMinutes(6)
//        }
//        DaySpan(event, startZdt, endZdt)
//    }
}

fun calculateTotalHeightFraction(span: DaySpan): Float {
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
val MIN_INSTANT_FRAC = 0.1f

