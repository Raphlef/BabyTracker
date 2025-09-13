package com.example.babytracker.data.event

import com.google.firebase.firestore.IgnoreExtraProperties
import java.util.Date
import java.util.UUID

@IgnoreExtraProperties
data class GrowthEvent(
    override val id: String = UUID.randomUUID().toString(),
    override val babyId: String,
    override val timestamp: Date = Date(), // Date of measurement
    override val notes: String? = null,
    val weightKg: Double? = null, // Weight in kilograms
    val heightCm: Double? = null, // Height/Length in centimeters
    val headCircumferenceCm: Double? = null // Head circumference in centimeters
) : Event(){

    // Constructeur sans argument requis par Firestore
    constructor() : this(
        id = "",
        babyId = "",
        timestamp = Date(),
        notes = null,
        weightKg = null,
        heightCm = null,
        headCircumferenceCm = null
    )
}