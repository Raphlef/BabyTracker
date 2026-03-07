package com.kouloundissa.twinstracker.data // Ensure this matches your package structure [1]

import android.content.Context
import com.google.firebase.firestore.IgnoreExtraProperties
import com.kouloundissa.twinstracker.R
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

    val startDate: Long? = null,
    val endDate: Long? = null,

    val notes: String? = null,

    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

fun buildTreatmentSummary(
    treatment: BabyTreatment,
    context: Context,
    customDrugTypes: List<CustomDrugType>
): String {
    val frequency = when (treatment.frequencyType) {
        TreatmentFrequencyType.HOURLY -> context.getString(R.string.frequency_hourly, treatment.frequencyInterval)
        TreatmentFrequencyType.DAILY ->  context.getString(R.string.frequency_daily, treatment.frequencyInterval)
        TreatmentFrequencyType.WEEKLY -> context.getString(R.string.frequency_weekly, treatment.frequencyInterval)
    }
    val drugName =
        if (treatment.drugType == DrugType.CUSTOM) {
            customDrugTypes.find { it.id == treatment.customDrugTypeId }?.name
                ?: treatment.drugType.getDisplayName(context)
        } else {
            treatment.drugType.getDisplayName(context)
        }
    return "${drugName} – $frequency"
}
object TreatmentScheduler {

    fun computeNextDose(
        treatment: BabyTreatment,
        lastEvent: DrugsEvent?
    ): Long? {

        val now = System.currentTimeMillis()

        if (treatment.endDate != null && now > treatment.endDate) {
            return null
        }

        val baseTime = lastEvent?.timestamp?.time ?: treatment.startDate?: return null
        if (baseTime > now) return null

        val intervalMillis = treatment.frequencyInterval * (treatment.frequencyType.millisPerInterval ?: 0L)
        if (intervalMillis <= 0) return null

        return baseTime + intervalMillis
    }
}
fun Baby.activeTreatments(): List<BabyTreatment> {
    val now = System.currentTimeMillis()

    return treatments.filter { treatment ->
        // Active si startDate est passé (null = considéré comme déjà commencé)
        val hasStarted = treatment.startDate?.let { now >= it } ?: true

        // Active si pas d'endDate ou endDate pas encore passé (null = indéfini/durant)
        val notEnded = treatment.endDate?.let { now <= it } ?: true

        hasStarted && notEnded
    }
}
fun findLastDrugEvent(
    events: List<Event>,
    treatment: BabyTreatment
): DrugsEvent? {

    return events
        .filterIsInstance<DrugsEvent>()
        .filter {
            it.drugType == treatment.drugType &&
                    it.customDrugTypeId == treatment.customDrugTypeId
        }
        .maxByOrNull { it.timestamp.time }
}
fun Baby.nextTreatmentsToTake(events: List<Event>): List<BabyTreatment> {
    val now = System.currentTimeMillis()
    return activeTreatments().filter { treatment ->
        // Réutilise computeNextDose existant
        val lastEvent = findLastDrugEvent(events, treatment)
        val nextDoseTime = TreatmentScheduler.computeNextDose(treatment, lastEvent)
        nextDoseTime != null && nextDoseTime <= now * 1.1  // Due ou overdue (marge 10%)
    }
}
fun Baby.nextTreatmentToTake(events: List<Event>): BabyTreatment? {
    return nextTreatmentsToTake(events).minByOrNull { treatment ->
        findLastDrugEvent(events, treatment)?.let { TreatmentScheduler.computeNextDose(treatment, it) } ?: Long.MAX_VALUE
    }
}
