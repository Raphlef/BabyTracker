package com.kouloundissa.twinstracker.presentation.analysis

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kouloundissa.twinstracker.data.*
import com.kouloundissa.twinstracker.data.WhoLms.WhoLmsRepository
import com.kouloundissa.twinstracker.presentation.viewmodel.BabyViewModel
import com.kouloundissa.twinstracker.presentation.viewmodel.EventViewModel
import java.time.LocalDate
import java.time.ZoneId
import com.kouloundissa.twinstracker.ui.components.AnalysisCard
import com.kouloundissa.twinstracker.ui.components.AnalysisFilterPanel
import com.kouloundissa.twinstracker.ui.components.AnalysisFilters
import com.kouloundissa.twinstracker.ui.components.BarChartView
import com.kouloundissa.twinstracker.ui.components.ComboChartView
import com.kouloundissa.twinstracker.ui.components.LineChartView
import com.kouloundissa.twinstracker.ui.components.MultiLineChartView
import com.kouloundissa.twinstracker.ui.theme.BackgroundColor
import com.kouloundissa.twinstracker.ui.theme.DarkBlue
import com.kouloundissa.twinstracker.ui.theme.DarkGrey
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale

@Composable
fun AnalysisScreen(
    contentPadding: PaddingValues = PaddingValues(),
    isVisible: Boolean,
    eventViewModel: EventViewModel = hiltViewModel(),
    babyViewModel: BabyViewModel = hiltViewModel()
) {
    val isLoading by eventViewModel.isLoading.collectAsState()
    val errorMessage by eventViewModel.errorMessage.collectAsState()
    val eventsByDay by eventViewModel.eventsByDay.collectAsState()
    val allGrowth by eventViewModel.getEventsOfTypeAsFlow(GrowthEvent::class)
        .collectAsState(initial = emptyList())

    val selectedBaby by babyViewModel.selectedBaby.collectAsState()
    val babies by babyViewModel.babies.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val context = LocalContext.current

    val omsGender = when (selectedBaby?.gender) {
        Gender.MALE -> Gender.MALE
        Gender.FEMALE -> Gender.FEMALE
        else -> Gender.MALE
    }

    // Add state for date range selection
    var selectedRange by remember { mutableStateOf(AnalysisRange.ONE_WEEK) }
    var customStartDate by remember { mutableStateOf<LocalDate?>(null) }
    var customEndDate by remember { mutableStateOf<LocalDate?>(null) }

    val (dateList, ageRange) = remember(
        selectedRange,
        customStartDate,
        customEndDate,
        selectedBaby
    ) {
        getDateRange(selectedRange, customStartDate, customEndDate, selectedBaby)
    }
    val (startAge, endAge) = ageRange
    val filters = remember { mutableStateOf(AnalysisFilters()) }

    val growthByDate = remember(allGrowth, dateList) {
        allGrowth.groupBy { it.timestamp.toLocalDate() }
    }
    // Generate labels for charts
    val chartLabels = remember(dateList) {
        when {
            dateList.size <= 7 -> dateList.map { date ->
                date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            }

            dateList.size <= 31 -> dateList.map { date ->
                date.format(DateTimeFormatter.ofPattern("dd/MM"))
            }

            else -> {
                // For longer ranges, show only some dates
                val step = (dateList.size / 10).coerceAtLeast(1)
                dateList.filterIndexed { index, _ -> index % step == 0 }
                    .map { it.format(DateTimeFormatter.ofPattern("dd/MM")) }
            }
        }
    }
    val backgroundcolor = BackgroundColor
    val contentcolor = DarkGrey
    val tint = DarkBlue
    val cornerShape = MaterialTheme.shapes.extraLarge

    LaunchedEffect(isVisible, selectedRange, selectedBaby?.id) {
        if (isVisible) {
            selectedBaby?.id?.let {
                // Debounce in the effect itself
                //  delay(150) // Wait for rapid changes to settle
                Log.d("AnalysisScreen", "Starting stream for babyId: ${it}")
                eventViewModel.refreshWithLastDays(it, selectedRange.days.toLong())
            }
        }
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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    shape = cornerShape,
                    color = backgroundcolor.copy(alpha = 0.85f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    AnalysisFilterPanel(
                        filters = filters.value,
                        onFiltersChanged = { newFilters ->
                            filters.value = newFilters
                            // Apply filters to data
                            //applyFiltersToData(newFilters)
                        },
                        availableBabies = babies
                    )
                }


                // Beautiful loading overlay with animation
                this@Column.AnimatedVisibility(
                    visible = isLoading,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.Center)
                            .background(
                                color = contentcolor.copy(alpha = 0.3f),
                                shape = cornerShape
                            )
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(48.dp),
                                strokeWidth = 4.dp,
                                color = DarkBlue,
                                trackColor = DarkBlue.copy(alpha = 0.2f)
                            )
                            Text(
                                text = "Loading baby events...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = DarkBlue,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }


            LazyColumn(
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
            ) {
                item {
                    val mealCounts = dateList.map { date ->
                        eventsByDay[date].orEmpty().count { it is FeedingEvent }
                    }
                    val mealVolumes = dateList.map { date ->
                        eventsByDay[date].orEmpty().filterIsInstance<FeedingEvent>()
                            .sumOf { it.amountMl ?: 0.0 }.toFloat()
                    }
                    AnalysisCard(title = "Meals") {
                        ComboChartView(
                            labels = chartLabels,
                            barValues = mealCounts.map { it.toFloat() },
                            lineValues = mealVolumes,
                            barLabel = "Meals",
                            lineLabel = "Volumes (ml)",
                        )
                    }
                }

                item {
                    val pumpingVolumes = dateList.map { date ->
                        eventsByDay[date].orEmpty().filterIsInstance<PumpingEvent>()
                            .sumOf { it.amountMl ?: 0.0 }.toFloat()
                    }
                    val pumpingCounts = dateList.map { date ->
                        eventsByDay[date].orEmpty()
                            .count { it is PumpingEvent }
                    }

                    AnalysisCard(title = "Pumping") {
                        ComboChartView(
                            labels = chartLabels,
                            barValues = pumpingCounts.map { it.toFloat() },
                            lineValues = pumpingVolumes,
                            barLabel = "Pumping number",
                            lineLabel = "Volumes (ml)",
                        )
                    }
                }

                item {
                    val poopCounts = dateList.map { date ->
                        eventsByDay[date].orEmpty()
                            .count { it is DiaperEvent && (it.diaperType == DiaperType.DIRTY || it.diaperType == DiaperType.MIXED) }
                    }
                    val wetCounts = dateList.map { date ->
                        eventsByDay[date].orEmpty()
                            .count { it is DiaperEvent && (it.diaperType == DiaperType.WET || it.diaperType == DiaperType.MIXED) }
                    }
                    AnalysisCard(title = "Poop") {
                        ComboChartView(
                            labels = chartLabels,
                            barValues = poopCounts.map { it.toFloat() },
                            lineValues = wetCounts.map { it.toFloat() },
                            barLabel = "Poop count",
                            lineLabel = "Wet count",
                        )
                    }
                }

                item {
                    val sleepMins = dateList.map { date ->
                        eventsByDay[date].orEmpty().filterIsInstance<SleepEvent>()
                            .sumOf { it.durationMinutes ?: 0L }.toFloat()
                    }
                    AnalysisCard(title = "Daily Sleep (min)") {
                        LineChartView(
                            labels = chartLabels,
                            values = sleepMins,
                            forceIncludeZero = true
                        )
                    }
                }

                item {
                    val babyWeight = dateList.map { date ->
                        growthByDate[date]
                            ?.maxByOrNull { it.timestamp }
                            ?.weightKg
                            ?.toFloat()
                            ?: Float.NaN
                    }

                    val birthDate = selectedBaby?.birthDate
                        ?.let {
                            Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                        }
                        ?: LocalDate.now()
                    val currentAgeDays = ChronoUnit.DAYS.between(birthDate, LocalDate.now())
                        .toInt().coerceAtLeast(0)

                    //  Build aligned WHO percentile curves
                    val weightPercentileCurves = remember(
                        omsGender, startAge, endAge, dateList
                    ) {
                        listOf(15.0, 50.0, 85.0).associate { pct ->
                            // Raw WHO data only for valid ages
                            val rawCurve = WhoLmsRepository.percentileCurveInRange(
                                context, "weight", omsGender, pct,
                                startAge, endAge.coerceAtMost(currentAgeDays)
                            ) // List<Pair<ageDays, value>>

                            // Align with dateList by computing each date’s age
                            val aligned = dateList.map { date ->
                                val ageForDate = ChronoUnit.DAYS.between(birthDate, date).toInt()
                                // Only look up if age >= 0 and <= currentAgeDays
                                rawCurve.find { it.first.toInt() == ageForDate }
                                    ?.second
                                    ?: Float.NaN
                            }

                            "${pct.toInt()}th pct" to aligned
                        }
                    }
                    AnalysisCard(title = "Weight Growth (kg)") {
                        MultiLineChartView(
                            labels = chartLabels,
                            series = listOf("Baby" to babyWeight) + weightPercentileCurves.map { (label, data) ->
                                label to data
                            }
                        )
                    }
                }

                item {
                    val babyLength = dateList.map { date ->
                        growthByDate[date]
                            ?.maxByOrNull { it.timestamp }
                            ?.heightCm
                            ?.toFloat()
                            ?: Float.NaN
                    }
                    val birthDate = selectedBaby?.birthDate
                        ?.let {
                            Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                        }
                        ?: LocalDate.now()
                    val currentAgeDays = ChronoUnit.DAYS.between(birthDate, LocalDate.now())
                        .toInt().coerceAtLeast(0)

                    // 3. Align WHO length curves
                    val lengthPercentileCurves = remember(omsGender, startAge, endAge, dateList) {
                        listOf(15.0, 50.0, 85.0).associate { pct ->
                            val rawCurve = WhoLmsRepository.percentileCurveInRange(
                                context, "length", omsGender, pct,
                                startAge, endAge.coerceAtMost(currentAgeDays)
                            )
                            val aligned = dateList.map { date ->
                                val ageForDate = ChronoUnit.DAYS.between(birthDate, date).toInt()
                                rawCurve.find { it.first.toInt() == ageForDate }
                                    ?.second
                                    ?: Float.NaN
                            }
                            "${pct.toInt()}th pct" to aligned
                        }
                    }

                    AnalysisCard(title = "Length Growth (cm)") {
                        MultiLineChartView(
                            labels = chartLabels,
                            series = listOf("Baby" to babyLength) + lengthPercentileCurves.map { (label, data) ->
                                label to data
                            }
                        )
                    }
                }

                item {
                    val babyHead = dateList.map { date ->
                        growthByDate[date]
                            ?.maxByOrNull { it.timestamp }
                            ?.headCircumferenceCm
                            ?.toFloat()
                            ?: Float.NaN
                    }
                    val birthDate = selectedBaby?.birthDate
                        ?.let {
                            Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                        }
                        ?: LocalDate.now()
                    val currentAgeDays = ChronoUnit.DAYS.between(birthDate, LocalDate.now())
                        .toInt().coerceAtLeast(0)

                    val headPercentileCurves = remember(omsGender, startAge, endAge, dateList) {
                        listOf(15.0, 50.0, 85.0).associate { pct ->
                            val rawCurve = WhoLmsRepository.percentileCurveInRange(
                                context, "head_circumference", omsGender, pct,
                                startAge, endAge.coerceAtMost(currentAgeDays)
                            )
                            val aligned = dateList.map { date ->
                                val ageForDate = ChronoUnit.DAYS.between(birthDate, date).toInt()
                                rawCurve.find { it.first.toInt() == ageForDate }
                                    ?.second
                                    ?: Float.NaN
                            }
                            "${pct.toInt()}th pct" to aligned
                        }
                    }
                    AnalysisCard(title = "Head Circumference (cm)") {
                        MultiLineChartView(
                            labels = chartLabels,
                            series = listOf("Baby" to babyHead) + headPercentileCurves.map { (label, data) ->
                                label to data
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun Date.toLocalDate(): LocalDate =
    this.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()

// First, create an enum for predefined ranges
enum class AnalysisRange(val displayName: String, val days: Int) {
    ONE_DAY("1 Day", 1),
    THREE_DAYS("3 Days", 3),
    ONE_WEEK("1 Week", 7),
    TWO_WEEKS("2 Weeks", 14),
    ONE_MONTH("1 Month", 30),
    THREE_MONTHS("3 Months", 90),
    CUSTOM("Custom", -1)
}


// Create a helper function to get date range
fun getDateRange(
    range: AnalysisRange,
    customStart: LocalDate?,
    customEnd: LocalDate?,
    baby: Baby?
): Pair<List<LocalDate>, Pair<Int, Int>> {
    val today = LocalDate.now()
    val birthDate = baby?.birthDate
        ?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }
        ?: today
    val ageDays = ChronoUnit.DAYS.between(birthDate, today).toInt().coerceAtLeast(0)

    // Determine start and end dates
    val (rawStart, rawEnd) = when (range) {
        AnalysisRange.CUSTOM -> {
            val s = customStart ?: today.minusDays(6)
            val e = customEnd ?: today
            if (s <= e) s to e else e to s
        }

        else -> {
            today.minusDays((range.days - 1).toLong()) to today
        }
    }

    // Clamp end to today if in future
    val startDate = rawStart.coerceAtMost(today)
    val endDate = rawEnd.coerceAtMost(today)

    // Build the list of dates
    val dateList = generateSequence(startDate) { it.plusDays(1) }
        .takeWhile { !it.isAfter(endDate) }
        .toList()

    // Ages for WHO curves
    val startAge = ChronoUnit.DAYS.between(birthDate, startDate).toInt().coerceAtLeast(0)
    val endAge = ageDays

    return dateList to (startAge to endAge)
}