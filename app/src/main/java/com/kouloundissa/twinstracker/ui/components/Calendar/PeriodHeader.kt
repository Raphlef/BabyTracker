package com.kouloundissa.twinstracker.ui.components.Calendar

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kouloundissa.twinstracker.R
import com.kouloundissa.twinstracker.ui.theme.BackgroundColor
import com.kouloundissa.twinstracker.ui.theme.DarkBlue
import com.kouloundissa.twinstracker.ui.theme.DarkGrey
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.roundToInt

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
    currentPeriodType: PeriodType,
    currentPeriodDate: LocalDate,
    onPeriodChange: (Long) -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }

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
                .clickable { showPicker = true }
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

        if (showPicker) {
            PeriodPicker(
                currentPeriodType = currentPeriodType,
                currentPeriodDate = currentPeriodDate,
                onPeriodSelected = { newDate ->
                    val delta = when (currentPeriodType) {
                        PeriodType.Day -> ChronoUnit.DAYS.between(currentPeriodDate, newDate)
                        PeriodType.Week -> ChronoUnit.WEEKS.between(
                            currentPeriodDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
                            newDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                        )

                        PeriodType.Month -> ChronoUnit.MONTHS.between(
                            currentPeriodDate.withDayOfMonth(
                                1
                            ), newDate.withDayOfMonth(1)
                        )
                    }
                    onPeriodChange(delta)
                    showPicker = false
                },
                onDismiss = { showPicker = false }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeriodPicker(
    currentPeriodType: PeriodType,
    currentPeriodDate: LocalDate,
    onPeriodSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = currentPeriodDate
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    )

    var tempSelectedMonth by remember { mutableStateOf(currentPeriodDate.withDayOfMonth(1)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BackgroundColor,
        title = {
            Text(
                text = when (currentPeriodType) {
                    PeriodType.Month -> stringResource(R.string.period_picker_select_month)
                    PeriodType.Day -> stringResource(R.string.period_picker_select_day)
                    PeriodType.Week -> stringResource(R.string.period_picker_select_week)
                }
            )
        },
        text = {
            if (currentPeriodType == PeriodType.Month) {
                MonthPicker(
                    currentDate = currentPeriodDate.withDayOfMonth(1),
                    onMonthChanged = { newDate ->
                        tempSelectedMonth = newDate
                    }
                )
            } else {
                DatePicker(
                    state = datePickerState,
                    modifier = Modifier.fillMaxWidth(),
                    title = null
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (currentPeriodType == PeriodType.Month) {
                    // Pour Month, on utilise la sélection temporaire
                    onPeriodSelected(tempSelectedMonth)
                } else {
                    // Pour Day et Week, on utilise le DatePicker
                    datePickerState.selectedDateMillis?.let { millis ->
                        val selectedDate = Instant.ofEpochMilli(millis)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()

                        val pickerDate = when (currentPeriodType) {
                            PeriodType.Day -> selectedDate
                            PeriodType.Week -> selectedDate.with(
                                TemporalAdjusters.previousOrSame(
                                    DayOfWeek.MONDAY
                                )
                            )

                            PeriodType.Month -> selectedDate.withDayOfMonth(1)
                        }
                        onPeriodSelected(pickerDate)
                    }
                }
            }) {
                Text(stringResource(R.string.ok_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text((stringResource(R.string.cancel_button)))
            }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MonthPicker(
    currentDate: LocalDate,
    onMonthChanged: (LocalDate) -> Unit
) {
    val itemHeight = 48.dp
    val visibleItemsCount =
        5 // Nombre d'items visibles (2 au-dessus + 1 sélectionné + 2 en-dessous)
    val itemHeightPx = with(LocalDensity.current) { itemHeight.toPx() }

    // Génération des listes
    val years = (1900..2100).toList()
    val months = (1..12).toList()

    // États de scroll avec initialisation à la date courante
    val yearListState = rememberLazyListState(
        initialFirstVisibleItemIndex = years.indexOf(currentDate.year)
    )
    val monthListState = rememberLazyListState(
        initialFirstVisibleItemIndex = currentDate.monthValue - 1
    )

    // Tracking des valeurs sélectionnées
    var selectedYear by remember { mutableIntStateOf(currentDate.year) }
    var selectedMonth by remember { mutableIntStateOf(currentDate.monthValue) }

    // Observer les changements de scroll pour mettre à jour la sélection
    LaunchedEffect(yearListState.firstVisibleItemIndex, yearListState.isScrollInProgress) {
        if (!yearListState.isScrollInProgress) {
            val centerIndex = yearListState.firstVisibleItemIndex +
                    (yearListState.firstVisibleItemScrollOffset / itemHeightPx).roundToInt()
            val newYear = years.getOrNull(centerIndex) ?: currentDate.year
            if (newYear != selectedYear) {
                selectedYear = newYear
                onMonthChanged(LocalDate.of(selectedYear, selectedMonth, 1))
            }
        }
    }

    LaunchedEffect(monthListState.firstVisibleItemIndex, monthListState.isScrollInProgress) {
        if (!monthListState.isScrollInProgress) {
            val centerIndex = monthListState.firstVisibleItemIndex +
                    (monthListState.firstVisibleItemScrollOffset / itemHeightPx).roundToInt()
            val newMonth = months.getOrNull(centerIndex) ?: currentDate.monthValue
            if (newMonth != selectedMonth) {
                selectedMonth = newMonth
                onMonthChanged(LocalDate.of(selectedYear, selectedMonth, 1))
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(itemHeight * visibleItemsCount).background(BackgroundColor)
    ) {
        // Indicateur visuel de la sélection (rectangle central)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(itemHeight)
                .align(Alignment.Center)
                .background(
                    DarkBlue.copy(alpha = 0.1f),
                    RoundedCornerShape(8.dp)
                )
                .border(
                    1.dp,
                    DarkBlue.copy(alpha = 0.5f),
                    RoundedCornerShape(8.dp)
                )
        )

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center
        ) {
            // Scroller pour les années
            PickerColumn(
                items = years,
                state = yearListState,
                itemHeight = itemHeight,
                visibleItemsCount = visibleItemsCount,
                modifier = Modifier.weight(1f),
                itemContent = { year, isSelected ->
                    PickerItem(
                        text = year.toString(),
                        isSelected = isSelected,
                        itemHeight = itemHeight
                    )
                }
            )

            // Scroller pour les mois
            PickerColumn(
                items = months,
                state = monthListState,
                itemHeight = itemHeight,
                visibleItemsCount = visibleItemsCount,
                modifier = Modifier.weight(1f),
                itemContent = { month, isSelected ->
                    PickerItem(
                        text = Month.of(month).getDisplayName(
                            TextStyle.FULL,
                            Locale.getDefault()
                        ),
                        isSelected = isSelected,
                        itemHeight = itemHeight
                    )
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun <T> PickerColumn(
    items: List<T>,
    state: LazyListState,
    itemHeight: Dp,
    visibleItemsCount: Int,
    modifier: Modifier = Modifier,
    itemContent: @Composable (item: T, isSelected: Boolean) -> Unit
) {
    val snapBehavior = rememberSnapFlingBehavior(lazyListState = state)
    val offsetItems = visibleItemsCount / 2

    LazyColumn(
        state = state,
        modifier = modifier,
        flingBehavior = snapBehavior,
        contentPadding = PaddingValues(vertical = itemHeight * offsetItems),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        itemsIndexed(items) { index, item ->
            val isSelected = index == state.firstVisibleItemIndex +
                    (state.firstVisibleItemScrollOffset / with(LocalDensity.current) {
                        itemHeight.toPx()
                    }).roundToInt()

            itemContent(item, isSelected)
        }
    }
}

@Composable
private fun PickerItem(
    text: String,
    isSelected: Boolean,
    itemHeight: Dp
) {
    Box(
        modifier = Modifier
            .height(itemHeight)
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (isSelected) {
                DarkBlue
            } else {
                DarkGrey.copy(alpha = 0.5f)
            },
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            fontSize = if (isSelected) 20.sp else 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

sealed class PeriodType {
    object Day : PeriodType()
    object Week : PeriodType()
    object Month : PeriodType()
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
    periodType: PeriodType,
    currentPeriodDate: LocalDate
) {
    PeriodHeader(
        onPreviousPeriod = { onPeriodChange(-1L) },
        onNextPeriod = { onPeriodChange(1L) },
        isCurrent = isCurrent,
        previousContentDescription = previousLabel,
        nextContentDescription = nextLabel,
        currentPeriodType = periodType,
        currentPeriodDate = currentPeriodDate,
        onPeriodChange = onPeriodChange,
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

// ============== WRAPPERS SPÉCIALISÉS  ==============

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
        periodType = PeriodType.Week,
        currentPeriodDate = currentWeekMonday,
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
        periodType = PeriodType.Month,
        currentPeriodDate = currentMonth,
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
        periodType = PeriodType.Day,
        currentPeriodDate = currentDate,
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