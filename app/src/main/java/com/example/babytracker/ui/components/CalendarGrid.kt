package com.example.babytracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.babytracker.data.Event
import com.example.babytracker.data.EventType
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun CalendarGrid(
    year: Int,
    month: Int,
    eventsByDay: Map<LocalDate, List<Event>>,
    onDayClick: (LocalDate) -> Unit
) {
    val firstOfMonth = LocalDate.of(year, month, 1)
    val daysInMonth = firstOfMonth.lengthOfMonth()
    val startDow = firstOfMonth.dayOfWeek.value % 7
    val totalCells = ((startDow + daysInMonth + 6) / 7) * 7
    val weeks = totalCells / 7

    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("S","M","T","W","T","F","S").forEach { dow ->
                Text(dow, Modifier.weight(1f), textAlign = TextAlign.Center)
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
                    DayCell(
                        date = LocalDate.of(year, month, dayNum),
                        events = eventsByDay[LocalDate.of(year, month, dayNum)].orEmpty(),
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
    onClick: (LocalDate) -> Unit
) {
    Box(
        Modifier
            .size(48.dp)
            .padding(2.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(Color(0xFFF5F5F5))
            .clickable { onClick(date) },
        contentAlignment = Alignment.TopCenter
    ) {
        Text("${date.dayOfMonth}", style = MaterialTheme.typography.bodySmall)
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            events.take(3).forEach { evt ->
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(EventType.forClass(evt::class).color)
                )
            }
            if (events.size > 3) {
                Text("+${events.size - 3}", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
