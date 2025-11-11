package com.kouloundissa.twinstracker.data.Firestore

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import com.google.firebase.storage.FirebaseStorage
import com.kouloundissa.twinstracker.data.AnalysisSnapshot
import com.kouloundissa.twinstracker.data.Baby
import com.kouloundissa.twinstracker.data.DailyAnalysis
import com.kouloundissa.twinstracker.data.DiaperEvent
import com.kouloundissa.twinstracker.data.DiaperType
import com.kouloundissa.twinstracker.data.Event
import com.kouloundissa.twinstracker.data.EventType
import com.kouloundissa.twinstracker.data.FeedingEvent
import com.kouloundissa.twinstracker.data.Firestore.FirestoreTimestampUtils.formatDateForGrouping
import com.kouloundissa.twinstracker.data.Firestore.FirestoreTimestampUtils.toLocalDate
import com.kouloundissa.twinstracker.data.GrowthEvent
import com.kouloundissa.twinstracker.data.GrowthMeasurement
import com.kouloundissa.twinstracker.data.PumpingEvent
import com.kouloundissa.twinstracker.data.SleepEvent
import com.kouloundissa.twinstracker.data.Theme
import com.kouloundissa.twinstracker.data.User
import com.kouloundissa.twinstracker.data.toEvent
import com.kouloundissa.twinstracker.presentation.analysis.AnalysisRange
import com.kouloundissa.twinstracker.ui.components.AnalysisFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject


