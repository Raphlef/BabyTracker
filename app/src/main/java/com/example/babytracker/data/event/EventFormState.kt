package com.example.babytracker.data.event

import com.example.babytracker.data.BreastSide
import com.example.babytracker.data.DiaperType
import com.example.babytracker.data.FeedType
import com.example.babytracker.data.PoopColor
import com.example.babytracker.data.PoopConsistency
import java.util.Date

sealed class EventFormState {
    data class Diaper(
        val diaperType: DiaperType = DiaperType.DRY,
        val poopColor: PoopColor? = null,
        val poopConsistency: PoopConsistency? = null,
        val notes: String = ""
    ) : EventFormState()

    data class Sleep(
        val isSleeping: Boolean = false,
        val beginTime: Date? = null,
        val endTime: Date? = null,
        val durationMinutes: Long? = null,
        val notes: String = ""
    ) : EventFormState()

    data class Feeding(
        val feedType: FeedType = FeedType.BREAST_MILK,
        val amountMl: String = "",
        val durationMin: String = "",
        val breastSide: BreastSide? = null,
        val notes: String = ""
    ) : EventFormState()

    data class Growth(
        val weightKg: String = "",
        val heightCm: String = "",
        val headCircumferenceCm: String = "",
        val notes: String = ""
    ) : EventFormState()
}
