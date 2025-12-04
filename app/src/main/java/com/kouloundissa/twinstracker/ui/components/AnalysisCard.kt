package com.kouloundissa.twinstracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
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
import com.kouloundissa.twinstracker.ui.theme.BackgroundColor
import com.kouloundissa.twinstracker.ui.theme.DarkBlue
import kotlin.math.abs
import android.graphics.Color as AndroidColor

@Composable
fun AnalysisCard(
    title: String,
    content: @Composable BoxScope.() -> Unit
) {
    val backgroundColor = BackgroundColor
    val contentColor= DarkBlue
    val cornerShape = MaterialTheme.shapes.large
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .padding(vertical = 8.dp)
            .clip(cornerShape),
        tonalElevation = 4.dp,
        color = Color.Transparent
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(backgroundColor.copy(alpha = 1f)    )
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(12.dp),
                color = contentColor
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
    labels: List<String>,
    values: List<Float>,
    forceIncludeZero: Boolean = false,
    paddingPercentage: Float = 0.1f
) {
    val context = LocalContext.current
    val (axisMin, axisMax) = remember(values) {
        calculateAxisRange(values, paddingPercentage, forceIncludeZero)
    }

    AndroidView(
        factory = {
            BarChart(context).apply {
                description.isEnabled = false
                setPinchZoom(false)
                axisRight.isEnabled = false
                setTouchEnabled(true)
                isDragEnabled = true
                setScaleEnabled(true)
                setPinchZoom(true)
            }
        },
        update = { chart ->
            // 1. Build entries and data set
            val entries = values.mapIndexed { i, v -> BarEntry(i.toFloat(), v) }
            val barSet = BarDataSet(entries, "").apply {
                color = "#FFB300".toColorInt()
                valueTextColor = AndroidColor.BLACK
                valueTextSize = 10f
                setDrawValues(values.size <= 10)
            }
            chart.data = BarData(barSet).apply { barWidth = 0.6f }

            // 2. Update axes
            chart.axisLeft.apply {
                axisMinimum = axisMin
                axisMaximum = axisMax
                spaceTop = 5f
                spaceBottom = 5f
            }
            chart.xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                // Show only every nth label to avoid clutter
                val maxLabels = 6
                val step = (labels.size / maxLabels).coerceAtLeast(1)
                granularity = step.toFloat()
                labelCount = (labels.size + step - 1) / step
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(v: Float): String {
                        val idx = v.toInt().coerceIn(0, labels.lastIndex)
                        return if (idx % step == 0) labels[idx] else ""
                    }
                }
            }

            // 3. Refresh chart
            chart.invalidate()
        },
        modifier = Modifier.fillMaxSize()
    )
}


