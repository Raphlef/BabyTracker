package com.example.babytracker.data.event


import com.example.babytracker.data.DiaperType
import java.util.Date
import java.util.UUID

data class DiaperEvent(
    override val id: String = UUID.randomUUID().toString(),
    override val babyId: String,
    override val timestamp: Date = Date(),
    override val notes: String? = null,
    val diaperType: DiaperType,
    val color: String? = null, // Optional: for poop color
    val consistency: String? = null // Optional: for poop consistency
) : Event()