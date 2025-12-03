package com.kouloundissa.twinstracker.presentation.event

import com.kouloundissa.twinstracker.data.Event
import java.time.Duration
import java.util.Date
import kotlin.math.roundToInt

object EventPrediction {
    enum class PredictionMethod {
        AVERAGE,        // simple averaging
        GROWTH_SPEED,    // growth rate-based prediction
        INTERVAL_BASED  // interval-based feeding time prediction
    }
    // ==================== FEEDING TIME PREDICTION ====================

    /**
     * Predict next feeding time based on historical feeding intervals
     *
     * Algorithm:
     * 1. Take last 10 events to calculate feeding intervals
     * 2. Remove outliers (drop first/last 20% of sorted intervals)
     * 3. Calculate weighted prediction using median + average of filtered intervals
     * 4. Apply minimum interval threshold (90 minutes)
     * 5. Return last feeding time + predicted interval
     *
     * @param events List of recent feeding events (must be sorted by timestamp descending)
     * @param now Current timestamp (defaults to now)
     * @return Predicted next feeding time in milliseconds, or null if calculation fails
     */
    fun <T : Event> predictNextFeedingTimeMs(
        events: List<T>,
        now: Date = Date()
    ): Long? {
        // Need at least 2 events to calculate intervals
        if (events.size < 2) return null

        val lastFeeding = events.maxBy { it.timestamp.time }
        val sortedEvents = events.sortedByDescending { it.timestamp.time }.take(10)

        // Calculate intervals between consecutive feedings
        val intervals = sortedEvents
            .zipWithNext { a, b ->
                    Duration.between(b.timestamp.toInstant(), a.timestamp.toInstant())
                        .toMillis()
            }
            .sorted()

        if (intervals.size < 2) return null

        // Calculate predicted interval using statistical methods
        val predictedIntervalMs = calculatePredictedInterval(intervals)

        // Apply minimum interval threshold
        val minIntervalMs = 90 * 60 * 1000L // 90 minutes minimum
        val finalIntervalMs = maxOf(predictedIntervalMs, minIntervalMs)

        // Return predicted next feeding time
        return lastFeeding.timestamp.time + finalIntervalMs
    }

    /**
     * Calculate the most reliable predicted interval using multiple strategies
     *
     * Strategy:
     * 1. Calculate median (robust to outliers)
     * 2. Remove outliers from distribution (exclude top/bottom 20%)
     * 3. Calculate average of filtered intervals
     * 4. Weight both results for final prediction
     */
    private fun calculatePredictedInterval(sortedIntervals: List<Long>): Long {
        if (sortedIntervals.isEmpty()) return 0L

        // Remove outliers: drop first and last 20%
        val outlierThreshold = maxOf(1, sortedIntervals.size / 5)

        val filteredIntervals = sortedIntervals
            .drop(outlierThreshold)
            .dropLast(outlierThreshold)
            .ifEmpty { sortedIntervals }

        // Median - robust to outliers
        val medianInterval = getMedian(filteredIntervals)
        // Average of filtered intervals - more stable
        val averageInterval = filteredIntervals.average().toLong()

        // Weight both: 40% median (stable), 60% average (adaptive)
        return (medianInterval * 0.4 + averageInterval * 0.6).toLong()
    }

    /**
     * Calculate median of a sorted list of values
     */
    private fun getMedian(sortedValues: List<Long>): Long {
        return when {
            sortedValues.isEmpty() -> 0L
            sortedValues.size % 2 == 1 -> sortedValues[sortedValues.size / 2]
            else -> {
                val mid = sortedValues.size / 2
                (sortedValues[mid - 1] + sortedValues[mid]) / 2
            }
        }
    }

