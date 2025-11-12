package com.kouloundissa.twinstracker.data

import android.net.Uri
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.outlined.BabyChangingStation
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.IgnoreExtraProperties
import com.kouloundissa.twinstracker.R
import java.util.Date
import java.util.UUID
import kotlin.reflect.KClass

/**
 * EventType enum associates a display name and a color for each event kind.
 */
enum class EventType(
    val Key: String,
    val displayName: String,
    val color: Color,
    val icon: ImageVector,
    @DrawableRes val drawableRes: Int,
    val eventClass: KClass<out Event>
) {
    DIAPER(
        "DIAPER",
        "Diaper",
        Color(0xFFFFC107),
        Icons.Outlined.BabyChangingStation,
        R.drawable.diaper,
        DiaperEvent::class
    ),
    FEEDING(
        "FEEDING",
        "Feeding",
        Color(0xFF4CAF50),
        Icons.Filled.Restaurant,
        R.drawable.feed,
        FeedingEvent::class
    ),
    SLEEP(
        "SLEEP",
        "Sleep", Color(0xFF2196F3), Icons.Filled.Bedtime, R.drawable.sleep, SleepEvent::class
    ),
    GROWTH(
        "GROWTH",
        "Growth",
        Color(0xFF9C27B0),
        Icons.Filled.BarChart,
        R.drawable.growth,
        GrowthEvent::class
    ),
    PUMPING(
        "PUMPING",
        "Pumping",
        Color(0xFFFF5722),
        Icons.Filled.WaterDrop,
        R.drawable.pumping,
        PumpingEvent::class
    ),
    DRUGS(
        "DRUGS",
        "Drugs",
        Color(0xFF3F51B5),
        Icons.Filled.MedicalServices,
        R.drawable.drugs,
        DrugsEvent::class
    );

    companion object {
        fun forClass(clazz: KClass<out Event>): EventType =
            entries.firstOrNull { it.eventClass == clazz }
                ?: DIAPER

        fun forClass(eventClass: Any): EventType {
            return when (eventClass) {
                is DiaperEvent -> DIAPER
                is FeedingEvent -> FEEDING
                is SleepEvent -> SLEEP
                is GrowthEvent -> GROWTH
                is PumpingEvent -> PUMPING
                is DrugsEvent -> DRUGS
                eventClass::class.java.simpleName.contains(
                    "DiaperEvent",
                    ignoreCase = true
                ) -> DIAPER

                eventClass::class.java.simpleName.contains(
                    "FeedingEvent",
                    ignoreCase = true
                ) -> FEEDING

                eventClass::class.java.simpleName.contains("SleepEvent", ignoreCase = true) -> SLEEP
                eventClass::class.java.simpleName.contains(
                    "GrowthEvent",
                    ignoreCase = true
                ) -> GROWTH

                eventClass::class.java.simpleName.contains(
                    "PumpingEvent",
                    ignoreCase = true
                ) -> PUMPING

                eventClass::class.java.simpleName.contains("DrugsEvent", ignoreCase = true) -> DRUGS
                else -> DIAPER  // default
            }
        }

        fun getEventClass(eventType: EventType): kotlin.reflect.KClass<out Event> {
            return when (eventType) {
                DIAPER -> DiaperEvent::class
                FEEDING -> FeedingEvent::class
                SLEEP -> SleepEvent::class
                GROWTH -> GrowthEvent::class
                PUMPING -> PumpingEvent::class
                DRUGS -> DrugsEvent::class
            }
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
    abstract val userId: String
    abstract val notes: String?
    abstract val photoUrl: String?

    /**
     * Automatically serialize all properties to a Map using Kotlin reflection.
     * Uses EventType enum to derive eventTypeString.
     */
    fun toMap(): Map<String, Any?> {
        val result = mutableMapOf<String, Any?>()

        // Reflect over all Kotlin properties in this instance
        this::class.members.forEach { prop ->
            // Get the raw value
            val value = try {
                when (prop) {
                    is kotlin.reflect.KProperty<*> -> prop.getter.call(this)
                    else -> null
                }
            } catch (e: Exception) {
                null
            }

            if (value != null) {
                // Convert special types for JSON serialization
                val converted = when (value) {
                    is Date -> value.time  // Store as Long timestamp
                    is Enum<*> -> value.name
                    else -> value
                }

                result[prop.name] = converted
            }
        }

        // Add eventTypeString for polymorphic deserialization
        val type = EventType.forClass(this)
        result["eventTypeString"] = type.name

        return result
    }
}

fun Event.setPhotoUrl(photoUrl: String?): Event = when (this) {
    is DiaperEvent -> this.copy(photoUrl = photoUrl)
    is FeedingEvent -> this.copy(photoUrl = photoUrl)
    is SleepEvent -> this.copy(photoUrl = photoUrl)
    is GrowthEvent -> this.copy(photoUrl = photoUrl)
    is PumpingEvent -> this.copy(photoUrl = photoUrl)
    is DrugsEvent -> this.copy(photoUrl = photoUrl)
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
    override val userId: String,
    override val timestamp: Date = Date(),
    override val notes: String? = null,
    override val photoUrl: String? = null,
    val diaperType: DiaperType = DiaperType.DRY,
    val poopColor: PoopColor? = null,
    val poopConsistency: PoopConsistency? = null
) : Event() {
    constructor() : this("", "", "", Date(), null, null, DiaperType.DRY, null, null)
}

@IgnoreExtraProperties
data class FeedingEvent(
    override val id: String = UUID.randomUUID().toString(),
    override val babyId: String,
    override val userId: String,
    override val timestamp: Date = Date(),
    override val notes: String? = null,
    override val photoUrl: String? = null,
    val feedType: FeedType = FeedType.BREAST_MILK,
    val amountMl: Double? = null,
    val durationMinutes: Int? = null,
    val breastSide: BreastSide? = null
) : Event() {
    constructor() : this("", "", "", Date(), null, null, FeedType.BREAST_MILK, null, null, null)
}

@IgnoreExtraProperties
data class SleepEvent(
    override val id: String = UUID.randomUUID().toString(),
    override val babyId: String,
    override val userId: String,
    override val timestamp: Date = Date(),
    override val notes: String? = null,
    override val photoUrl: String? = null,
    val isSleeping: Boolean = false,
    val beginTime: Date? = null,
    val endTime: Date? = null,
    val durationMinutes: Long? = null
) : Event() {
    constructor() : this("", "", "", Date(), null, null, false, null, null, null)
}

@IgnoreExtraProperties
data class GrowthEvent(
    override val id: String = UUID.randomUUID().toString(),
    override val babyId: String,
    override val userId: String,
    override val timestamp: Date = Date(),
    override val notes: String? = null,
    override val photoUrl: String? = null,
    val weightKg: Double? = null,
    val heightCm: Double? = null,
    val headCircumferenceCm: Double? = null
) : Event() {
    constructor() : this("", "", "", Date(), null, null, null, null, null)
}

@IgnoreExtraProperties
data class PumpingEvent(
    override val id: String = UUID.randomUUID().toString(),
    override val babyId: String,
    override val userId: String,
    override val timestamp: Date = Date(),
    override val notes: String? = null,
    override val photoUrl: String? = null,
    val amountMl: Double? = null,
    val durationMinutes: Int? = null,
    val breastSide: BreastSide? = null
) : Event() {
    constructor() : this("", "", "", Date(), null, null, null, null, null)
}

@IgnoreExtraProperties
data class DrugsEvent(
    override val id: String = UUID.randomUUID().toString(),
    override val babyId: String,
    override val userId: String,
    override val timestamp: Date = Date(),
    override val notes: String? = null,
    override val photoUrl: String? = null,

    // New fields
    val drugType: DrugType = DrugType.PARACETAMOL,
    val dosage: Double? = null,            // dosage in mg
    val unit: String = "mg",                 // generic unit string
    val otherDrugName: String? = null        // only if drugType == OTHER
) : Event() {
    // no-arg constructor for Firestore
    constructor() : this("", "", "", Date(), null, null, DrugType.PARACETAMOL, null, "mg", null)
}

/**
 * Extension function on Event companion to deserialize from Map
 * No changes to existing API - seamlessly integrated with Firestore
 *
 * Usage: val event = Event.fromMap(eventMap)
 */
fun EventType.Companion.fromMap(map: Map<String, Any?>): Event? {
    return try {
        val typeName = map["eventTypeString"] as? String ?: return null
        val eventType = try {
            EventType.valueOf(typeName)
        } catch (e: IllegalArgumentException) {
            Log.w("Event", "Unknown eventTypeString: $typeName")
            return null
        }

        val eventClass = eventType.eventClass.java

        // Use Gson to deserialize (same as Firestore does)
        val gson = com.google.gson.Gson()
        val json = gson.toJson(map)
        gson.fromJson(json, eventClass) as? Event
    } catch (e: Exception) {
        Log.w("Event", "Error deserializing event from map: ${e.message}", e)
        null
    }
}

/**
 * Helper function: Convert Map to specific Event type
 * Handles timestamp conversion (Long → Date)
 */
fun Map<String, Any?>.toEvent(): Event? {
    // Convert timestamp Long back to Date if needed
    val mutableMap = this.toMutableMap()

    // Check if any date fields are stored as Long (from cache)
    val dateFields = listOf("timestamp", "beginTime", "endTime", "cachedAt")
    dateFields.forEach { field ->
        if (mutableMap.containsKey(field) && mutableMap[field] is Long) {
            mutableMap[field] = Date(mutableMap[field] as Long)
        }
    }

    return EventType.fromMap(mutableMap)
}

/**
 * Extension for Event.Companion object to add fromMap as static-like method
 */
operator fun EventType.Companion.invoke(map: Map<String, Any?>): Event? {
    return fromMap(map)
}

/**
 * UI form state representing the in-memory values while editing or creating an event.
 */
sealed class EventFormState {
    abstract val event: Event?
    abstract val eventId: String?
    abstract val eventType: EventType
    abstract val eventTimestamp: Date
    abstract var photoUrl: String?
    abstract var newPhotoUrl: Uri?
    abstract var photoRemoved: Boolean
    fun validateAndToEvent(babyId: String, userId: String): Result<Event> {
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
                            userId = userId,
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
                            userId = userId,
                            timestamp = eventTimestamp,
                            isSleeping = isSleeping,
                            beginTime = beginTime,
                            endTime = endTime,
                            durationMinutes = durationMinutes,
                            notes = notes.takeIf(String::isNotBlank),
                            photoUrl = photoUrl
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
                        userId = userId,
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
                            userId = userId,
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
                            userId = userId,
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

            is EventFormState.Drugs -> {
                val dose = dosage.toDoubleOrNull()
                if (dose == null || dose <= 0) {
                    Result.failure(IllegalArgumentException("Enter a positive numeric dosage."))
                } else if (drugType == DrugType.OTHER && otherDrugName.isBlank()) {
                    Result.failure(IllegalArgumentException("Specify the drug name for “Other”."))
                } else {
                    Result.success(
                        DrugsEvent(
                            id = eventId ?: UUID.randomUUID().toString(),
                            babyId = babyId,
                            userId = userId,
                            timestamp = eventTimestamp,
                            notes = notes.takeIf(String::isNotBlank),
                            photoUrl = photoUrl,
                            drugType = drugType,
                            dosage = dose,
                            unit = unit,
                            otherDrugName = otherDrugName.takeIf(String::isNotBlank)
                        )
                    )
                }
            }
        }
    }

    data class Diaper(
        override val event: Event? = null,
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
        override val event: Event? = null,
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
        override val event: Event? = null,
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
        override val event: Event? = null,
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
        override val event: Event? = null,
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

    data class Drugs(
        override val event: Event? = null,
        override val eventId: String? = null,
        override val eventType: EventType = EventType.DRUGS,
        override val eventTimestamp: Date = Date(),
        override var photoUrl: String? = null,
        override var newPhotoUrl: Uri? = null,
        override var photoRemoved: Boolean = false,

        val drugType: DrugType = DrugType.PARACETAMOL,
        val dosage: String = "",          // user input as string
        val unit: String = "mg",          // default unit
        val otherDrugName: String = "",   // when drugType == OTHER
        val notes: String = ""
    ) : EventFormState()

}
