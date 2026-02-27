package com.kouloundissa.twinstracker.data // Ensure this matches your package structure [1]

import android.content.Context
import com.google.firebase.firestore.IgnoreExtraProperties
import java.util.UUID


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
    val treatments: List<BabyTreatment> = emptyList(),
    val pediatricianName: String? = null,
    val pediatricianPhone: String? = null,

    // Other tracking notes
    val notes: String? = null,                      // General notes about the baby

    // Timestamps for record keeping
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@IgnoreExtraProperties
data class BabyTreatment(
    val id: String = UUID.randomUUID().toString(),
    val drugType: DrugType = DrugType.PARACETAMOL,
    val customDrugTypeId: String? = null,

    val dosage: String? = null, // ex: "5 ml", "1 comprimé"

    val frequencyType: TreatmentFrequencyType = TreatmentFrequencyType.DAILY,
    val frequencyInterval: Int = 1, // ex: every 8h → 8, every 2 days → 2

    val startDate: Long = System.currentTimeMillis(),
    val endDate: Long? = null,

    val notes: String? = null,

    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

fun buildTreatmentSummary(t: BabyTreatment, context: Context): String {
    val frequency = when (t.frequencyType) {
        TreatmentFrequencyType.HOURLY ->  "Every ${t.frequencyInterval}h"
        TreatmentFrequencyType.DAILY -> "Every ${t.frequencyInterval} day(s)"
        TreatmentFrequencyType.WEEKLY -> "Every ${t.frequencyInterval} week(s)"
    }

    return "${t.drugType.getDisplayName(context)} – $frequency"
}
