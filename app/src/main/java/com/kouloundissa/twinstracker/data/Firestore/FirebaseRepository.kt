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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
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
    suspend fun addEvent(
        event: Event,
        firebaseCache: FirebaseCache = FirebaseCache(context, db),
    ): Result<Unit> {
        return try {
            val userId = authHelper.getCurrentUserId()
            val data = event.toMap()
                .withUserIdAndTimestamp(userId)

            db.collection(FirestoreConstants.Collections.EVENTS)
                .document(event.id)
                .set(data)
                .await()
            firebaseCache.invalidateCacheDay(event.babyId, event.timestamp)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error adding event", e)
            Result.failure(e)
        }
    }

    suspend fun updateEvent(
        eventId: String, event: Event,
        firebaseCache: FirebaseCache = FirebaseCache(context, db),
    ): Result<Unit> = runCatching {
        authHelper.getCurrentUserId()
        val data = event.toMap()
            .withUpdatedAt()

        db.collection(FirestoreConstants.Collections.EVENTS)
            .document(eventId)
            .set(data, SetOptions.merge())
            .await()
        firebaseCache.invalidateCacheDay(event.babyId, event.timestamp)
    }

    suspend fun getEvent(eventId: String): Result<Event?> {
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

    suspend fun deleteEvent(
        event: Event,
        firebaseCache: FirebaseCache = FirebaseCache(context, db),
    ): Result<Unit> {
        return try {
            db.collection(FirestoreConstants.Collections.EVENTS)
                .document(event.id)
                .delete()
                .await()

            firebaseCache.invalidateCacheDay(event.babyId, event.timestamp)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting event: $event.id", e)
            Result.failure(e)
        }
    }

    suspend fun clearBabyCache(
        babyId: String,
        firebaseCache: FirebaseCache = FirebaseCache(context, db),
    ) {
        firebaseCache.clearCacheForBaby(babyId)
        Log.d(TAG, "Cache cleared successfully for baby: $babyId")
    }

    /**
     * Optimized event streaming with intelligent caching
     *
     * Strategy:
     * 1. Plan data retrieval (cached days, missing days, today)
     * 2. Emit cached data immediately
     * 3. Query missing days once per day, cache each day, emit merged
     * 4. Setup real-time listener for today only, emit updates
     * 5. Merge all sources using Map<id, Event> (overwrites stale)
     *
     * Key improvements:
     * - Only ONE real-time listener (today) instead of N overlapping listeners
     * - Simple one-time queries for missing past days
     * - Better cache reuse with day-based bucketing
     * - Fixed deduplication using Map instead of distinctBy()
     * - Proper TTL logic (FRESH data queried, not cached)
     */

    fun streamEventsForBaby(
        babyId: String,
        startDate: Date,
        endDate: Date,
        firebaseCache: FirebaseCache = FirebaseCache(context, db),
    ): Flow<List<Event>> = flow {
        // Validation
        FirebaseValidators.validateBabyId(babyId)
        FirebaseValidators.validateDateRange(startDate, endDate)
        authHelper.getCurrentUserId()
        val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault())
        Log.d(TAG, "→ streamEventsForBaby: baby=$babyId, range=[${startDate.time}, ${endDate.time}]")

        // STEP 1: Plan data retrieval using new day-based strategy
        val plan = firebaseCache.validateAndPlanDataRetrieval(babyId, startDate, endDate)

        // In-memory collection: Map<eventId, Event> for deduplication
        // Using Map ensures:
        // - No duplicate IDs
        // - Updates overwrite old versions (unlike distinctBy which keeps first)
        // - O(1) lookup for merging
        val allEvents = mutableMapOf<String, Event>()

        // STEP 2: Emit cached data immediately (fast UI feedback)
        if (plan.hasCachedData()) {
            Log.d(TAG, "→ Emitting ${plan.cachedDays.size} cached days")

            plan.cachedDays.values.forEach { cachedDay ->
                cachedDay.events.forEach { event ->
                    allEvents[event.id] = event
                }
            }

            val cachedEvents = allEvents.values.sortedByDescending { it.timestamp }
            emit(cachedEvents)
            Log.d(TAG, "✓ Emitted ${cachedEvents.size} cached events (fast feedback)")
        }

        // STEP 3: Query missing days (one-time queries, no listeners)
        if (plan.hasMissingDays()) {
            Log.d(TAG, "→ Querying ${plan.missingDays.size} missing days...")

            for (missingDay in plan.missingDays) {
                try {
                    val dayStart = getDayStart(missingDay)
                    val dayEnd = getDayEnd(missingDay)

                    // Simple one-time query for this specific day
                    // This replaces the old real-time listener approach for past dates
                    val queriedEvents = queryEventsForRangeOnce(
                        babyId = babyId,
                        startDate = dayStart,
                        endDate = dayEnd,
                        db = db
                    )

                    // Cache this day's results for next time
                    if (queriedEvents.isNotEmpty()) {
                        firebaseCache.cacheDayEvents(babyId, dayStart, queriedEvents)
                    }

                    // Add to in-memory collection (dedup by ID)
                    queriedEvents.forEach { event ->
                        allEvents[event.id] = event
                    }

                    Log.d(TAG, "✓ Queried day ${dayStart.time}: ${queriedEvents.size} events")
                } catch (e: Exception) {
                    Log.e(TAG, "✗ Error querying day: ${e.message}", e)
                    throw e
                }
            }

            // Emit combined data after all missing days queried
            val combinedEvents = allEvents.values.sortedByDescending { it.timestamp }
            emit(combinedEvents)
            Log.d(TAG, "✓ Emitted ${combinedEvents.size} total events (cached + queried)")
        }

        // STEP 4: Setup real-time listener for TODAY ONLY
        // This is the only real-time listener created per request
        // Unlike the old system with multiple overlapping listeners
        if (plan.hasRealtimeListener()) {
            val startDate = dateFormat.format(plan.realtimeDate!!)
            val endDate = dateFormat.format(plan.realtimeDate)
            Log.d(TAG, "→ Setting up real-time listener for $startDate → $endDate")

            val todayStart = getDayStart(plan.realtimeDate!!)
            val todayEnd = getDayEnd(plan.realtimeDate)

            db.collection(FirestoreConstants.Collections.EVENTS)
                .whereEqualTo(FirestoreConstants.Fields.BABY_ID, babyId)
                .whereGreaterThanOrEqualTo(FirestoreConstants.Fields.TIMESTAMP, todayStart)
                .whereLessThan(FirestoreConstants.Fields.TIMESTAMP, todayEnd)
                .orderBy(FirestoreConstants.Fields.TIMESTAMP, Query.Direction.DESCENDING)
                .snapshots()
                .collect { snapshot ->
                    val streamEventCount = snapshot.documents.size
                    val todayEvents = snapshot.documents.mapNotNull { it.toEvent() }

                    Log.d(TAG, "↓ Received $streamEventCount events from stream ($startDate → $endDate)")
                    // Update in-memory collection with today's fresh data
                    todayEvents.forEach { event ->
                        allEvents[event.id] = event  // Overwrites stale cached version
                    }

                    // Cache today's events for next app launch
                    if (todayEvents.isNotEmpty()) {
                        try {
                            firebaseCache.cacheDayEvents(babyId, todayStart, todayEvents)
                        } catch (e: Exception) {
                            Log.e(TAG, "✗ Failed to cache today's events: ${e.message}")
                        }
                    }

                    val finalEvents = allEvents.values.sortedByDescending { it.timestamp }
                    emit(finalEvents)

                    Log.d(TAG, "✓ Real-time update: ${finalEvents.size} total events")
                }
        }

    }.catch { e ->
        Log.e(TAG, "✗ Stream error for baby=$babyId: ${e.message}", e)
        throw e
    }.flowOn(Dispatchers.IO)

    /**
     * REFACTORED streamEventCountsByDayTyped() with new day-based caching
     *
     * Improvements:
     * - Uses new validateAndPlanDataRetrieval() for day-based planning
     * - Single real-time listener (today only)
     * - Simple grouping logic
     * - Better cache integration
     *
     * Signature UNCHANGED - fully backward compatible
     */
    fun streamEventCountsByDayTyped(
        babyId: String,
        startDate: Date,
        endDate: Date,
        firebaseCache: FirebaseCache = FirebaseCache(context, db),
    ): Flow<Map<String, EventDayCount>> = flow {
        FirebaseValidators.validateBabyId(babyId)
        FirebaseValidators.validateDateRange(startDate, endDate)
        authHelper.getCurrentUserId()

        val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault())

        Log.d(TAG, "→ streamEventCountsByDayTyped: baby=$babyId, range=[${startDate.time}, ${endDate.time}]")

        // STEP 1: Plan data retrieval using new day-based strategy
        val plan = firebaseCache.validateAndPlanDataRetrieval(babyId, startDate, endDate)

        // In-memory collection for merging counts
        val allEvents = mutableMapOf<String, Event>()

        // STEP 2: Process cached days immediately
        if (plan.hasCachedData()) {
            Log.d(TAG, "→ Processing ${plan.cachedDays.size} cached days")

            plan.cachedDays.values.forEach { cachedDay ->
                cachedDay.events.forEach { event ->
                    allEvents[event.id] = event
                }
            }

            // Group cached events by day and emit counts
            val cachedCounts = allEvents.values
                .groupingBy { event -> dateFormatter.formatDateForGrouping(event.timestamp) }
                .eachCount()
                .mapValues { (_, count) -> EventDayCount(count = count) }

            emit(cachedCounts)
            Log.d(TAG, "✓ Emitted ${cachedCounts.size} cached day counts (fast feedback)")
        }

        // STEP 3: Query missing days (one-time queries, no listeners)
        if (plan.hasMissingDays()) {
            Log.d(TAG, "→ Querying ${plan.missingDays.size} missing days...")

            for (missingDay in plan.missingDays) {
                try {
                    val dayStart = getDayStart(missingDay)
                    val dayEnd = getDayEnd(missingDay)

                    // Simple one-time query for this day
                    val queriedEvents = queryEventsForRangeOnce(
                        babyId = babyId,
                        startDate = dayStart,
                        endDate = dayEnd,
                        db = db
                    )

                    // Cache this day's results
                    if (queriedEvents.isNotEmpty()) {
                        firebaseCache.cacheDayEvents(babyId, dayStart, queriedEvents)
                    }

                    // Add to in-memory collection (dedup by ID)
                    queriedEvents.forEach { event ->
                        allEvents[event.id] = event
                    }

                    Log.d(TAG, "✓ Queried day ${dayStart.time}: ${queriedEvents.size} events")
                } catch (e: Exception) {
                    Log.e(TAG, "✗ Error querying day: ${e.message}", e)
                    throw e
                }
            }

            // Group all events and emit combined counts
            val combinedCounts = allEvents.values
                .groupingBy { event -> dateFormatter.formatDateForGrouping(event.timestamp) }
                .eachCount()
                .mapValues { (_, count) -> EventDayCount(count = count) }

            emit(combinedCounts)
            Log.d(TAG, "✓ Emitted ${combinedCounts.size} combined day counts (cached + queried)")
        }

        // STEP 4: Setup real-time listener for TODAY ONLY
        if (plan.hasRealtimeListener()) {
            val startDate = dateFormat.format(plan.realtimeDate!!)
            val endDate = dateFormat.format(plan.realtimeDate)
            Log.d(TAG, "→ Setting up real-time listener for $startDate → $endDate")

            val todayStart = getDayStart(plan.realtimeDate!!)
            val todayEnd = getDayEnd(plan.realtimeDate)

            db.collection(FirestoreConstants.Collections.EVENTS)
                .whereEqualTo(FirestoreConstants.Fields.BABY_ID, babyId)
                .whereGreaterThanOrEqualTo(FirestoreConstants.Fields.TIMESTAMP, todayStart)
                .whereLessThan(FirestoreConstants.Fields.TIMESTAMP, todayEnd)
                .orderBy(FirestoreConstants.Fields.TIMESTAMP, Query.Direction.DESCENDING)
                .snapshots()
                .collect { snapshot ->
                    val streamEventCount = snapshot.documents.size
                    val todayEvents = snapshot.documents.mapNotNull { it.toEvent() }
                    Log.d(TAG, "↓ Received $streamEventCount events from stream ($startDate → $endDate)")
                    // Update in-memory collection with today's fresh data
                    todayEvents.forEach { event ->
                        allEvents[event.id] = event  // Overwrites stale version
                    }

                    // Cache today's events
                    if (todayEvents.isNotEmpty()) {
                        try {
                            firebaseCache.cacheDayEvents(babyId, todayStart, todayEvents)
                        } catch (e: Exception) {
                            Log.e(TAG, "✗ Failed to cache today's events: ${e.message}")
                        }
                    }

                    // Group all events (cached + queried + today's real-time)
                    val finalCounts = allEvents.values
                        .groupingBy { event -> dateFormatter.formatDateForGrouping(event.timestamp) }
                        .eachCount()
                        .mapValues { (_, count) -> EventDayCount(count = count) }

                    emit(finalCounts)

                    Log.d(TAG, "✓ Real-time update: ${finalCounts.size} day counts")
                }
        }

    }.catch { e ->
        Log.e(TAG, "✗ Stream error for baby=$babyId: ${e.message}", e)
        throw e
    }.flowOn(Dispatchers.IO)


    fun streamAnalysisMetrics(
        babyId: String,
        startDate: Date,
        endDate: Date,
        eventTypes: Set<EventType> = EventType.entries.toSet(),
        firebaseCache: FirebaseCache = FirebaseCache(context, db),
    ): Flow<AnalysisSnapshot> = flow {
        FirebaseValidators.validateBabyId(babyId)
        FirebaseValidators.validateDateRange(startDate, endDate)
        authHelper.getCurrentUserId()

        Log.d(TAG, "→ streamAnalysisMetrics: baby=$babyId, range=[${startDate.time}, ${endDate.time}]")

        // STEP 1: Plan data retrieval using new day-based strategy
        val plan = firebaseCache.validateAndPlanDataRetrieval(babyId, startDate, endDate)

        // In-memory collection for merging all events
        val allEvents = mutableMapOf<String, Event>()

        // STEP 2: Process cached days immediately (incremental analysis)
        if (plan.hasCachedData()) {
            Log.d(TAG, "→ Processing ${plan.cachedDays.size} cached days")

            plan.cachedDays.values.forEach { cachedDay ->
                cachedDay.events.forEach { event ->
                    allEvents[event.id] = event
                }
            }

            // Build and emit analysis from cached data
            val cachedAnalysis = buildAnalysisSnapshot(
                startDate, endDate, babyId, allEvents.values.toList(), eventTypes
            )
            emit(cachedAnalysis)

            Log.d(TAG, "✓ Emitted cached analysis (${allEvents.size} events, fast feedback)")
        }

        // STEP 3: Query missing days (one-time queries, no listeners)
        if (plan.hasMissingDays()) {
            Log.d(TAG, "→ Querying ${plan.missingDays.size} missing days for analysis...")

            for (missingDay in plan.missingDays) {
                try {
                    val dayStart = getDayStart(missingDay)
                    val dayEnd = getDayEnd(missingDay)

                    // Simple one-time query for this day
                    val queriedEvents = queryEventsForRangeOnce(
                        babyId = babyId,
                        startDate = dayStart,
                        endDate = dayEnd,
                        db = db
                    )

                    // Cache this day's results
                    if (queriedEvents.isNotEmpty()) {
                        firebaseCache.cacheDayEvents(babyId, dayStart, queriedEvents)
                    }

                    // Add to in-memory collection (dedup by ID)
                    queriedEvents.forEach { event ->
                        allEvents[event.id] = event
                    }

                    Log.d(TAG, "✓ Queried day ${dayStart.time}: ${queriedEvents.size} events")
                } catch (e: Exception) {
                    Log.e(TAG, "✗ Error querying day: ${e.message}", e)
                    throw e
                }
            }

            // Build analysis from combined data (cached + queried)
            val combinedAnalysis = buildAnalysisSnapshot(
                startDate, endDate, babyId, allEvents.values.toList(), eventTypes
            )
            emit(combinedAnalysis)

            Log.d(TAG, "✓ Emitted combined analysis (${allEvents.size} total events)")
        }

        // STEP 4: Setup real-time listener for TODAY ONLY
        if (plan.hasRealtimeListener()) {
            Log.d(TAG, "→ Setting up real-time listener for today's analysis")

            val todayStart = getDayStart(plan.realtimeDate!!)
            val todayEnd = getDayEnd(plan.realtimeDate)

            db.collection(FirestoreConstants.Collections.EVENTS)
                .whereEqualTo(FirestoreConstants.Fields.BABY_ID, babyId)
                .whereGreaterThanOrEqualTo(FirestoreConstants.Fields.TIMESTAMP, todayStart)
                .whereLessThan(FirestoreConstants.Fields.TIMESTAMP, todayEnd)
                .orderBy(FirestoreConstants.Fields.TIMESTAMP, Query.Direction.DESCENDING)
                .snapshots()
                .collect { snapshot ->
                    val streamEventCount = snapshot.documents.size
                    val todayEvents = snapshot.documents.mapNotNull { it.toEvent() }

                    Log.d(TAG, "↓ Received $streamEventCount events from stream ($startDate → $endDate)")
                    // Update in-memory collection with today's fresh data
                    todayEvents.forEach { event ->
                        allEvents[event.id] = event  // Overwrites stale version
                    }

                    // Cache today's events
                    if (todayEvents.isNotEmpty()) {
                        try {
                            firebaseCache.cacheDayEvents(babyId, todayStart, todayEvents)
                        } catch (e: Exception) {
                            Log.e(TAG, "✗ Failed to cache today's events: ${e.message}")
                        }
                    }

                    // Build analysis from all events (cached + queried + today's real-time)
                    val finalAnalysis = buildAnalysisSnapshot(
                        startDate, endDate, babyId, allEvents.values.toList(), eventTypes
                    )
                    emit(finalAnalysis)

                    Log.d(TAG, "✓ Real-time analysis update (${allEvents.size} total events)")
                }
        }

    }.catch { e ->
        Log.e(TAG, "✗ Stream error for baby=$babyId: ${e.message}", e)
        throw e
    }.flowOn(Dispatchers.IO)



    /**
     * Query a specific date range from Firestore
     * Keeps DB query logic isolated and reusable
     */
    private suspend fun queryEventsForRangeOnce(
        babyId: String,
        startDate: Date,
        endDate: Date,
        db: FirebaseFirestore
    ): List<Event> {
        return db.collection(FirestoreConstants.Collections.EVENTS)
            .whereEqualTo(FirestoreConstants.Fields.BABY_ID, babyId)
            .whereGreaterThanOrEqualTo(FirestoreConstants.Fields.TIMESTAMP, startDate)
            .whereLessThan(FirestoreConstants.Fields.TIMESTAMP, endDate)
            .orderBy(FirestoreConstants.Fields.TIMESTAMP, Query.Direction.DESCENDING)
            .get()
            .await()
            .documents
            .mapNotNull { it.toEvent() }
    }

    private fun buildAnalysisSnapshot(
        startDate: Date,
        endDate: Date,
        babyId: String,
        events: List<Event>,
        eventTypes: Set<EventType> = EventType.entries.toSet()
    ): AnalysisSnapshot {

        val filteredEvents = events.filter { event ->
            EventType.forClass(event) in eventTypes
        }
        val growthEvents = filteredEvents
            .filterIsInstance<GrowthEvent>()
        val dateList = FirestoreTimestampUtils.generateDateRange(startDate, endDate)
        val eventsByDay = filteredEvents.groupBy { it.timestamp.toLocalDate() }
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
