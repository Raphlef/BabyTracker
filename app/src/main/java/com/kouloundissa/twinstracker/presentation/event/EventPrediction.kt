package com.kouloundissa.twinstracker.presentation.event

import com.kouloundissa.twinstracker.data.Event
import java.util.Date

/**
 * EventPrediction - Calculates presets based on growth speed and trend analysis
 * Instead of averaging, this calculates the rate of change (speed) over time
 * to predict future values more accurately
 */
class EventPrediction {

    /**
     * Represents a single data point with timestamp and value
     */
    data class TimestampedValue(
        val timestamp: Date,
        val value: Double
    )

    /**
     * Analysis result containing growth metrics
     */
    data class GrowthAnalysis(
        val currentValue: Double,
        val averageGrowthSpeed: Double, // ml/hour or similar unit
        val trendDirection: TrendDirection,
        val volatility: Double, // Standard deviation of speeds
        val predictedValue: Double // Predicted value for next event
    )

    enum class TrendDirection {
        INCREASING,  // Positive growth
        STABLE,      // Near zero growth
        DECREASING   // Negative growth (or needs attention)
    }

    companion object {
        private const val HOURS_BETWEEN_EVENTS = 4.0 // Expected hours between events
        private const val MIN_DATA_POINTS = 2 // Minimum points needed for speed calculation

        /**
         * Calculate growth speed and trend from timestamped values
         * @param values Sorted list of (timestamp, value) pairs (most recent first)
         * @return GrowthAnalysis with metrics and prediction
         */
        fun analyzeGrowth(values: List<TimestampedValue>): GrowthAnalysis {
            if (values.isEmpty()) {
                return GrowthAnalysis(
                    currentValue = 0.0,
                    averageGrowthSpeed = 0.0,
                    trendDirection = TrendDirection.STABLE,
                    volatility = 0.0,
                    predictedValue = 0.0
                )
            }

            val currentValue = values.first().value

            if (values.size < MIN_DATA_POINTS) {
                return GrowthAnalysis(
                    currentValue = currentValue,
                    averageGrowthSpeed = 0.0,
                    trendDirection = TrendDirection.STABLE,
                    volatility = 0.0,
                    predictedValue = currentValue
                )
            }

            // Calculate growth speeds between consecutive events
            val growthSpeeds = calculateGrowthSpeeds(values)

            if (growthSpeeds.isEmpty()) {
                return GrowthAnalysis(
                    currentValue = currentValue,
                    averageGrowthSpeed = 0.0,
                    trendDirection = TrendDirection.STABLE,
                    volatility = 0.0,
                    predictedValue = currentValue
                )
            }

            val averageSpeed = growthSpeeds.average()
            val volatility = calculateVolatility(growthSpeeds, averageSpeed)
            val trendDirection = determineTrend(averageSpeed)
            val predictedValue = predictNextValue(currentValue, averageSpeed)

            return GrowthAnalysis(
                currentValue = currentValue,
                averageGrowthSpeed = averageSpeed,
                trendDirection = trendDirection,
                volatility = volatility,
                predictedValue = predictedValue
            )
        }

        /**
         * Calculate speed of growth between consecutive events
         * Speed = (current_value - previous_value) / time_difference_in_hours
         */
        private fun calculateGrowthSpeeds(values: List<TimestampedValue>): List<Double> {
            val speeds = mutableListOf<Double>()

            for (i in 0 until values.size - 1) {
                val current = values[i]
                val previous = values[i + 1]

                val timeDiffMs = current.timestamp.time - previous.timestamp.time
                val timeDiffHours = timeDiffMs / (1000.0 * 60 * 60)

                // Only calculate if time difference is meaningful (> 10 minutes)
                if (timeDiffHours > 0.167) {
                    val valueDiff = current.value - previous.value
                    val speed = valueDiff / timeDiffHours
                    speeds.add(speed)
                }
            }

            return speeds
        }

        /**
         * Calculate volatility (standard deviation) of growth speeds
         */
        private fun calculateVolatility(speeds: List<Double>, mean: Double): Double {
            if (speeds.size < 2) return 0.0

            val variance = speeds.map { (it - mean) * (it - mean) }.average()
            return kotlin.math.sqrt(variance)
        }

        /**
         * Determine trend direction based on average growth speed
         */
        private fun determineTrend(averageSpeed: Double): TrendDirection {
            return when {
                averageSpeed > 0.5 -> TrendDirection.INCREASING
                averageSpeed < -0.5 -> TrendDirection.DECREASING
                else -> TrendDirection.STABLE
            }
        }

        /**
         * Predict the next value based on current value and growth speed
         * Uses expected time between events to calculate prediction
         */
        private fun predictNextValue(currentValue: Double, averageSpeed: Double): Double {
            return currentValue + (averageSpeed * HOURS_BETWEEN_EVENTS)
        }

        /**
         * Convert analysis to preset recommendations
         */
        fun generatePresets(
            analysis: GrowthAnalysis,
            defaultPresets: List<Int> = listOf(50, 100, 150, 200),
            factors: List<Double> = listOf(0.75, 1.0, 1.25)
        ): List<Int> {
            // Use predicted value as base instead of simple average
            val baseValue = when {
                analysis.predictedValue > 0 -> analysis.predictedValue
                analysis.currentValue > 0 -> analysis.currentValue
                else -> return defaultPresets
            }

            val niceBase = roundToNiceNumber(baseValue.toInt())

            // Generate presets with slight adjustment based on trend
            val trendAdjustment = when (analysis.trendDirection) {
                TrendDirection.INCREASING -> 1.05  // Add 5% if increasing
                TrendDirection.DECREASING -> 0.95  // Reduce 5% if decreasing
                TrendDirection.STABLE -> 1.0       // Keep as is
            }

            return factors.map { factor ->
                val adjusted = (niceBase * factor * trendAdjustment).toInt()
                roundToNiceNumber(adjusted)
            }.filter { it > 0 }
        }

        /**
         * Round to nearest 5ml for nice preset values
         */
        private fun roundToNiceNumber(value: Int): Int {
            return (value / 5.0f).toInt() * 5
        }
    }
}

