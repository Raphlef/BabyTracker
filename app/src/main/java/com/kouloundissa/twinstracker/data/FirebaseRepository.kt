package com.kouloundissa.twinstracker.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log // For logging
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Filter
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import com.google.firebase.firestore.toObject
import com.google.firebase.firestore.toObjects
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.kouloundissa.twinstracker.presentation.viewmodel.FamilyViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlin.runCatching


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
    suspend fun sendPasswordReset(email: String) {
        auth.sendPasswordResetEmail(email).await()
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

    suspend fun signInWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential).await()
    }

    suspend fun getCurrentUserProfile(): User {
        val userId = auth.currentUser?.uid
            ?: throw IllegalStateException("Utilisateur non authentifié")
        val doc = db.collection(USERS_COLLECTION).document(userId).get().await()
        return doc.toObject(User::class.java)
            ?: throw IllegalStateException("Profil utilisateur introuvable")
    }

    suspend fun getUserProfileById(userId: String): User {
        val doc = db.collection(USERS_COLLECTION).document(userId).get().await()
        return doc.toObject(User::class.java)
            ?: throw IllegalStateException("Profil utilisateur introuvable pour $userId")
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
    suspend fun getUserById(userId: String): User? {
        return try {
            db.collection(USERS_COLLECTION)
                .document(userId)
                .get()
                .await()
                .toObject(User::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching user $userId", e)
            null
        }
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

        Log.d(
            TAG,
            "Starting photo upload for entityType=$entityType, entityId=$entityId, userId=$userId"
        )

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

        val storageRef = FirebaseStorage.getInstance().reference
            .child("photos")
            .child(entityType)
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

//        // 4. Update Firestore document with the field "photoUrl"
//        db.collection(entityType)
//            .document(entityId)
//            .update(
//                mapOf(
//                    "photoUrl" to downloadUrl,
//                    "updatedAt" to System.currentTimeMillis()
//                )
//            )
//            .await()

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
            .child("$entityId.jpg")

        // Delete photo from Storage
        try {
            photoRef.delete().await()
            Log.d("PhotoDeletion", "Photo deleted successfully: $entityType/$entityId")
        } catch (e: StorageException) {
            if (e.errorCode == StorageException.ERROR_OBJECT_NOT_FOUND) {
                // File doesn't exist - this is fine, just continue silently
                Log.d("PhotoDeletion", "Photo not found, skipping: $entityType/$entityId")
            } else {
                // Other errors (permission denied, network, etc.) - still throw
                throw e
            }
        }

        // Remove photoUrl from Firestore document
        try {
            db.collection(entityType)
                .document(entityId)
                .update(
                    mapOf(
                        "photoUrl" to FieldValue.delete(),
                        "updatedAt" to System.currentTimeMillis()
                    )
                )
                .await()
            Log.d("PhotoDeletion", "PhotoUrl field removed from Firestore: $entityType/$entityId")
        } catch (e: FirebaseFirestoreException) {
            if (e.code == FirebaseFirestoreException.Code.NOT_FOUND) {
                // Document doesn't exist - this is fine, just continue silently
                Log.d("PhotoDeletion", "Document not found, skipping: $entityType/$entityId")
            } else {
                // Other Firestore errors - still throw
                throw e
            }
        }
    }

    // --- Baby Methods ---
    suspend fun addOrUpdateBaby(baby: Baby): Result<Baby> = runCatching {
        val userId = auth.currentUser?.uid
            ?: throw IllegalStateException("User not authenticated")

        // 1. Pre-check for duplicate name across user's families
        val families = getCurrentUserFamilies().getOrThrow()
        val allBabyIds = families.flatMap { it.babyIds }.distinct()

        if (allBabyIds.isNotEmpty()) {
            val existing = allBabyIds.chunked(10).flatMap { chunk ->
                db.collection(BABIES_COLLECTION)
                    .whereIn(FieldPath.documentId(), chunk)
                    .whereEqualTo("name", baby.name.trim())
                    .get()
                    .await()
                    .toObjects<Baby>()
            }


            val conflict = existing.any { it.id != baby.id }
            if (conflict) {
                throw IllegalStateException("Un bébé portant ce nom existe déjà.")
            }
        }
        val existingBaby = allBabyIds.find { it == baby.id }
        val isNew =  existingBaby == null
        // 2. Determine document ref
        val docRef = if (isNew) {
            db.collection(BABIES_COLLECTION).document()
        } else {
            db.collection(BABIES_COLLECTION).document(baby.id)
        }

        val finalBaby = baby.copy(id = docRef.id)

        // 3. Write baby document (no parentIds needed)
        docRef.set(finalBaby).await()

        // 4. For new baby, add its ID to every family of the user
        if (isNew) {
            families.forEach { family ->
                if (finalBaby.id !in family.babyIds) {
                    val updated = family.copy(
                        babyIds = (family.babyIds + finalBaby.id).distinct(),
                        updatedAt = System.currentTimeMillis()
                    )
                    addOrUpdateFamily(updated)
                }
            }
        }
        finalBaby
    }

    fun streamBabies(): Flow<List<Baby>> {
        val currentUserId = auth.currentUser?.uid
            ?: throw IllegalStateException("User not authenticated")

        return streamFamilies() // Reuse your existing streamFamilies method
            .flatMapLatest { families ->
                val allBabyIds = families.flatMap { it.babyIds }.distinct()

                if (allBabyIds.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    // Stream babies based on the family baby IDs
                    streamBabiesByIds(allBabyIds)
                }
            }
            .distinctUntilChanged()
    }
    private fun streamBabiesByIds(babyIds: List<String>): Flow<List<Baby>> {
        if (babyIds.isEmpty()) {
            return flowOf(emptyList())
        }

        return callbackFlow {
            val listeners = mutableListOf<ListenerRegistration>()
            val babiesMap = mutableMapOf<String, Baby>()

            // Chunk baby IDs to respect Firestore's whereIn limit of 10
            babyIds.chunked(10).forEach { chunk ->
                val listener = db.collection(BABIES_COLLECTION)
                    .whereIn(FieldPath.documentId(), chunk)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            close(error)
                            return@addSnapshotListener
                        }

                        // Update babies map
                        snapshot?.documents?.forEach { doc ->
                            doc.toObject(Baby::class.java)?.let { baby ->
                                babiesMap[baby.id] = baby
                            }
                        }

                        // Remove babies that are no longer in the chunk
                        val chunkIds = chunk.toSet()
                        babiesMap.keys.retainAll { it in chunkIds || it in babyIds }

                        // Emit current list sorted by creation date
                        val sortedBabies = babiesMap.values
                            .filter { it.id in babyIds }
                            .sortedByDescending { it.createdAt }

                        trySend(sortedBabies)
                    }
                listeners.add(listener)
            }

            awaitClose {
                listeners.forEach { it.remove() }
            }
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

    suspend fun removeBabyFromAllFamilies(babyId: String) = withContext(Dispatchers.IO) {
        val familiesSnapshot = db.collection(FAMILIES_COLLECTION)
            .whereArrayContains("babyIds", babyId)
            .get()
            .await()

        val batch = db.batch()
        familiesSnapshot.documents.forEach { doc ->
            batch.update(
                doc.reference,
                "babyIds",
                FieldValue.arrayRemove(babyId),
                "updatedAt",
                System.currentTimeMillis()
            )
        }
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
    suspend fun getEventById(eventId: String): Result<Event?> {
        return try {
            val documentSnapshot = db.collection(EVENTS_COLLECTION).document(eventId).get().await()
            val event = documentSnapshot.toEvent()
            Result.success(event)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching event by ID: $eventId", e)
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
