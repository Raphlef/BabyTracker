package com.example.babytracker.data.event

import com.google.firebase.firestore.IgnoreExtraProperties
import java.util.Date
import java.util.UUID

@IgnoreExtraProperties
data class SleepEvent(
    override val id: String = UUID.randomUUID().toString(),
    override val babyId: String,
    override val timestamp: Date = Date(),
    override val notes: String? = null,
    val isSleeping: Boolean = false,
    val beginTime: Date? = null,
    val endTime: Date? = null, // Sleep end time, nullable if currently sleeping
    val durationMinutes: Long? = null // Can be calculated or directly stored
    // val location: String? = null // e.g., "Crib", "Stroller"
) : Event() {

    // Constructeur sans arguments pour Firestore
    constructor() : this(
        id             = "",
        babyId         = "",
        timestamp      = Date(),
        notes          = null,
        isSleeping     = false,
        beginTime      = null,
        endTime        = null,
        durationMinutes= null
    )
}