/**
 * Extension function to convert events to TimestampedValues
 * Usage: feedingEvents.toTimestampedValues { it.amountMl ?: 0.0 }
 */
fun <T> List<T>.toTimestampedValues(
    timestampSelector: (T) -> Date,
    valueSelector: (T) -> Double
): List<EventPrediction.TimestampedValue> {
    return this.map { item ->
        EventPrediction.TimestampedValue(
            timestamp = timestampSelector(item),
            value = valueSelector(item)
        )
    }
}

/**
 * Extension function to calculate presets with growth prediction
 * Usage: feedingEvents.calculatePresetsWithPrediction()
 */
fun <T : Event> List<T>.calculatePresetsWithPrediction(
    valueSelector: (T) -> Double? = { 0.0 },
    defaultPresets: List<Int> = listOf(50, 100, 150, 200)
): List<Int> {
    val timestampedValues = this
        .filter { valueSelector(it) != null && valueSelector(it)!! > 0 }
        .sortedByDescending { it.timestamp }
        .toTimestampedValues(
            timestampSelector = { it.timestamp },
            valueSelector = { valueSelector(it) ?: 0.0 }
        )

    val analysis = EventPrediction.analyzeGrowth(timestampedValues)
    return EventPrediction.generatePresets(analysis, defaultPresets)
}

/**
 * Simplified helper for quick growth analysis
 * Usage: val analysis = feedingEvents.analyzeGrowthTrend()
 */
fun <T : Event> List<T>.analyzeGrowthTrend(
    valueSelector: (T) -> Double? = { 0.0 }
): EventPrediction.GrowthAnalysis {
    val timestampedValues = this
        .filter { valueSelector(it) != null && valueSelector(it)!! > 0 }
        .sortedByDescending { it.timestamp }
        .toTimestampedValues(
            timestampSelector = { it.timestamp },
            valueSelector = { valueSelector(it) ?: 0.0 }
        )

    return EventPrediction.analyzeGrowth(timestampedValues)
}