    /**
     * Calculate presets using growth speed prediction
     * @param events List of events with amounts and timestamps (must be sorted by timestamp ascending)
     * @param now Current timestamp (defaults to now)
     * @param defaultPresets Fallback presets if calculation fails
     * @param factors Multipliers applied to predicted amount (0.75 = smaller, 1.0 = normal, 1.25 = larger)
     * @return List of recommended preset amounts
     */
    fun <T : EventWithAmount> calculatePresetsFromGrowthSpeed(
        events: List<T>,
        now: Date = Date(),
        defaultPresets: List<Int> = listOf(50, 100, 150, 200),
        factors: List<Double> = listOf(0.75, 1.0, 1.25)
    ): List<Int> {
        if (events.isEmpty()) {
            return defaultPresets
        }

        // Calculate the predicted amount at current time
        val predictedAmount = predictNextAmount(events, now)

        // If prediction failed or resulted in unrealistic value, fallback to average
        if (predictedAmount <= 0) {
            return calculatePresetsFromAverage(
                events.mapNotNull { it.getAmountValue() },
                defaultPresets,
                factors
            )
        }

        // Generate presets based on predicted amount
        return factors.map { factor ->
            val scaled = (predictedAmount * factor).toInt()
            roundToNiceNumber(scaled)
        }.filter { it > 0 }
    }

    /**
     * Predict the next amount based on growth rate
     *
     * Algorithm:
     * 1. Take last events to calculate growth rate (speed)
     * 2. Calculate time difference and amount difference
     * 3. Determine speed = amount change / time change
     * 4. Predict: speed * time_elapsed
     * 5. Apply sanity checks (min/max boundaries)
     *
     * @return Predicted amount, or -1 if calculation failed
     */
    private fun <T : EventWithAmount> predictNextAmount(
        events: List<T>,
        now: Date
    ): Double {
        if (events.isEmpty()) return -1.0

        val lastEvent = events.first()
        val lastAmount = lastEvent.getAmountValue() ?: return -1.0

        // If only one event, can't calculate speed - return last amount
        if (events.size == 1) {
            return lastAmount
        }

        val recentEvents = events.takeIf { it.size >= 2 } ?: return lastAmount

        // Calculate consumptionRate  using linear regression (more robust)
        val consumptionRate = calculateAverageConsumptionRate(recentEvents)

        if (consumptionRate.isNaN() || consumptionRate.isInfinite() || consumptionRate <= 0) {
            return -1.0
        }

        // Calculate time elapsed since last event
        val timeElapsedMs = now.time - lastEvent.getTimestampValue().time
        val timeElapsedHours = timeElapsedMs / (1000.0 * 60 * 60)

        // Predict amount
        var predictedAmount = (timeElapsedHours * consumptionRate)

        // Apply sanity checks: growth should be reasonable
        predictedAmount = applyGrowthSanityChecks(predictedAmount, lastAmount, timeElapsedHours)

        return predictedAmount
    }

    /**
     * Calculate average consumption rate from multiple events
     *
     * Formula: totalAmountConsumed / totalTimeSpan
     *
     * This gives us ml per hour across all events
     */
    private fun <T : EventWithAmount> calculateAverageConsumptionRate(
        events: List<T>
    ): Double {
        if (events.size < 2) return 0.0

        // Ensure events are sorted in ascending chronological order
        val orderedEvents = events.sortedBy { it.getTimestampValue().time }

        // Sum all amounts consumed
        val totalAmount = orderedEvents.mapNotNull { it.getAmountValue() }.sum()

        // Calculate total time span in hours (from first to last event)
        val firstEventTime = orderedEvents.first().getTimestampValue().time
        val lastEventTime = orderedEvents.last().getTimestampValue().time
        val totalTimeMs = lastEventTime - firstEventTime

        if (totalTimeMs <= 0) return 0.0

        val totalTimeHours = totalTimeMs / (1000.0 * 60 * 60)

        // Average consumption per hour
        return totalAmount / totalTimeHours
    }

