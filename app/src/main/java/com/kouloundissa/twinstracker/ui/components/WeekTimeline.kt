package com.kouloundissa.twinstracker.ui.components

import DaySpan
import EventBar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kouloundissa.twinstracker.data.AnalysisSnapshot
import com.kouloundissa.twinstracker.data.Event
import com.kouloundissa.twinstracker.data.EventType
import com.kouloundissa.twinstracker.data.SleepEvent
import com.kouloundissa.twinstracker.ui.theme.BackgroundColor
import com.kouloundissa.twinstracker.ui.theme.DarkBlue
import com.kouloundissa.twinstracker.ui.theme.DarkGrey
import computeDaySpans
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

private val HOUR_LABEL_WIDTH = 32.dp
private val HOUR_ROW_HEIGHT = 48.dp
val VERTICAL_SPACING_BETWEEN_STACKED = 2.dp

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
            computeDaySpans(events)
        }
    }


    Column(modifier = modifier) {
        // HEADERS
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            Box(Modifier.width(HOUR_LABEL_WIDTH))

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
                        hourRowHeight = HOUR_ROW_HEIGHT,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // ✅ LAYER 2: Overlay continu des événements
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = HOUR_LABEL_WIDTH)  // Offset du label d'heure
            ) {
                repeat(7) { dayIndex ->
                    val day = weekStart.plusDays(dayIndex.toLong())
                    val daySpans = weekDaySpans[day] ?: emptyList()

                    DrawEventsForDay(
                        day = day,
                        daySpans = daySpans,
                        hourRowHeight = HOUR_ROW_HEIGHT,
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
            .padding(vertical = 4.dp)
            .background(
                if (isSelected) {
                    DarkBlue.copy(alpha = 0.1f)
                } else {
                    BackgroundColor.copy(alpha = 0.1f)
                }
            )
            .clip(CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = day.format(DateTimeFormatter.ofPattern("E", Locale.getDefault())),
                fontSize = 10.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = DarkBlue
            )
            Text(
                text = day.dayOfMonth.toString(),
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = DarkBlue
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
    hourRowHeight: Dp,
    modifier: Modifier = Modifier
) {
    val contentColor = DarkGrey.copy(alpha = 0.5f)

    Row(modifier = modifier.height(hourRowHeight)) {
        // COLONNE HEURES
        Box(
            modifier = Modifier
                .width(HOUR_LABEL_WIDTH)
                .fillMaxHeight()
                .padding(vertical = VERTICAL_SPACING_BETWEEN_STACKED),
            contentAlignment = Alignment.TopCenter
        ) {
            Text(
                text = "%02d:00".format(hour),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = contentColor,
                modifier = Modifier.padding(top = VERTICAL_SPACING_BETWEEN_STACKED)
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
                    //.clip(RoundedCornerShape(3.dp))
                    .background(
                        if (isSelectedDay) {
                            DarkBlue.copy(alpha = 0.1f)
                        } else {
                            BackgroundColor.copy(alpha = 0.1f)
                        }
                    )
            )
        }
    }
}

@Composable
private fun DrawEventsForDay(
    day: LocalDate,
    daySpans: List<DaySpan>,
    hourRowHeight: Dp,
    onEdit: (Event) -> Unit,
    modifier: Modifier = Modifier
) {
    if (daySpans.isEmpty()) return

    Box(modifier = modifier) {
        val dayEventsForThisDay = daySpans.mapNotNull { span ->
            val spanStart = span.start.toLocalDate()
            val spanEnd = span.end.toLocalDate()

            when {
                spanStart == spanEnd && spanStart == day -> span
                spanStart < spanEnd && spanStart == day -> {
                    val endOfDay = span.start.toLocalDate().atTime(23, 59, 59)
                        .atZone(ZoneId.systemDefault())
                    span.copy(end = endOfDay)
                }

                spanStart < spanEnd && spanEnd == day -> {
                    val startOfDay = day.atTime(0, 0, 0)
                        .atZone(ZoneId.systemDefault())
                    span.copy(start = startOfDay)
                }

                else -> null
            }
        }

        val sleepEvents = dayEventsForThisDay.filter { it.evt is SleepEvent }
        val otherEventsByHour = dayEventsForThisDay
            .filter { it.evt !is SleepEvent }
            .groupBy { it.startHour }

        // SLEEP: full width
        sleepEvents.forEachIndexed { stackIndex, span ->
            EventBar(
                span = span,
                stackIndex = stackIndex,
                hourRowHeight = hourRowHeight,
                onEdit = onEdit,
                isAbsoluteOffset = true
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
                    onEdit = onEdit,
                    isAbsoluteOffset = true
                )
            }
        }
    }
}




