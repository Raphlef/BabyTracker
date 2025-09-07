package com.example.babytracker.data

import android.content.Context
import android.util.Log // For logging
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.example.babytracker.data.event.DiaperEvent
import com.example.babytracker.data.event.Event
import com.example.babytracker.data.event.FeedingEvent
import com.example.babytracker.data.event.GrowthEvent
import com.example.babytracker.data.event.PumpingEvent
import com.example.babytracker.data.event.SleepEvent
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.toObject
import com.google.firebase.firestore.toObjects
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.util.Date // Keep this if your Baby class uses it directly
import java.util.UUID
import javax.inject.Inject

private val Context.dataStore by preferencesDataStore(name = "user_prefs")

class FirebaseRepository @Inject constructor(

    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
    private val context: Context
) {
    private val TAG = "FirebaseRepository"

    companion object {
        private const val BABIES_COLLECTION = "babies"
        private const val EVENTS_COLLECTION = "events"
        private val REMEMBER_ME_KEY = booleanPreferencesKey("remember_me")
    }
    // Méthodes d'authentification
    suspend fun login(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password).await()
    }

    suspend fun register(email: String, password: String) {
        auth.createUserWithEmailAndPassword(email, password).await()
    }

    fun isUserLoggedIn(): Boolean {
        return auth.currentUser != null
    }

    fun getCurrentUserId(): String? {
        return auth.currentUser?.uid
    }
    suspend fun saveUserSession() {
        context.dataStore.edit { prefs ->
            prefs[REMEMBER_ME_KEY] = true
        }
    }
    suspend fun clearUserSession() {
        context.dataStore.edit { prefs ->
            prefs.remove(REMEMBER_ME_KEY)
        }
        auth.signOut()
    }

    fun getUserSession(): Flow<Boolean> {
        return context.dataStore.data.map { prefs ->
            prefs[REMEMBER_ME_KEY] ?: false
        }
    }
    // --- Baby Methods ---
    suspend fun addOrUpdateBaby(baby: Baby): Result<Unit> {
        return try {
            val userId = auth.currentUser?.uid
            if (userId == null) {
                return Result.failure(Exception("User not authenticated"))
            }
            // Ensure the current user is part of parentIds or add them
            val updatedParentIds = if (baby.parentIds.contains(userId)) {
                baby.parentIds
            } else {
                baby.parentIds + userId
            }
            val babyWithUser = baby.copy(parentIds = updatedParentIds)

            if (babyWithUser.id.isBlank() || babyWithUser.id == UUID.randomUUID().toString()) { // Heuristic for new baby
                // For a new baby, let Firestore generate the ID or use a pre-generated one if it's truly unique
                db.collection(BABIES_COLLECTION)
                    .document() // Firestore generates ID
                    .set(babyWithUser.copy(id = db.collection(BABIES_COLLECTION).document().id)) // set the generated id back to object
                    .await()
            } else {
                // For updating an existing baby
                db.collection(BABIES_COLLECTION).document(babyWithUser.id).set(babyWithUser).await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error adding/updating baby", e)
            Result.failure(e)
        }
    }

    suspend fun getBabies(): Result<List<Baby>> {
        return try {
            val userId = auth.currentUser?.uid ?: return Result.failure(Exception("User not authenticated"))
            val babiesList = db.collection(BABIES_COLLECTION)
                .whereArrayContains("parentIds", userId)
                .orderBy("createdAt", Query.Direction.DESCENDING) // Optional: order by creation
                .get()
                .await()
                .toObjects<Baby>() // Use reified type for cleaner conversion
            Result.success(babiesList)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching babies", e)
            Result.failure(e)
        }
    }

    suspend fun getBabyById(babyId: String): Result<Baby?> {
        return try {
            val documentSnapshot = db.collection(BABIES_COLLECTION).document(babyId).get().await()
            Result.success(documentSnapshot.toObject<Baby>())
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching baby by ID: $babyId", e)
            Result.failure(e)
        }
    }

    suspend fun deleteBaby(babyId: String): Result<Unit> {
        return try {
            withTimeout(10000L) { // timeout à 10 secondes
                db.collection(BABIES_COLLECTION).document(babyId).delete().await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting baby: $babyId", e)
            Result.failure(e)
        }
    }


    // --- Event Methods ---

    /**
     * Adds any type of event to Firestore.
     * It's crucial that your Event subclasses are properly serializable by Firestore.
     * (i.e., they are data classes with public default constructors and public properties).
     */
    suspend fun addEvent(event: Event): Result<Unit> {
        return try {
            val userId = auth.currentUser?.uid ?: return Result.failure(Exception("User not authenticated"))
            // We assume the event object already has its 'id' (e.g., UUID.randomUUID().toString())
            // and 'babyId' correctly set before calling this function.

            // To help with querying by event type later, we can add a simple string field.
            // This is one way; another is to query based on the presence of specific fields.
            val eventData = mutableMapOf<String, Any?>()
            when (event) {
                is FeedingEvent -> eventData.putAll(event.asMap().plus("eventTypeString" to "FEEDING"))
                is DiaperEvent -> eventData.putAll(event.asMap().plus("eventTypeString" to "DIAPER"))
                is SleepEvent -> eventData.putAll(event.asMap().plus("eventTypeString" to "SLEEP"))
                is GrowthEvent -> eventData.putAll(event.asMap().plus("eventTypeString" to "GROWTH"))
                is PumpingEvent -> eventData.putAll(event.asMap().plus("eventTypeString" to "PUMPING"))
                // Add cases for other event types if you have them
            }

            // Ensure common fields are there
            eventData["id"] = event.id
            eventData["babyId"] = event.babyId
            eventData["timestamp"] = com.google.firebase.Timestamp(event.timestamp) // Convert to Firebase Timestamp
            eventData["notes"] = event.notes
            eventData["userId"] = userId // Store the ID of the user who logged the event

            db.collection(EVENTS_COLLECTION).document(event.id).set(eventData).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error adding event", e)
            Result.failure(e)
        }
    }

    /**
     * Fetches all events for a specific baby, ordered by timestamp.
     * This method will require careful handling on the client-side to cast
     * the generic Map<String, Any> back to specific Event types.
     */
    suspend fun getAllEventsForBaby(babyId: String, limit: Long = 50): Result<List<Event>> {
        return try {
            val userId = auth.currentUser?.uid ?: return Result.failure(Exception("User not authenticated"))
            val eventDocuments = db.collection(EVENTS_COLLECTION)
                .whereEqualTo("babyId", babyId)
                .whereEqualTo("userId", userId) // Ensure user can only fetch their events
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(limit)
                .get()
                .await()

            val eventsList = eventDocuments.mapNotNull { document ->
                // Convert Firestore Timestamps back to java.util.Date for notes and timestamp
                val data = document.data
                val firestoreTimestamp = data["timestamp"] as? com.google.firebase.Timestamp
                val notes = data["notes"] as? String

                when (document.getString("eventTypeString")) {
                    "FEEDING" -> document.toObject<FeedingEvent>()?.copy(
                        timestamp = firestoreTimestamp?.toDate() ?: Date(),
                        notes = notes
                    )
                    "DIAPER" -> document.toObject<DiaperEvent>()?.copy(
                        timestamp = firestoreTimestamp?.toDate() ?: Date(),
                        notes = notes
                    )
                    "SLEEP" -> document.toObject<SleepEvent>()?.copy(
                        timestamp = firestoreTimestamp?.toDate() ?: Date(),
                        notes = notes,
                        // Handle endTime if it's also a Timestamp
                        endTime = (data["endTime"] as? com.google.firebase.Timestamp)?.toDate()
                    )
                    "GROWTH" -> document.toObject<GrowthEvent>()?.copy(
                        timestamp = firestoreTimestamp?.toDate() ?: Date(),
                        notes = notes
                    )
                    "PUMPING" -> document.toObject<PumpingEvent>()?.copy(
                        timestamp = firestoreTimestamp?.toDate() ?: Date(),
                        notes = notes
                    )
                    else -> {
                        Log.w(TAG, "Unknown event type for document: ${document.id}")
                        null
                    }
                }
            }
            Result.success(eventsList)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching all events for baby: $babyId", e)
            Result.failure(e)
        }
    }

    // --- Helper to convert data class to Map for Firestore ---
    // You might need to make these more robust or use a library for complex objects
    private fun FeedingEvent.asMap(): Map<String, Any?> {
        return mapOf(
            // "id" and "babyId" and "timestamp" and "notes" handled in addEvent
            "feedType" to feedType.name,
            "amountMl" to amountMl,
            "durationMinutes" to durationMinutes,
            "breastSide" to breastSide?.name
        )
    }

    private fun DiaperEvent.asMap(): Map<String, Any?> {
        return mapOf(
            "diaperType" to diaperType.name,
            "color" to color,
            "consistency" to consistency
        )
    }

    private fun SleepEvent.asMap(): Map<String, Any?> {
        return mapOf(
            "endTime" to endTime?.let { com.google.firebase.Timestamp(it) }, // Convert to Firebase Timestamp
            "durationMinutes" to durationMinutes
        )
    }

    private fun GrowthEvent.asMap(): Map<String, Any?> {
        return mapOf(
            "weightKg" to weightKg,
            "heightCm" to heightCm,
            "headCircumferenceCm" to headCircumferenceCm
        )
    }

    private fun PumpingEvent.asMap(): Map<String, Any?> {
        return mapOf(
            "amountMl" to amountMl,
            "durationMinutes" to durationMinutes,
            "breastSide" to breastSide?.name
        )
    }


    // --- Methods to fetch specific event types (Optional but can be useful) ---

    suspend fun getFeedingEvents(babyId: String, limit: Long = 20): Result<List<FeedingEvent>> {
        return getEventsByType(babyId, "FEEDING", FeedingEvent::class.java, limit)
    }

    suspend fun getDiaperEvents(babyId: String, limit: Long = 20): Result<List<DiaperEvent>> {
        return getEventsByType(babyId, "DIAPER", DiaperEvent::class.java, limit)
    }

    suspend fun getGrowthEvents(babyId: String, limit: Long = 20): Result<List<GrowthEvent>> {
        return getEventsByType(babyId, "GROWTH", GrowthEvent::class.java, limit)
    }

    suspend fun getSleepEvents(babyId: String, limit: Long = 20): Result<List<SleepEvent>> {
        return try {
            val userId = auth.currentUser?.uid ?: return Result.failure(Exception("User not authenticated"))
            val documents = db.collection(EVENTS_COLLECTION)
                .whereEqualTo("babyId", babyId)
                .whereEqualTo("userId", userId)
                .whereEqualTo("eventTypeString", "SLEEP")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(limit)
                .get()
                .await()

            val events = documents.mapNotNull { doc ->
                val event = doc.toObject(SleepEvent::class.java)
                // Manually convert endTime if it's a Timestamp
                val endTimeTimestamp = doc.getTimestamp("endTime")
                event.copy(
                    timestamp = doc.getTimestamp("timestamp")?.toDate() ?: Date(),
                    endTime = endTimeTimestamp?.toDate()
                )
            }
            Result.success(events)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching sleep events for baby: $babyId", e)
            Result.failure(e)
        }
    }
    // Add similar get...Events for GrowthEvent, PumpingEvent if needed

    private suspend fun <T : Event> getEventsByType(
        babyId: String,
        eventTypeString: String,
        clazz: Class<T>,
        limit: Long
    ): Result<List<T>> {
        return try {
            val userId = auth.currentUser?.uid ?: return Result.failure(Exception("User not authenticated"))
            val documents = db.collection(EVENTS_COLLECTION)
                .whereEqualTo("babyId", babyId)
                .whereEqualTo("userId", userId) // Security rule
                .whereEqualTo("eventTypeString", eventTypeString)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(limit)
                .get()
                .await()

            val events = documents.mapNotNull { doc ->
                val event = doc.toObject(clazz)
                // Re-apply java.util.Date for timestamp and notes if needed, as they might be lost
                // This is a bit tricky with generics and Firestore's toObject.
                // The getAllEventsForBaby approach with manual mapping is more robust for timestamp conversion.
                // For simplicity here, we assume toObject works well enough, or you might need custom deserializers.
                // The base `timestamp` and `notes` are part of the `Event` sealed class.
                // Firestore should map them correctly if the field names match.
                // However, `com.google.firebase.Timestamp` will be the type for `timestamp` field.
                // Need to convert it back to `java.util.Date`.
                val firestoreTimestamp = doc.getTimestamp("timestamp")
                // This part is tricky with generics. A common pattern is to have a common interface
                // for events that need timestamp conversion or handle it in the ViewModel.
                // For now, let's assume direct mapping works or do it manually if it's a known type.
                if (firestoreTimestamp != null) {
                    // This reflection part is a bit of a hack and might not be robust.
                    try {
                        val timestampField = clazz.getDeclaredField("timestamp")
                        timestampField.isAccessible = true
                        timestampField.set(event, firestoreTimestamp.toDate())
                    } catch (e: NoSuchFieldException) { /* ignore */ }
                }
                event
            }
            Result.success(events)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching $eventTypeString events for baby: $babyId", e)
            Result.failure(e)
        }
    }

    suspend fun deleteEvent(eventId: String): Result<Unit> {
        return try {
            db.collection(EVENTS_COLLECTION).document(eventId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting event: $eventId", e)
            Result.failure(e)
        }
    }
}
