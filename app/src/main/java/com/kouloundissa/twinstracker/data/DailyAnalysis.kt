package com.kouloundissa.twinstracker.data

import com.kouloundissa.twinstracker.ui.components.AnalysisFilter
import java.time.LocalDate
import java.time.ZoneId

data class DailyAnalysis(
    val date: LocalDate,
    val mealCount: Int = 0,
    val mealVolume: Float = 0f,
    val pumpingCount: Int = 0,
    val pumpingVolume: Float = 0f,
    val poopCount: Int = 0,
    val wetCount: Int = 0,
    val sleepMinutes: Long = 0,
    val growthMeasurements: GrowthMeasurement? = null,
    val events: List<Event> = emptyList()
)

data class GrowthMeasurement(
    val weightKg: Float = Float.NaN,
    val heightCm: Float = Float.NaN,
    val headCircumferenceCm: Float = Float.NaN,
    val timestamp: Long
)

// Container for all analysis results
data class AnalysisSnapshot(
    val dailyAnalysis: List<DailyAnalysis>,
    val events: List<Event> = emptyList(),
    val eventsByDay: Map<LocalDate, List<Event>> = emptyMap(),
    val dateRange: AnalysisFilter.DateRange,
    val babyId: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Groupe les événements par date en gérant les événements qui traversent minuit
 * (ex: SleepEvent de 22h à 3h du matin)
 */
public fun List<Event>.groupByDateSpanning(
    systemZone: ZoneId = ZoneId.systemDefault()
): Map<LocalDate, List<Event>> {
    val result = mutableMapOf<LocalDate, MutableList<Event>>()

    forEach { event ->
        val eventStartDate = event.timestamp.toInstant()
            .atZone(systemZone)
            .toLocalDate()

        val eventEndDate = when (event) {
            is SleepEvent -> event.endTime?.toInstant()
                ?.atZone(systemZone)
                ?.toLocalDate() ?: eventStartDate
            else -> eventStartDate
        }

        // Add event to ALL dates it spans
        var currentDate = eventStartDate
        while (!currentDate.isAfter(eventEndDate)) {
            result.getOrPut(currentDate) { mutableListOf() }.add(event)
            currentDate = currentDate.plusDays(1)
        }
    }

    return result
}

/**
 * Extension : récupère les événements pour une date spécifique
 * en tenant compte du spanning minuit
 */
public fun List<Event>.filterByDate(
    targetDate: LocalDate,
    filterTypes: Set<EventType>,
    systemZone: ZoneId = ZoneId.systemDefault()
): List<Event> {
    return this
        .filter { event ->
            val eventStartDate = event.timestamp.toInstant()
                .atZone(systemZone)
                .toLocalDate()

            val eventEndDate = when (event) {
                is SleepEvent -> event.endTime?.toInstant()
                    ?.atZone(systemZone)
                    ?.toLocalDate() ?: eventStartDate
                else -> eventStartDate
            }

            // Event spans the target date if: startDate <= targetDate <= endDate
            !eventStartDate.isAfter(targetDate) && !targetDate.isAfter(eventEndDate) &&
                    filterTypes.contains(EventType.forClass(event::class))
        }
}

