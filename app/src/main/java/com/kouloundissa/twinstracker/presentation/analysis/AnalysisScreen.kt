package com.kouloundissa.twinstracker.presentation.analysis

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kouloundissa.twinstracker.data.*
import com.kouloundissa.twinstracker.data.WhoLms.WhoLmsRepository
import com.kouloundissa.twinstracker.presentation.viewmodel.BabyViewModel
import com.kouloundissa.twinstracker.presentation.viewmodel.EventViewModel
import com.kouloundissa.twinstracker.ui.components.WHOHeadCircumferenceCurve
import com.kouloundissa.twinstracker.ui.components.WHOLengthCurve
import com.kouloundissa.twinstracker.ui.components.WHOWeightCurve
import java.time.LocalDate
import java.time.ZoneId
import com.kouloundissa.twinstracker.ui.components.AnalysisCard
import com.kouloundissa.twinstracker.ui.components.BarChartView
import com.kouloundissa.twinstracker.ui.components.ComboChartView
import com.kouloundissa.twinstracker.ui.components.LineChartView
import com.kouloundissa.twinstracker.ui.components.MultiLineChartView
import com.kouloundissa.twinstracker.ui.theme.BackgroundColor
import com.kouloundissa.twinstracker.ui.theme.DarkBlue
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.max

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
    val context = LocalContext.current
    val nowMillis = System.currentTimeMillis()
    val ageMillis = nowMillis - (selectedBaby?.birthDate ?: nowMillis)
    val oneDayMillis = 24 * 60 * 60 * 1000L
    val currentAgeDays = (ageMillis / oneDayMillis).toInt().coerceAtLeast(0)
    val startAge = max(0, currentAgeDays - 6) // Last 7 days including today
    val endAge = currentAgeDays
    val omsGender = when (selectedBaby?.gender) {
        Gender.MALE -> Gender.MALE
        Gender.FEMALE -> Gender.FEMALE
        else -> Gender.MALE
    }

    val weightpercentileCurves = remember(omsGender) {
        listOf( 15.0, 50.0, 85.0).associate { pct ->
            "$pct th pct" to WhoLmsRepository.percentileCurveInRange(
                context,
                "weight",
                omsGender,
                pct,
                startAge,
                endAge
            )
        }
    }
    val lengthpercentileCurves = remember(omsGender) {
        listOf(15.0, 50.0, 85.0).associate { pct ->
            "$pct th pct" to WhoLmsRepository.percentileCurveInRange(
                context,
                "length",
                omsGender,
                pct,
                startAge,
                endAge
            )
        }
    }
    val headpercentileCurves = remember(omsGender) {
        listOf(15.0, 50.0, 85.0).associate { pct ->
            "$pct th pct" to WhoLmsRepository.percentileCurveInRange(
                context,
                "head_circumference",
                omsGender,
                pct,
                startAge,
                endAge
            )
        }
    }
    val last7Days = (0L..6L).map { today.minusDays(6 - it) }
    val last7DaysLabels = last7Days.map { date ->
        val dayName = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
        "$dayName"
    }



    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            eventViewModel.clearErrorMessage()
        }
    }
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->

        LazyColumn(
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
        ) {
            item {
                val startDate = last7Days.first().format(DateTimeFormatter.ofPattern("dd/MM"))
                val endDate = last7Days.last().format(DateTimeFormatter.ofPattern("dd/MM"))
                Text(
                    "Weekly Analysis \n($startDate - $endDate)",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = BackgroundColor,
                )
            }

            item {
                val poopCounts = last7Days.map { date ->
                    eventsByDay[date].orEmpty()
                        .count { it is DiaperEvent && it.poopColor != null }
                }
                AnalysisCard(title = "Daily Poop") {
                    BarChartView(
                        labels = last7DaysLabels,
                        values = poopCounts.map { it.toFloat() },
                        forceIncludeZero = true
                    )
                }
            }

            item {
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

            }

            item {

                val babyWeight = allGrowth.sortedBy { it.timestamp }
                    .map { it.weightKg?.toFloat() ?: 0f }
                AnalysisCard(title = "Weight Growth (kg)") {
                    MultiLineChartView(
                        labels = last7DaysLabels,
                        series = listOf("Baby" to babyWeight) + weightpercentileCurves.map { (label, data) ->
                            label to data.map { it.second }
                        }
                    )
                }
            }

            item {
                AnalysisCard(title = "Length Growth (cm)") {
                    MultiLineChartView(
                        labels = last7DaysLabels,
                        series = listOf(
                            "Baby" to growthPoints.map { (_, _) ->
                                // Use baby's length data from your GrowthEvent model or list
                                allGrowth.find { it.heightCm != null }?.heightCm?.toFloat() ?: 0f
                            }
                        ) + lengthpercentileCurves.map { (label, data) ->
                            label to data.map { it.second }
                        }
                    )
                }
            }

            item {
                AnalysisCard(title = "Head Circumference (cm)") {
                    MultiLineChartView(
                        labels = last7DaysLabels,
                        series = listOf(
                            "Baby" to allGrowth.sortedBy { it.timestamp }.map {
                                (it.headCircumferenceCm ?: 0.0).toFloat()
                            }
                        ) + headpercentileCurves.map { (label, data) ->
                            label to data.map { it.second }
                        }
                    )
                }
            }
        }
    }
}