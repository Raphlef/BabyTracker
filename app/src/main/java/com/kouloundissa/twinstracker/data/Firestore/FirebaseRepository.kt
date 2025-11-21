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
     * Generic stream builder that handles the 4-step pattern for all stream functions
     *
     * The 4 steps are always the same:
     * 1. Plan data retrieval (cached/missing/realtime)
     * 2. Process cached data → emit transformed
     * 3. Query missing days → emit transformed
     * 4. Setup real-time listener → emit transformed
     *
     * Only the transformation function differs per stream type
     */
    private inline fun <reified T> buildEventStream(
        babyId: String,
        startDate: Date,
        endDate: Date,
        firebaseCache: FirebaseCache,
        crossinline transform: (Map<String, Event>) -> T?,
        crossinline logPrefix: () -> String = { "Stream" }
    ): Flow<T> = flow {
        // STEP 0: Shared validation
        FirebaseValidators.validateBabyId(babyId)
        FirebaseValidators.validateDateRange(startDate, endDate)
        authHelper.getCurrentUserId()

        val prefix = logPrefix()
        Log.d(TAG, "→ $prefix: baby=$babyId, range=[${startDate.time}, ${endDate.time}]")

        // STEP 1: Plan data retrieval (SHARED)
        val plan = firebaseCache.validateAndPlanDataRetrieval(babyId, startDate, endDate)
        val allEvents = mutableMapOf<String, Event>()

        // STEP 2: Process cached days (SHARED - only transform differs)
        if (plan.hasCachedData()) {
            Log.d(TAG, "→ Processing ${plan.cachedDays.size} cached days")

            plan.cachedDays.values.forEach { cachedDay ->
                cachedDay.events.forEach { event ->
                    allEvents[event.id] = event
                }
            }

            val transformed = transform(allEvents)
            if (transformed != null) {
                emit(transformed)
                Log.d(TAG, "✓ Emitted cached data (fast feedback)")
            }
        }

        // STEP 3: Query missing days (SHARED - only transform differs)
        if (plan.hasMissingDays()) {
            Log.d(TAG, "→ Querying ${plan.missingDays.size} missing days...")

            for (missingDay in plan.missingDays) {
                try {
                    val dayStart = getDayStart(missingDay)
                    val dayEnd = getDayEnd(missingDay)
                    val now = System.currentTimeMillis()

                    val queriedEvents = queryEventsForRangeOnce(babyId, dayStart, dayEnd, db)

                    if (queriedEvents.isNotEmpty()) {
                        val isCompletedDay = dayEnd.time < now
                        val cacheableEvents = if (isCompletedDay) {
                            // Jour terminé : on cache tous les événements
                            queriedEvents
                        } else {
                            // Jour en cours : seulement les > 6h
                            queriedEvents.filter { event ->
                                now - event.timestamp.time >= CacheTTL.FRESH.ageThresholdMs
                            }
                        }
                        if (cacheableEvents.isNotEmpty()) {
                            firebaseCache.cacheDayEvents(babyId, dayStart, cacheableEvents)
                        }
                    }

                    queriedEvents.forEach { event ->
                        allEvents[event.id] = event
                    }

                    Log.d(TAG, "✓ Queried day ${dayStart.time}: ${queriedEvents.size} events")
                } catch (e: Exception) {
                    Log.e(TAG, "✗ Error querying day: ${e.message}", e)
                    throw e
                }
            }

            val transformed = transform(allEvents)
            if (transformed != null) {
                emit(transformed)
                Log.d(TAG, "✓ Emitted combined data (cached + queried)")
            }
        }

        // STEP 4: Real-time listener (SHARED - only transform differs)
        if (plan.hasRealtimeListener()) {
            val todayStart = getDayStart(plan.realtimeDate!!)
            val todayEnd = getDayEnd(plan.realtimeDate)
            val listenerStart = plan.realtime6hBeforeTimestamp?.let { Date(it) } ?: todayStart

            val dateFormatter = SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault())
            val endDateStr = dateFormatter.format(todayEnd)

            Log.d(TAG, "→ Checking cache for pre-listener period: $todayStart → $todayEnd")

            var todaysCachedData = firebaseCache.getCachedDayEvents(babyId, todayStart)

            if (todaysCachedData != null) {
                // ✓ Cache hit for today
                Log.d(TAG, "✓ Using today's cache: ${todaysCachedData.events.size} events")

                val preListenerEvents = todaysCachedData.events.filter { event ->
                    event.timestamp.time < listenerStart.time
                }

                preListenerEvents.forEach { event ->
                    allEvents[event.id] = event
                }

                if (preListenerEvents.isNotEmpty()) {
                    val transformed = transform(allEvents)
                    if (transformed != null) {
                        emit(transformed)
                        Log.d(TAG, "✓ Emitted ${preListenerEvents.size} cached pre-listener events")
                    }
                }
            } else {
                // ✗ No valid cache for today - must query pre-listener period
                try {
                    Log.d(TAG, "→ No cache for pre-listener period - querying from $todayStart to $listenerStart")

                    val queriedEvents = queryEventsForRangeOnce(
                        babyId,
                        todayStart,
                        listenerStart,
                        db
                    )

                    if (queriedEvents.isNotEmpty()) {
                        queriedEvents.forEach { event ->
                            allEvents[event.id] = event
                        }

                        val transformed = transform(allEvents)
                        if (transformed != null) {
                            emit(transformed)
                            Log.d(TAG, "✓ Emitted queried pre-listener data: ${queriedEvents.size} events")
                        }

                        val cacheableEvents = queriedEvents.filter { event ->
                            System.currentTimeMillis() - event.timestamp.time >= CacheTTL.FRESH.ageThresholdMs
                        }
                        if (cacheableEvents.isNotEmpty()) {
                            try {
                                firebaseCache.cacheDayEvents(babyId, todayStart, cacheableEvents)
                                Log.d(TAG, "✓ Cached ${cacheableEvents.size}/${queriedEvents.size} pre-listener events")
                            } catch (e: Exception) {
                                Log.w(TAG, "⚠ Failed to cache: ${e.message}")
                            }
                        }
                    } else {
                        Log.d(TAG, "ℹ No events in pre-listener period")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "✗ Error querying pre-listener period: ${e.message}", e)
                }
            }

            // Now setup real-time listener starting 6h ago (covers FRESH + new events)
            Log.d(TAG, "→ Setting up real-time listener for $listenerStart → $endDateStr")

            db.collection(FirestoreConstants.Collections.EVENTS)
                .whereEqualTo(FirestoreConstants.Fields.BABY_ID, babyId)
                .whereGreaterThanOrEqualTo(FirestoreConstants.Fields.TIMESTAMP, listenerStart)  // ← 6h ago
                .whereLessThan(FirestoreConstants.Fields.TIMESTAMP, todayEnd)
                .orderBy(FirestoreConstants.Fields.TIMESTAMP, Query.Direction.DESCENDING)
                .snapshots()
                .collect { snapshot ->
                    val streamEventCount = snapshot.documents.size
                    val listenerEvents = snapshot.documents.mapNotNull { it.toEvent() }

                    Log.d(TAG, "↓ Received $streamEventCount events from stream ($listenerStart → $endDateStr)")

                    // Remove ALL today's events first, then add fresh ones
                    val beforeRemoval = allEvents.size
                    allEvents.values.removeAll { event ->
                        event.timestamp.time >= listenerStart.time && event.timestamp.time < todayEnd.time
                    }
                    val removed = beforeRemoval - allEvents.size
                    listenerEvents.forEach { event ->
                        allEvents[event.id] = event
                    }

                    if (listenerEvents.isNotEmpty()) {
                        val now = System.currentTimeMillis()
                        val cacheableEvents = listenerEvents.filter { event ->
                            now - event.timestamp.time >= CacheTTL.FRESH.ageThresholdMs
                        }

                        if (cacheableEvents.isNotEmpty()) {
                            try {
                                firebaseCache.cacheDayEvents(babyId, todayStart, cacheableEvents)
                                Log.d(TAG, "  → Cached ${cacheableEvents.size}/${listenerEvents.size} events (6h+ old)")
                            } catch (e: Exception) {
                                Log.w(TAG, "⚠ Failed to cache real-time events: ${e.message}")
                            }
                        }
                    }

                    val transformed = transform(allEvents)
                    if (transformed != null) {
                        emit(transformed)
                        Log.d(TAG, "✓ Real-time update")
                    }
                }
        }

    }.catch { e ->
        Log.e(TAG, "✗ Stream error for baby=$babyId: ${e.message}", e)
        throw e
    }.flowOn(Dispatchers.IO)
    fun streamEventsForBaby(
        babyId: String,
        startDate: Date,
        endDate: Date,
        firebaseCache: FirebaseCache = FirebaseCache(context, db),
    ): Flow<List<Event>> = buildEventStream(
        babyId = babyId,
        startDate = startDate,
        endDate = endDate,
        firebaseCache = firebaseCache,
        transform = { events ->
            events.values.sortedByDescending { it.timestamp }
        },
        logPrefix = { "streamEventsForBaby" }
    )

    fun streamEventCountsByDayTyped(
        babyId: String,
        startDate: Date,
        endDate: Date,
        firebaseCache: FirebaseCache = FirebaseCache(context, db),
    ): Flow<Map<String, EventDayCount>> = buildEventStream(
        babyId = babyId,
        startDate = startDate,
        endDate = endDate,
        firebaseCache = firebaseCache,
        transform = { events ->
            val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            events.values
                .groupingBy { event -> dateFormatter.formatDateForGrouping(event.timestamp) }
                .eachCount()
                .mapValues { (_, count) -> EventDayCount(count = count) }
        },
        logPrefix = { "streamEventCountsByDayTyped" }
    )


    fun streamAnalysisMetrics(
        babyId: String,
        startDate: Date,
        endDate: Date,
        eventTypes: Set<EventType> = EventType.entries.toSet(),
        firebaseCache: FirebaseCache = FirebaseCache(context, db),
    ): Flow<AnalysisSnapshot> = buildEventStream(
        babyId = babyId,
        startDate = startDate,
        endDate = endDate,
        firebaseCache = firebaseCache,
        transform = { events ->
            buildAnalysisSnapshot(
                startDate = startDate,
                endDate = endDate,
                babyId = babyId,
                events = events.values.toList(),
                eventTypes = eventTypes
            )
        },
        logPrefix = { "streamAnalysisMetrics" }
    )
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
