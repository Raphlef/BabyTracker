package com.kouloundissa.twinstracker.data

import android.net.Uri
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
enum class EventType(
    val displayName: String, val color: Color, val icon: ImageVector,
    val eventClass: KClass<out Event>
) {
    DIAPER("Diaper", Color(0xFFFFC107), Icons.Filled.ChildCare, DiaperEvent::class),
    FEEDING("Feeding", Color(0xFF4CAF50), Icons.Filled.Fastfood, FeedingEvent::class),
    SLEEP("Sleep", Color(0xFF2196F3), Icons.Filled.Hotel, SleepEvent::class),
    GROWTH("Growth", Color(0xFF9C27B0), Icons.AutoMirrored.Filled.ShowChart, GrowthEvent::class),
    PUMPING("Pumping", Color(0xFFFF5722), Icons.Filled.Add, PumpingEvent::class);

    companion object {
        fun forClass(clazz: KClass<out Event>): EventType =
            entries.firstOrNull { it.eventClass == clazz }
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
    abstract val photoUrl: String?

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
                is Date -> com.google.firebase.Timestamp(value)
                is Enum<*> -> value.name
                else -> value
            }

            result[prop.name] = converted
        }

        // Automatically add the eventTypeString from EventType enum
        val type = EventType.forClass(javaClass.kotlin)
        result["eventTypeString"] = type.name

        return result
    }
}
fun Event.setPhotoUrl(photoUrl: String?): Event = when (this) {
    is DiaperEvent  -> this.copy(photoUrl = photoUrl)
    is FeedingEvent -> this.copy(photoUrl = photoUrl)
    is SleepEvent   -> this.copy(photoUrl = photoUrl)
    is GrowthEvent  -> this.copy(photoUrl = photoUrl)
    is PumpingEvent -> this.copy(photoUrl = photoUrl)
    else            -> this // fallback (shouldn't occur)
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
    override val photoUrl: String? = null,
    val diaperType: DiaperType = DiaperType.DRY,
    val poopColor: PoopColor? = null,
    val poopConsistency: PoopConsistency? = null
) : Event() {
    constructor() : this("", "", Date(), null, null, DiaperType.DRY, null, null)
}

@IgnoreExtraProperties
data class FeedingEvent(
    override val id: String = UUID.randomUUID().toString(),
    override val babyId: String,
    override val timestamp: Date = Date(),
    override val notes: String? = null,
    override val photoUrl: String? = null,
    val feedType: FeedType = FeedType.BREAST_MILK,
    val amountMl: Double? = null,
    val durationMinutes: Int? = null,
    val breastSide: BreastSide? = null
) : Event() {
    constructor() : this("", "", Date(), null, null, FeedType.BREAST_MILK, null, null, null)
}

@IgnoreExtraProperties
data class SleepEvent(
    override val id: String = UUID.randomUUID().toString(),
    override val babyId: String,
    override val timestamp: Date = Date(),
    override val notes: String? = null,
    override val photoUrl: String? = null,
    val isSleeping: Boolean = false,
    val beginTime: Date? = null,
    val endTime: Date? = null,
    val durationMinutes: Long? = null
) : Event() {
    constructor() : this("", "", Date(), null, null, false, null, null, null)
}

@IgnoreExtraProperties
data class GrowthEvent(
    override val id: String = UUID.randomUUID().toString(),
    override val babyId: String,
    override val timestamp: Date = Date(),
    override val notes: String? = null,
    override val photoUrl: String? = null,
    val weightKg: Double? = null,
    val heightCm: Double? = null,
    val headCircumferenceCm: Double? = null
) : Event() {
    constructor() : this("", "", Date(), null, null, null, null, null)
}

@IgnoreExtraProperties
data class PumpingEvent(
    override val id: String = UUID.randomUUID().toString(),
    override val babyId: String,
    override val timestamp: Date = Date(),
    override val notes: String? = null,
    override val photoUrl: String? = null,
    val amountMl: Double? = null,
    val durationMinutes: Int? = null,
    val breastSide: BreastSide? = null
) : Event() {
    constructor() : this("", "", Date(), null, null, null, null, null)
}

/**
 * UI form state representing the in-memory values while editing or creating an event.
 */
