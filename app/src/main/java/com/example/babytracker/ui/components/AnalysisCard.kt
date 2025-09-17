package com.example.babytracker.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.toColorInt
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.CombinedChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.CombinedData
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import kotlin.math.abs
import android.graphics.Color as AndroidColor

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
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(8.dp), content = content
            )
        }
    }
}

@Composable
fun BarChartView(
    labels: List<String>, values: List<Float>,
    forceIncludeZero: Boolean = false,
    paddingPercentage: Float = 0.1f
) {
    val context = LocalContext.current
    // Calculate optimal axis range
    val (axisMin, axisMax) = remember(values) {
        calculateAxisRange(values, paddingPercentage, forceIncludeZero)
    }
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
                axisLeft.apply {
                    axisMinimum = axisMin
                    axisMaximum = axisMax
                    spaceTop = 5f   // Additional visual spacing
                    spaceBottom = 5f
                }
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
            chart.axisLeft.apply {
                axisMinimum = axisMin
                axisMaximum = axisMax
            }

            chart.invalidate()
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
fun LineChartView(
    labels: List<String>, values: List<Float>,
    forceIncludeZero: Boolean = false,
    paddingPercentage: Float = 0.1f
) {
    val context = LocalContext.current
    val (axisMin, axisMax) = remember(values) {
        calculateAxisRange(values, paddingPercentage, forceIncludeZero)
    }

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
                axisLeft.apply {
                    axisMinimum = axisMin
                    axisMaximum = axisMax
                    spaceTop = 5f
                    spaceBottom = 5f
                }
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
            chart.axisLeft.apply {
                axisMinimum = axisMin
                axisMaximum = axisMax
            }
            chart.invalidate()
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
fun MultiLineChartView(
    labels: List<String>,
    series: List<Pair<String, List<Float>>>,
    forceIncludeZero: Boolean = false,
    paddingPercentage: Float = 0.1f
) {
    val context = LocalContext.current
    val (axisMin, axisMax) = remember(series) {
        val allValues = series.flatMap { it.second }
        calculateAxisRange(allValues, paddingPercentage, forceIncludeZero)
    }
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
                axisLeft.apply {
                    axisMinimum = axisMin
                    axisMaximum = axisMax
                    setSpaceTop(5f)
                    setSpaceBottom(5f)
                }
            }
        },
        update = { chart ->
            val sets = series.mapIndexed { idx, (_, vals) ->
                val entries = vals.mapIndexed { i, v -> Entry(i.toFloat(), v) }
                LineDataSet(entries, series[idx].first).apply {
                    color = listOf(
                        "#E91E63".toColorInt(),
                        "#4CAF50".toColorInt(),
                        "#2196F3".toColorInt(),
                        "#FFC107".toColorInt()
                    )[idx % 4]
                    lineWidth = 2f
                    setDrawCircles(false)
                }
            }
            chart.data = LineData(sets)
            chart.axisLeft.apply {
                axisMinimum = axisMin
                axisMaximum = axisMax
            }
            chart.invalidate()
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
fun ComboChartView(
    labels: List<String>,
    barValues: List<Float>,
    lineValues: List<Float>,
    forceIncludeZeroLeft: Boolean = false,
    forceIncludeZeroRight: Boolean = false,
    paddingPercentage: Float = 0.1f
) {
    val context = LocalContext.current
    // Calculate separate ranges for each axis
    val (leftAxisMin, leftAxisMax) = remember(barValues) {
        calculateAxisRange(barValues, paddingPercentage, forceIncludeZeroLeft)
    }

    val (rightAxisMin, rightAxisMax) = remember(lineValues) {
        calculateAxisRange(lineValues, paddingPercentage, forceIncludeZeroRight)
    }
    AndroidView(
        factory = {
            CombinedChart(context).apply {
                description.isEnabled = false
                setTouchEnabled(false)
                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    granularity = 1f
                    valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(v: Float) = labels[v.toInt()]
                    }
                }
                // Left axis for bar (meals)
                axisLeft.apply {
                    isEnabled = true
                    axisMinimum = leftAxisMin
                    axisMaximum = leftAxisMax
                    textColor = "#FF5722".toColorInt()
                    granularity = 1f
                    spaceTop = 5f
                    setSpaceBottom(5f)
                }
                // Right axis with calculated range
                axisRight.apply {
                    isEnabled = true
                    axisMinimum = rightAxisMin
                    axisMaximum = rightAxisMax
                    textColor = "#009688".toColorInt()
                    granularity = 1f
                    spaceTop = 5f
                    spaceBottom = 5f
                }
                legend.isEnabled = true
            }
        },
        update = { chart ->
            // Bar entries and dataset
            val barEntries = barValues.mapIndexed { i, v -> BarEntry(i.toFloat(), v) }
            val barSet = BarDataSet(barEntries, "Meals").apply {
                color = "#FF5722".toColorInt()
                axisDependency = YAxis.AxisDependency.LEFT
            }
            // Line entries and dataset
            val lineEntries = lineValues.mapIndexed { i, v -> Entry(i.toFloat(), v) }
            val lineSet = LineDataSet(lineEntries, "Volume (ml)").apply {
                color = "#009688".toColorInt()
                lineWidth = 2f
                setDrawCircles(true)
                circleRadius = 4f
                axisDependency = YAxis.AxisDependency.RIGHT
            }
            // Combine data
            chart.data = CombinedData().apply {
                setData(BarData(barSet).apply { barWidth = 0.4f })
                setData(LineData(lineSet))
            }
            chart.axisLeft.apply {
                axisMinimum = leftAxisMin
                axisMaximum = leftAxisMax
            }
            chart.axisRight.apply {
                axisMinimum = rightAxisMin
                axisMaximum = rightAxisMax
            }
            chart.invalidate()
        },
        modifier = Modifier.fillMaxSize()
    )
}

fun calculateAxisRange(
    values: List<Float>,
    paddingPercentage: Float = 0.1f,
    forceIncludeZero: Boolean = false
): Pair<Float, Float> {
    if (values.isEmpty()) return 0f to 1f

    var minVal = values.minOrNull() ?: 0f
    var maxVal = values.maxOrNull() ?: 1f

    // Smart zero inclusion logic
    if (forceIncludeZero || shouldIncludeZero(values)) {
        minVal = minOf(minVal, 0f)
        maxVal = maxOf(maxVal, 0f)
    }

    // Handle identical values
    if (minVal == maxVal) {
        val padding = if (minVal == 0f) 1f else abs(minVal) * 0.1f
        return (minVal - padding) to (maxVal + padding)
    }

    // Add padding for visual breathing room
    val dataRange = maxVal - minVal
    val padding = dataRange * paddingPercentage

    return (minVal - padding) to (maxVal + padding)
}

private fun shouldIncludeZero(values: List<Float>): Boolean {
    val min = values.minOrNull() ?: 0f
    val max = values.maxOrNull() ?: 0f

    // Include zero if data spans across it
    if (min < 0 && max > 0) return true

    // Include zero if minimum is close to zero (within 20% of range)
    if (min >= 0 && min <= (max - min) * 0.2f) return true

    return false
}

