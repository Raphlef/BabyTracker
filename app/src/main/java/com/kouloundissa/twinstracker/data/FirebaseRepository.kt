package com.kouloundissa.twinstracker.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log // For logging
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import com.google.firebase.firestore.toObject
import com.google.firebase.firestore.toObjects
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject


private val Context.dataStore by preferencesDataStore(name = "user_prefs")

class FirebaseRepository @Inject constructor(

    val auth: FirebaseAuth,
    val db: FirebaseFirestore,
    private val context: Context
) {
    private val TAG = "FirebaseRepository"

   companion object {
        private const val USERS_COLLECTION = "users"
        private const val BABIES_COLLECTION = "babies"
        private const val EVENTS_COLLECTION = "events"
        private const val FAMILIES_COLLECTION = "families"
        private val REMEMBER_ME_KEY = booleanPreferencesKey("remember_me")
    }

    // Méthodes d'authentification
    suspend fun login(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password).await()
    }

    suspend fun register(email: String, password: String) {
        val normalizedEmail = email.trim().lowercase(Locale.getDefault())
        auth.createUserWithEmailAndPassword(normalizedEmail, password).await()
        val userId = auth.currentUser?.uid
            ?: throw IllegalStateException("Impossible de récupérer l’UID utilisateur")
        val user = User(
            id = userId,
            email = normalizedEmail,
            displayName = "",            // Peut être rempli via un formulaire ultérieur
            photoUrl = null,
            theme = Theme.SYSTEM,
            notificationsEnabled = true,
            locale = Locale.getDefault().language
        )
        db.collection(USERS_COLLECTION)
            .document(userId)
            .set(user)
            .await()
    }

    suspend fun getCurrentUserProfile(): User {
        val userId = auth.currentUser?.uid
            ?: throw IllegalStateException("Utilisateur non authentifié")
        val doc = db.collection(USERS_COLLECTION).document(userId).get().await()
        return doc.toObject(User::class.java)
            ?: throw IllegalStateException("Profil utilisateur introuvable")
    }

    suspend fun updateUserProfile(updates: Map<String, Any?>) {
        val userId = auth.currentUser?.uid
            ?: throw IllegalStateException("Utilisateur non authentifié")
        val merged = updates + ("updatedAt" to System.currentTimeMillis())
        db.collection(USERS_COLLECTION)
            .document(userId)
            .update(merged)
            .await()
    }
    fun isUserLoggedIn(): Boolean {
        return auth.currentUser != null
    }

    fun getCurrentUserEmail(): String? {
        return auth.currentUser?.email
    }

    // Lit la préférence rememberMe en DataStore (suspend)
    suspend fun isRemembered(): Boolean {
        return context.dataStore.data.map { prefs ->
            prefs[REMEMBER_ME_KEY] ?: false
        }.first()
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

    /**
     * Uploads an image URI to Firebase Storage under a common “photos/{entityType}/{entityId}/” path
     * and updates the corresponding Firestore document with the resulting download URL.
     *
     * Can be used for any entity collection (e.g. “babies”, “events”, “users”).
     */
    suspend fun addPhotoToEntity(
        entityType: String,
        entityId: String,
        uri: Uri
    ): String = withContext(Dispatchers.IO) {
        val TAG = "FirebaseRepository"

        val userId = auth.currentUser?.uid
            ?: throw IllegalStateException("User not authenticated")

        Log.d(TAG, "Starting photo upload for entityType=$entityType, entityId=$entityId, userId=$userId")

        // 1. Persistable URI permission if needed (for ACTION_OPEN_DOCUMENT)
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            Log.d(TAG, "Persistable URI permission granted for $uri")
        } catch (e: SecurityException) {
            Log.w(TAG, "Persistable URI permission already granted or failed: ${e.message}")
            // May already have permission; ignoring
        }

        // 2. Upload to Storage under photos/{entityType}/{userId}/{entityId}.jpg
        val storageRef = FirebaseStorage.getInstance().reference
            .child("photos")
            .child(entityType)
            .child(userId)
            .child("$entityId.jpg")

        Log.d(TAG, "Uploading photo to Storage at path: ${storageRef.path}")

        context.contentResolver.openInputStream(uri).use { stream ->
            requireNotNull(stream) { "Unable to open image URI: $uri" }
            storageRef.putStream(stream).await()
            Log.d(TAG, "Upload complete for $uri")
        }

        // 3. Fetch download URL
        val downloadUrl = storageRef.downloadUrl.await().toString()
        Log.d(TAG, "Download URL fetched: $downloadUrl")

        // 4. Update Firestore document with the field "photoUrl"
        db.collection(entityType)
            .document(entityId)
            .update(
                mapOf(
                    "photoUrl" to downloadUrl,
                    "updatedAt" to System.currentTimeMillis()
                )
            )
            .await()

        Log.d(TAG, "Firestore document updated with photoUrl for $entityType/$entityId")

        downloadUrl
    }

    suspend fun deletePhotoFromEntity(
        entityType: String,
        entityId: String
    ) = withContext(Dispatchers.IO) {
        val userId = auth.currentUser?.uid
            ?: throw IllegalStateException("User not authenticated")

        // Reference to the photo in Storage
        val photoRef = FirebaseStorage.getInstance().reference
            .child("photos")
            .child(entityType)
            .child(userId)
            .child("$entityId.jpg")

        // Delete photo from Storage
        try {
            photoRef.delete().await()
        } catch (e: Exception) {
            // Optionally log error but don’t fail if photo missing
            if (e is com.google.firebase.storage.StorageException && e.errorCode == 404) {
                // Not found is OK, photo already deleted or never uploaded
            } else {
                throw e
            }
        }

        // Remove photoUrl from Firestore document
        db.collection(entityType)
            .document(entityId)
            .update(
                mapOf(
                    "photoUrl" to FieldValue.delete(),
                    "updatedAt" to System.currentTimeMillis()
                )
            )
            .await()
    }
    // --- Baby Methods ---
    suspend fun addOrUpdateBaby(baby: Baby): Result<Unit> = runCatching {
        val userId = auth.currentUser?.uid
            ?: throw IllegalStateException("User not authenticated")

        // 1. Pre-check for duplicate name under this user
        val existing = db.collection(BABIES_COLLECTION)
            .whereEqualTo("name", baby.name.trim())
            .whereArrayContains("parentIds", userId)
            .get()
            .await()
            .toObjects<Baby>()

        // If adding new (blank id) or renaming to an existing other baby, error out
        val isNew = baby.id.isBlank()
        val conflict = existing.any { it.id != baby.id }
        if (conflict) {
            throw IllegalStateException("Un bébé portant ce nom existe déjà.")
        }

        // 2. Prepare baby object with ensured parentIds
        val babyWithUser = baby.copy(
            id = baby.id,
            parentIds = (baby.parentIds + userId).distinct()
        )

        // 3. Determine document ref
        val docRef = if (isNew) {
            db.collection(BABIES_COLLECTION).document()
        } else {
            db.collection(BABIES_COLLECTION).document(babyWithUser.id)
        }

        val finalBaby = babyWithUser.copy(id = docRef.id)
        // 4. Write with the generated or provided ID
        docRef.set(finalBaby).await()

        // 5. For new baby, add its ID to every family of the user
        // 4. If new, add baby to all user families
        if (isNew) {
            getFamilies().onSuccess { families ->
                families.forEach { family ->
                    // Append baby ID if not already present
                    if (finalBaby.id !in family.babyIds) {
                        val updated = family.copy(
                            babyIds = (family.babyIds + finalBaby.id).distinct(),
                            updatedAt = System.currentTimeMillis()
                        )
                        addOrUpdateFamily(updated)
                    }
                }
            }
        }
    }

    fun streamBabies(): Flow<List<Baby>> {
        val userId = auth.currentUser?.uid
            ?: throw IllegalStateException("User not authenticated")
        return db.collection(BABIES_COLLECTION)
            .whereArrayContains("parentIds", userId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .snapshots()                              // écoute en temps réel
            .map { snapshot ->
                snapshot.toObjects(Baby::class.java)
            }
    }

    suspend fun getBabies(): Result<List<Baby>> {
        return try {
            val userId =
                auth.currentUser?.uid ?: return Result.failure(Exception("User not authenticated"))
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

    suspend fun getFirstBabyId(): String? {
        val userId = auth.currentUser?.uid
            ?: throw IllegalStateException("User not authenticated")

        val snapshot = db.collection(BABIES_COLLECTION)
            .whereArrayContains("parentIds", userId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .await()

        val babies = snapshot.toObjects<Baby>()
        return babies.firstOrNull()?.id
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

    suspend fun deleteBabyAndEvents(babyId: String): Result<Unit> = runCatching {
        val batch = db.batch()
        // Supprimer le bébé
        val babyRef = db.collection(BABIES_COLLECTION).document(babyId)
        batch.delete(babyRef)
        // Rechercher tous les événements liés
        val eventsSnapshot = db.collection(EVENTS_COLLECTION)
            .whereEqualTo("babyId", babyId)
            .get()
            .await()
        eventsSnapshot.documents.forEach { doc ->
            batch.delete(doc.reference)
        }
        // Commit batch single write
        batch.commit().await()
    }
    suspend fun findUserIdByEmail(email: String): String? {
        val normalizedEmail = email.trim().lowercase(Locale.getDefault())
        val snapshot = db.collection(USERS_COLLECTION)
            .whereEqualTo("email", email)
            .limit(1)
            .get()
            .await()
        return snapshot.documents.firstOrNull()?.id
    }

    /**
     * Invite un parent à un bébé en ajoutant son userId
     * à la liste parentsIds du document Baby.
     */
    suspend fun addParentToBaby(babyId: String, parentEmail: String) {
        // 1. Trouver l'utilisateur
        val userId = findUserIdByEmail(parentEmail)
            ?: throw IllegalArgumentException("Aucun utilisateur avec cet email")
        // 2. Référence du document Baby
        val babyRef = db.collection(BABIES_COLLECTION).document(babyId)
        // 3. Transaction pour ajouter sans écraser
        db.runTransaction { tx ->
            val snapshot = tx.get(babyRef)
            // Récupérer ou initialiser la liste
            val current = snapshot.get("parentIds") as? List<String> ?: emptyList()
            if (!current.contains(userId)) {
                tx.update(
                    babyRef,
                    "parentIds", current + userId,
                    "updatedAt", System.currentTimeMillis()
                )
            }
        }.await()
    }
    /**
     * Récupère la liste des utilisateurs correspondant aux IDs fournis.
     * Retourne une liste vide si ids est vide.
     */
    suspend fun getUsersByIds(ids: List<String>): List<User> {
        if (ids.isEmpty()) return emptyList()
        // Firestore limite whereIn à 10 éléments.
        return ids.chunked(10).flatMap { chunk ->
            db.collection(USERS_COLLECTION)
                .whereIn(FieldPath.documentId(), chunk)
                .get()
                .await()
                .toObjects(User::class.java)
        }
    }
    // --- Event Methods ---

    suspend fun updateEvent(eventId: String, event: Event): Result<Unit> = runCatching {
        val userId = auth.currentUser?.uid
            ?: throw IllegalStateException("User not authenticated")

        // Leverage Event.toMap() for *all* fields
        val data = event.toMap().toMutableMap().apply {
            put("userId", userId)
        }

        db.collection(EVENTS_COLLECTION)
            .document(eventId)
            .set(data, SetOptions.merge())
            .await()
    }

    /**
     * Adds any type of event to Firestore.
     * It's crucial that your Event subclasses are properly serializable by Firestore.
     * (i.e., they are data classes with public default constructors and public properties).
     */
    suspend fun addEvent(event: Event): Result<Unit> {
        return try {
            val userId =
                auth.currentUser?.uid ?: return Result.failure(Exception("User not authenticated"))

            val data = event.toMap().toMutableMap().apply {
                put("userId", userId)
            }

            db.collection(EVENTS_COLLECTION).document(event.id).set(data).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error adding event", e)
            Result.failure(e)
        }
    }

    fun streamEventsForBaby(babyId: String): Flow<List<Event>> {
        val userId = auth.currentUser?.uid
            ?: throw IllegalStateException("User not authenticated")

        return db.collection(EVENTS_COLLECTION)
            .whereEqualTo("babyId", babyId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { it.toEvent() }
            }
    }

    /**
     * Fetches all events for a specific baby, ordered by timestamp.
     * This method will require careful handling on the client-side to cast
     * the generic Map<String, Any> back to specific Event types.
     */
    suspend fun getAllEventsForBaby(
        babyId: String,
        limit: Long = 50
    ): Result<List<Event>> = try {
        val userId = auth.currentUser
            ?.uid
            ?: return Result.failure(Exception("User not authenticated"))

        var query = db.collection(EVENTS_COLLECTION)
            .whereEqualTo("babyId", babyId)
            .orderBy("timestamp", Query.Direction.DESCENDING)

        if (limit > 0) query = query.limit(limit)

        val snapshots = query.get().await().documents

        val events = snapshots.mapNotNull { it.toEvent() }

        Result.success(events)
    } catch (e: Exception) {
        Log.e(TAG, "Error fetching events for baby $babyId", e)
        Result.failure(e)
    }



    suspend fun getLastGrowthEvent(babyId: String): Result<GrowthEvent?> = runCatching {
        val userId = auth.currentUser?.uid
            ?: throw IllegalStateException("User not authenticated")

        val snapshot = db.collection(EVENTS_COLLECTION)
            .whereEqualTo("babyId", babyId)
            .whereEqualTo("eventTypeString", "GROWTH")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .await()

        snapshot.toObjects(GrowthEvent::class.java).firstOrNull()
    }

    suspend fun getGrowthEventsInRange(
        babyId: String,
        startDate: Date,
        endDate: Date
    ): Result<List<GrowthEvent>> = runCatching {
        val userId = auth.currentUser?.uid
            ?: throw IllegalStateException("User not authenticated")

        // Bornes : si même jour, on cherche entre min(hour) et max(hour)
        val calStart = Calendar.getInstance().apply {
            time = startDate
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val calEnd = Calendar.getInstance().apply {
            time = endDate
            set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
        }

        val tsStart = com.google.firebase.Timestamp(calStart.time)
        val tsEnd = com.google.firebase.Timestamp(calEnd.time)

        val query = db.collection(EVENTS_COLLECTION)
            .whereEqualTo("babyId", babyId)
            .whereEqualTo("eventTypeString", "GROWTH")
            .whereGreaterThanOrEqualTo("timestamp", tsStart)
            .whereLessThanOrEqualTo("timestamp", tsEnd)
            .orderBy("timestamp", Query.Direction.ASCENDING)

        // Si on veut un seul résultat (même journée), on peut limiter à 1
        if (calStart.time == calEnd.time) {
            query.limit(1)
        }

        val snapshot = query.get().await()
        snapshot.toObjects(GrowthEvent::class.java)
    }

    suspend fun getOneGrowthEventInRange(
        babyId: String,
        start: Date,
        end: Date
    ): Result<GrowthEvent?> = runCatching {
        val userId = auth.currentUser?.uid ?: throw IllegalStateException("Not authenticated")
        val snapshot = db.collection(EVENTS_COLLECTION)
            .whereEqualTo("babyId", babyId)
            .whereEqualTo("eventTypeString", "GROWTH")
            .whereGreaterThanOrEqualTo("timestamp", com.google.firebase.Timestamp(start))
            .whereLessThanOrEqualTo("timestamp", com.google.firebase.Timestamp(end))
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .limit(1)
            .get()
            .await()
        snapshot.toObjects(GrowthEvent::class.java).firstOrNull()
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


    private suspend fun <T : Event> getEventsByType(
        babyId: String,
        eventTypeString: String,
        clazz: Class<T>,
        limit: Long
    ): Result<List<T>> {
        return try {
            val userId = auth.currentUser?.uid
                ?: return Result.failure(Exception("User not authenticated"))
            // Construction de la query sans .limit() par défaut
            var query = db.collection(EVENTS_COLLECTION)
                .whereEqualTo("babyId", babyId)
                .whereEqualTo("eventTypeString", eventTypeString)
                .orderBy("timestamp", Query.Direction.DESCENDING)
            // Appliquer la limite si elle est strictement positive
            if (limit > 0) {
                query = query.limit(limit)
            }
            val documents = query.get().await()

            val events = documents.mapNotNull { doc ->
                val event = doc.toObject(clazz)
                val firestoreTimestamp = doc.getTimestamp("timestamp")
                if (firestoreTimestamp != null) {
                    try {
                        val timestampField = clazz.getDeclaredField("timestamp")
                        timestampField.isAccessible = true
                        timestampField.set(event, firestoreTimestamp.toDate())
                    } catch (_: NoSuchFieldException) {
                    }
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
