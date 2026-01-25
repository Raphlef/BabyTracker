package com.kouloundissa.twinstracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kouloundissa.twinstracker.data.AnalysisSnapshot
import com.kouloundissa.twinstracker.data.Event
import com.kouloundissa.twinstracker.data.EventType
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

    Column(modifier = modifier) {
        // ✅ Headers jours
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Colonne heures vide
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

        // ✅ Grille heures
        repeat(24) { hour ->
            HourRow(
                hour = hour,
                weekEventsByDay = weekEventsByDay,
                modifier = Modifier.fillMaxWidth()
            )
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
private fun HourRow(
    hour: Int,
    weekEventsByDay: Map<LocalDate, List<Event>>,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier) {
        // ✅ Heure
        Text(
            text = String.format("%02d", hour),
            modifier = Modifier.width(32.dp),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = DarkGrey
        )

        // ✅ Cellules jours
        weekEventsByDay.keys.sorted().take(7).forEach { day ->
            val dayEvents = weekEventsByDay[day] ?: emptyList()
            val hourEvents = dayEvents.filter { event ->
                val eventHour = event.timestamp.toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalTime().hour
                eventHour == hour
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(16.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        if (hourEvents.isNotEmpty()) {
                            EventType.forClass(hourEvents.first()::class).color.copy(alpha = 0.25f)
                        } else Color.Transparent
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (hourEvents.size > 1) {
                    Text(
                        "${hourEvents.size}",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkGrey
                    )
                }
            }
        }
    }
}
