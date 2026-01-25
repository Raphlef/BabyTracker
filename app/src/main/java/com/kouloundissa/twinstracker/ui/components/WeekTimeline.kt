package com.kouloundissa.twinstracker.ui.components

import DaySpan
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.times
import com.kouloundissa.twinstracker.data.AnalysisSnapshot
import com.kouloundissa.twinstracker.data.Event
import com.kouloundissa.twinstracker.data.EventType
import com.kouloundissa.twinstracker.data.EventType.Companion.getDisplayName
import com.kouloundissa.twinstracker.data.FeedingEvent
import com.kouloundissa.twinstracker.data.PumpingEvent
import com.kouloundissa.twinstracker.data.SleepEvent
import com.kouloundissa.twinstracker.ui.theme.BackgroundColor
import com.kouloundissa.twinstracker.ui.theme.DarkGrey
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

@Composable
fun WeekTimeline(
    analysisSnapshot: AnalysisSnapshot,
    selectedDate: LocalDate,
    filterTypes: Set<EventType>,
    onDayClick: (LocalDate) -> Unit,
    onEdit: (Event) -> Unit,
    modifier: Modifier = Modifier
) {
    val weekStart = remember(selectedDate) {
        selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    }

    val weekEventsByDay = remember(analysisSnapshot, weekStart, filterTypes) {
        analysisSnapshot.eventsByDay
            .filterKeys { date ->
                !date.isBefore(weekStart) && !date.isAfter(weekStart.plusDays(6))
            }
            .mapValues { (_, dayEvents) ->
                dayEvents.filter { event ->
                    filterTypes.contains(EventType.forClass(event::class))
                }
            }
    }
    val weekDaySpans = remember(weekEventsByDay) {
        weekEventsByDay.mapValues { (_, events) ->
            computeDaySpansForWeek(LocalDate.now(), events)
        }
    }

    val hourRowHeight = 48.dp

    Column(modifier = modifier) {
        // HEADERS
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            Box(Modifier.width(32.dp))

            repeat(7) { index ->
                val day = weekStart.plusDays(index.toLong())
                DayHeader(
                    day = day,
                    events = weekEventsByDay[day] ?: emptyList(),
                    isSelected = day == selectedDate,
                    onClick = { onDayClick(day) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // CONTENEUR AVEC GRILLE + OVERLAY
        Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            // LAYER 1: Grille des heures (structure)
            Column(modifier = Modifier.fillMaxWidth()) {
                repeat(24) { hour ->
                    HourRowStructure(
                        hour = hour,
                        weekStart = weekStart,
                        selectedDate = selectedDate,
                        weekEventsByDay = weekEventsByDay,
                        hourRowHeight = hourRowHeight,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // ✅ LAYER 2: Overlay continu des événements
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 32.dp)  // Offset du label d'heure
            ) {
                repeat(7) { dayIndex ->
                    val day = weekStart.plusDays(dayIndex.toLong())
                    val daySpans = weekDaySpans[day] ?: emptyList()

                    DrawContinuousEventsForDay(
                        day = day,
                        dayIndex = dayIndex,
                        daySpans = daySpans,
                        hourRowHeight = hourRowHeight,
                        weekStart = weekStart,
                        onEdit = onEdit,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            }
        }
    }
}

@Composable
private fun DayHeader(
    day: LocalDate,
    events: List<Event>,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dominantEventType = remember(events) {
        events
            .groupingBy { EventType.forClass(it::class) }
            .eachCount()
            .maxByOrNull { it.value }?.key
    }

    Box(
        modifier = modifier
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = day.format(DateTimeFormatter.ofPattern("E", Locale.getDefault())),
                fontSize = 10.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
            Text(
                text = day.dayOfMonth.toString(),
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )

            if (events.isNotEmpty() && dominantEventType != null) {
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(dominantEventType.color.copy(alpha = 0.8f))
                )
            }
        }
    }
}

@Composable
private fun HourRowStructure(
    hour: Int,
    weekStart: LocalDate,
    selectedDate: LocalDate,
    weekEventsByDay: Map<LocalDate, List<Event>>,
    hourRowHeight: Dp,
    modifier: Modifier = Modifier
) {
    val contentColor = DarkGrey.copy(alpha = 0.5f)

    Row(modifier = modifier.height(hourRowHeight)) {
        // COLONNE HEURES
        Box(
            modifier = Modifier
                .width(32.dp)
                .fillMaxHeight()
                .padding(vertical = 2.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Text(
                text = "%02d:00".format(hour),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = contentColor,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        // GRILLE 7 JOURS (juste background, pas d'events)
        repeat(7) { index ->
            val day = weekStart.plusDays(index.toLong())
            val isSelectedDay = day == selectedDate

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        if (isSelectedDay) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                        } else {
                            BackgroundColor.copy(alpha = 0.1f)
                        }
                    )
            )
        }
    }
}

@Composable
private fun DrawContinuousEventsForDay(
    day: LocalDate,
    dayIndex: Int,
    daySpans: List<DaySpan>,
    hourRowHeight: Dp,
    weekStart: LocalDate,
    onEdit: (Event) -> Unit,
    modifier: Modifier = Modifier
) {
    if (daySpans.isEmpty()) return

    Box(modifier = modifier) {
        val dayEventsForThisDay = mutableListOf<DaySpan>()

        daySpans.forEach { span ->
            val spanStart = span.start.toLocalDate()
            val spanEnd = span.end.toLocalDate()

            when {
                // Event sur un seul jour
                spanStart == spanEnd && spanStart == day -> {
                    dayEventsForThisDay.add(span)
                }
                // Event traverse minuit ET c'est le jour de début
                spanStart < spanEnd && spanStart == day -> {
                    // Créer un segment jusqu'à 23:59:59
                    val endOfDay = span.start.toLocalDate().atTime(23, 59, 59)
                        .atZone(ZoneId.systemDefault())
                    dayEventsForThisDay.add(span.copy(end = endOfDay))
                }
                // Event traverse minuit ET c'est le jour de fin
                spanStart < spanEnd && spanEnd == day -> {
                    // Créer un segment depuis 00:00:00
                    val startOfDay = day.atTime(0, 0, 0)
                        .atZone(ZoneId.systemDefault())
                    dayEventsForThisDay.add(span.copy(start = startOfDay))
                }
            }
        }

        val sleepEvents = dayEventsForThisDay.filter { it.evt is SleepEvent }
        val otherEvents = dayEventsForThisDay.filter { it.evt !is SleepEvent }

        // SLEEP: full width
        sleepEvents.forEachIndexed { stackIndex, span ->
            ContinuousEventBar(
                span = span,
                widthFraction = 1f,
                xOffsetFraction = 0f,
                stackIndex = stackIndex,
                hourRowHeight = hourRowHeight,
                onEdit = onEdit,
                modifier = Modifier.fillMaxWidth()
            )
        }

        val otherEventsByStartHour = otherEvents.groupBy { it.startHour }

        otherEventsByStartHour.forEach { (_, eventsInSameHour) ->
            // Pour chaque groupe d'événements à la même heure
            val numConcurrent = eventsInSameHour.size

            eventsInSameHour.forEachIndexed { index, span ->
                ContinuousEventBar(
                    span = span,
                    widthFraction = 1f / numConcurrent,
                    xOffsetFraction = index.toFloat() / numConcurrent,
                    stackIndex = sleepEvents.size,
                    hourRowHeight = hourRowHeight,
                    modifier = Modifier.fillMaxWidth(),
                    onEdit = onEdit
                )
            }
        }
    }
}


@Composable
private fun ContinuousEventBar(
    span: DaySpan,
    widthFraction: Float,
    xOffsetFraction: Float,
    stackIndex: Int,
    hourRowHeight: Dp,
    modifier: Modifier = Modifier,
    onEdit: (Event) -> Unit
) {
    val type = EventType.forClass(span.evt::class)

    // Hauteur TOTALE de l'event (start à end, peut être plusieurs heures)
    val totalMinutes = (span.endHour - span.startHour) * 60 +
            (span.endMinute - span.startMinute)
    val heightFraction = (totalMinutes / 60f).coerceAtLeast(0.1f)

    // Position absolue depuis le top (hour de début + minutes)
    val topFraction = span.startHour + (span.startMinute / 60f)

    Box(
        modifier = modifier.fillMaxWidth()
    ) {
        BoxWithConstraints(
            modifier = modifier.fillMaxWidth()
        ) {
            val parentWidth = maxWidth

            val eventWidth = parentWidth * widthFraction - 2.dp
            val eventOffset = parentWidth * xOffsetFraction
            val eventHeight = hourRowHeight * heightFraction
            val topOffset = hourRowHeight * topFraction
            val verticalSpacing = stackIndex * 2.dp

            Box(
                modifier = Modifier
                    .offset(
                        x = eventOffset,
                        y = topOffset + verticalSpacing
                    )
                    .width(eventWidth)
                    .height(eventHeight)
                    .clip(RoundedCornerShape(3.dp))
                    .background(type.color.copy(alpha = 0.8f))
                    .clickable { onEdit(span.evt) }
                    .padding(4.dp),
                contentAlignment = Alignment.TopStart
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    Icon(
                        imageVector = type.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = span.evt.notes.takeIf { !it.isNullOrBlank() }
                            ?: type.getDisplayName(LocalContext.current),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 8.sp
                    )
                }
            }
        }
    }
}

fun computeDaySpansForWeek(date: LocalDate, events: List<Event>): List<DaySpan> {
    return events.mapNotNull { event ->
        val (start, end) = when (event) {
            is SleepEvent -> {
                val startZdt = event.timestamp.toInstant()
                    .atZone(ZoneId.systemDefault())
                val endZdt = event.endTime?.toInstant()
                    ?.atZone(ZoneId.systemDefault())
                    ?: startZdt.plusHours(1)
                startZdt to endZdt
            }

            is FeedingEvent -> {
                val startZdt = event.timestamp.toInstant()
                    .atZone(ZoneId.systemDefault())
                val durationMinutes = event.durationMinutes ?: 0
                val endZdt = startZdt.plusMinutes(durationMinutes.toLong())
                startZdt to endZdt
            }

            is PumpingEvent -> {
                val startZdt = event.timestamp.toInstant()
                    .atZone(ZoneId.systemDefault())
                val durationMinutes = event.durationMinutes ?: 0
                val endZdt = startZdt.plusMinutes(durationMinutes.toLong())
                startZdt to endZdt
            }

            else -> {
                // Événements ponctuels : durée de 6 minutes (MIN_INSTANT_FRAC)
                val startZdt = event.timestamp.toInstant()
                    .atZone(ZoneId.systemDefault())
                startZdt to startZdt.plusMinutes(6)
            }
        }
        DaySpan(event, start, end)
    }
}


@Composable
private fun DrawEventsForWeekHour(
    hour: Int,
    day: LocalDate,
    daySpans: List<DaySpan>,
    hourRowHeight: Dp
) {
    // Afficher l'event dans TOUTES ses heures
    val eventsInHour = daySpans.filter { span ->
        // L'event s'affiche si: startHour <= hour < endHour
        hour in span.startHour..span.endHour
    }

    if (eventsInHour.isEmpty()) return

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 1.dp, vertical = 1.dp)
    ) {

        val sleepEvents = eventsInHour.filter { it.evt is SleepEvent }
        val otherEvents = eventsInHour.filter { it.evt !is SleepEvent }

        // SLEEP: empilées verticalement (full width)
        sleepEvents.forEachIndexed { index, span ->
            EventOverlayWeek(
                span = span,
                currentHour = hour,
                widthFraction = 1f,
                xOffsetFraction = 0f,
                stackIndex = index,
                hourRowHeight = hourRowHeight
            )
        }

        // AUTRES: côte à côte
        val numOtherEvents = otherEvents.size
        otherEvents.forEachIndexed { index, span ->
            EventOverlayWeek(
                span = span,
                currentHour = hour,
                widthFraction = 1f / numOtherEvents,
                xOffsetFraction = index.toFloat() / numOtherEvents,
                stackIndex = sleepEvents.size,
                hourRowHeight = hourRowHeight
            )
        }
    }
}