@Composable
fun LineChartView(
    labels: List<String>,
    values: List<Float>,
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
                axisRight.isEnabled = false
                setTouchEnabled(true)
                isDragEnabled = true
                setScaleEnabled(true)
                setPinchZoom(true)
            }
        },
        update = { chart ->
            // 1. Build entries
            val entries = values.mapIndexed { i, v -> Entry(i.toFloat(), v) }
            val dataSet = LineDataSet(entries, "").apply {
                color = "#2196F3".toColorInt()
                valueTextColor = AndroidColor.BLACK
                lineWidth = 2f
                circleRadius = 1f
                setDrawCircles(true)
                setDrawValues(values.size <= 10)
            }
            chart.data = LineData(dataSet)

            // 2. Update axes
            chart.axisLeft.apply {
                axisMinimum = axisMin
                axisMaximum = axisMax
                spaceTop = 5f
                spaceBottom = 5f
            }
            chart.xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                // Show only every nth label to avoid clutter
                val maxLabels = 6
                val step = (labels.size / maxLabels).coerceAtLeast(1)
                granularity = step.toFloat()
                labelCount = (labels.size + step - 1) / step
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(v: Float): String {
                        val idx = v.toInt().coerceIn(0, labels.lastIndex)
                        return if (idx % step == 0) labels[idx] else ""
                    }
                }
            }

            // 3. Refresh
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
    val maxPoints = remember(series) {
        series.maxOfOrNull { it.second.size } ?: 0
    }
    val isHighDensity = maxPoints > 15
    val isMediumDensity = maxPoints in 6..15
    val (axisMin, axisMax) = remember(series) {
        val allValues = series.flatMap { it.second }
        calculateAxisRange(allValues, paddingPercentage, forceIncludeZero)
    }

    AndroidView(
        factory = {
            LineChart(context).apply {
                description.isEnabled = false
                axisRight.isEnabled = false
                legend.isEnabled = true
                setTouchEnabled(true)
                isDragEnabled = true
                setScaleEnabled(true)
                setPinchZoom(true)
            }
        },
        update = { chart ->
            val showValues = maxPoints <= 10

            // Build data sets, including NaN entries
            val sets = series.mapIndexed { idx, (label, vals) ->
                val entries = vals.mapIndexedNotNull { i, v ->
                    if (v.isNaN()) null else Entry(i.toFloat(), v)
                }
                LineDataSet(entries, label).apply {
                    color = listOf(
                        "#E91E63".toColorInt(),
                        "#4CAF50".toColorInt(),
                        "#2196F3".toColorInt(),
                        "#FFC107".toColorInt()
                    )[idx % 4]
                    lineWidth = if (isHighDensity) 1f else 2f
                    setDrawCircles(isHighDensity.not())
                    if (!isHighDensity) {
                        circleRadius = if (isMediumDensity) 2f else 3f
                    }
                    // Skip NaN values automatically, leave gaps
                    setDrawValues(showValues)
                    mode = LineDataSet.Mode.LINEAR
                }
            }

            // Apply data
            chart.data = LineData(sets)

            // Update left axis
            chart.axisLeft.apply {
                axisMinimum = axisMin
                axisMaximum = axisMax
                setSpaceTop(5f)
                setSpaceBottom(5f)
            }

            // X-axis - adaptive label count
            chart.xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                labelRotationAngle = when {
                    isHighDensity -> 45f
                    isMediumDensity -> 30f
                    else -> 0f
                }
                val maxLabels = when {
                    isHighDensity -> 4
                    isMediumDensity -> 6
                    else -> labels.size
                }
                val step = (labels.size / maxLabels).coerceAtLeast(1)
                granularity = step.toFloat()
                labelCount = (labels.size + step - 1) / step
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(v: Float): String {
                        val idx = v.toInt().coerceIn(0, labels.lastIndex)
                        return if (idx % step == 0) labels[idx] else ""
                    }
                }
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
    barLabel: String = "Bar Data",
    lineLabel: String = "Line Data",
    forceIncludeZeroLeft: Boolean = false,
    forceIncludeZeroRight: Boolean = false,
    paddingPercentage: Float = 0.1f
) {
    val context = LocalContext.current

    val dataPointCount = labels.size
    val isHighDensity = dataPointCount > 15
    val isMediumDensity = dataPointCount in 6..15

    // Calculate separate ranges for each axis
    val (leftAxisMin, leftAxisMax) = remember(barValues) {
        calculateAxisRange(barValues, paddingPercentage, forceIncludeZeroLeft)
    }

    val (rightAxisMin, rightAxisMax) = remember(lineValues) {
        calculateAxisRange(lineValues, paddingPercentage, forceIncludeZeroRight)
    }

    // Check if scales are significantly different
    val leftRange = leftAxisMax - leftAxisMin
    val rightRange = rightAxisMax - rightAxisMin
    val scaleRatio = if (leftRange > 0) rightRange / leftRange else 1f
    val needsDualAxes = scaleRatio > 5f || scaleRatio < 0.2f

    AndroidView(
        factory = {
            CombinedChart(context).apply {
                description.isEnabled = false
                setTouchEnabled(true)
                isDragEnabled = true
                setScaleEnabled(true)
                setPinchZoom(true)
                legend.isEnabled = true
                setDrawGridBackground(false)
                setDrawBorders(false)
            }
        },
        update = { chart ->
            // 1. Build datasets
            val barEntries = barValues.mapIndexed { i, v -> BarEntry(i.toFloat(), v) }
            val barSet = BarDataSet(barEntries, barLabel).apply {
                color = "#FF5722".toColorInt()
                axisDependency = YAxis.AxisDependency.LEFT
                setDrawValues(false)
                valueTextSize = 10f
            }

            val lineEntries = lineValues.mapIndexed { i, v -> Entry(i.toFloat(), v) }
            val lineSet = LineDataSet(lineEntries, lineLabel).apply {
                color = "#009688".toColorInt()
                lineWidth = when {
                    isHighDensity -> 1.5f
                    isMediumDensity -> 1.8f
                    else -> 2f
                }
                setDrawCircles(isHighDensity.not())
                if (!isHighDensity) {
                    circleRadius = if (isMediumDensity) 2f else 3f
                }
                circleRadius = 1f
                axisDependency = if (needsDualAxes) {
                    YAxis.AxisDependency.RIGHT
                } else {
                    YAxis.AxisDependency.LEFT
                }
                setDrawValues(dataPointCount <= 10)
                valueTextSize = 10f
                mode = LineDataSet.Mode.LINEAR
            }

            // 2. Set combined data
            chart.data = CombinedData().apply {
                setData(BarData(barSet).apply {
                    barWidth = when {
                        isHighDensity -> 0.3f
                        isMediumDensity -> 0.35f
                        else -> 0.4f
                    }
                })
                setData(LineData(lineSet))
            }

            // 3. Update axes
            chart.axisLeft.apply {
                isEnabled = true
                axisMinimum = leftAxisMin
                axisMaximum = leftAxisMax
                setSpaceTop(5f)
                setSpaceBottom(5f)
                // Fewer grid lines for cleaner look
                setDrawGridLines(true)
                gridLineWidth = 0.5f
                gridColor = androidx.compose.ui.graphics.Color.Gray.copy(alpha = 0.2f).toArgb()
                textSize = when {
                    isHighDensity -> 9f
                    isMediumDensity -> 10f
                    else -> 11f
                }
                textColor = androidx.compose.ui.graphics.Color(0xFFFF5722).toArgb()
            }

            chart.axisRight.apply {
                isEnabled = needsDualAxes  // Only enable if scales differ significantly

                if (needsDualAxes) {
                    axisMinimum = rightAxisMin
                    axisMaximum = rightAxisMax
                    setSpaceTop(5f)
                    setSpaceBottom(5f)
                    // Minimal grid
                    setDrawGridLines(false)  // No grid on right to reduce clutter
                    textSize = when {
                        isHighDensity -> 9f
                        isMediumDensity -> 10f
                        else -> 11f
                    }
                    // Color code to match line dataset
                    textColor = androidx.compose.ui.graphics.Color(0xFF009688).toArgb()
                }
            }

            // 4. Update X-axis with safe formatter
            chart.xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)

                val maxLabels = when {
                    isHighDensity -> 4
                    isMediumDensity -> 6
                    else -> labels.size
                }
                val step = (labels.size / maxLabels).coerceAtLeast(1)
                granularity = step.toFloat()
                labelCount = (labels.size + step - 1) / step

                labelRotationAngle = when {
                    isHighDensity -> 45f
                    isMediumDensity -> 30f
                    else -> 0f
                }

                textSize = when {
                    isHighDensity -> 8f
                    isMediumDensity -> 9f
                    else -> 10f
                }

                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(v: Float): String {
                        val idx = v.toInt().coerceIn(0, labels.lastIndex)
                        return if (idx % step == 0) labels[idx] else ""
                    }
                }
            }

            // 5. Refresh
            chart.invalidate()
        },
        modifier = Modifier.fillMaxSize()
    )
}


