package com.example.babytracker.data // Ensure this matches your package structure [1]

import java.util.UUID // For generating unique IDs

// Enum for Gender, if not already defined elsewhere
enum class Gender {
    MALE,
    FEMALE,
    OTHER,
    PREFER_NOT_TO_SAY,
    UNKNOWN
}

data class Baby(
    val id: String = UUID.randomUUID().toString(), // Auto-generated unique ID [1]
    val name: String = "",                         // [1]
    val birthDate: Long = 0L,                      // Timestamp (milliseconds since epoch) [1]
    val gender: Gender = Gender.UNKNOWN,
    val photoUri: String? = null,                  // URI for a profile picture (local or remote)
    val parentIds: List<String> = emptyList(),     // IDs of associated users/parents [1]

    // Optional detailed birth information
    val birthWeightKg: Double? = null,             // Weight in kilograms
    val birthLengthCm: Double? = null,             // Length/Height in centimeters
    val birthHeadCircumferenceCm: Double? = null,  // Head circumference in centimeters
    val birthTime: String? = null,                 // E.g., "10:35 AM" - store as string for simplicity or Long for timestamp

    // Medical Information (Optional)
    val bloodType: String? = null,
    val allergies: List<String> = emptyList(),
    val medicalConditions: List<String> = emptyList(),
    val pediatricianName: String? = null,
    val pediatricianContact: String? = null,

    // Other tracking notes
    val notes: String? = null,                      // General notes about the baby

    // Timestamps for record keeping
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
