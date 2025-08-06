package com.example.babytracker.data.event

import java.util.Date
import java.util.UUID

data class SleepEvent(
    override val id: String = UUID.randomUUID().toString(),
    override val babyId: String,
    override val timestamp: Date = Date(), // Represents sleep start time
    override val notes: String? = null,
    val endTime: Date? = null, // Sleep end time, nullable if currently sleeping
    val durationMinutes: Long? = null // Can be calculated or directly stored
    // val location: String? = null // e.g., "Crib", "Stroller"
) : Event()