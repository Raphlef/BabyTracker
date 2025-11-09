package com.kouloundissa.twinstracker.data

import com.kouloundissa.twinstracker.ui.components.AnalysisFilter
import java.time.LocalDate

data class DailyAnalysis(
    val date: LocalDate,
    val mealCount: Int = 0,
    val mealVolume: Float = 0f,
    val pumpingCount: Int = 0,
    val pumpingVolume: Float = 0f,
    val poopCount: Int = 0,
    val wetCount: Int = 0,
    val sleepMinutes: Long = 0,
    val growthMeasurements: GrowthMeasurement? = null
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
    val dateRange: AnalysisFilter.DateRange,
    val babyId: String,
    val timestamp: Long = System.currentTimeMillis()
)
