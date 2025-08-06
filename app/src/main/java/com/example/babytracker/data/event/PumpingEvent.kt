package com.example.babytracker.data.event

import com.example.babytracker.data.BreastSide
import java.util.Date
import java.util.UUID

data class PumpingEvent(
    override val id: String = UUID.randomUUID().toString(),
    override val babyId: String, // Or could be motherId if tracking for mother
    override val timestamp: Date = Date(),
    override val notes: String? = null,
    val amountMl: Double? = null, // Amount pumped in milliliters
    val durationMinutes: Int? = null,
    val breastSide: BreastSide? = null
) : Event()