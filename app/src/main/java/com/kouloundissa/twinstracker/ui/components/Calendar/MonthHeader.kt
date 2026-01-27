package com.kouloundissa.twinstracker.ui.components.Calendar


import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kouloundissa.twinstracker.ui.theme.BackgroundColor
import com.kouloundissa.twinstracker.ui.theme.DarkBlue
import com.kouloundissa.twinstracker.ui.theme.DarkGrey
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun MonthHeader(
    currentMonth: LocalDate,
    onMonthChange: (deltaMonths: Long) -> Unit
) {
    val today = LocalDate.now()
    val isCurrentMonth = currentMonth.year == today.year &&
            currentMonth.monthValue == today.monthValue

    val contentColor = DarkGrey.copy(alpha = 0.5f)
    val backgroundColor = BackgroundColor.copy(alpha = 0.2f)
    val tint = DarkBlue
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = { onMonthChange(-1L) },
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Previous Month",
                tint = DarkGrey
            )
        }
        Text(
            text = currentMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
            style = MaterialTheme.typography.titleMedium,
            color = DarkGrey,
            modifier = Modifier
                .border(
                    width = if (isCurrentMonth) 1.dp else 0.dp,
                    color = if (isCurrentMonth) Color(0xFFFF9800) else Color.Transparent,
                    shape = MaterialTheme.shapes.medium
                )
                .padding(if (isCurrentMonth) 8.dp else 0.dp)
        )
        IconButton(
            onClick = { onMonthChange(1L) },
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Next Month",
                tint = DarkGrey
            )
        }
    }
}