package com.example.babytracker.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.google.firebase.firestore.IgnoreExtraProperties
import java.util.Date
import java.util.UUID
import kotlin.reflect.KClass

/**
 * EventType enum associates a display name and a color for each event kind.
 */
enum class EventType(val displayName: String, val color: Color,val icon: ImageVector) {
    DIAPER("Diaper", Color(0xFFFFC107), Icons.Filled.ChildCare),
    FEEDING("Feeding", Color(0xFF4CAF50), Icons.Filled.Fastfood),
    SLEEP("Sleep", Color(0xFF2196F3), Icons.Filled.Hotel),
    GROWTH("Growth", Color(0xFF9C27B0), Icons.AutoMirrored.Filled.ShowChart),
    PUMPING("Pumping", Color(0xFFFF5722), Icons.Filled.Add);

    companion object {
        fun forClass(clazz: KClass<out Event>): EventType = when (clazz) {
            DiaperEvent::class   -> DIAPER
            FeedingEvent::class  -> FEEDING
            SleepEvent::class    -> SLEEP
            GrowthEvent::class   -> GROWTH
            PumpingEvent::class  -> PUMPING
            else                  -> DIAPER
        }
    }
}

/**
 * Base sealed class for all persisted events in Firestore.
 * Each subclass must have a no‐arg constructor for deserialization.
 */
@IgnoreExtraProperties
sealed class Event {
    abstract val id: String
    abstract val babyId: String
    abstract val timestamp: Date
    abstract val notes: String?
}

@IgnoreExtraProperties
data class DiaperEvent(
    override val id: String = UUID.randomUUID().toString(),
    override val babyId: String,
    override val timestamp: Date = Date(),
    override val notes: String? = null,
    val diaperType: DiaperType = DiaperType.DRY,
    val poopColor: PoopColor? = null,
    val poopConsistency: PoopConsistency? = null
) : Event() {
    constructor() : this("", "", Date(), null, DiaperType.DRY, null, null)
}

@IgnoreExtraProperties
data class FeedingEvent(
    override val id: String = UUID.randomUUID().toString(),
    override val babyId: String,
    override val timestamp: Date = Date(),
    override val notes: String? = null,
    val feedType: FeedType = FeedType.BREAST_MILK,
    val amountMl: Double? = null,
    val durationMinutes: Int? = null,
    val breastSide: BreastSide? = null
) : Event() {
    constructor() : this("", "", Date(), null, FeedType.BREAST_MILK, null, null, null)
}

@IgnoreExtraProperties
data class SleepEvent(
    override val id: String = UUID.randomUUID().toString(),
    override val babyId: String,
    override val timestamp: Date = Date(),
    override val notes: String? = null,
    val isSleeping: Boolean = false,
    val beginTime: Date? = null,
    val endTime: Date? = null,
    val durationMinutes: Long? = null
) : Event() {
    constructor() : this("", "", Date(), null, false, null, null, null)
}

@IgnoreExtraProperties
data class GrowthEvent(
    override val id: String = UUID.randomUUID().toString(),
    override val babyId: String,
    override val timestamp: Date = Date(),
    override val notes: String? = null,
    val weightKg: Double? = null,
    val heightCm: Double? = null,
    val headCircumferenceCm: Double? = null
) : Event() {
    constructor() : this("", "", Date(), null, null, null, null)
}

@IgnoreExtraProperties
data class PumpingEvent(
    override val id: String = UUID.randomUUID().toString(),
    override val babyId: String,
    override val timestamp: Date = Date(),
    override val notes: String? = null,
    val amountMl: Double? = null,
    val durationMinutes: Int? = null,
    val breastSide: BreastSide? = null
) : Event() {
    constructor() : this("", "", Date(), null, null, null, null)
}

/**
 * UI form state representing the in-memory values while editing or creating an event.
 */
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