/**
 * Calculate optimal axis range ensuring no negative values for positive data
 */
fun calculateAxisRange(
    values: List<Float>,
    paddingPercentage: Float = 0.1f,
    forceIncludeZero: Boolean = false
): Pair<Float, Float> {
    val data = values.filterNot { it.isNaN() }
    if (data.isEmpty()) return 0f to 1f

    val minVal = data.minOrNull() ?: 0f
    val maxVal = data.maxOrNull() ?: 1f

    // Handle case where all values are the same
    if (minVal == maxVal) {
        if (minVal >= 0f) {
            // For non-negative single values, create small range around the value
            val padding = if (minVal == 0f) 1f else minVal * 0.1f
            return minVal to (minVal + padding)
        } else {
            // For negative single values, add padding normally
            val padding = abs(minVal) * 0.1f
            return (minVal - padding) to (maxVal + padding)
        }
    }

    // If all values are non-negative, ensure axis minimum is never negative
    if (minVal >= 0f) {
        val dataRange = maxVal - minVal
        val padding = dataRange * paddingPercentage

        val calculatedMin = if (forceIncludeZero || shouldIncludeZero(data)) {
            0f  // Include zero and don't go below it
        } else {
            minVal - padding
        }

        val calculatedMax = maxVal + padding
        return calculatedMin to calculatedMax
    }

    // If data contains actual negative values, use normal calculation
    val dataRange = maxVal - minVal
    val padding = dataRange * paddingPercentage
    return (minVal - padding) to (maxVal + padding)
}

private fun shouldIncludeZero(values: List<Float>): Boolean {
    val min = values.minOrNull() ?: 0f
    val max = values.maxOrNull() ?: 0f

    // If data spans across zero, include it
    if (min < 0 && max > 0) return true

    // If all positive and min is less than 20% of range, include zero
    if (min >= 0 && min <= (max - min) * 0.2f) return true

    return false
}