// 4️⃣ VARIANT COMPACTE D'EventOverlay POUR SEMAINE
@Composable
private fun EventOverlayWeek(
    span: DaySpan,
    currentHour: Int,
    widthFraction: Float,
    xOffsetFraction: Float,
    stackIndex: Int,
    hourRowHeight: Dp
) {
    val type = EventType.forClass(span.evt::class)

    val hasDuration = when (span.evt) {
        is SleepEvent -> span.evt.endTime != null
        is FeedingEvent -> (span.evt.durationMinutes ?: 0) > 0
        is PumpingEvent -> (span.evt.durationMinutes ?: 0) > 0
        else -> false
    }

    // ✅ Calcul correct de la hauteur pour cette ligne spécifique
    val heightFraction = when {
        // Si c'est la première heure (où commence l'event)
        currentHour == span.startHour -> {
            // Hauteur = fin de cette heure jusqu'à fin de l'event
            val minutesToEndOfHour = 60 - span.startMinute
            val totalMinutes =
                minutesToEndOfHour + (span.endHour - span.startHour - 1) * 60 + span.endMinute
            (totalMinutes / 60f).coerceAtLeast(if (hasDuration) 0.5f else 0.1f)
        }
        // Si c'est la dernière heure (où finit l'event)
        currentHour == span.endHour -> {
            // Hauteur = du début de cette heure jusqu'à fin de l'event
            (span.endMinute / 60f).coerceAtLeast(if (hasDuration) 0.5f else 0.1f)
        }
        // Si c'est une heure intermédiaire
        else -> {
            // Hauteur = 1 heure complète
            1f
        }
    }

    // ✅ TopOffset seulement si c'est le début
    val topOffsetFraction = if (currentHour == span.startHour) {
        span.startMinute / 60f
    } else {
        0f  // Commence au top de la cellule pour heures intermédiaires
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth()
    ) {
        val parentWidth = maxWidth
        val eventWidth = parentWidth * widthFraction - 1.dp
        val eventOffset = parentWidth * xOffsetFraction
        val eventHeight = hourRowHeight * heightFraction
        val topOffset = hourRowHeight * topOffsetFraction
        val verticalSpacing = stackIndex * 1.dp

        Box(
            modifier = Modifier
                .offset(x = eventOffset, y = topOffset + verticalSpacing)
                .width(eventWidth)
                .height(eventHeight)
                .clip(RoundedCornerShape(2.dp))
                .background(type.color.copy(alpha = 0.75f))
                .padding(2.dp),
            contentAlignment = Alignment.TopStart
        ) {
            // Version compacte du contenu
            Text(
                text = span.evt.notes.takeIf { !it.isNullOrBlank() }
                    ?: type.getDisplayName(LocalContext.current),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 8.sp
            )
        }
    }
}
