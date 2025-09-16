package com.example.babytracker.presentation.analysis

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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

@Composable
fun AnalysisScreen(
    eventViewModel: EventViewModel = hiltViewModel(),
    babyViewModel: BabyViewModel = hiltViewModel()
) {
    val selectedBaby by babyViewModel.selectedBaby.collectAsState()
    val eventsByDay by eventViewModel.eventsByDay.collectAsState()
    val allSleep by remember { derivedStateOf { eventViewModel.getEventsOfType(SleepEvent::class) } }
    val allGrowth by remember { derivedStateOf { eventViewModel.getEventsOfType(GrowthEvent::class) } }
    val allFeeding by remember { derivedStateOf { eventViewModel.getEventsOfType(FeedingEvent::class) } }
    val allDiaper by remember { derivedStateOf { eventViewModel.getEventsOfType(DiaperEvent::class) } }

    val today = LocalDate.now()
    val last7Days = (0L..6L).map { today.minusDays(6 - it) }
    val omsGender = when (selectedBaby?.gender) {
        Gender.MALE -> Gender.MALE
        Gender.FEMALE -> Gender.FEMALE
        else -> Gender.MALE
    }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .background(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.6f), Color.White.copy(alpha = 0.2f))
                )
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Weekly Analysis",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(16.dp))

        // 1. Poop count per day
        val poopCounts = last7Days.map { date ->
            eventsByDay[date].orEmpty()
                .count { it is DiaperEvent && it.poopColor != null }
        }
        AnalysisCard(title = "Daily Poop Count") {
            BarChartView(
                labels = last7Days.map { it.dayOfMonth.toString() },
                values = poopCounts.map { it.toFloat() }
            )
        }

        // 2. Sleep minutes per day
        val sleepMins = last7Days.map { date ->
            eventsByDay[date].orEmpty().filterIsInstance<SleepEvent>()
                .sumOf { it.durationMinutes ?: 0L }.toFloat()
        }
        AnalysisCard(title = "Daily Sleep (min)") {
            LineChartView(
                labels = last7Days.map { it.dayOfMonth.toString() },
                values = sleepMins
            )
        }

        // 3. Weight growth + WHO curves
        val growthPoints = allGrowth.sortedBy { it.timestamp }
            .map {
                val d = it.timestamp.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
                d.dayOfMonth.toFloat() to (it.weightKg ?: 0.0).toFloat()
            }

        AnalysisCard(title = "Weight Growth (kg)") {
            MultiLineChartView(
                labels = growthPoints.map { it.first.toInt().toString() },
                series = listOf(
                    "Baby" to growthPoints.map { it.second },
                    "15th pct" to WHOWeightCurve(omsGender, 15).map { it.second },
                    "50th pct" to WHOWeightCurve(omsGender, 50).map { it.second },
                    "85th pct" to WHOWeightCurve(omsGender, 85).map { it.second }
                )
            )
        }

        // Similarly for length
        AnalysisCard(title = "Length Growth (cm)") {
            MultiLineChartView(
                labels = growthPoints.map { it.first.toInt().toString() },
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

        // And for head circumference
        AnalysisCard(title = "Head Circumference (cm)") {
            MultiLineChartView(
                labels = growthPoints.map { it.first.toInt().toString() },
                series = listOf(
                    "Baby" to allGrowth.sortedBy { it.timestamp }.map {
                        (it.headCircumferenceCm ?: 0.0).toFloat()
                    },
                    "15th pct" to WHOHeadCircumferenceCurve(omsGender, 15).map { it.second },
                    "50th pct" to WHOHeadCircumferenceCurve(omsGender, 50).map { it.second },
                    "85th pct" to WHOHeadCircumferenceCurve(omsGender, 85).map { it.second }
                )
            )
        }
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
                labels = last7Days.map { it.dayOfMonth.toString() },
                barValues = mealCounts.map { it.toFloat() },
                lineValues = mealVolumes
            )
        }
    }
}

@Composable
fun AnalysisCard(
    title: String,
    content: @Composable BoxScope.() -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.4f),
        tonalElevation = 8.dp
    ) {
        Column(Modifier.fillMaxSize()) {
            Text(
                title, style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(12.dp)
            )
            Box(Modifier
                .fillMaxSize()
                .padding(8.dp), content = content)
        }
    }
}