    /**
     * Apply sanity checks to predicted growth
     * Prevents unrealistic predictions due to:
     * - Anomalies in the data
     * - Extreme growth rates
     * - Backwards prediction (negative growth beyond threshold)
     */
    private fun applyGrowthSanityChecks(
        predicted: Double,
        lastAmount: Double,
        timeElapsedHours: Double
    ): Double {
        // Clamp to reasonable boundaries
        val minAmount = lastAmount * 0.25  // Don't drop below 25% of last
        val maxAmount = lastAmount * 2.5  // Don't exceed 250% of last

        // Prevent extreme growth in short time
        val maxReasonableAmount = when {
            timeElapsedHours <= 1.0 -> lastAmount * 1.3  // 30% max growth in 1 hour
            timeElapsedHours <= 4.0 -> lastAmount * 1.7  // 70% max growth in 4 hours
            else -> maxAmount                         // 250% for longer periods
        }

        // Prevent negative values
        if (predicted <= 0) return lastAmount

        return predicted.coerceIn(minAmount, maxReasonableAmount)
    }

    /**
     * Calculate presets using traditional average method (legacy)
     * @param amounts List of recent amounts
     * @param defaultPresets Fallback presets if list is empty
     * @param factors Multipliers applied to average (0.75 = smaller, 1.0 = normal, 1.25 = larger)
     * @return List of recommended preset amounts
     */
    fun calculatePresetsFromAverage(
        amounts: List<Double>,
        defaultPresets: List<Int> = listOf(50, 100, 150, 200),
        factors: List<Double> = listOf(0.75, 1.0, 1.25)
    ): List<Int> {
        if (amounts.isEmpty()) {
            return defaultPresets
        }

        val avg = amounts.average()
        val nicAvg = roundToNiceNumber(avg.toInt())

        // Calculate presets
        return factors.map { factor ->
            val scaled = (nicAvg * factor).toInt()
            roundToNiceNumber(scaled)
        }.filter { it > 0 }
    }

    /**
     * Round to nearest 5ml for nice preset values
     */
    private fun roundToNiceNumber(value: Int): Int {
        return (value / 5.0f).roundToInt() * 5
    }
}

/**
 * Interface for events that contain an amount value
 * Implement this in your event classes
 */

interface EventWithAmount {
    fun getAmountValue(): Double?
    fun getTimestampValue(): Date
}

/**
 * Extension function for easy access to FeedingEvent
 */
fun <T : EventWithAmount> List<T>.calculatePresets(
    method: EventPrediction.PredictionMethod = EventPrediction.PredictionMethod.GROWTH_SPEED,
    now: Date = Date(),
    defaultPresets: List<Int> = listOf(50, 100, 150, 200),
    factors: List<Double> = listOf(0.75, 1.0, 1.25)
): List<Int> {
    val sorted = this.sortedByDescending { it.getTimestampValue() }

    return when (method) {
        EventPrediction.PredictionMethod.GROWTH_SPEED -> {
            EventPrediction.calculatePresetsFromGrowthSpeed(sorted, now, defaultPresets, factors)
        }

        EventPrediction.PredictionMethod.AVERAGE -> {
            EventPrediction.calculatePresetsFromAverage(
                sorted.mapNotNull { it.getAmountValue() },
                defaultPresets,
                factors
            )
        }

        EventPrediction.PredictionMethod.INTERVAL_BASED -> {
            // For interval-based, return average presets as fallback
            EventPrediction.calculatePresetsFromAverage(
                sorted.mapNotNull { it.getAmountValue() },
                defaultPresets,
                factors
            )
        }
    }
}

/**
 * Extension function for easy access with average method
 */
fun <T : Number> List<T>.calculatePresetsFromNumbers(
    defaultPresets: List<Int> = listOf(50, 100, 150, 200),
    factors: List<Double> = listOf(0.75, 1.0, 1.25)
): List<Int> {
    return EventPrediction.calculatePresetsFromAverage(
        this.map { it.toDouble() },
        defaultPresets,
        factors
    )
}
