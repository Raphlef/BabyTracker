package com.example.babytracker.data.event

import java.util.Date
import java.util.UUID

data class GrowthEvent(
    override val id: String = UUID.randomUUID().toString(),
    override val babyId: String,
    override val timestamp: Date = Date(), // Date of measurement
    override val notes: String? = null,
    val weightKg: Double? = null, // Weight in kilograms
    val heightCm: Double? = null, // Height/Length in centimeters
    val headCircumferenceCm: Double? = null // Head circumference in centimeters
) : Event()