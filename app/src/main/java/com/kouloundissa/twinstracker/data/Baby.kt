package com.kouloundissa.twinstracker.data // Ensure this matches your package structure [1]

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import com.google.firebase.firestore.IgnoreExtraProperties
import java.util.UUID // For generating unique IDs

// Enum for Gender, if not already defined elsewhere
@IgnoreExtraProperties
enum class Gender(val icon: ImageVector, val displayName: String, val emoji: String) {
    MALE(Icons.Filled.Male, "Garçon","👶🏻"),
    FEMALE(Icons.Filled.Female, "Fille","👧🏻"),
    OTHER(Icons.Filled.Transgender, "Autre","🧒🏻"),
    PREFER_NOT_TO_SAY(Icons.Filled.VisibilityOff, "❔","👶"),
    UNKNOWN(Icons.AutoMirrored.Filled.HelpOutline, "❔","👶")
}
enum class BloodType(val icon: ImageVector) {
    A(Icons.Filled.Bloodtype),
    B(Icons.Filled.Bloodtype),
    AB(Icons.Filled.Bloodtype),
    O(Icons.Filled.Bloodtype),
    UNKNOWN(Icons.AutoMirrored.Filled.HelpOutline)
}
@IgnoreExtraProperties
data class Baby(
    val id: String = UUID.randomUUID().toString(), // Auto-generated unique ID [1]
    val name: String = "",                         // [1]
    val birthDate: Long = 0L,                      // Timestamp (milliseconds since epoch) [1]
    val gender: Gender = Gender.UNKNOWN,

    val photoUrl: String? = null,                  // URI for a profile picture (local or remote)
    val parentIds: List<String> = emptyList(),     // IDs of associated users/parents [1]

    // Optional detailed birth information
    val birthWeightKg: Double? = null,             // Weight in kilograms
    val birthLengthCm: Double? = null,             // Length/Height in centimeters
    val birthHeadCircumferenceCm: Double? = null,  // Head circumference in centimeters
    val birthTime: String? = null,                 // E.g., "10:35 AM" - store as string for simplicity or Long for timestamp

    // Medical Information (Optional)
    val bloodType: BloodType = BloodType.UNKNOWN,
    val allergies: List<String> = emptyList(),
    val medicalConditions: List<String> = emptyList(),
    val pediatricianContact: String? = null,

    // Other tracking notes
    val notes: String? = null,                      // General notes about the baby

    // Timestamps for record keeping
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
