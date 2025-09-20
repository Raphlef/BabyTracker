package com.kouloundissa.twinstracker.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kouloundissa.twinstracker.data.Event
import com.kouloundissa.twinstracker.data.EventType
import java.time.LocalDate

@Composable
fun CalendarGrid(
    year: Int,
    month: Int,
    eventsByDay: Map<LocalDate, List<Event>>,
    selectedDate: LocalDate,
    onDayClick: (LocalDate) -> Unit
) {
    val firstOfMonth = LocalDate.of(year, month, 1)
    val daysInMonth = firstOfMonth.lengthOfMonth()
    val startDow = firstOfMonth.dayOfWeek.value % 7
    val totalCells = ((startDow + daysInMonth + 6) / 7) * 7
    val weeks = totalCells / 7

    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("S", "M", "T", "W", "T", "F", "S").forEach { dow ->
                Text(
                    dow, Modifier.weight(1f), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        events = eventsByDay[LocalDate.of(year, month, dayNum)].orEmpty(),
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
    val backgroundColor = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer
        events.isNotEmpty() -> MaterialTheme.colorScheme.surfaceContainerHighest
        else -> MaterialTheme.colorScheme.surfaceContainer
    }
    val contentColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    Box(
        Modifier
            .size(48.dp)
            .padding(2.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(backgroundColor)
            .clickable { onClick(date) },
        contentAlignment = Alignment.TopCenter
    ) {
        Text(
            "${date.dayOfMonth}",
            style = MaterialTheme.typography.bodySmall,
            color = contentColor,
            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
        )
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 2.dp, start = 2.dp, end = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterHorizontally)
        ) {

            events.take(3).forEach { evt ->
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background((EventType.forClass(evt::class).color))
                )
            }
            if (events.size > 3) {
                Text(
                    text = "+${events.size - 3}",
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.7f),
                    fontSize = 8.sp
                )
            }
        }
    }
}