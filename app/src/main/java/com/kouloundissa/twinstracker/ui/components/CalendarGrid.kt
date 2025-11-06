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
import com.kouloundissa.twinstracker.ui.theme.BackgroundColor
import com.kouloundissa.twinstracker.ui.theme.DarkBlue
import com.kouloundissa.twinstracker.ui.theme.DarkGrey
import java.time.LocalDate

@Composable
fun CalendarGrid(
    year: Int,
    month: Int,
    eventCountsByDay: Map<LocalDate, Int>,
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

    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach { dow ->
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
                        eventCount = eventCountsByDay[date] ?: 0,
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
    eventCount: Int,
    isSelected: Boolean,
    onClick: (LocalDate) -> Unit
) {

    val tint = DarkBlue
    val backgroundColor = when {
        isSelected -> tint.copy(alpha = 0.7f)
        eventCount > 0 -> BackgroundColor.copy(alpha = 0.7f)
        else -> BackgroundColor.copy(alpha = 0.4f)
    }
    val contentColor = when {
        isSelected -> BackgroundColor
        else -> DarkGrey
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
        // Indicateur simple basé sur le comptage
        if (eventCount > 0) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 4.dp)
            ) {
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(tint)
                )
            }
        }
    }
}