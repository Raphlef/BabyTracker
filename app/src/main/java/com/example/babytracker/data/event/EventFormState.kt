package com.example.babytracker.data.event

import com.example.babytracker.data.BreastSide
import com.example.babytracker.data.DiaperType
import com.example.babytracker.data.FeedType
import com.example.babytracker.data.PoopColor
import com.example.babytracker.data.PoopConsistency
import java.util.Date

enum class EventType(val displayName: String) {
    DIAPER("Diaper"),
    FEEDING("Feeding"),
    SLEEP("Sleep"),
    GROWTH("Growth")
}
sealed class EventFormState {
    abstract val eventType: EventType
    data class Diaper(
        override val eventType: EventType = EventType.DIAPER,
        val diaperType: DiaperType = DiaperType.DRY,
        val poopColor: PoopColor? = null,
        val poopConsistency: PoopConsistency? = null,
        val notes: String = ""
    ) : EventFormState()

    data class Sleep(
        override val eventType: EventType = EventType.SLEEP,
        val isSleeping: Boolean = false,
        val beginTime: Date? = null,
        val endTime: Date? = null,
        val durationMinutes: Long? = null,
        val notes: String = ""
    ) : EventFormState()

    data class Feeding(
        override val eventType: EventType = EventType.FEEDING,
        val feedType: FeedType = FeedType.BREAST_MILK,
        val amountMl: String = "",
        val durationMin: String = "",
        val breastSide: BreastSide? = null,
        val notes: String = ""
    ) : EventFormState()

    data class Growth(
        override val eventType: EventType = EventType.GROWTH,
        val weightKg: String = "",
        val heightCm: String = "",
        val headCircumferenceCm: String = "",
        val notes: String = ""
    ) : EventFormState()
}
