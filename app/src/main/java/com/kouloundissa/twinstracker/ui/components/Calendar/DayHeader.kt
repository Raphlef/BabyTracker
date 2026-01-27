package com.kouloundissa.twinstracker.ui.components.Calendar

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kouloundissa.twinstracker.R
import com.kouloundissa.twinstracker.ui.theme.DarkGrey
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun DayHeader(
    currentDate: LocalDate,
    onDateChanged: (deltaDays: Long) -> Unit
) {
    val isToday = currentDate == LocalDate.now()

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Previous day button
        IconButton(
            onClick = { onDateChanged(-1L) },
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Previous day",
                tint = DarkGrey
            )
        }

        // Date display (with today indicator)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (isToday) stringResource(id = R.string.today) else currentDate.dayOfWeek.getDisplayName(
                    TextStyle.SHORT,
                    Locale.getDefault()
                ),
                style = MaterialTheme.typography.labelLarge,
                color = DarkGrey,
                modifier = Modifier
                    .border(
                        width = if (isToday) 1.dp else 0.dp,
                        color = if (isToday) Color(0xFFFF9800) else Color.Transparent,
                        shape = MaterialTheme.shapes.medium
                    )
                    .padding(if (isToday) 8.dp else 0.dp)
            )

            Text(
                text = currentDate.format(DateTimeFormatter.ofPattern("MMM dd, yyyy")),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
        }

        // Next day button
        IconButton(
            onClick = { onDateChanged(1L) },
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Next day",
                tint = DarkGrey
            )
        }
    }
}