package com.kouloundissa.twinstracker.presentation.analysis

import android.app.Activity
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kouloundissa.twinstracker.R
import com.kouloundissa.twinstracker.data.AnalysisRange
import com.kouloundissa.twinstracker.data.Baby
import com.kouloundissa.twinstracker.data.DailyAnalysis
import com.kouloundissa.twinstracker.data.EventType
import com.kouloundissa.twinstracker.data.Gender
import com.kouloundissa.twinstracker.data.GrowthEvent
import com.kouloundissa.twinstracker.data.WhoLms.WhoLmsRepository
import com.kouloundissa.twinstracker.presentation.viewmodel.BabyViewModel
import com.kouloundissa.twinstracker.presentation.viewmodel.EventViewModel
import com.kouloundissa.twinstracker.ui.components.Ad.AdManager
import com.kouloundissa.twinstracker.ui.components.Ad.InlineBannerAd
import com.kouloundissa.twinstracker.ui.components.AnalysisCard
import com.kouloundissa.twinstracker.ui.components.AnalysisFilter
import com.kouloundissa.twinstracker.ui.components.AnalysisFilterPanel
import com.kouloundissa.twinstracker.ui.components.AnalysisFilters
import com.kouloundissa.twinstracker.ui.components.ComboChartView
import com.kouloundissa.twinstracker.ui.components.LineChartView
import com.kouloundissa.twinstracker.ui.components.MultiLineChartView
import com.kouloundissa.twinstracker.ui.theme.BackgroundColor
import com.kouloundissa.twinstracker.ui.theme.DarkBlue
import com.kouloundissa.twinstracker.ui.theme.DarkGrey
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

