package com.kouloundissa.twinstracker.ui.components.Calendar

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
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
fun WeekHeader(
    currentWeekMonday: LocalDate,
    onWeekChange: (deltaWeeks: Long) -> Unit
) {
    val today = LocalDate.now()
    val weekEnd = currentWeekMonday.plusDays(6)
    val isCurrentWeek = currentWeekMonday <= today && today <= weekEnd


    val backgroundColor = BackgroundColor.copy(alpha = 0.2f)
    val tint = DarkBlue

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = { onWeekChange(-1L) },
            colors = IconButtonDefaults.iconButtonColors(containerColor = DarkGrey.copy(alpha = 0.2f))
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous Week")
        }

        Text(
            text = "${currentWeekMonday.format(DateTimeFormatter.ofPattern("d MMM"))} - " +
                    "${weekEnd.format(DateTimeFormatter.ofPattern("d MMM yyyy"))}",
            style = MaterialTheme.typography.titleMedium,
            color = DarkGrey,
            modifier = Modifier
                .border(
                    width = if (isCurrentWeek) 1.dp else 0.dp,
                    color = if (isCurrentWeek) Color(0xFFFF9800) else Color.Transparent,
                    shape = MaterialTheme.shapes.medium
                )
                .padding(if (isCurrentWeek) 8.dp else 0.dp)
        )

        IconButton(
            onClick = { onWeekChange(1L) },
            colors = IconButtonDefaults.iconButtonColors(containerColor = DarkGrey.copy(alpha = 0.2f))
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Week")
        }
    }
}
