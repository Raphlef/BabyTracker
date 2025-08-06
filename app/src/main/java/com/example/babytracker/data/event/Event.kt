package com.example.babytracker.data.event

import java.util.Date
import java.util.UUID

// Base sealed class for all events
sealed class Event {
    abstract val id: String
    abstract val babyId: String // ID of the baby this event belongs to
    abstract val timestamp: Date
    abstract val notes: String? // Optional notes for any event
}
