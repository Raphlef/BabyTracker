package com.example.babytracker.data.event

import com.example.babytracker.data.BreastSide
import com.google.firebase.firestore.IgnoreExtraProperties
import java.util.Date
import java.util.UUID

@IgnoreExtraProperties
data class PumpingEvent(
    override val id: String = UUID.randomUUID().toString(),
    override val babyId: String, // Or could be motherId if tracking for mother
    override val timestamp: Date = Date(),
    override val notes: String? = null,
    val amountMl: Double? = null, // Amount pumped in milliliters
    val durationMinutes: Int? = null,
    val breastSide: BreastSide? = null
) : Event() {

    constructor() : this(
        id             = "",
        babyId         = "",
        timestamp      = Date(),
        notes          = null,
        amountMl       = null,
        durationMinutes= null,
        breastSide     = null
    )
}