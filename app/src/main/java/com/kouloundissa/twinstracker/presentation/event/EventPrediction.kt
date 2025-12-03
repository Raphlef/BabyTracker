package com.kouloundissa.twinstracker.presentation.event

import java.util.Date
import kotlin.math.roundToInt

object EventPrediction {
    enum class PredictionMethod {
        AVERAGE,        // Original: simple averaging
        GROWTH_SPEED    // New: growth rate-based prediction
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
    return when (method) {
        EventPrediction.PredictionMethod.GROWTH_SPEED -> {
            EventPrediction.calculatePresetsFromGrowthSpeed(this, now, defaultPresets, factors)
        }

        EventPrediction.PredictionMethod.AVERAGE -> {
            EventPrediction.calculatePresetsFromAverage(
                this.mapNotNull { it.getAmountValue() },
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
