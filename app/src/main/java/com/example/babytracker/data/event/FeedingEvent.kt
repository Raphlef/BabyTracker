package com.example.babytracker.data.event

import java.util.Date
import java.util.UUID

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