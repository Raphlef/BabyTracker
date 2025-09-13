package com.example.babytracker.data.event

import com.google.firebase.firestore.IgnoreExtraProperties
import java.util.Date
import java.util.UUID

@IgnoreExtraProperties
// Base sealed class for all events
sealed class Event {
    abstract val id: String
    abstract val babyId: String // ID of the baby this event belongs to
    abstract val timestamp: Date
    abstract val notes: String? // Optional notes for any event

}
