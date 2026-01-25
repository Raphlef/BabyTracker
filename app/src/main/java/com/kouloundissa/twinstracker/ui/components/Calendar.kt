package com.kouloundissa.twinstracker.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.with
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.kouloundissa.twinstracker.data.Event
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun Calendar(
    currentMonth: LocalDate,
    onMonthChange: (delta: Long) -> Unit,
    eventsByDay : Map<LocalDate, List<Event>>,
    selectedDate: LocalDate,
    onDayClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    // Remember previous month to determine animation direction
    var previousMonth by remember { mutableStateOf(currentMonth) }
    LaunchedEffect(currentMonth) {
        previousMonth = currentMonth
    }

    // Gesture detection state
    var dragOffset by remember { mutableStateOf(0f) }
    val swipeThreshold = with(LocalDensity.current) { 100.dp.toPx() }

    Box(
        modifier
            .pointerInput(currentMonth) {
                // Only intercept horizontal drags
                detectHorizontalDragGestures(
                    onDragStart = { dragOffset = 0f },
                    onHorizontalDrag = { _, deltaX -> dragOffset += deltaX },
                    onDragEnd = {
                        when {
                            dragOffset > swipeThreshold -> onMonthChange(-1L)
                            dragOffset < -swipeThreshold -> onMonthChange(1L)
                        }
                        dragOffset = 0f
                    }
                )
            }
    ) {
        AnimatedContent(
            targetState = currentMonth,
            transitionSpec = {
                if (targetState > initialState) {
                    slideInHorizontally { width -> width } + fadeIn() with
                            slideOutHorizontally { width -> -width } + fadeOut()
                } else {
                    slideInHorizontally { width -> -width } + fadeIn() with
                            slideOutHorizontally { width -> width } + fadeOut()
                }
            }
        ) { month ->
            Column {
                MonthHeader(
                    currentMonth = month,
                    onMonthChange = onMonthChange
                )
                Spacer(Modifier.height(8.dp))
                CalendarGrid(
                    year = month.year,
                    month = month.monthValue,
                    eventsByDay = eventsByDay,
                    selectedDate = selectedDate,
                    onDayClick = onDayClick
                )
            }
        }
    }
}


