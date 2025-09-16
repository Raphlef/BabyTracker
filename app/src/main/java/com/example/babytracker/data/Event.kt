package com.example.babytracker.data

import android.util.Log
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.IgnoreExtraProperties
import java.util.Date
import java.util.UUID
import kotlin.reflect.KClass
import kotlin.reflect.full.memberProperties

/**
 * EventType enum associates a display name and a color for each event kind.
 */
enum class EventType(val displayName: String, val color: Color,val icon: ImageVector,
                     val eventClass: KClass<out Event> ) {
    DIAPER("Diaper", Color(0xFFFFC107), Icons.Filled.ChildCare,DiaperEvent::class),
    FEEDING("Feeding", Color(0xFF4CAF50), Icons.Filled.Fastfood,FeedingEvent::class),
    SLEEP("Sleep", Color(0xFF2196F3), Icons.Filled.Hotel,         SleepEvent::class),
    GROWTH("Growth", Color(0xFF9C27B0), Icons.AutoMirrored.Filled.ShowChart, GrowthEvent::class),
    PUMPING("Pumping", Color(0xFFFF5722), Icons.Filled.Add,       PumpingEvent::class);

    companion object {
        fun forClass(clazz: KClass<out Event>): EventType = entries.firstOrNull { it.eventClass == clazz }
            ?: DIAPER
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

    /**
     * Automatically serialize all properties to a Map using Kotlin reflection.
     * Uses EventType enum to derive eventTypeString.
     */
    fun toMap(): Map<String, Any?> {
        val result = mutableMapOf<String, Any?>()

        // Reflect over all Kotlin properties in this instance
        javaClass.kotlin.memberProperties.forEach { prop ->
            // Get the raw value
            val value = prop.getter.call(this)

            // Convert Date→Timestamp and Enum→String
            val converted = when (value) {
                is Date        -> com.google.firebase.Timestamp(value)
                is Enum<*>     -> value.name
                else           -> value
            }

            result[prop.name] = converted
        }

        // Automatically add the eventTypeString from EventType enum
        val type = EventType.forClass(javaClass.kotlin)
        result["eventTypeString"] = type.name

        return result
    }
}

fun DocumentSnapshot.toEvent(): Event? {
    val typeName = getString("eventTypeString") ?: return null
    val et = try {
        EventType.valueOf(typeName)
    } catch (e: IllegalArgumentException) {
        Log.w("Error raph", "Unknown eventTypeString: $typeName"); return null
    }

    // Use Firestore’s data‐class mapping
    val cls = et.eventClass.java
    val event = toObject(cls) as? Event ?: return null

    // Ensure timestamp (and any Date fields) are correctly rehydrated
    // Firestore already maps Timestamp → Date for any Date-typed property,
    // including beginTime/endTime because they were written via toMap() as Timestamp.

    return event
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
    abstract val eventId: String?
    abstract val eventType: EventType
    abstract val eventTimestamp: Date

    data class Diaper(
        override val eventId: String? = null,
        override val eventType: EventType = EventType.DIAPER,
        override val eventTimestamp: Date = Date(),
        val diaperType: DiaperType = DiaperType.DRY,
        val poopColor: PoopColor? = null,
        val poopConsistency: PoopConsistency? = null,
        val notes: String = ""
    ) : EventFormState()

    data class Sleep(
        override val eventId: String? = null,
        override val eventType: EventType = EventType.SLEEP,
        override val eventTimestamp: Date = Date(),
        val isSleeping: Boolean = false,
        val beginTime: Date? = null,
        val endTime: Date? = null,
        val durationMinutes: Long? = null,
        val notes: String = ""
    ) : EventFormState()

    data class Feeding(
        override val eventId: String? = null,
        override val eventType: EventType = EventType.FEEDING,
        override val eventTimestamp: Date = Date(),
        val feedType: FeedType = FeedType.BREAST_MILK,
        val amountMl: String = "",
        val durationMin: String = "",
        val breastSide: BreastSide? = null,
        val notes: String = ""
    ) : EventFormState()

    data class Growth(
        override val eventId: String? = null,
        override val eventType: EventType = EventType.GROWTH,
        override val eventTimestamp: Date = Date(),
        val weightKg: String = "",
        val heightCm: String = "",
        val headCircumferenceCm: String = "",
        val notes: String = ""
    ) : EventFormState()

    data class Pumping(
        override val eventId: String? = null,
        override val eventType: EventType = EventType.PUMPING,
        override val eventTimestamp: Date = Date(),
        val amountMl: String = "",
        val durationMin: String = "",
        val breastSide: BreastSide? = null,
        val notes: String = ""
    ) : EventFormState()
}