class FirebaseRepository @Inject constructor(
    val auth: FirebaseAuth,
    val db: FirebaseFirestore,
    private val context: Context
) {
    private val TAG = "FirebaseRepository"

    // Helper instances
    public val authHelper = FirebaseAuthHelper(auth)
    val queryHelper = FirestoreQueryHelper(db)
    private val photoHelper = FirebasePhotoHelper(
        FirebaseStorage.getInstance(),
        db,
        context,
        authHelper
    )

    // ===== AUTHENTICATION =====
    suspend fun login(email: String, password: String) {
        FirebaseValidators.validateEmail(email)
        val normalized = FirebaseValidators.normalizeEmail(email)
        auth.signInWithEmailAndPassword(normalized, password).await()
    }

    suspend fun sendPasswordReset(email: String) {
        FirebaseValidators.validateEmail(email)
        auth.sendPasswordResetEmail(email).await()
    }

    suspend fun register(email: String, password: String) {
        FirebaseValidators.validateEmail(email)
        val normalized = FirebaseValidators.normalizeEmail(email)

        auth.createUserWithEmailAndPassword(normalized, password).await()
        val userId = authHelper.getCurrentUserId()

        val user = User(
            id = userId,
            email = normalized,
            displayName = "",
            photoUrl = null,
            theme = Theme.SYSTEM,
            notificationsEnabled = true,
            locale = Locale.getDefault().language
        )

        db.collection(FirestoreConstants.Collections.USERS)
            .document(userId)
            .set(user)
            .await()
    }

    suspend fun signInWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential).await()
    }

    // ===== USER PROFILE =====
    suspend fun getCurrentUserProfile(): User {
        val userId = authHelper.getCurrentUserId()
        val doc = db.collection(FirestoreConstants.Collections.USERS)
            .document(userId)
            .get()
            .await()
        return doc.toObjectSafely()
            ?: throw IllegalStateException("User profile not found")
    }

    suspend fun getUserProfileById(userId: String): User {
        val doc = db.collection(FirestoreConstants.Collections.USERS)
            .document(userId)
            .get()
            .await()
        return doc.toObjectSafely()
            ?: throw IllegalStateException("User profile not found for $userId")
    }

    suspend fun updateUserProfile(updates: Map<String, Any?>) {
        val userId = authHelper.getCurrentUserId()
        db.collection(FirestoreConstants.Collections.USERS)
            .document(userId)
            .update(updates.withUpdatedAt())
            .await()
    }

    suspend fun findUserIdByEmail(email: String): String? {
        val normalized = FirebaseValidators.normalizeEmail(email)
        return db.collection(FirestoreConstants.Collections.USERS)
            .whereEqualTo(FirestoreConstants.Fields.EMAIL, normalized)
            .limit(1)
            .get()
            .await()
            .documents.firstOrNull()?.id
    }

    suspend fun getUsersByIds(ids: List<String>): List<User> =
        queryHelper.queryByIds(FirestoreConstants.Collections.USERS, ids)

    // ===== SESSION & PREFERENCES =====
    private val Context.userDataStore by preferencesDataStore(name = "user_prefs")
    private val rememberMeKey = booleanPreferencesKey("remember_me")
    private val favoriteEventTypesKey = stringSetPreferencesKey("favorite_event_types")

    suspend fun saveUserSession() {
        context.userDataStore.edit { prefs -> prefs[rememberMeKey] = true }
    }

    suspend fun clearUserSession() {
        context.userDataStore.edit { prefs -> prefs.remove(rememberMeKey) }
        authHelper.logout()
    }

    suspend fun isRemembered(): Boolean =
        context.userDataStore.data.map { it[rememberMeKey] ?: false }.first()

    fun getUserSession(): Flow<Boolean> =
        context.userDataStore.data.map { it[rememberMeKey] ?: false }

    fun getFavoriteEventTypes(): Flow<Set<EventType>> {
        return context.userDataStore.data.map { prefs ->
            prefs[favoriteEventTypesKey]
                ?.mapNotNull { runCatching { EventType.valueOf(it) }.getOrNull() }
                ?.toSet()
                ?: emptySet()
        }
    }

    suspend fun toggleFavoriteEventType(eventType: EventType) {
        context.userDataStore.edit { prefs ->
            val current = prefs[favoriteEventTypesKey]?.toMutableSet() ?: mutableSetOf()
            val typeName = eventType.name

            if (current.contains(typeName)) current.remove(typeName)
            else current.add(typeName)

            prefs[favoriteEventTypesKey] = current
        }
    }

    suspend fun setFavoriteEventTypes(eventTypes: Set<EventType>) {
        context.userDataStore.edit { prefs ->
            prefs[favoriteEventTypesKey] = eventTypes.map { it.name }.toSet()
        }
    }

    suspend fun clearFavoriteEventTypes() {
        context.userDataStore.edit { prefs -> prefs.remove(favoriteEventTypesKey) }
    }

    // ===== PHOTO OPERATIONS (Delegated) =====
    suspend fun addPhotoToEntity(
        entityType: String,
        entityId: String,
        uri: Uri
    ): String = photoHelper.uploadPhoto(entityType, entityId, uri)

    suspend fun deletePhotoFromEntity(entityType: String, entityId: String) =
        photoHelper.deletePhoto(entityType, entityId)

    // ===== PUBLIC ACCESS METHODS (for backward compatibility) =====
    fun isUserLoggedIn(): Boolean = authHelper.isLoggedIn()
    fun getCurrentUserEmail(): String? = authHelper.getCurrentEmail()
    fun getCurrentUserId(): String? = auth.currentUser?.uid

    // ===== BABY OPERATIONS =====
    suspend fun addOrUpdateBaby(baby: Baby): Result<Baby> = runCatching {
        val userId = authHelper.getCurrentUserId()
        val families = getCurrentUserFamilies().getOrThrow()
        val allBabyIds = families.flatMap { it.babyIds }.distinct()

        // Check for duplicate names
        if (allBabyIds.isNotEmpty()) {
            val existing = queryHelper.queryByIds<Baby>(
                FirestoreConstants.Collections.BABIES,
                allBabyIds
            ).filter { it.name.trim() == baby.name.trim() && it.id != baby.id }

            if (existing.isNotEmpty()) {
                throw IllegalStateException("Un bébé portant ce nom existe déjà.")
            }
        }

        val isNew = baby.id.isBlank()
        val docRef = if (isNew) {
            db.collection(FirestoreConstants.Collections.BABIES).document()
        } else {
            db.collection(FirestoreConstants.Collections.BABIES).document(baby.id)
        }

        val finalBaby = baby.copy(id = docRef.id)
        docRef.set(finalBaby).await()

        // Add to families
        if (isNew) {
            families.forEach { family ->
                if (finalBaby.id !in family.babyIds) {
                    val updated = family.copy(
                        babyIds = (family.babyIds + finalBaby.id).distinct(),
                        updatedAt = FirestoreTimestampUtils.getCurrentTimestamp()
                    )
                    addOrUpdateFamily(updated).getOrThrow()
                }
            }
        }

        finalBaby
    }

    fun streamBabies(): Flow<List<Baby>> {
        authHelper.getCurrentUserId() // Verify auth
        return streamFamilies()
            .flatMapLatest { families ->
                val allBabyIds = families.flatMap { it.babyIds }.distinct()
                if (allBabyIds.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    streamBabiesByIds(allBabyIds)
                }
            }
            .distinctUntilChanged()
    }

    private fun streamBabiesByIds(babyIds: List<String>): Flow<List<Baby>> {
        if (babyIds.isEmpty()) return flowOf(emptyList())

        return callbackFlow {
            val listeners = mutableListOf<ListenerRegistration>()
            val babiesMap = mutableMapOf<String, Baby>()

            babyIds.chunked(10).forEach { chunk ->
                val listener = db.collection(FirestoreConstants.Collections.BABIES)
                    .whereIn(FieldPath.documentId(), chunk)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            close(error)
                            return@addSnapshotListener
                        }

                        snapshot?.documents?.forEach { doc ->
                            doc.toObjectSafely<Baby>()?.let { baby ->
                                babiesMap[baby.id] = baby
                            }
                        }

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
        FirebaseValidators.validateBabyId(babyId)
        return try {
            val doc = db.collection(FirestoreConstants.Collections.BABIES)
                .document(babyId)
                .get()
                .await()
            Result.success(doc.toObjectSafely())
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching baby: $babyId", e)
            Result.failure(e)
        }
    }

    suspend fun deleteBabyAndEvents(babyId: String): Result<Unit> = runCatching {
        FirebaseValidators.validateBabyId(babyId)
        val batch = db.batch()

        // Delete baby
        batch.delete(
            db.collection(FirestoreConstants.Collections.BABIES).document(babyId)
        )

        // Delete related events
        val eventsSnapshot = db.collection(FirestoreConstants.Collections.EVENTS)
            .whereEqualTo(FirestoreConstants.Fields.BABY_ID, babyId)
            .get()
            .await()

        eventsSnapshot.documents.forEach { doc ->
            batch.delete(doc.reference)
        }

        batch.commit().await()
    }

    suspend fun removeBabyFromAllFamilies(babyId: String) = withContext(Dispatchers.IO) {
        FirebaseValidators.validateBabyId(babyId)
        val familiesSnapshot = db.collection(FirestoreConstants.Collections.FAMILIES)
            .whereArrayContains(FirestoreConstants.Fields.BABY_IDS, babyId)
            .get()
            .await()

        val batch = db.batch()
        familiesSnapshot.documents.forEach { doc ->
            batch.update(
                doc.reference,
                FirestoreConstants.Fields.BABY_IDS,
                FieldValue.arrayRemove(babyId),
                FirestoreConstants.Fields.UPDATED_AT,
                FirestoreTimestampUtils.getCurrentTimestamp()
            )
        }
        batch.commit().await()
    }

    suspend fun addParentToBaby(babyId: String, parentEmail: String) {
        FirebaseValidators.validateBabyId(babyId)
        FirebaseValidators.validateEmail(parentEmail)

        val userId = findUserIdByEmail(parentEmail)
            ?: throw IllegalArgumentException("No user found with this email")

        val babyRef = db.collection(FirestoreConstants.Collections.BABIES).document(babyId)

        db.runTransaction { tx ->
            val snapshot = tx.get(babyRef)
            val current = (snapshot.get(FirestoreConstants.Fields.PARENT_IDS) as? List<String>)
                .orEmpty()

            if (!current.contains(userId)) {
                tx.update(
                    babyRef,
                    FirestoreConstants.Fields.PARENT_IDS,
                    current + userId,
                    FirestoreConstants.Fields.UPDATED_AT,
                    FirestoreTimestampUtils.getCurrentTimestamp()
                )
            }
        }.await()
    }
    // ===== EVENT OPERATIONS =====
    suspend fun addEvent(event: Event): Result<Unit> {
        return try {
            val userId = authHelper.getCurrentUserId()
            val data = event.toMap()
                .withUserIdAndTimestamp(userId)

            db.collection(FirestoreConstants.Collections.EVENTS)
                .document(event.id)
                .set(data)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error adding event", e)
            Result.failure(e)
        }
    }

    suspend fun updateEvent(eventId: String, event: Event): Result<Unit> = runCatching {
        authHelper.getCurrentUserId()
        val data = event.toMap()
            .withUpdatedAt()

        db.collection(FirestoreConstants.Collections.EVENTS)
            .document(eventId)
            .set(data, SetOptions.merge())
            .await()
    }

    suspend fun getEventById(eventId: String): Result<Event?> {
        return try {
            val doc = db.collection(FirestoreConstants.Collections.EVENTS)
                .document(eventId)
                .get()
                .await()
            Result.success(doc.toEvent())
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching event: $eventId", e)
            Result.failure(e)
        }
    }

    suspend fun deleteEvent(eventId: String): Result<Unit> {
        return try {
            db.collection(FirestoreConstants.Collections.EVENTS)
                .document(eventId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting event: $eventId", e)
            Result.failure(e)
        }
    }
    
    fun streamEventsForBaby(
        babyId: String,
        startDate: Date,
        endDate: Date
    ): Flow<List<Event>> {
        FirebaseValidators.validateBabyId(babyId)
        FirebaseValidators.validateDateRange(startDate, endDate)
        authHelper.getCurrentUserId()

        return db.collection(FirestoreConstants.Collections.EVENTS)
            .whereEqualTo(FirestoreConstants.Fields.BABY_ID, babyId)
            .whereGreaterThanOrEqualTo(FirestoreConstants.Fields.TIMESTAMP, startDate)
            .whereLessThan(FirestoreConstants.Fields.TIMESTAMP, endDate)
            .orderBy(FirestoreConstants.Fields.TIMESTAMP, Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { it.toEvent() }
            }
            .catch { e ->
                Log.e(TAG, "Error streaming events for baby=$babyId", e)
                throw e
            }
    }

    fun streamEventCountsByDayTyped(
        babyId: String,
        startDate: Date,
        endDate: Date
    ): Flow<Map<String, EventDayCount>> {
        FirebaseValidators.validateBabyId(babyId)
        FirebaseValidators.validateDateRange(startDate, endDate)
        authHelper.getCurrentUserId()

        val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        return db.collection(FirestoreConstants.Collections.EVENTS)
            .whereEqualTo(FirestoreConstants.Fields.BABY_ID, babyId)
            .whereGreaterThanOrEqualTo(FirestoreConstants.Fields.TIMESTAMP, startDate)
            .whereLessThan(FirestoreConstants.Fields.TIMESTAMP, endDate)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { it.toEvent() }
                    .groupingBy { event ->
                        dateFormatter.formatDateForGrouping(event.timestamp)
                    }
                    .eachCount()
                    .mapValues { (_, count) -> EventDayCount(count = count) }
            }
            .catch { e ->
                Log.e(TAG, "Error streaming event counts", e)
                throw e
            }
    }

    fun streamAnalysisMetrics(
        babyId: String,
        startDate: Date,
        endDate: Date,
        eventTypes: Set<EventType> = EventType.entries.toSet()
    ): Flow<AnalysisSnapshot> {
        FirebaseValidators.validateBabyId(babyId)
        FirebaseValidators.validateDateRange(startDate, endDate)
        authHelper.getCurrentUserId()

        return combine(
            db.collection(FirestoreConstants.Collections.EVENTS)
                .whereEqualTo(FirestoreConstants.Fields.BABY_ID, babyId)
                .whereGreaterThanOrEqualTo(FirestoreConstants.Fields.TIMESTAMP, startDate)
                .whereLessThan(FirestoreConstants.Fields.TIMESTAMP, endDate)
                .snapshots()
                .map { snapshot ->
                    snapshot.documents.mapNotNull { it.toEvent() }
                },
            db.collection(FirestoreConstants.Collections.EVENTS)
                .whereEqualTo(FirestoreConstants.Fields.BABY_ID, babyId)
                .whereEqualTo(FirestoreConstants.Fields.EVENT_TYPE_STRING, "GROWTH")
                .whereGreaterThanOrEqualTo(FirestoreConstants.Fields.TIMESTAMP, startDate)
                .whereLessThan(FirestoreConstants.Fields.TIMESTAMP, endDate)
                .snapshots()
                .map { snapshot ->
                    snapshot.documents.mapNotNull { it.toEvent() as? GrowthEvent }
                }
        ) { events, growthEvents ->
            buildAnalysisSnapshot(startDate, endDate, babyId, events, growthEvents)
        }
            .catch { e ->
                Log.e(TAG, "Error streaming analysis metrics", e)
                throw e
            }
    }

    private fun buildAnalysisSnapshot(
        startDate: Date,
        endDate: Date,
        babyId: String,
        events: List<Event>,
        growthEvents: List<GrowthEvent>
    ): AnalysisSnapshot {
        val dateList = FirestoreTimestampUtils.generateDateRange(startDate, endDate)
        val eventsByDay = events.groupBy { it.timestamp.toLocalDate() }
        val growthByDate = growthEvents.groupBy { it.timestamp.toLocalDate() }

        val dailyAnalysis = dateList.map { date ->
            val dayEvents = eventsByDay[date].orEmpty()

            DailyAnalysis(
                date = date,
                mealCount = dayEvents.count { it is FeedingEvent },
                mealVolume = dayEvents
                    .filterIsInstance<FeedingEvent>()
                    .sumOf { it.amountMl ?: 0.0 }
                    .toFloat(),
                pumpingCount = dayEvents.count { it is PumpingEvent },
                pumpingVolume = dayEvents
                    .filterIsInstance<PumpingEvent>()
                    .sumOf { it.amountMl ?: 0.0 }
                    .toFloat(),
                poopCount = dayEvents.count { event ->
                    event is DiaperEvent &&
                            (event.diaperType == DiaperType.DIRTY || event.diaperType == DiaperType.MIXED)
                },
                wetCount = dayEvents.count { event ->
                    event is DiaperEvent &&
                            (event.diaperType == DiaperType.WET || event.diaperType == DiaperType.MIXED)
                },
                sleepMinutes = dayEvents
                    .filterIsInstance<SleepEvent>()
                    .sumOf { it.durationMinutes ?: 0L },
                growthMeasurements = growthByDate[date]
                    ?.maxByOrNull { it.timestamp }
                    ?.let {
                        GrowthMeasurement(
                            weightKg = it.weightKg?.toFloat() ?: Float.NaN,
                            heightCm = it.heightCm?.toFloat() ?: Float.NaN,
                            headCircumferenceCm = it.headCircumferenceCm?.toFloat() ?: Float.NaN,
                            timestamp = it.timestamp.time
                        )
                    }
            )
        }

        return AnalysisSnapshot(
            dailyAnalysis = dailyAnalysis,
            dateRange = AnalysisFilter.DateRange(
                AnalysisRange.CUSTOM,
                startDate.toLocalDate(),
                endDate.toLocalDate()
            ),
            babyId = babyId
        )
    }

    suspend fun getLastGrowthEvent(babyId: String): Result<GrowthEvent?> = runCatching {
        FirebaseValidators.validateBabyId(babyId)
        authHelper.getCurrentUserId()

        val snapshot = db.collection(FirestoreConstants.Collections.EVENTS)
            .whereEqualTo(FirestoreConstants.Fields.BABY_ID, babyId)
            .whereEqualTo(FirestoreConstants.Fields.EVENT_TYPE_STRING, "GROWTH")
            .orderBy(FirestoreConstants.Fields.TIMESTAMP, Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .await()

        snapshot.toObjects(GrowthEvent::class.java).firstOrNull()
    }

    // ===== DATA CLASS =====
    data class EventDayCount(
        val count: Int,
        val hasEvents: Boolean = count > 0
    )
}
