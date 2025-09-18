package com.example.babytracker.presentation.analysis

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.babytracker.data.*
import com.example.babytracker.presentation.viewmodel.BabyViewModel
import com.example.babytracker.presentation.viewmodel.EventViewModel
import com.example.babytracker.ui.components.WHOHeadCircumferenceCurve
import com.example.babytracker.ui.components.WHOLengthCurve
import com.example.babytracker.ui.components.WHOWeightCurve
import java.time.LocalDate
import java.time.ZoneId
import com.github.mikephil.charting.charts.*
import com.github.mikephil.charting.components.*
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.ValueFormatter
import android.graphics.Color as AndroidColor
import androidx.core.graphics.toColorInt
import com.example.babytracker.ui.components.AnalysisCard
import com.example.babytracker.ui.components.BarChartView
import com.example.babytracker.ui.components.ComboChartView
import com.example.babytracker.ui.components.LineChartView
import com.example.babytracker.ui.components.MultiLineChartView
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun AnalysisScreen(
    contentPadding: PaddingValues = PaddingValues(),
    eventViewModel: EventViewModel = hiltViewModel(),
    babyViewModel: BabyViewModel = hiltViewModel()
) {

    val isLoading by eventViewModel.isLoading.collectAsState()
    val errorMessage by eventViewModel.errorMessage.collectAsState()
    val selectedBaby by babyViewModel.selectedBaby.collectAsState()
    val eventsByDay by eventViewModel.eventsByDay.collectAsState()
    val allSleep by remember { derivedStateOf { eventViewModel.getEventsOfType(SleepEvent::class) } }
    val allGrowth by remember { derivedStateOf { eventViewModel.getEventsOfType(GrowthEvent::class) } }
    val allFeeding by remember { derivedStateOf { eventViewModel.getEventsOfType(FeedingEvent::class) } }
    val allDiaper by remember { derivedStateOf { eventViewModel.getEventsOfType(DiaperEvent::class) } }
    val growthPoints = allGrowth.sortedBy { it.timestamp }
    val snackbarHostState = remember { SnackbarHostState() }

    val today = LocalDate.now()
    val last7Days = (0L..6L).map { today.minusDays(6 - it) }
    val last7DaysLabels = last7Days.map { date ->
        val dayName = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
        "$dayName"
    }

    val omsGender = when (selectedBaby?.gender) {
        Gender.MALE -> Gender.MALE
        Gender.FEMALE -> Gender.FEMALE
        else -> Gender.MALE
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            eventViewModel.clearErrorMessage()
        }
    }
    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->

        val bgTop = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        val bgBottom = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)


        LazyColumn(
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(bgTop, bgBottom)
                    )
                ).padding(innerPadding)
        ) {
            item {
                val startDate = last7Days.first().format(DateTimeFormatter.ofPattern("dd/MM"))
                val endDate = last7Days.last().format(DateTimeFormatter.ofPattern("dd/MM"))
                Text(
                    "Weekly Analysis \n($startDate - $endDate)",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                // 1. Poop count per day

                val poopCounts = last7Days.map { date ->
                    eventsByDay[date].orEmpty()
                        .count { it is DiaperEvent && it.poopColor != null }
                }
                AnalysisCard(title = "Daily Poop Count") {
                    BarChartView(
                        labels = last7DaysLabels,
                        values = poopCounts.map { it.toFloat() },
                        forceIncludeZero = true
                    )
                }
            }
            item {
                // 2. Sleep minutes per day
                val sleepMins = last7Days.map { date ->
                    eventsByDay[date].orEmpty().filterIsInstance<SleepEvent>()
                        .sumOf { it.durationMinutes ?: 0L }.toFloat()
                }
                AnalysisCard(title = "Daily Sleep (min)") {
                    LineChartView(
                        labels = last7DaysLabels,
                        values = sleepMins,
                        forceIncludeZero = true
                    )
                }
            }

            item {
                // 3. Weight growth + WHO curves
                growthPoints.map {
                    val d =
                        it.timestamp.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
                    d.dayOfMonth.toFloat() to (it.weightKg ?: 0.0).toFloat()
                }
                val lengthData = allGrowth.sortedBy { it.timestamp }
                    .map { it.heightCm?.toFloat() ?: 0f }
                AnalysisCard(title = "Weight Growth (kg)") {
                    MultiLineChartView(
                        labels = last7DaysLabels,
                        series = listOf(
                            "Baby" to lengthData,
                            "15th pct" to WHOWeightCurve(omsGender, 15).map { it.second },
                            "50th pct" to WHOWeightCurve(omsGender, 50).map { it.second },
                            "85th pct" to WHOWeightCurve(omsGender, 85).map { it.second }
                        )
                    )
                }
            }

            item {
                // Similarly for length
                AnalysisCard(title = "Length Growth (cm)") {
                    MultiLineChartView(
                        labels = last7DaysLabels,
                        series = listOf(
                            "Baby" to growthPoints.map { (_, weight) ->
                                // You'll need height data from GrowthEvent.heightCm
                                allGrowth.find { it.heightCm != null }?.heightCm?.toFloat() ?: 0f
                            },
                            "15th pct" to WHOLengthCurve(omsGender, 15).map { it.second },
                            "50th pct" to WHOLengthCurve(omsGender, 50).map { it.second },
                            "85th pct" to WHOLengthCurve(omsGender, 85).map { it.second }
                        )
                    )
                }
            }

            item {
                // And for head circumference
                AnalysisCard(title = "Head Circumference (cm)") {
                    MultiLineChartView(
                        labels = last7DaysLabels,
                        series = listOf(
                            "Baby" to allGrowth.sortedBy { it.timestamp }.map {
                                (it.headCircumferenceCm ?: 0.0).toFloat()
                            },
                            "15th pct" to WHOHeadCircumferenceCurve(
                                omsGender,
                                15
                            ).map { it.second },
                            "50th pct" to WHOHeadCircumferenceCurve(
                                omsGender,
                                50
                            ).map { it.second },
                            "85th pct" to WHOHeadCircumferenceCurve(omsGender, 85).map { it.second }
                        )
                    )
                }
            }

            item {


                // 4. Meals & Volume
                val mealCounts = last7Days.map { date ->
                    eventsByDay[date].orEmpty().count { it is FeedingEvent }
                }
                val mealVolumes = last7Days.map { date ->
                    eventsByDay[date].orEmpty().filterIsInstance<FeedingEvent>()
                        .sumOf { it.amountMl ?: 0.0 }.toFloat()
                }
                AnalysisCard(title = "Meals & Volume") {
                    ComboChartView(
                        labels = last7DaysLabels,
                        barValues = mealCounts.map { it.toFloat() },
                        lineValues = mealVolumes
                    )
                }

                // Loading Overlay
                if (isLoading) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f))
                    ) {
                        CircularProgressIndicator(Modifier.align(Alignment.Center))
                    }
                }
            }
        }
    }
}