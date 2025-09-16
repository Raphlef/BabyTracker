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
                    axisMinimum = 0f
                    textColor = AndroidColor.parseColor("#FF5722")
                    granularity = 1f
                }
                // Right axis for line (ml)
                axisRight.apply {
                    isEnabled = true
                    axisMinimum = 0f
                    textColor = AndroidColor.parseColor("#009688")
                    granularity = 1f
                }
                legend.isEnabled = true
            }
        },
        update = { chart ->
            // Bar entries and dataset
            val barEntries = barValues.mapIndexed { i, v -> BarEntry(i.toFloat(), v) }
            val barSet = BarDataSet(barEntries, "Meals").apply {
                color = AndroidColor.parseColor("#FF5722")
                axisDependency = YAxis.AxisDependency.LEFT
            }
            // Line entries and dataset
            val lineEntries = lineValues.mapIndexed { i, v -> Entry(i.toFloat(), v) }
            val lineSet = LineDataSet(lineEntries, "Volume (ml)").apply {
                color = AndroidColor.parseColor("#009688")
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
            chart.invalidate()
        },
        modifier = Modifier.fillMaxSize()
    )
}
