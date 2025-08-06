package com.example.babytracker.data

import java.util.Date
import java.util.UUID

// Base sealed class for all events
sealed class Event {
    abstract val id: String
    abstract val babyId: String // ID of the baby this event belongs to
    abstract val timestamp: Date
    abstract val notes: String? // Optional notes for any event
}


// Enum for different Diaper event types
enum class DiaperType {
    WET,
    DIRTY, // Could represent "poop"
    MIXED // Both wet and dirty
}

// Enum for Feeding types
enum class FeedType {
    BREAST_MILK,
    FORMULA,
    SOLID
}

// Enum for Breast side during feeding or pumping
enum class BreastSide {
    LEFT,
    RIGHT,
    BOTH
}

data class FeedingEvent(
    override val id: String = UUID.randomUUID().toString(),
    override val babyId: String,
    override val timestamp: Date = Date(),
    override val notes: String? = null,
    val feedType: FeedType,
    val amountMl: Double? = null, // Milliliters, for formula or pumped milk
    val durationMinutes: Int? = null, // For breastfeeding duration
    val breastSide: BreastSide? = null // For breastfeeding or pumping
) : Event()

data class DiaperEvent(
    override val id: String = UUID.randomUUID().toString(),
    override val babyId: String,
    override val timestamp: Date = Date(),
    override val notes: String? = null,
    val diaperType: DiaperType,
    val color: String? = null, // Optional: for poop color
    val consistency: String? = null // Optional: for poop consistency
) : Event()

data class SleepEvent(
    override val id: String = UUID.randomUUID().toString(),
    override val babyId: String,
    override val timestamp: Date = Date(), // Represents sleep start time
    override val notes: String? = null,
    val endTime: Date? = null, // Sleep end time, nullable if currently sleeping
    val durationMinutes: Long? = null // Can be calculated or directly stored
    // val location: String? = null // e.g., "Crib", "Stroller"
) : Event()

data class GrowthEvent(
    override val id: String = UUID.randomUUID().toString(),
    override val babyId: String,
    override val timestamp: Date = Date(), // Date of measurement
    override val notes: String? = null,
    val weightKg: Double? = null, // Weight in kilograms
    val heightCm: Double? = null, // Height/Length in centimeters
    val headCircumferenceCm: Double? = null // Head circumference in centimeters
) : Event()

data class PumpingEvent(
    override val id: String = UUID.randomUUID().toString(),
    override val babyId: String, // Or could be motherId if tracking for mother
    override val timestamp: Date = Date(),
    override val notes: String? = null,
    val amountMl: Double? = null, // Amount pumped in milliliters
    val durationMinutes: Int? = null,
    val breastSide: BreastSide? = null
) : Event()

// You can add more specific event types as needed, for example:
// - MedicationEvent
// - TemperatureEvent
// - MilestoneEvent (e.g., first smile, first steps)
// - BathEvent
// - TummyTimeEvent

/*
If you prefer to keep a single "EventType" enum and a generic details field for some cases,
you could modify the original structure. However, having distinct data classes for each
event type is generally more robust and easier to manage, especially with pattern matching
(when statements) in Kotlin.

If you choose the specific data class approach above, you won't need the `EventType` enum
and the `details` field in the base `Event` class anymore, as the class type itself
defines the event type, and specific fields hold the details.
*/