@Composable
fun AnalysisScreen(
    contentPadding: PaddingValues = PaddingValues(),
    isVisible: Boolean,
    eventViewModel: EventViewModel = hiltViewModel(),
    babyViewModel: BabyViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val activity = context as Activity
    val errorMessage by eventViewModel.errorMessage.collectAsState()
    val favoriteEventTypes by eventViewModel.favoriteEventTypes.collectAsState()
    val allGrowth by eventViewModel.getEventsOfTypeAsFlow(GrowthEvent::class)
        .collectAsState(initial = emptyList())

    val selectedBaby by babyViewModel.selectedBaby.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val filters = remember { mutableStateOf(AnalysisFilters()) }
    val analysisSnapshot by eventViewModel.analysisSnapshot.collectAsState()
    val selectedRange = filters.value.dateRange.selectedRange

    val omsGender = when (selectedBaby?.gender) {
        Gender.MALE -> Gender.MALE
        Gender.FEMALE -> Gender.FEMALE
        else -> Gender.MALE
    }

    // Add state for date range selection
    var customStartDate by remember { mutableStateOf<LocalDate?>(null) }
    var customEndDate by remember { mutableStateOf<LocalDate?>(null) }


    val percentileFormat = stringResource(id = R.string.chart_percentile_format)

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
    val backgroundcolor = BackgroundColor
    val contentcolor = DarkGrey
    val tint = DarkBlue

    LaunchedEffect(selectedBaby, favoriteEventTypes) {
        val newBabyFilter = selectedBaby?.let { baby ->
            AnalysisFilter.BabyFilter(selectedBabies = setOf(baby))
        } ?: AnalysisFilter.BabyFilter()

        filters.value = AnalysisFilters(
            dateRange = filters.value.dateRange,
            babyFilter = newBabyFilter,
            eventTypeFilter = AnalysisFilter.EventTypeFilter(selectedTypes = favoriteEventTypes)
        )
    }
    LaunchedEffect(isVisible, filters.value) {
        if (isVisible) {
            filters.value.babyFilter.selectedBabies.firstOrNull()?.let {
                babyViewModel.selectBaby(it)
                Log.d("AnalysisScreen", "Starting stream for babyId: ${it}")
                eventViewModel.refreshWithFilters(filters.value)
                if (filters.value.dateRange != AnalysisFilter.DateRange(AnalysisRange.ONE_DAY) &&
                    filters.value.dateRange != AnalysisFilter.DateRange(AnalysisRange.THREE_DAYS)&&
                    filters.value.dateRange != AnalysisFilter.DateRange(AnalysisRange.ONE_WEEK))
                    AdManager.showInterstitial(activity)
            }
        }
    }
    LaunchedEffect(Unit) {
        AdManager.preloadInterstitial(
            context = context,
            adUnitId = "ca-app-pub-2976291373414752/5389690911"
        )
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
            AnalysisFilterPanel(
                filters = filters.value,
                onFiltersChanged = { newFilters ->
                    filters.value = newFilters
                },
            )

            LazyColumn(
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
            ) {
                if (filters.value.eventTypeFilter.selectedTypes.isEmpty() ||
                    filters.value.eventTypeFilter.selectedTypes.contains(EventType.FEEDING)
                ) {
                    item {
                        val mealCounts =
                            analysisSnapshot.dailyAnalysis.map { it.mealCount.toFloat() }
                        val mealVolumes = analysisSnapshot.dailyAnalysis.map { it.mealVolume }

                        AnalysisCard(title = stringResource(id = R.string.chart_meals)) {
                            ComboChartView(
                                labels = chartLabels,
                                barValues = mealCounts.map { it.toFloat() },
                                lineValues = mealVolumes,
                                barLabel = stringResource(id = R.string.chart_meals_label),
                                lineLabel = stringResource(id = R.string.chart_meals_volumes_label),
                            )
                        }
                    }
                    item {
                        // AdManager.showInterstitial(activity)
                        InlineBannerAd(
                            adUnitId = "ca-app-pub-2976291373414752/6090374978" // banner ad unit id
                        )
                    }
                }

                if (filters.value.eventTypeFilter.selectedTypes.isEmpty() ||
                    filters.value.eventTypeFilter.selectedTypes.contains(EventType.PUMPING)
                ) {
                    item {
                        val pumpingVolumes =
                            analysisSnapshot.dailyAnalysis.map { it.pumpingCount.toFloat() }
                        val pumpingCounts = analysisSnapshot.dailyAnalysis.map { it.pumpingVolume }

                        AnalysisCard(title = stringResource(id = R.string.chart_pumping)) {
                            ComboChartView(
                                labels = chartLabels,
                                barValues = pumpingCounts.map { it.toFloat() },
                                lineValues = pumpingVolumes,
                                barLabel = stringResource(id = R.string.chart_pumping_number_label),
                                lineLabel = stringResource(id = R.string.chart_pumping_volumes_label),
                            )
                        }
                    }
                    item {
                        // AdManager.showInterstitial(activity)
                        InlineBannerAd(
                            adUnitId = "ca-app-pub-2976291373414752/6090374978" // banner ad unit id
                        )
                    }
                }

                if (filters.value.eventTypeFilter.selectedTypes.isEmpty() ||
                    filters.value.eventTypeFilter.selectedTypes.contains(EventType.DIAPER)
                ) {
                    item {
                        val poopCounts =
                            analysisSnapshot.dailyAnalysis.map { it.poopCount.toFloat() }
                        val wetCounts = analysisSnapshot.dailyAnalysis.map { it.wetCount.toFloat() }

                        AnalysisCard(title = stringResource(id = R.string.chart_poop)) {
                            ComboChartView(
                                labels = chartLabels,
                                barValues = poopCounts.map { it.toFloat() },
                                lineValues = wetCounts.map { it.toFloat() },
                                barLabel = stringResource(id = R.string.chart_poop_count_label),
                                lineLabel = stringResource(id = R.string.chart_wet_count_label),
                            )
                        }
                    }
                    item {
                        // AdManager.showInterstitial(activity)
                        InlineBannerAd(
                            adUnitId = "ca-app-pub-2976291373414752/6090374978" // banner ad unit id
                        )
                    }
                }
                if (filters.value.eventTypeFilter.selectedTypes.isEmpty() ||
                    filters.value.eventTypeFilter.selectedTypes.contains(EventType.SLEEP)
                ) {
                    item {
                        val sleepMinutes =
                            analysisSnapshot.dailyAnalysis.map { it.sleepMinutes.toFloat() }
                        AnalysisCard(title = stringResource(id = R.string.chart_sleep)) {
                            LineChartView(
                                labels = chartLabels,
                                values = sleepMinutes,
                                forceIncludeZero = true
                            )
                        }
                    }
                    item {
                        // AdManager.showInterstitial(activity)
                        InlineBannerAd(
                            adUnitId = "ca-app-pub-2976291373414752/6090374978" // banner ad unit id
                        )
                    }
                }
                if (filters.value.eventTypeFilter.selectedTypes.isEmpty() ||
                    filters.value.eventTypeFilter.selectedTypes.contains(EventType.GROWTH)
                ) {
                    val weights = alignGrowthData(
                        dateList,
                        analysisSnapshot.dailyAnalysis
                    ) { it.growthMeasurements?.weightKg }
                    val heights = alignGrowthData(
                        dateList,
                        analysisSnapshot.dailyAnalysis
                    ) { it.growthMeasurements?.heightCm }
                    val heads = alignGrowthData(
                        dateList,
                        analysisSnapshot.dailyAnalysis
                    ) { it.growthMeasurements?.headCircumferenceCm }
                    item {
                        val birthDate = selectedBaby?.birthDate
                            ?.let {
                                Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())
                                    .toLocalDate()
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

                                // Align with dateList by computing each date's age
                                val aligned = dateList.map { date ->
                                    val ageForDate =
                                        ChronoUnit.DAYS.between(birthDate, date).toInt()
                                    // Only look up if age >= 0 and <= currentAgeDays
                                    rawCurve.find { it.first.toInt() == ageForDate }
                                        ?.second
                                        ?: Float.NaN
                                }
                                "${pct.toInt()}$percentileFormat" to aligned
                            }
                        }
                        AnalysisCard(title = stringResource(id = R.string.chart_weight)) {
                            MultiLineChartView(
                                labels = chartLabels,
                                series = listOf(stringResource(id = R.string.chart_baby_label) to weights) + weightPercentileCurves.map { (label, data) ->
                                    label to data
                                }
                            )
                        }
                    }

                    item {
                        val birthDate = selectedBaby?.birthDate
                            ?.let {
                                Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())
                                    .toLocalDate()
                            }
                            ?: LocalDate.now()
                        val currentAgeDays = ChronoUnit.DAYS.between(birthDate, LocalDate.now())
                            .toInt().coerceAtLeast(0)

                        // 3. Align WHO length curves
                        val lengthPercentileCurves =
                            remember(omsGender, startAge, endAge, dateList) {
                                listOf(15.0, 50.0, 85.0).associate { pct ->
                                    val rawCurve = WhoLmsRepository.percentileCurveInRange(
                                        context, "length", omsGender, pct,
                                        startAge, endAge.coerceAtMost(currentAgeDays)
                                    )
                                    val aligned = dateList.map { date ->
                                        val ageForDate =
                                            ChronoUnit.DAYS.between(birthDate, date).toInt()
                                        rawCurve.find { it.first.toInt() == ageForDate }
                                            ?.second
                                            ?: Float.NaN
                                    }
                                    "${pct.toInt()}$percentileFormat" to aligned
                                }
                            }

                        AnalysisCard(title = stringResource(id = R.string.chart_height)) {
                            MultiLineChartView(
                                labels = chartLabels,
                                series = listOf(stringResource(id = R.string.chart_baby_label) to heights) + lengthPercentileCurves.map { (label, data) ->
                                    label to data
                                }
                            )
                        }
                    }

                    item {
                        val birthDate = selectedBaby?.birthDate
                            ?.let {
                                Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())
                                    .toLocalDate()
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
                                    val ageForDate =
                                        ChronoUnit.DAYS.between(birthDate, date).toInt()
                                    rawCurve.find { it.first.toInt() == ageForDate }
                                        ?.second
                                        ?: Float.NaN
                                }
                                "${pct.toInt()}$percentileFormat" to aligned
                            }
                        }
                        AnalysisCard(title = stringResource(id = R.string.head_circumference)) {
                            MultiLineChartView(
                                labels = chartLabels,
                                series = listOf(stringResource(id = R.string.chart_baby_label) to heads) + headPercentileCurves.map { (label, data) ->
                                    label to data
                                }
                            )
                        }
                    }
                    item {
                        // AdManager.showInterstitial(activity)
                        InlineBannerAd(
                            adUnitId = "ca-app-pub-2976291373414752/6090374978" // banner ad unit id
                        )
                    }
                }
            }
        }
    }
}

// ============================================================================
// HELPER FUNCTIONS
// ============================================================================

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

fun alignGrowthData(
    dateList: List<LocalDate>,
    dailyAnalysis: List<DailyAnalysis>,
    selector: (DailyAnalysis) -> Float?
): List<Float> {
    val measurementByDate = dailyAnalysis.associateBy { it.date }

    val alignedValues = mutableListOf<Float>()
    var lastKnownValue: Float? = null

    dateList.forEach { date ->
        val value = measurementByDate[date]?.let(selector)?.toFloat()
        val finalValue = value ?: lastKnownValue ?: Float.NaN
        alignedValues.add(finalValue)
        if (value != null) lastKnownValue = value
    }
    return alignedValues
}

