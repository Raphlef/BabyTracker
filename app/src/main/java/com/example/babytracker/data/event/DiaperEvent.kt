package com.example.babytracker.data.event


import com.example.babytracker.data.DiaperType
import com.example.babytracker.data.PoopColor
import com.example.babytracker.data.PoopConsistency
import com.google.firebase.firestore.IgnoreExtraProperties
import java.util.Date
import java.util.UUID

@IgnoreExtraProperties
data class DiaperEvent(
    override val id: String = UUID.randomUUID().toString(),
    override val babyId: String,
    override val timestamp: Date = Date(),
    override val notes: String? = null,
    val diaperType: DiaperType,
    val poopColor: PoopColor? = null,
    val poopConsistency: PoopConsistency? = null
) : Event() {

    constructor() : this(
        id              = "",
        babyId          = "",
        timestamp       = Date(),
        notes           = null,
        diaperType      = DiaperType.DRY,
        poopColor       = null,
        poopConsistency = null
    )
}