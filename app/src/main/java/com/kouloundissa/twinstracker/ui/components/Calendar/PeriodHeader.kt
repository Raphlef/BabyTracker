package com.kouloundissa.twinstracker.ui.components.Calendar

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import com.kouloundissa.twinstracker.ui.theme.DarkBlue
import com.kouloundissa.twinstracker.ui.theme.DarkGrey
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

object HeaderDefaults {
    val IconSize = 40.dp
    val ContentColor = DarkGrey.copy(alpha = 0.5f)
    val Tint = DarkBlue
    val CurrentIndicatorColor = Color(0xFFFF9800)
    val CurrentIndicatorWidth = 1.dp
    val CurrentIndicatorPadding = 8.dp
}

// ============== COMPOSABLE GÉNÉRIQUE (Layout + Logique) ==============
@Composable
fun PeriodHeader(
    modifier: Modifier = Modifier,
    onPreviousPeriod: () -> Unit,
    onNextPeriod: () -> Unit,
    isCurrent: Boolean,
    previousContentDescription: String,
    nextContentDescription: String,
    content: @Composable () -> Unit,
    onContentClick: () -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Previous button
        IconButton(
            onClick = onPreviousPeriod,
            modifier = Modifier.size(HeaderDefaults.IconSize)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = previousContentDescription,
                tint = DarkGrey
            )
        }

        // Content avec indicateur "current"
        Box(
            modifier = Modifier
                .border(
                    width = if (isCurrent) HeaderDefaults.CurrentIndicatorWidth else 0.dp,
                    color = if (isCurrent) HeaderDefaults.CurrentIndicatorColor else Color.Transparent,
                    shape = MaterialTheme.shapes.medium
                )
                .padding(if (isCurrent) HeaderDefaults.CurrentIndicatorPadding else 0.dp)
                .clickable(onClick = onContentClick)
        ) {
            content()
        }

        // Next button
        IconButton(
            onClick = onNextPeriod,
            modifier = Modifier.size(HeaderDefaults.IconSize)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = nextContentDescription,
                tint = DarkGrey
            )
        }
    }
}

// ============== WRAPPER GÉNÉRIQUE ==============
@Composable
fun <T : Any> GenericPeriodHeader(
    currentPeriod: T,
    onPeriodChange: (delta: Long) -> Unit,
    isCurrent: Boolean,
    previousLabel: String,
    nextLabel: String,
    formatter: (T) -> String = { it.toString() },
    customContent: (@Composable (isCurrent: Boolean) -> Unit)? = null,
    onContentClick: () -> Unit = {},
) {
    PeriodHeader(
        onPreviousPeriod = { onPeriodChange(-1L) },
        onNextPeriod = { onPeriodChange(1L) },
        isCurrent = isCurrent,
        previousContentDescription = previousLabel,
        nextContentDescription = nextLabel,
        onContentClick = onContentClick,
        content = {
            if (customContent != null) {
                customContent(isCurrent)
            } else {
                Text(
                    text = formatter(currentPeriod),
                    style = MaterialTheme.typography.titleMedium,
                    color = DarkGrey
                )
            }
        }
    )
}

// ============== WRAPPERS SPÉCIALISÉS (Minimalistes) ==============

@Composable
fun WeekHeader(
    currentWeekMonday: LocalDate,
    onWeekChange: (deltaWeeks: Long) -> Unit
) {
    val today = LocalDate.now()
    val weekEnd = currentWeekMonday.plusDays(6)
    val isCurrentWeek = currentWeekMonday <= today && today <= weekEnd

    GenericPeriodHeader(
        currentPeriod = currentWeekMonday,
        onPeriodChange = onWeekChange,
        isCurrent = false,//isCurrentWeek,
        previousLabel = "Previous Week",
        nextLabel = "Next Week",
        formatter = { monday ->
            "${monday.format(DateTimeFormatter.ofPattern("d MMM"))} - " +
                    "${monday.plusDays(6).format(DateTimeFormatter.ofPattern("d MMM yyyy"))}"
        }
    )
}

@Composable
fun MonthHeader(
    currentMonth: LocalDate,
    onMonthChange: (deltaMonths: Long) -> Unit
) {
    val today = LocalDate.now()
    val isCurrentMonth = currentMonth.year == today.year &&
            currentMonth.monthValue == today.monthValue

    GenericPeriodHeader(
        currentPeriod = currentMonth,
        onPeriodChange = onMonthChange,
        isCurrent = isCurrentMonth,
        previousLabel = "Previous Month",
        nextLabel = "Next Month",
        formatter = { month ->
            month.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
        }
    )
}

@Composable
fun DayHeader(
    currentDate: LocalDate,
    onDayChange: (deltaDays: Long) -> Unit
) {
    val isToday = currentDate == LocalDate.now()

    GenericPeriodHeader(
        currentPeriod = currentDate,
        onPeriodChange = onDayChange,
        isCurrent = isToday,
        previousLabel = "Previous day",
        nextLabel = "Next day",
        customContent = { _ ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (isToday) stringResource(id = R.string.today)
                    else currentDate.dayOfWeek.getDisplayName(
                        TextStyle.SHORT,
                        Locale.getDefault()
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    color = DarkGrey
                )

                Text(
                    text = currentDate.format(DateTimeFormatter.ofPattern("MMM dd, yyyy")),
                    style = MaterialTheme.typography.titleMedium,
                    color = DarkGrey,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    )
}