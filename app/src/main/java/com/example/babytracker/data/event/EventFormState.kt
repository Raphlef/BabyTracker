package com.example.babytracker.data.event

import androidx.compose.ui.graphics.Color
import com.example.babytracker.data.BreastSide
import com.example.babytracker.data.DiaperType
import com.example.babytracker.data.FeedType
import com.example.babytracker.data.PoopColor
import com.example.babytracker.data.PoopConsistency
import java.util.Date
import kotlin.reflect.KClass

enum class EventType(val displayName: String, val color: Color) {
    DIAPER("Diaper", Color(0xFFFFC107)),    // Amber
    FEEDING("Feeding", Color(0xFF4CAF50)),  // Green
    SLEEP("Sleep", Color(0xFF2196F3)),      // Blue
    GROWTH("Growth", Color(0xFF9C27B0)),    // Purple
    PUMPING("Pumping", Color(0xFFFF5722));  // Orange
    companion object {
        fun forClass(clazz: KClass<out Event>): EventType = when (clazz) {
            DiaperEvent::class  -> DIAPER
            FeedingEvent::class -> FEEDING
            SleepEvent::class   -> SLEEP
            GrowthEvent::class  -> GROWTH
            PumpingEvent::class  -> PUMPING
            else                 -> DIAPER
        }
    }
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

    data class Pumping(
        override val eventType: EventType = EventType.PUMPING,
        val amountMl: String = "",
        val durationMin: String = "",
        val breastSide: BreastSide? = null,
        val notes: String = ""
    ) : EventFormState()
}
