package com.kouloundissa.twinstracker.presentation.event

import com.kouloundissa.twinstracker.data.Event
import java.time.Duration
import java.time.ZoneId
import java.util.Date
import kotlin.math.pow
import kotlin.math.roundToInt
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
    )
    // ==================== FEEDING TIME PREDICTION ====================
    /**
     * Predicts the next feeding time based on historical event data.
     *
     * Analyzes feeding patterns from events within the last 3 days to determine
     * the most likely next feeding time. Uses interval-based, time-based, or hybrid
     * prediction strategies depending on detected feeding patterns.
     *
     * @param events List of feeding events to analyze
     * @return FeedingPrediction with predicted time, confidence, and pattern type,
     *         or null if insufficient data (fewer than 2 events in last 3 days)
     */
    fun <T : Event> predictNextFeedingTimeMs(
        events: List<T>
    ): FeedingPrediction? {

        // Keep only events from last 3 days
        val now = System.currentTimeMillis()
        val threeDaysAgo = now - (3 * 24 * 60 * 60 * 1000)
        val recentEvents = events.filter { it.timestamp.time >= threeDaysAgo }

        // Need at least 2 events to calculate intervals
        if (recentEvents.size < 2) return null

        val lastFeeding = recentEvents.maxBy { it.timestamp.time }
        val sortedEvents = recentEvents.sortedByDescending { it.timestamp.time }

        // Calculate intervals between consecutive feedings
        val intervals = calculateIntervals(sortedEvents)
        if (intervals.size < 2) return null

        val clusters = extractFeedingClusters(sortedEvents, clusterWindowMinutes = 60)

        // Détecter le pattern
        val pattern = detectFeedingPattern(intervals, clusters)

        // Prédire selon le pattern
        val prediction = when (pattern) {
            FeedingPattern.INTERVAL_BASED -> predictByInterval(lastFeeding, intervals)
            FeedingPattern.TIME_BASED -> predictByHourOfDay(lastFeeding, clusters)
            FeedingPattern.MIXED -> predictHybrid(lastFeeding, intervals, clusters)
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
        clusters: List<FeedingCluster>
    ): FeedingPattern {
        if (intervals.size < 3 || clusters.isEmpty()) {
            return FeedingPattern.INTERVAL_BASED
        }

        // METRIC 1: Interval Regularity
        val intervalMean = intervals.average()
        val intervalVariance = intervals.map { (it - intervalMean) * (it - intervalMean) }.average()
        val intervalCV = sqrt(intervalVariance) / intervalMean

        // METRIC 2: Cluster Quality (size + consistency)
        val avgClusterStdDev = clusters.map { it.stdDev }.average()
        val avgClusterSize = clusters.map { it.count }.average()

        val clusterQuality = when {
            clusters.size in 3..5 && avgClusterStdDev < 20 && avgClusterSize >= 2.5 -> 1.0
            clusters.size >= 3 && avgClusterStdDev < 30 && avgClusterSize >= 2.0 -> 0.7
            clusters.size >= 2 && avgClusterStdDev < 40 -> 0.4
            else -> 0.2
        }

        // METRIC 3: Cluster Stability (% of stable clusters)
        val stableClustersCount = clusters.count { it.stdDev < 30 }
        val clusterStability = stableClustersCount.toDouble() / clusters.size

        // ==================== DECISION LOGIC ====================

        return when {
            // Très régulier → INTERVAL_BASED
            intervalCV < 0.30 -> FeedingPattern.INTERVAL_BASED

            // Pattern TIME_BASED fort: 3-5 clusters, haute stabilité
            clusters.size in 3..5 &&
                    clusterQuality >= 0.7 &&
                    clusterStability >= 0.75 -> FeedingPattern.TIME_BASED

            // Pattern TIME_BASED modéré: bonne stabilité globale
            clusters.size >= 3 &&
                    clusterQuality >= 0.7 &&
                    clusterStability >= 0.65 -> FeedingPattern.TIME_BASED

            // Pattern MIXED: début de structure
            intervalCV < 0.55 &&
                    clusterQuality >= 0.4 &&
                    clusterStability >= 0.5 -> FeedingPattern.MIXED

            // Pattern MIXED: quelques clusters stables
            clusterStability >= 0.65 &&
                    clusters.size >= 2 -> FeedingPattern.MIXED

            // Default
            else -> FeedingPattern.INTERVAL_BASED
        }
    }

    // ==================== INTERVAL-BASED PREDICTION ====================

    /**
     * Prédire par intervalles (jeune bébé)
     * Utilise la médiane pondérée pour robustesse
     */
    private fun <T : Event> predictByInterval(
        lastFeeding: T,
        intervals: List<Long>
    ): FeedingPrediction {
        val predictedIntervalMs = calculateSafeMedianAverage(intervals)
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
    data class FeedingCluster(
        val centerMinutes: Int,
        val feedings: List<Int>,
        val variance: Double
    ) {
        val hour: Int = centerMinutes / 60
        val minute: Int = centerMinutes % 60
        val count: Int = feedings.size
        val stdDev: Double = sqrt(variance)
    }
    /**
     * Extract and cluster feeding times (handles hour boundaries!)
     * Groups feedings within clusterWindowMinutes together
     */
    private fun <T : Event> extractFeedingClusters(
        events: List<T>,
        clusterWindowMinutes: Int
    ): List<FeedingCluster> {
        val zoneId = ZoneId.systemDefault()

        // Convert each feeding to minutes from midnight
        val feedingMinutes = events.map { event ->
            val zoned = event.timestamp.toInstant().atZone(zoneId)
            zoned.hour * 60 + zoned.minute
        }.sorted()

        if (feedingMinutes.isEmpty()) return emptyList()

        // Cluster nearby times
        val clusters = mutableListOf<MutableList<Int>>()
        var currentCluster = mutableListOf(feedingMinutes[0])

        for (i in 1 until feedingMinutes.size) {
            val timeDiff = feedingMinutes[i] - feedingMinutes[i - 1]

            if (timeDiff <= clusterWindowMinutes) {
                currentCluster.add(feedingMinutes[i])
            } else {
                clusters.add(currentCluster)
                currentCluster = mutableListOf(feedingMinutes[i])
            }
        }
        clusters.add(currentCluster)

        // Convert to FeedingCluster objects
        return clusters.map { cluster ->
            val center = cluster.average().toInt()
            val variance = cluster.map { (it - center).toDouble().pow(2) }.average()

            FeedingCluster(
                centerMinutes = center,
                feedings = cluster,
                variance = variance
            )
        }.sortedBy { it.centerMinutes }
    }

    /**
     * Find next cluster in sequence
     */
    private fun getNextFeedingCluster(
        lastFeedingMinutes: Int,
        clusters: List<FeedingCluster>
    ): FeedingCluster {
        // Cherche le prochain cluster en dehors de la fenêtre du cluster actuel
        val nextInSequence = clusters.firstOrNull { cluster ->
            // Un cluster est "prochain" si son centre est au moins
            // à 1.5× son stdDev après le dernier biberon
            val clusterWindow = maxOf(cluster.stdDev * 1.5, 30.0) // minimum 30min
            cluster.centerMinutes > lastFeedingMinutes + clusterWindow
        }

        return nextInSequence ?: clusters.first()
    }

    /**
     * Calculate next feeding time based on cluster
     */
    private fun calculateNextTimeAtMinutes(
        lastTime: Date,
        nextClusterCenterMinutes: Int,
        needsNextDay: Boolean = false
    ): Long {
        val instant = lastTime.toInstant()
        val zoneId = ZoneId.systemDefault()
        val lastLocalTime = instant.atZone(zoneId)

        val nextHour = nextClusterCenterMinutes / 60
        val nextMinute = nextClusterCenterMinutes % 60

        var nextLocal = lastLocalTime
            .withHour(nextHour)
            .withMinute(nextMinute)
            .withSecond(0)
            .withNano(0)

        if (needsNextDay || nextLocal.isBefore(lastLocalTime)) {
            nextLocal = nextLocal.plusDays(1)
        }

        return nextLocal.toInstant().toEpochMilli()
    }

    /**
     * Updated TIME_BASED prediction using clusters
     */
    private fun <T : Event> predictByHourOfDay(
        lastFeeding: T,
        clusters: List<FeedingCluster>
    ): FeedingPrediction? {
        if (clusters.isEmpty()) return null

        // Get last feeding in minutes
        val lastFeedingZoned = lastFeeding.timestamp.toInstant()
            .atZone(ZoneId.systemDefault())
        val lastFeedingMinutes = lastFeedingZoned.hour * 60 + lastFeedingZoned.minute

        // Find next cluster
        val nextCluster = getNextFeedingCluster(lastFeedingMinutes, clusters)
        val needsNextDay = nextCluster.centerMinutes <= lastFeedingMinutes

        // Calculate next feeding time
        val nextTime = calculateNextTimeAtMinutes(
            lastFeeding.timestamp,
            nextCluster.centerMinutes,
            needsNextDay
        )

        // Confidence based on cluster consistency (stdDev)
        val maxStdDev = 30.0
        val confidence = 1.0 / (1.0 + (nextCluster.stdDev / maxStdDev))

        return FeedingPrediction(
            nextFeedingTimeMs = nextTime,
            pattern = FeedingPattern.TIME_BASED,
            confidence = confidence,
        )
    }
    // ==================== HYBRID PREDICTION ====================

    /**
     * Prédire en utilisant les deux méthodes (transition)
     * Pondération : 60% intervalles (stabilité) + 40% heures (pattern)
     */
    private fun <T : Event> predictHybrid(
        lastFeeding: T,
        intervals: List<Long>,
        clusters: List<FeedingCluster>
    ): FeedingPrediction? {
        val byInterval = predictByInterval(lastFeeding, intervals) ?: return null
        val byHour = predictByHourOfDay(lastFeeding, clusters) ?: return null

        // Pondération intelligente
        val weight = 0.6  // 60% intervalle, 40% heure
        val nextTime = (byInterval.nextFeedingTimeMs * weight +
                byHour.nextFeedingTimeMs * (1 - weight)).toLong()

        return FeedingPrediction(
            nextFeedingTimeMs = nextTime,
            pattern = FeedingPattern.MIXED,
            confidence = (byInterval.confidence + byHour.confidence) / 2.0,
            estimatedIntervalMs = byInterval.estimatedIntervalMs,
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

    private fun calculateSafeMedianAverage(values: List<Long>): Long {
        if (values.isEmpty()) return 0L

        val outlierThreshold = maxOf(1, values.size / 5)
        val filteredValues = values
            .drop(outlierThreshold)
            .dropLast(outlierThreshold)
            .ifEmpty { values }

        val medianInterval = getMedian(filteredValues)
        val averageInterval = filteredValues.average().toLong()

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
        var predictedAmount = predictNextAmount(events, now)

        // If prediction failed or resulted in unrealistic value, fallback to average
        if (predictedAmount <= 0) {
            return calculatePresetsFromAverage(
                events.mapNotNull { it.getAmountValue() },
                defaultPresets,
                factors
            )
        }

        // Constrain predicted amount within realistic bounds (50% - 150% of median)
        val medianAmount = calculateSafeMedianAverage(events.mapNotNull { it.getAmountValue()?.toLong() })
        val minAmount = medianAmount * 0.50
        val maxAmount = medianAmount * 1.50
        predictedAmount = predictedAmount.coerceIn(minAmount, maxAmount)

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

        val avg = calculateSafeMedianAverage(amounts.map { it.toLong() })
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
    EventPrediction.predictNextFeedingTimeMs(this.sortedByDescending { it.timestamp.time })
