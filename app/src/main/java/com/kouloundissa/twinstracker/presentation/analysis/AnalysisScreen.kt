package com.kouloundissa.twinstracker.presentation.analysis

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
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
    val weightpercentileCurves = remember(omsGender) {
        listOf(15.0, 50.0, 85.0).associate { pct ->
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
                val startDate = dateList.first().format(DateTimeFormatter.ofPattern("dd/MM"))
                val endDate = dateList.last().format(DateTimeFormatter.ofPattern("dd/MM"))
                val title = when (selectedRange) {
                    AnalysisRange.CUSTOM -> "Custom Analysis"
                    else -> "${selectedRange.displayName} Analysis"
                }
                Text(
                    "$title\n($startDate - $endDate)",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = BackgroundColor,
                )
            }
            item {
                DateRangeSelector(
                    selectedRange = selectedRange,
                    customStartDate = customStartDate,
                    customEndDate = customEndDate,
                    onRangeSelected = { range, start, end ->
                        selectedRange = range
                        customStartDate = start
                        customEndDate = end
                    },
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            item {
                val poopCounts = dateList.map { date ->
                    eventsByDay[date].orEmpty()
                        .count { it is DiaperEvent && it.poopColor != null }
                }
                AnalysisCard(title = "Daily Poop") {
                    BarChartView(
                        labels = chartLabels,
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

// Add this data class for date range state
data class DateRangeState(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val range: AnalysisRange
)

// Create a helper function to get date range
fun getDateRange(
    range: AnalysisRange,
    customStart: LocalDate?,
    customEnd: LocalDate?,
    baby: Baby?
): Pair<List<LocalDate>, Pair<Int, Int>> {
    val today = LocalDate.now()
    val nowMillis = System.currentTimeMillis()
    val ageMillis = nowMillis - (baby?.birthDate ?: nowMillis)
    val oneDayMillis = 24 * 60 * 60 * 1000L
    val currentAgeDays = (ageMillis / oneDayMillis).toInt().coerceAtLeast(0)

    val (startDate, endDate) = when (range) {
        AnalysisRange.CUSTOM -> {
            if (customStart != null && customEnd != null) {
                customStart to customEnd
            } else {
                today.minusDays(6) to today
            }
        }

        else -> {
            val daysBack = range.days - 1
            today.minusDays(daysBack.toLong()) to today
        }
    }

    val dateList = generateSequence(startDate) { it.plusDays(1) }
        .takeWhile { !it.isAfter(endDate) }
        .toList()

    // Calculate age range for WHO curves
    val startAgeDays = currentAgeDays - ChronoUnit.DAYS.between(startDate, today).toInt()
    val endAgeDays = currentAgeDays

    return dateList to (startAgeDays.coerceAtLeast(0) to endAgeDays)
}

// Create a reusable date range selector component
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangeSelector(
    selectedRange: AnalysisRange,
    customStartDate: LocalDate?,
    customEndDate: LocalDate?,
    onRangeSelected: (AnalysisRange, LocalDate?, LocalDate?) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDateRangePicker by remember { mutableStateOf(false) }
    val dateRangePickerState = rememberDateRangePickerState()

    val baseColor = BackgroundColor
    val contentColor = DarkBlue
    val cornerShape = MaterialTheme.shapes.extraLarge

    Column(modifier = modifier) {
        Text(
            text = "Analysis Period",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = BackgroundColor,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Range selection chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(AnalysisRange.entries.toTypedArray()) { range ->
                FilterChip(
                    onClick = {
                        if (range == AnalysisRange.CUSTOM) {
                            showDateRangePicker = true
                        } else {
                            onRangeSelected(range, null, null)
                        }
                    },
                    label = {
                        Text(
                            text = range.displayName,
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    selected = selectedRange == range,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = baseColor.copy(alpha = 0.15f),
                        labelColor = contentColor.copy(alpha = 0.85f),
                        selectedContainerColor = baseColor.copy(alpha = 0.85f),
                        selectedLabelColor = contentColor
                    ), border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = true,
                        borderColor = contentColor.copy(alpha = 0.55f),
                        selectedBorderColor = contentColor.copy(alpha = 0.55f),
                        borderWidth = 0.5.dp,
                        selectedBorderWidth = 0.5.dp
                    ),
                    shape = cornerShape
                )
            }
        }

        // Show custom range if selected
        if (selectedRange == AnalysisRange.CUSTOM && customStartDate != null && customEndDate != null) {
            Text(
                text = "${customStartDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))} - ${
                    customEndDate.format(
                        DateTimeFormatter.ofPattern("dd/MM/yyyy")
                    )
                }",
                style = MaterialTheme.typography.bodyMedium,
                color = BackgroundColor,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }

    // Date Range Picker Dialog
    if (showDateRangePicker) {
        DatePickerDialog(
            onDismissRequest = { showDateRangePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        dateRangePickerState.selectedStartDateMillis?.let { startMillis ->
                            dateRangePickerState.selectedEndDateMillis?.let { endMillis ->
                                val startDate = Instant.ofEpochMilli(startMillis)
                                    .atZone(ZoneId.systemDefault()).toLocalDate()
                                val endDate = Instant.ofEpochMilli(endMillis)
                                    .atZone(ZoneId.systemDefault()).toLocalDate()
                                onRangeSelected(AnalysisRange.CUSTOM, startDate, endDate)
                            }
                        }
                        showDateRangePicker = false
                    },
                    enabled = dateRangePickerState.selectedEndDateMillis != null
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDateRangePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DateRangePicker(
                state = dateRangePickerState,
                title = { Text("Select Date Range") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(500.dp)
                    .padding(16.dp)
            )
        }
    }
}