@Composable
fun BarChartView(labels: List<String>, values: List<Float>) {
    val context = LocalContext.current
    AndroidView(
        factory = {
            BarChart(context).apply {
                this.description.isEnabled = false
                this.setPinchZoom(false)
                this.axisRight.isEnabled = false
                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    granularity = 1f
                    valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(v: Float) = labels[v.toInt()]
                    }
                }
                axisLeft.axisMinimum = 0f
                legend.isEnabled = false
            }
        },
        update = { chart ->
            val entries = values.mapIndexed { i, v -> BarEntry(i.toFloat(), v) }
            val set = BarDataSet(entries, "").apply {
                color = "#FFB300".toColorInt()
                valueTextColor = AndroidColor.BLACK
                valueTextSize = 10f
            }
            chart.data = BarData(set).apply { barWidth = 0.6f }
            chart.invalidate()
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
fun LineChartView(labels: List<String>, values: List<Float>) {
    val context = LocalContext.current
    AndroidView(
        factory = {
            LineChart(context).apply {
                description.isEnabled = false
                setTouchEnabled(false)
                axisRight.isEnabled = false
                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    granularity = 1f
                    valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(v: Float) = labels[v.toInt()]
                    }
                }
                legend.isEnabled = false
                axisLeft.axisMinimum = 0f
            }
        },
        update = { chart ->
            val entries = values.mapIndexed { i, v -> Entry(i.toFloat(), v) }
            val set = LineDataSet(entries, "").apply {
                color = AndroidColor.parseColor("#2196F3")
                valueTextColor = AndroidColor.BLACK
                lineWidth = 2f
                circleRadius = 4f
                setDrawCircles(true)
            }
            chart.data = LineData(set)
            chart.invalidate()
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
fun MultiLineChartView(
    labels: List<String>,
    series: List<Pair<String, List<Float>>>
) {
    val context = LocalContext.current
    AndroidView(
        factory = {
            LineChart(context).apply {
                description.isEnabled = false
                setTouchEnabled(false)
                axisRight.isEnabled = false
                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    granularity = 1f
                    valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(v: Float) =
                            labels[v.toInt().coerceIn(labels.indices)]
                    }
                }
                legend.isEnabled = true
                axisLeft.axisMinimum = 0f
            }
        },
        update = { chart ->
            val sets = series.mapIndexed { idx, (_, vals) ->
                val entries = vals.mapIndexed { i, v -> Entry(i.toFloat(), v) }
                LineDataSet(entries, series[idx].first).apply {
                    color = listOf(
                        AndroidColor.parseColor("#E91E63"),
                        AndroidColor.parseColor("#4CAF50"),
                        AndroidColor.parseColor("#2196F3"),
                        AndroidColor.parseColor("#FFC107")
                    )[idx % 4]
                    lineWidth = 2f
                    setDrawCircles(false)
                }
            }
            chart.data = LineData(sets)
            chart.invalidate()
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
fun ComboChartView(
    labels: List<String>,
    barValues: List<Float>,
    lineValues: List<Float>
) {
    val context = LocalContext.current
    AndroidView(
        factory = {
            CombinedChart(context).apply {
                description.isEnabled = false
                setTouchEnabled(false)
                axisRight.isEnabled = false
                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    granularity = 1f
                    valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(v: Float) = labels[v.toInt()]
                    }
                }
                legend.isEnabled = true
                axisLeft.axisMinimum = 0f
            }
        },
        update = { chart ->
            val barEntries = barValues.mapIndexed { i, v -> BarEntry(i.toFloat(), v) }
            val barSet = BarDataSet(barEntries, "Meals").apply {
                color = AndroidColor.parseColor("#FF5722")
            }
            val lineEntries = lineValues.mapIndexed { i, v -> Entry(i.toFloat(), v) }
            val lineSet = LineDataSet(lineEntries, "ml").apply {
                color = AndroidColor.parseColor("#009688")
                lineWidth = 2f
                setDrawCircles(true)
            }
            chart.data = CombinedData().apply {
                setData(BarData(barSet).apply { barWidth = 0.4f })
                setData(LineData(lineSet))
            }
            chart.invalidate()
        },
        modifier = Modifier.fillMaxSize()
    )
}