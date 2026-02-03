package com.kouloundissa.twinstracker.data

import com.github.mikephil.charting.formatter.ValueFormatter
import com.kouloundissa.twinstracker.ui.components.AnalysisFilter
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

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

class HoursMinutesFormatter : ValueFormatter() {
    override fun getFormattedValue(value: Float): String {
        val totalMinutes = value.toInt()
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) {
            "${hours}h${minutes.toString().padStart(2, '0')}"
        } else {
            "${minutes}min"
        }
    }
}

/**
 * Aligne avec interpolation cubique pour une courbe plus naturelle
 * Utilise une interpolation de Catmull-Rom simplifiée
 */
fun alignGrowthDataWithInterpolation(
    dateList: List<LocalDate>,
    dailyAnalysis: List<DailyAnalysis>,
    selector: (DailyAnalysis) -> Float?
): List<Float> {
    val allMeasurements = dailyAnalysis
        .mapNotNull { daily ->
            selector(daily)?.let { value ->
                daily.date to value
            }
        }
        .sortedBy { it.first }

    val tolerance = 0.1f
    // Filtrer pour ne garder que les vraies nouvelles mesures
    // (ignorer les valeurs identiques consécutives = copies)
    val realMeasurements = allMeasurements.filterIndexed { index, (_, value) ->
        if (index == 0) {
            // Toujours garder la première mesure
            true
        } else {
            // Garder seulement si différent de la mesure précédente
            kotlin.math.abs(value - allMeasurements[index - 1].second) > tolerance
        }
    }

    if (realMeasurements.size < 2) {
        return if (realMeasurements.isEmpty()) {
            List(dateList.size) { Float.NaN }
        } else {
            dateList.map { date ->
                if (date >= realMeasurements.first().first)
                    realMeasurements.first().second
                else Float.NaN
            }
        }
    }


    return dateList.map { date ->
        realMeasurements.find { it.first == date }?.second
            ?: run {
                val afterIndex = realMeasurements.indexOfFirst { it.first.isAfter(date) }

                when {
                    afterIndex == -1 -> realMeasurements.last().second
                    afterIndex == 0 -> Float.NaN
                    else -> {
                        val p1 = realMeasurements[afterIndex - 1]
                        val p2 = realMeasurements[afterIndex]

                        val totalDays = ChronoUnit.DAYS.between(p1.first, p2.first).toFloat()
                        val daysFromP1 = ChronoUnit.DAYS.between(p1.first, date).toFloat()
                        val t = daysFromP1 / totalDays

                        // Cubic Bezier avec contrôle doux
                        cubicBezierInterpolation(p1.second, p2.second, t)
                    }
                }
            }
    }
}

/**
 * Cubic Bezier : courbe très lisse sans oscillations
 */
private fun cubicBezierInterpolation(y0: Float, y1: Float, t: Float): Float {
    val t2 = t * t
    val t3 = t2 * t

    // Points de contrôle calculés pour une courbe douce (1/3 des segments)
    val control1 = y0 + (y1 - y0) * 0.1f
    val control2 = y1 - (y1 - y0) * 0.1f

    val a0 = (1 - t) * (1 - t) * (1 - t) * y0
    val a1 = 3 * (1 - t) * (1 - t) * t * control1
    val a2 = 3 * (1 - t) * t2 * control2
    val a3 = t3 * y1

    return a0 + a1 + a2 + a3
}