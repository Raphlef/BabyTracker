package com.kouloundissa.twinstracker.presentation.event

import com.kouloundissa.twinstracker.data.Event
import java.time.Duration
import java.time.ZoneId
import java.util.Date
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object EventPrediction {
    enum class PredictionMethod {
        AVERAGE,        // simple averaging
        GROWTH_SPEED,    // growth rate-based prediction
        INTERVAL_BASED  // interval-based feeding time prediction
    }
    enum class FeedingPattern {
        INTERVAL_BASED,  // Jeune bébé : intervalles réguliers
        TIME_BASED,      // Bébé plus grand : heures fixes
        MIXED             // Transition entre les deux
    }

    data class FeedingPrediction(
        val nextFeedingTimeMs: Long,
        val pattern: FeedingPattern,
        val confidence: Double,  // 0.0 to 1.0
        val estimatedIntervalMs: Long? = null,
        val preferredHourOfDay: Int? = null  // 0-23
    )
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
    ): FeedingPrediction? {
        // Need at least 2 events to calculate intervals
        if (events.size < 2) return null

        val lastFeeding = events.maxBy { it.timestamp.time }
        val sortedEvents = events.sortedByDescending { it.timestamp.time }

        // Calculate intervals between consecutive feedings
        val intervals = calculateIntervals(sortedEvents)
        if (intervals.size < 2) return null

        // Analyser la variance des heures
        val hourDistribution = extractHourOfDay(sortedEvents)

        // Détecter le pattern
        val pattern = detectFeedingPattern(intervals, hourDistribution)

        // Prédire selon le pattern
        val prediction = when (pattern) {
            FeedingPattern.INTERVAL_BASED -> predictByInterval(lastFeeding, intervals, now)
            FeedingPattern.TIME_BASED -> predictByHourOfDay(lastFeeding, hourDistribution, now)
            FeedingPattern.MIXED -> predictHybrid(lastFeeding, intervals, hourDistribution, now)
        }

        return prediction
    }

    // ==================== PATTERN DETECTION ====================

    /**
     * Détecter si le pattern est intervalle-basé ou heure-basé
     *
     * Métrique : coefficient de variation des heures vs intervalles
     */
    private fun detectFeedingPattern(
        intervals: List<Long>,
        hourDistribution: Map<Int, Int>
    ): FeedingPattern {
        if (intervals.size < 3) return FeedingPattern.INTERVAL_BASED

        // Variance des intervalles (régularité temporelle)
        val intervalMean = intervals.average()
        val intervalVariance = intervals.map { (it - intervalMean) * (it - intervalMean) }.average()
        val intervalCV = sqrt(intervalVariance) / intervalMean  // Coefficient de variation

        // Concentration des heures (régularité horaire)
        val hoursWithFeedings = hourDistribution.size
        val maxFeedingsInHour = hourDistribution.values.maxOrNull() ?: 1
        val hourConcentration = maxFeedingsInHour.toDouble() / intervals.size

        // Décision
        return when {
            // Intervalles très réguliers (CV < 0.3) = jeune bébé
            intervalCV < 0.3 -> FeedingPattern.INTERVAL_BASED

            // Heures très concentrées = bébé plus grand
            hourConcentration > 0.5 && hoursWithFeedings < 6 -> FeedingPattern.TIME_BASED

            // Les deux patterns présents = transition
            else -> FeedingPattern.MIXED
        }
    }

    // ==================== INTERVAL-BASED PREDICTION ====================

    /**
     * Prédire par intervalles (jeune bébé)
     * Utilise la médiane pondérée pour robustesse
     */
    private fun <T : Event> predictByInterval(
        lastFeeding: T,
        intervals: List<Long>,
        now: Date
    ): FeedingPrediction {
        val predictedIntervalMs = calculatePredictedInterval(intervals)
        val minIntervalMs = 10 * 60 * 1000L  // 10 minutes minimum
        val finalIntervalMs = maxOf(predictedIntervalMs, minIntervalMs)

        // Confiance basée sur la régularité
        val variance = intervals.map { it - intervals.average() }
            .map { it * it }.average()
        val intervalCV = sqrt(variance) / intervals.average()
        val confidence = (1.0 - minOf(intervalCV, 0.5)) / 0.5  // 0-1

        val nextTime = lastFeeding.timestamp.time + finalIntervalMs

        return FeedingPrediction(
            nextFeedingTimeMs = nextTime,
            pattern = FeedingPattern.INTERVAL_BASED,
            confidence = confidence,
            estimatedIntervalMs = finalIntervalMs
        )
    }

    // ==================== TIME-BASED PREDICTION ====================

    /**
     * Prédire par heures fixes (bébé plus grand)
     * Utilise la moyenne circulaire pour les heures
     */
    private fun <T : Event> predictByHourOfDay(
        lastFeeding: T,
        hourDistribution: Map<Int, Int>,
        now: Date
    ): FeedingPrediction? {
        if (hourDistribution.isEmpty()) return null

        // Trouver l'heure "moyenne" des repas
        val preferredHour = getCircularMeanHour(hourDistribution)

        // Calculer la concentration (confiance)
        val totalFeedings = hourDistribution.values.sum()
        val maxFeedingsInHour = hourDistribution.values.maxOrNull() ?: 1
        val confidence = maxFeedingsInHour.toDouble() / totalFeedings

        // Générer prochaine heure de repas
        val nextTime = calculateNextTimeAtHour(lastFeeding.timestamp, preferredHour)

        return FeedingPrediction(
            nextFeedingTimeMs = nextTime,
            pattern = FeedingPattern.TIME_BASED,
            confidence = confidence,
            preferredHourOfDay = preferredHour
        )
    }

    /**
     * Calculer l'heure "moyenne" using circular mean
     * Gère le wraparound (23h et 1h = minuit)
     */
    private fun getCircularMeanHour(hourDistribution: Map<Int, Int>): Int {
        if (hourDistribution.isEmpty()) return 12

        var sinSum = 0.0
        var cosSum = 0.0

        for ((hour, count) in hourDistribution) {
            val angle = (hour / 24.0) * 2.0 * Math.PI
            sinSum += count * sin(angle)
            cosSum += count * cos(angle)
        }

        val meanAngle = atan2(sinSum, cosSum)
        val meanHour = ((meanAngle / (2.0 * Math.PI)) * 24.0 + 24.0) % 24.0

        return meanHour.toInt()
    }

    /**
     * Calculer le prochain créneau à une heure donnée
     * Gère le passage du jour
     */
    private fun calculateNextTimeAtHour(lastTime: Date, preferredHour: Int): Long {
        val instant = lastTime.toInstant()
        val zoneId = ZoneId.systemDefault()
        val lastLocalTime = instant.atZone(zoneId)

        // Prochaine occurrence de l'heure préférée
        var nextLocal = lastLocalTime
            .withHour(preferredHour)
            .withMinute(0)
            .withSecond(0)

        // Si l'heure est passée aujourd'hui, demain
        if (nextLocal.isBefore(lastLocalTime)) {
            nextLocal = nextLocal.plusDays(1)
        }

        return nextLocal.toInstant().toEpochMilli()
    }

    // ==================== HYBRID PREDICTION ====================

    /**
     * Prédire en utilisant les deux méthodes (transition)
     * Pondération : 60% intervalles (stabilité) + 40% heures (pattern)
     */
    private fun <T : Event> predictHybrid(
        lastFeeding: T,
        intervals: List<Long>,
        hourDistribution: Map<Int, Int>,
        now: Date
    ): FeedingPrediction? {
        val byInterval = predictByInterval(lastFeeding, intervals, now) ?: return null
        val byHour = predictByHourOfDay(lastFeeding, hourDistribution, now) ?: return null

        // Pondération intelligente
        val weight = 0.6  // 60% intervalle, 40% heure
        val nextTime = (byInterval.nextFeedingTimeMs * weight +
                byHour.nextFeedingTimeMs * (1 - weight)).toLong()

        return FeedingPrediction(
            nextFeedingTimeMs = nextTime,
            pattern = FeedingPattern.MIXED,
            confidence = (byInterval.confidence + byHour.confidence) / 2.0,
            estimatedIntervalMs = byInterval.estimatedIntervalMs,
            preferredHourOfDay = byHour.preferredHourOfDay
        )
    }

    // ==================== UTILITIES ====================

    private fun <T : Event> calculateIntervals(sortedEvents: List<T>): List<Long> {
        return sortedEvents
            .zipWithNext { a, b ->
                Duration.between(b.timestamp.toInstant(), a.timestamp.toInstant())
                    .toMillis()
            }
            .filter { it > 0 }
            .sorted()
    }

    private fun <T : Event> extractHourOfDay(events: List<T>): Map<Int, Int> {
        return events
            .map { event ->
                event.timestamp
                    .toInstant()
                    .atZone(ZoneId.systemDefault())
                    .hour
            }
            .groupingBy { it }
            .eachCount()
    }

    private fun calculatePredictedInterval(sortedIntervals: List<Long>): Long {
        if (sortedIntervals.isEmpty()) return 0L

        val outlierThreshold = maxOf(1, sortedIntervals.size / 5)
        val filteredIntervals = sortedIntervals
            .drop(outlierThreshold)
            .dropLast(outlierThreshold)
            .ifEmpty { sortedIntervals }

        val medianInterval = getMedian(filteredIntervals)
        val averageInterval = filteredIntervals.average().toLong()

        return (medianInterval * 0.4 + averageInterval * 0.6).toLong()
    }

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

fun <T : Event> List<T>.predictNextFeedingTime(
    now: Date = Date()
): EventPrediction.FeedingPrediction? =
    EventPrediction.predictNextFeedingTimeMs(this.sortedByDescending { it.timestamp.time }, now)

fun <T : Event> List<T>.getPatternType(): EventPrediction.FeedingPattern? {
    val prediction = this.predictNextFeedingTime() ?: return null
    return prediction.pattern
}

fun <T : Event> List<T>.getFeedingConfidence(): Double? {
    val prediction = this.predictNextFeedingTime() ?: return null
    return prediction.confidence
}
