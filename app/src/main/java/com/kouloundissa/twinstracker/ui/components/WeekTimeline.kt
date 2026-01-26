package com.kouloundissa.twinstracker.ui.components

import DrawEventsForDay
import HourRowLabel
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kouloundissa.twinstracker.data.AnalysisSnapshot
import com.kouloundissa.twinstracker.data.Event
import com.kouloundissa.twinstracker.data.EventType
import com.kouloundissa.twinstracker.ui.theme.BackgroundColor
import com.kouloundissa.twinstracker.ui.theme.DarkBlue
import computeDaySpans
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val WEEK_HOUR_LABEL_WIDTH = 32.dp
private val WEEK_HOUR_ROW_HEIGHT = 48.dp
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
    val weekDays = remember(selectedDate) {
        val mondayOfWeek = selectedDate.minusDays(
            (selectedDate.dayOfWeek.value - 1L) // DayOfWeek: lundi=1, dimanche=7
        )

        // Génère lundi (jour 0) -> dimanche (jour 6)
        (0..6).map { offset ->
            mondayOfWeek.plusDays(offset.toLong())
        }
    }

    val weekEventsByDay = remember(analysisSnapshot, weekDays, filterTypes) {
        analysisSnapshot.eventsByDay
            .filterKeys { date ->
                weekDays.contains(date)  // Utilise exactement ces 7 jours
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
        ) {

            Box(Modifier.width(WEEK_HOUR_LABEL_WIDTH))

            weekDays.forEach { day ->
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
                    HourRowLabel(
                        hour = hour,
                        hourRowHeight = WEEK_HOUR_ROW_HEIGHT,
                        hourLabelWidth = WEEK_HOUR_LABEL_WIDTH,
                        contentColumns = {  // RowScope disponible!
                            weekDays.forEach { day ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .background(
                                            if (day == selectedDate) {
                                                DarkBlue.copy(alpha = 0.1f)
                                            } else {
                                                BackgroundColor
                                            }
                                        )
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // ✅ LAYER 2: Overlay continu des événements
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = WEEK_HOUR_LABEL_WIDTH)  // Offset du label d'heure
            ) {
                weekDays.forEach { day ->
                    val daySpans = weekDaySpans[day] ?: emptyList()

                    DrawEventsForDay(
                        day = day,
                        daySpans = daySpans,
                        hourRowHeight = WEEK_HOUR_ROW_HEIGHT,
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
    val today = LocalDate.now()
    val isToday = day == today

    val dominantEventType = remember(events) {
        events
            .groupingBy { EventType.forClass(it::class) }
            .eachCount()
            .maxByOrNull { it.value }?.key
    }
    Box(
        modifier = modifier
            .clickable { onClick() }
            .background(
                color = if (isSelected) {
                    DarkBlue.copy(alpha = 0.1f)
                } else {
                    BackgroundColor
                },
                shape = if (isToday) {
                    MaterialTheme.shapes.medium
                } else {
                    RectangleShape
                }
            )
            .border(
                width = if (isToday) 1.dp else 0.dp,
                color = if (isToday) Color(0xFFFF9800) else Color.Transparent,
                shape = MaterialTheme.shapes.medium
            ),
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