sealed class EventFormState {
    abstract val eventId: String?
    abstract val eventType: EventType
    abstract val eventTimestamp: Date
    abstract var photoUrl: String?
    abstract var newPhotoUrl: Uri?
    abstract var photoRemoved: Boolean
    fun validateAndToEvent(babyId: String): Result<Event> {
        return when (this) {
            is Diaper -> {
                if ((diaperType == DiaperType.DIRTY || diaperType == DiaperType.MIXED)
                    && poopColor == null && poopConsistency == null
                ) {
                    Result.failure(
                        IllegalArgumentException("For dirty diapers, specify color or consistency.")
                    )
                } else {
                    Result.success(
                        DiaperEvent(
                            id = eventId ?: UUID.randomUUID().toString(),
                            babyId = babyId,
                            timestamp = eventTimestamp,
                            diaperType = diaperType,
                            poopColor = poopColor,
                            poopConsistency = poopConsistency,
                            notes = notes.takeIf(String::isNotBlank),
                            photoUrl = photoUrl
                        )
                    )
                }
            }

            is Sleep -> {
                if (!isSleeping && beginTime == null && endTime == null) {
                    Result.failure(
                        IllegalArgumentException("Please start and stop sleep before saving.")
                    )
                } else {
                    Result.success(
                        SleepEvent(
                            id = eventId ?: UUID.randomUUID().toString(),
                            babyId = babyId,
                            timestamp = eventTimestamp,
                            isSleeping = isSleeping,
                            beginTime = beginTime,
                            endTime = endTime,
                            durationMinutes = durationMinutes,
                            notes = notes.takeIf(String::isNotBlank),
                            photoUrl = photoUrl as String?
                        )
                    )
                }
            }

            is Feeding -> {
                val amount = amountMl.toDoubleOrNull()
                val duration = durationMin.toIntOrNull()
                when (feedType) {
                    FeedType.BREAST_MILK -> {
                        if (amount == null && duration == null && breastSide == null) {
                            return Result.failure(
                                IllegalArgumentException("Provide duration/side or amount for breast milk.")
                            )
                        }
                    }

                    FeedType.FORMULA -> {
                        if (amount == null) {
                            return Result.failure(
                                IllegalArgumentException("Amount is required for formula.")
                            )
                        }
                    }

                    FeedType.SOLID -> {
                        if (amount == null && notes.isBlank()) {
                            return Result.failure(
                                IllegalArgumentException("Provide notes or amount for solids.")
                            )
                        }
                    }
                }
                Result.success(
                    FeedingEvent(
                        id = eventId ?: UUID.randomUUID().toString(),
                        babyId = babyId,
                        timestamp = eventTimestamp,
                        feedType = feedType,
                        amountMl = amount,
                        durationMinutes = duration,
                        breastSide = breastSide,
                        notes = notes.takeIf(String::isNotBlank),
                        photoUrl = photoUrl
                    )
                )
            }

            is Growth -> {
                val weight = weightKg.toDoubleOrNull()
                val height = heightCm.toDoubleOrNull()
                val head = headCircumferenceCm.toDoubleOrNull()
                if (weight == null && height == null && head == null) {
                    Result.failure(IllegalArgumentException("At least one measurement required."))
                } else {
                    Result.success(
                        GrowthEvent(
                            id = eventId ?: UUID.randomUUID().toString(),
                            babyId = babyId,
                            timestamp = eventTimestamp,
                            weightKg = weight,
                            heightCm = height,
                            headCircumferenceCm = head,
                            notes = notes.takeIf(String::isNotBlank),
                            photoUrl = photoUrl
                        )
                    )
                }
            }

            is Pumping -> {
                val amount = amountMl.toDoubleOrNull()
                val duration = durationMin.toIntOrNull()
                if (amount == null && duration == null) {
                    Result.failure(
                        IllegalArgumentException("Provide amount or duration for pumping.")
                    )
                } else {
                    Result.success(
                        PumpingEvent(
                            id = eventId ?: UUID.randomUUID().toString(),
                            babyId = babyId,
                            timestamp = eventTimestamp,
                            amountMl = amount,
                            durationMinutes = duration,
                            breastSide = breastSide,
                            notes = notes.takeIf(String::isNotBlank),
                            photoUrl = photoUrl
                        )
                    )
                }
            }
        }
    }

