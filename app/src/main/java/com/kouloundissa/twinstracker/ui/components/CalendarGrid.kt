package com.kouloundissa.twinstracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kouloundissa.twinstracker.data.Event
import com.kouloundissa.twinstracker.data.EventType
import com.kouloundissa.twinstracker.ui.theme.BackgroundColor
import com.kouloundissa.twinstracker.ui.theme.DarkBlue
import com.kouloundissa.twinstracker.ui.theme.DarkGrey
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun CalendarGrid(
    year: Int,
    month: Int,
    eventsByDay: Map<LocalDate, List<Event>>,
    selectedDate: LocalDate,
    onDayClick: (LocalDate) -> Unit
) {
    val contentColor = DarkGrey.copy(alpha = 0.5f)
    val backgroundColor = BackgroundColor.copy(alpha = 0.2f)
    val tint = DarkBlue.copy(alpha = 0.2f)

    val firstOfMonth = LocalDate.of(year, month, 1)
    val daysInMonth = firstOfMonth.lengthOfMonth()
    val startDow = firstOfMonth.dayOfWeek.value % 7 - 1
    val totalCells = ((startDow + daysInMonth + 6) / 7) * 7
    val weeks = totalCells / 7

    val locale = Locale.getDefault()
    val shortDayNames = remember(locale) {
        (1..7).map { dayOfWeek ->
            DayOfWeek.of(dayOfWeek).getDisplayName(TextStyle.SHORT_STANDALONE, locale)
        }
    }
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            shortDayNames.forEach { dow ->
                Text(
                    dow, Modifier.weight(1f), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = BackgroundColor
                )
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier
                .fillMaxWidth()
                .height((weeks * 48).dp),
            userScrollEnabled = false
        ) {
            items(totalCells) { idx ->
                val dayNum = idx - startDow + 1
                if (idx < startDow || dayNum > daysInMonth) {
                    Spacer(Modifier.size(48.dp))
                } else {
                    val date = LocalDate.of(year, month, dayNum)
                    DayCell(
                        date = date,
                        events = eventsByDay[date] ?: emptyList(),
                        isSelected = date == selectedDate,
                        onClick = onDayClick
                    )
                }
            }
        }
    }
}


@Composable
fun DayCell(
    date: LocalDate,
    events: List<Event>,
    isSelected: Boolean,
    onClick: (LocalDate) -> Unit
) {
    val tint = DarkBlue
    val eventCount = events.size

    val activityLevel = ActivityLevel.fromEventCount(eventCount)

    val backgroundColor = when {
        isSelected -> tint.copy(alpha = 0.6f)
        eventCount > 0 -> BackgroundColor.copy(alpha = 0.4f + (activityLevel.ordinal * 0.2f))
        else -> BackgroundColor.copy(alpha = 0.3f)
    }

    val contentColor = when {
        isSelected -> BackgroundColor
        else -> DarkGrey
    }

    val today = LocalDate.now()
    val isToday = date == today

    val eventTypesByFrequency =
        events
            .groupingBy { EventType.forClass(it::class) }
            .eachCount()
            .toList()
            .sortedByDescending { it.second }
            .map { it.first }


    Box(
        Modifier
            .size(48.dp)
            .padding(2.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(backgroundColor)
            .border(
                width = if (isToday) 1.dp else 0.dp,
                color = if (isToday) Color(0xFFFF9800) else Color.Transparent,
                shape = MaterialTheme.shapes.medium
            )
            .clickable { onClick(date) },
        contentAlignment = Alignment.TopCenter
    ) {
        // Numéro du jour
        Text(
            "${date.dayOfMonth}",
            style = MaterialTheme.typography.bodySmall,
            color = contentColor,
            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
            modifier = Modifier.padding(top = 2.dp)
        )

        // Indicateur visuel
        if (eventCount > 0) {
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 2.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (activityLevel) {
                    ActivityLevel.LOW -> {
                        eventTypesByFrequency.take(1).forEach { eventType ->
                            // Point simple
                            Box(
                                Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(eventType.color.copy(alpha = if (isSelected) 1f else 0.7f))
                            )
                        }
                    }

                    ActivityLevel.MEDIUM -> {
                        // Deux points
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {

                            eventTypesByFrequency.take(2).forEach { eventType ->
                                Box(
                                    Modifier
                                        .size(4.dp)
                                        .clip(CircleShape)
                                        .background(eventType.color.copy(alpha = if (isSelected) 1f else 0.8f))
                                )
                            }
                        }

                    }

                    ActivityLevel.HIGH -> {
                        // Badge avec nombre
                        Box(
                            Modifier
                                .height(11.dp)
                                .widthIn(min = 16.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(
                                    eventTypesByFrequency.getOrNull(0)?.color?.copy(
                                        alpha = if (isSelected) 1f else 0.9f
                                    ) ?: tint
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (eventCount > 99) "99+" else eventCount.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = BackgroundColor,
                                fontSize = 7.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    ActivityLevel.NONE -> { /* Rien */
                    }
                }
            }
        }
    }
}

enum class ActivityLevel(val maxCount: Int) {
    NONE(0),
    LOW(5),
    MEDIUM(10),
    HIGH(Int.MAX_VALUE);

    companion object {
        fun fromEventCount(count: Int): ActivityLevel {
            return when {
                count == 0 -> NONE
                count <= LOW.maxCount -> LOW
                count <= MEDIUM.maxCount -> MEDIUM
                else -> HIGH
            }
        }
    }
}