    data class Diaper(
        override val eventId: String? = null,
        override val eventType: EventType = EventType.DIAPER,
        override val eventTimestamp: Date = Date(),
        override var photoUrl: String? = null,
        override var newPhotoUrl: Uri? = null,
        override var photoRemoved: Boolean = false,
        val diaperType: DiaperType = DiaperType.DRY,
        val poopColor: PoopColor? = null,
        val poopConsistency: PoopConsistency? = null,
        val notes: String = "",
    ) : EventFormState()

    data class Sleep(
        override val eventId: String? = null,
        override val eventType: EventType = EventType.SLEEP,
        override val eventTimestamp: Date = Date(),
        override var photoUrl: String? = null,
        override var newPhotoUrl: Uri? = null,
        override var photoRemoved: Boolean = false,
        val isSleeping: Boolean = false,
        val beginTime: Date? = null,
        val endTime: Date? = null,
        val durationMinutes: Long? = null,
        val notes: String = "",
    ) : EventFormState()

    data class Feeding(
        override val eventId: String? = null,
        override val eventType: EventType = EventType.FEEDING,
        override val eventTimestamp: Date = Date(),
        override var photoUrl: String? = null,
        override var newPhotoUrl: Uri? = null,
        override var photoRemoved: Boolean = false,
        val feedType: FeedType = FeedType.BREAST_MILK,
        val amountMl: String = "",
        val durationMin: String = "",
        val breastSide: BreastSide? = null,
        val notes: String = "",
    ) : EventFormState()

    data class Growth(
        override val eventId: String? = null,
        override val eventType: EventType = EventType.GROWTH,
        override val eventTimestamp: Date = Date(),
        override var photoUrl: String? = null,
        override var newPhotoUrl: Uri? = null,
        override var photoRemoved: Boolean = false,
        val weightKg: String = "",
        val heightCm: String = "",
        val headCircumferenceCm: String = "",
        val notes: String = "",
    ) : EventFormState()

    data class Pumping(
        override val eventId: String? = null,
        override val eventType: EventType = EventType.PUMPING,
        override val eventTimestamp: Date = Date(),
        override var photoUrl: String? = null,
        override var newPhotoUrl: Uri? = null,
        override var photoRemoved: Boolean = false,
        val amountMl: String = "",
        val durationMin: String = "",
        val breastSide: BreastSide? = null,
        val notes: String = "",
    ) : EventFormState()
}

private fun EventFormState.toEvent(babyId: String): Event = when (this) {
    is EventFormState.Diaper -> DiaperEvent(
        id = eventId ?: UUID.randomUUID().toString(),
        babyId = babyId,
        timestamp = eventTimestamp,
        notes = notes.takeIf(String::isNotBlank),
        photoUrl = photoUrl,
        diaperType = diaperType,
        poopColor = poopColor,
        poopConsistency = poopConsistency
    )

    is EventFormState.Sleep -> SleepEvent(
        id = eventId ?: UUID.randomUUID().toString(),
        babyId = babyId,
        timestamp = eventTimestamp,
        notes = notes.takeIf(String::isNotBlank),
        photoUrl = photoUrl,
        isSleeping = isSleeping,
        beginTime = beginTime,
        endTime = endTime,
        durationMinutes = durationMinutes
    )

    is EventFormState.Feeding -> FeedingEvent(
        id = eventId ?: UUID.randomUUID().toString(),
        babyId = babyId,
        timestamp = eventTimestamp,
        notes = notes.takeIf(String::isNotBlank),
        photoUrl = photoUrl,
        feedType = feedType,
        amountMl = amountMl.toDoubleOrNull(),
        durationMinutes = durationMin.toIntOrNull(),
        breastSide = breastSide
    )

    is EventFormState.Growth -> GrowthEvent(
        id = eventId ?: UUID.randomUUID().toString(),
        babyId = babyId,
        timestamp = eventTimestamp,
        notes = notes.takeIf(String::isNotBlank),
        photoUrl = photoUrl,
        weightKg = weightKg.toDoubleOrNull(),
        heightCm = heightCm.toDoubleOrNull(),
        headCircumferenceCm = headCircumferenceCm.toDoubleOrNull()
    )

    is EventFormState.Pumping -> PumpingEvent(
        id = eventId ?: UUID.randomUUID().toString(),
        babyId = babyId,
        timestamp = eventTimestamp,
        notes = notes.takeIf(String::isNotBlank),
        photoUrl = photoUrl,
        amountMl = amountMl.toDoubleOrNull(),
        durationMinutes = durationMin.toIntOrNull(),
        breastSide = breastSide
    )
}

