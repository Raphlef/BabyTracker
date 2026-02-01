package com.kouloundissa.twinstracker.data.Firestore

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import com.google.firebase.storage.FirebaseStorage
import com.kouloundissa.twinstracker.data.AnalysisRange
import com.kouloundissa.twinstracker.data.AnalysisSnapshot
import com.kouloundissa.twinstracker.data.Baby
import com.kouloundissa.twinstracker.data.DailyAnalysis
import com.kouloundissa.twinstracker.data.DiaperEvent
import com.kouloundissa.twinstracker.data.DiaperType
import com.kouloundissa.twinstracker.data.Event
import com.kouloundissa.twinstracker.data.EventType
import com.kouloundissa.twinstracker.data.Family
import com.kouloundissa.twinstracker.data.FeedingEvent
import com.kouloundissa.twinstracker.data.Firestore.FirestoreTimestampUtils.toLocalDate
import com.kouloundissa.twinstracker.data.GrowthEvent
import com.kouloundissa.twinstracker.data.GrowthMeasurement
import com.kouloundissa.twinstracker.data.PumpingEvent
import com.kouloundissa.twinstracker.data.SleepEvent
import com.kouloundissa.twinstracker.data.Theme
import com.kouloundissa.twinstracker.data.User
import com.kouloundissa.twinstracker.data.groupByDateSpanning
import com.kouloundissa.twinstracker.data.toEvent
import com.kouloundissa.twinstracker.ui.components.AnalysisFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.Locale
import javax.inject.Inject


class FirebaseRepository @Inject constructor(
    val auth: FirebaseAuth,
    val db: FirebaseFirestore,
    private val context: Context,
    val authManager: FirebaseAuthorizationManager
) {
    private val TAG = "FirebaseRepository"


    // ===== AUTHENTICATION =====
    fun isUserLoggedIn(): Boolean = auth.currentUser != null
    fun getCurrentUserEmail(): String? = auth.currentUser?.email
    fun getCurrentUserIdOrThrow(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("User not authenticated")

    suspend fun login(email: String, password: String) {
        FirebaseValidators.validateEmail(email)
        val normalized = FirebaseValidators.normalizeEmail(email)
        auth.signInWithEmailAndPassword(normalized, password).await()
    }

    suspend fun sendPasswordReset(email: String) {
        FirebaseValidators.validateEmail(email)
        val normalized = FirebaseValidators.normalizeEmail(email)
        auth.sendPasswordResetEmail(normalized).await()
    }

    suspend fun register(email: String, password: String) {
        FirebaseValidators.validateEmail(email)
        val normalized = FirebaseValidators.normalizeEmail(email)

        auth.createUserWithEmailAndPassword(normalized, password).await()

        reloadUser()
        val userId = getCurrentUserIdOrThrow()
        if (userId.isEmpty()) {
            throw IllegalStateException("Failed to get user ID after registration")
        }

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

        // Wait a moment for user to be fully created
        delay(1000)

        sendVerificationEmail()
    }

    suspend fun checkEmailVerification(): Boolean {
        reloadUser()
        return isEmailVerified()
    }

    suspend fun signInWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential).await()
    }

    /**
     * Envoie un email de vérification à l'utilisateur actuel
     * @return true si envoi réussi, false sinon
     */
    suspend fun sendVerificationEmail(): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val user = auth.currentUser

            if (user == null) {
                Log.e("EmailVerification", "No current user found")
                return@withContext false
            }

            if (user.isEmailVerified) {
                Log.i("EmailVerification", "Email already verified for ${user.email}")
                return@withContext true
            }

            user.sendEmailVerification().await()
            Log.i("EmailVerification", "Verification email sent to ${user.email}")
            true
        } catch (e: Exception) {
            Log.e("EmailVerification", "Erreur envoi email", e)
            false
        }
    }

    /**
     * Renvoie l'email pour vérification (avec délai pour éviter spam)
     * @param minDelaySeconds délai minimum entre 2 envois
     */
    suspend fun resendVerificationEmail(minDelaySeconds: Int = 60): Result<Unit> =
        withContext(Dispatchers.IO) {
            return@withContext try {

                auth.currentUser?.sendEmailVerification()?.await()
                Result.success(Unit)
            } catch (e: Exception) {
                when {
                    e.message?.contains("too-many-requests") == true ->
                        Result.failure(Exception("Trop de tentatives. Réessayez dans ${minDelaySeconds}s"))

                    else -> Result.failure(e)
                }
            }
        }

    /**
     * Vérifie si l'utilisateur a confirmé son email
     */
    fun isEmailVerified(): Boolean {
        return true //hack because it never work, if you have time, you can try to make it work
        //  return auth.currentUser?.isEmailVerified ?: false
    }

    /**
     * Recharge l'état utilisateur depuis Firebase
     */
    suspend fun reloadUser() = withContext(Dispatchers.IO) {
        auth.currentUser?.reload()?.await()
    }


    // Helper instances
    val queryHelper = FirestoreQueryHelper(db)
    private val photoHelper = FirebasePhotoHelper(
        authManager = authManager,
        storage = FirebaseStorage.getInstance(),
        db,
        context,
        this
    )

    // ===== USER PROFILE =====
    suspend fun getCurrentUserProfile(): User {
        authManager.requireRead()
        val userId = getCurrentUserIdOrThrow()
        val doc = db.collection(FirestoreConstants.Collections.USERS)
            .document(userId)
            .get()
            .await()
        return doc.toObjectSafely()
            ?: throw IllegalStateException("User profile not found")
    }

    suspend fun getUserProfileById(userId: String): User {
        authManager.requireRead()
        val doc = db.collection(FirestoreConstants.Collections.USERS)
            .document(userId)
            .get()
            .await()
        return doc.toObjectSafely()
            ?: throw IllegalStateException("User profile not found for $userId")
    }

    suspend fun updateUserProfile(updates: Map<String, Any?>) {
        val userId = getCurrentUserIdOrThrow()
        db.collection(FirestoreConstants.Collections.USERS)
            .document(userId)
            .update(updates.withUpdatedAt())
            .await()
    }

    suspend fun findUserIdByEmail(email: String): String? {
        authManager.requireRead()
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
    private val lastSelectedFamilyIdKey = stringPreferencesKey("last_selected_family_id")

    private val lastNewEventTimestampKey = longPreferencesKey("last_new_event_timestamp")
    suspend fun saveUserSession() {
        context.userDataStore.edit { prefs -> prefs[rememberMeKey] = true }
    }

    suspend fun clearUserSession() {
        context.userDataStore.edit { prefs -> prefs.remove(rememberMeKey) }
        auth.signOut()
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

    suspend fun saveLastNewEventTimestamp(timestamp: Long) {
        context.userDataStore.edit { prefs ->
            prefs[lastNewEventTimestampKey] = timestamp
        }
    }

    suspend fun getLastNewEventTimestamp(): Long {
        return context.userDataStore.data
            .map { it[lastNewEventTimestampKey] ?: 0L }
            .first()
    }

    fun getLastNewEventTimestampFlow(): Flow<Long> {
        return context.userDataStore.data
            .map { it[lastNewEventTimestampKey] ?: 0L }
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


    // ===== BABY OPERATIONS =====
    suspend fun addOrUpdateBaby(baby: Baby, family: Family?): Result<Baby> = runCatching {

        authManager.requireWrite()
        val userId = getCurrentUserIdOrThrow()

        if (family == null) {
            throw IllegalStateException("L'utilisateur ne fait partie d'aucune famille")
        }
        val existingBabyIds = family.babyIds.toMutableList()

        // Validate name uniqueness (exclude current baby if updating)
        if (existingBabyIds.isNotEmpty()) {
            val duplicates = queryHelper.queryByIds<Baby>(
                FirestoreConstants.Collections.BABIES,
                existingBabyIds
            ).filter {
                it.name.trim().equals(baby.name.trim(), ignoreCase = true) &&
                        it.id != baby.id
            }

            if (duplicates.isNotEmpty()) {
                throw IllegalStateException("Un bébé portant ce nom existe déjà dans votre famille.")
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

        // Add to family
        if (isNew) {
            if (finalBaby.id !in family.babyIds) {
                val updated = family.copy(
                    babyIds = (family.babyIds + finalBaby.id).distinct(),
                    updatedAt = FirestoreTimestampUtils.getCurrentTimestamp()
                )
                addOrUpdateFamily(updated).getOrThrow()
            }
        }

        finalBaby
    }

    private val _selectedFamily = MutableStateFlow<Family?>(null)
    val selectedFamily: StateFlow<Family?> = _selectedFamily.asStateFlow()
    suspend fun saveLastSelectedFamilyId(familyId: String?) {
        context.userDataStore.edit { prefs ->
            if (familyId != null) {
                prefs[lastSelectedFamilyIdKey] = familyId
            } else {
                prefs.remove(lastSelectedFamilyIdKey)
            }
        }
    }

    // Get last selected family ID as Flow
    fun getLastSelectedFamilyId(): Flow<String?> =
        context.userDataStore.data.map { it[lastSelectedFamilyIdKey] }

    fun setSelectedFamily(family: Family?) {
        _selectedFamily.value = family
    }

    fun streamBabiesByFamily(family: Family): Flow<List<Baby>> {
        authManager.requireRead()
        val babyIds = family.babyIds.distinct()
        return if (babyIds.isEmpty()) {
            flowOf(emptyList())
        } else {
            streamBabiesByIds(babyIds)
        }
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
        authManager.requireWrite()
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

        authManager.requireWrite()
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

    // ===== EVENT OPERATIONS =====
    suspend fun addEvent(
        event: Event,
        firebaseCache: FirebaseCache = FirebaseCache(context, db),
    ): Result<Unit> {
        return try {
            authManager.requireWrite()
            val userId = getCurrentUserIdOrThrow()
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
        authManager.requireWrite()
        getCurrentUserIdOrThrow()
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

            authManager.requireRead()
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
    suspend fun invalidateCacheDay(
        babyId: String,
        timestamp: Date,
        firebaseCache: FirebaseCache = FirebaseCache(context, db)
    ) {
        val dayKey = timestamp.toLocalDate().toString()
        firebaseCache.invalidateCacheDay(babyId, timestamp)
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

        authManager.requireRead()
        // STEP 0: Shared validation
        FirebaseValidators.validateBabyId(babyId)
        FirebaseValidators.validateDateRange(startDate, endDate)
        getCurrentUserIdOrThrow()

        val prefix = logPrefix()
        val allEvents = mutableMapOf<String, Event>()

        Log.d(TAG, "→ $prefix: baby=$babyId, range=[${startDate}, ${endDate}]")

        // STEP 1: Plan data retrieval
        val plan = firebaseCache.validateAndPlanDataRetrieval(babyId, startDate, endDate)

        // STEP 2: Process cached days
        processCachedDays(babyId, plan, firebaseCache, allEvents, transform)

        // STEP 3: Query missing days
        queryMissingDays(babyId, plan, firebaseCache, allEvents, transform )

        // STEP 4: Setup real-time listener
        setupRealtimeListener(babyId, plan, firebaseCache, allEvents, transform)

    }.catch { e ->
        Log.e(TAG, "✗ Stream error for baby=$babyId: ${e.message}", e)
        throw e
    }.flowOn(Dispatchers.IO)

    /**
     * STEP 2: Process all cached days from the plan
     */
    private suspend inline fun <reified T> FlowCollector<T>.processCachedDays(
        babyId: String,
        plan: DataRetrievalPlan,
        firebaseCache: FirebaseCache,
        allEvents: MutableMap<String, Event>,
        crossinline transform: (Map<String, Event>) -> T?
    ) {
        if (!plan.hasCachedData()) return

        Log.d(TAG, "→ Processing ${plan.cachedDays.size} cached days")

        // Load all cached events into allEvents map
        loadCachedEventsIntoMap(plan, allEvents)

        // Emit transformed data
        emitIfNotNull(allEvents, transform, "Emitted cached data (fast feedback)")
    }

    /**
     * Extract: Load cached events from plan into map
     */
    private fun loadCachedEventsIntoMap(
        plan: DataRetrievalPlan,
        allEvents: MutableMap<String, Event>
    ) {
        plan.cachedDays.values.forEach { cachedDay ->
            cachedDay.events.forEach { event ->
                allEvents[event.id] = event
            }
        }
    }

    /**
     * STEP 3: Query all missing days from the plan
     */
    private suspend inline fun <reified T> FlowCollector<T>.queryMissingDays(
        babyId: String,
        plan: DataRetrievalPlan,
        firebaseCache: FirebaseCache,
        allEvents: MutableMap<String, Event>,
        crossinline transform: (Map<String, Event>) -> T?
    ) {
        if (!plan.hasMissingDays()) return

        Log.d(TAG, "→ Querying ${plan.missingDays.size} missing days...")

        try {
            // Build minimal set of time ranges that cover only missing days
            val ranges = buildMissingDayRanges(plan.missingDays)

            val allQueriedEvents = mutableListOf<Event>()

            for ((start, end) in ranges) {
                Log.d(TAG, "  → Querying range $start → $end")
                val events = queryEventsForRangeOnce(
                    babyId = babyId,
                    startDate = start,
                    endDate = end,
                    db = db
                )
                allQueriedEvents += events
            }

            Log.d(
                TAG,
                "✓ Queried ${plan.missingDays.size} missing days in ${ranges.size} range(s), " +
                        "total events: ${allQueriedEvents.size}"
            )

            // Distribute events per day + collect cache operations
            val cacheOps = distributeMissingDaysEvents(
                babyId = babyId,
                missingDays = plan.missingDays,
                events = allQueriedEvents,
                allEvents = allEvents,
                now = System.currentTimeMillis()
            )

            // Execute all cache operations in background (don't await)
            if (cacheOps.isNotEmpty()) {
                launchBackgroundCaching(babyId, cacheOps, firebaseCache)
            }

        } catch (e: Exception) {
            Log.e(TAG, "✗ Error querying missing days: ${e.message}", e)
            throw e
        }

        // Emit combined data
        emitIfNotNull(allEvents, transform, "Emitted combined data (cached + queried)")
    }

    /**
     * Build minimal list of [start, end) ranges that cover only missing days.
     * Consecutive missing days are merged into a single range.
     * Non-consecutive days create separate ranges (so we don't query holes).
     */
    private fun buildMissingDayRanges(missingDays: List<Date>): List<Pair<Date, Date>> {
        if (missingDays.isEmpty()) return emptyList()

        // Sort by time
        val sorted = missingDays.sortedBy { it.time }

        val ranges = mutableListOf<Pair<Date, Date>>()

        var currentStart = getDayStart(sorted.first())
        var currentEnd = getDayEnd(sorted.first())

        val maxGapMs = 10 * 60 * 1000L // 10 minutes in milliseconds

        for (i in 1 until sorted.size) {
            val previousDayEnd = getDayEnd(sorted[i - 1])
            val currentDayStart = getDayStart(sorted[i])

            // Calculate gap between end of previous day and start of current day
            val gapMs = currentDayStart.time - previousDayEnd.time

            if (gapMs <= maxGapMs) {
                // Gap is small enough, extend the range
                currentEnd = getDayEnd(sorted[i])
            } else {
                // Gap is too large, close current range and start a new one
                ranges += currentStart to currentEnd
                currentStart = currentDayStart
                currentEnd = getDayEnd(sorted[i])
            }
        }

        // Add last range
        ranges += currentStart to currentEnd

        Log.d(TAG, "  → Built ${ranges.size} range(s) from ${sorted.size} missing days")
        ranges.forEachIndexed { idx, (start, end) ->
            Log.d(TAG, "    Range ${idx + 1}: $start → $end")
        }

        return ranges
    }

    /**
     * Distribute events per missing day and collect cache operations.
     * Returns cache operations WITHOUT executing them (async later).
     */
    private fun distributeMissingDaysEvents(
        babyId: String,
        missingDays: List<Date>,
        events: List<Event>,
        allEvents: MutableMap<String, Event>,
        now: Long
    ): List<CacheOperation> {
        val missingDayStarts = missingDays.map { getDayStart(it).time }.toSet()
        val cacheOps = mutableListOf<CacheOperation>()

        // Group events by day
        val eventsByDay = mutableMapOf<Long, MutableList<Event>>()
        for (event in events) {
            val eventDayStart = getDayStart(Date(event.timestamp.time)).time
            if (eventDayStart in missingDayStarts) {
                eventsByDay.getOrPut(eventDayStart) { mutableListOf() }.add(event)
            }
        }

        Log.d(TAG, "  → Distributed ${events.size} events across ${eventsByDay.size} missing day(s)")

        // Create cache operations for each day with events
        eventsByDay.forEach { (dayStartMs, dayEvents) ->
            val dayStart = Date(dayStartMs)
            val dayEnd = getDayEnd(dayStart)

            // Build cache operation
            val cacheOp = buildCacheOperation(dayStart, dayEnd, dayEvents, now)
            if (cacheOp != null) {
                cacheOps += cacheOp
            }

            // Add events to allEvents immediately
            dayEvents.forEach { event ->
                allEvents[event.id] = event
            }

            Log.d(TAG, "  ✓ Day $dayStart: ${dayEvents.size} events")
        }

        // Log empty days
        val queriedDayStarts = eventsByDay.keys
        val emptyDays = missingDayStarts - queriedDayStarts
        if (emptyDays.isNotEmpty()) {
            Log.d(TAG, "  → ${emptyDays.size} missing day(s) with no events (not cached)")
        }

        return cacheOps
    }

    /**
     * Build a single cache operation for a day (don't execute yet)
     */
    private fun buildCacheOperation(
        dayStart: Date,
        dayEnd: Date,
        queriedEvents: List<Event>,
        now: Long
    ): CacheOperation? {
        // Skip empty days entirely
        if (queriedEvents.isEmpty()) return null

        val isCompletedDay = dayEnd.time < now

        val eventsToCache = if (isCompletedDay) {
            // ✓ COMPLETED DAY: Cache all events
            queriedEvents
        } else {
            // CURRENT DAY: Only cache events older than 6h
            queriedEvents.filter { event ->
                now - event.timestamp.time >= CacheTTL.FRESH.ageThresholdMs
            }
        }

        // Only return operation if there are events to cache
        return if (eventsToCache.isNotEmpty()) {
            CacheOperation(
                dayStart = dayStart,
                events = eventsToCache,
                isComplete = isCompletedDay
            )
        } else {
            null
        }
    }

    /**
     * Execute all cache operations in background without blocking
     */
    private fun launchBackgroundCaching(
        babyId: String,
        cacheOps: List<CacheOperation>,
        firebaseCache: FirebaseCache
    ) {
        // Launch in IO context to avoid blocking UI
        GlobalScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "  ↳ Caching ${cacheOps.size} days in background...")

                // Execute all cache operations (Room will batch if possible)
                cacheOps.forEach { op ->
                    firebaseCache.cacheDayEvents(
                        babyId = babyId,
                        dayStart = op.dayStart,
                        events = op.events,
                        isComplete = op.isComplete
                    )
                }

                Log.d(TAG, "  ✓ Background caching completed for ${cacheOps.size} days")
            } catch (e: Exception) {
                Log.e(TAG, "✗ Background caching failed: ${e.message}", e)
            }
        }
    }

    /**
     * Simple data class to represent a cache operation
     */
    private data class CacheOperation(
        val dayStart: Date,
        val events: List<Event>,
        val isComplete: Boolean
    )


    /**
     * STEP 4: Setup real-time listener from the plan
     */
    private suspend inline fun <reified T> FlowCollector<T>.setupRealtimeListener(
        babyId: String,
        plan: DataRetrievalPlan,
        firebaseCache: FirebaseCache,
        allEvents: MutableMap<String, Event>,
        crossinline transform: (Map<String, Event>) -> T?
    ) {
        if (!plan.hasRealtimeListener()) return

        val todayStart = getDayStart(plan.realtimeDate!!)
        val todayEnd = getDayEnd(plan.realtimeDate)
        val listenerStart = plan.realtime6hBeforeTimestamp?.let { Date(it) } ?: todayStart
        val listenerStartDay = getDayStart(listenerStart)

        Log.d(TAG, "→ Real-time strategy: listener from $listenerStart (always 6h back)")

        // PHASE 1: Load stable cached data
        loadStableCachedData(
            babyId,
            plan,
            firebaseCache,
            allEvents,
            todayStart,
            listenerStart,
            listenerStartDay
        )

        // Emit stable data if loaded
        val hasStableData = allEvents.isNotEmpty()
        if (hasStableData) {
            emitIfNotNull(allEvents, transform, "Emitted stable cached data")
        }

        // PHASE 2: Setup fresh real-time listener
        setupFreshRealtimeListener(
            babyId, listenerStart, todayEnd, firebaseCache, allEvents, transform
        )
    }

    /**
     * Extract: Load stable (>6h old) cached data from yesterday and today
     */
    private suspend fun loadStableCachedData(
        babyId: String,
        plan: DataRetrievalPlan,
        firebaseCache: FirebaseCache,
        allEvents: MutableMap<String, Event>,
        todayStart: Date,
        listenerStart: Date,
        listenerStartDay: Date
    ) {
        val now = System.currentTimeMillis()
        val freshThresholdMs = CacheTTL.FRESH.ageThresholdMs

        // Load stable events from previous day if listener spans midnight
        if (listenerStart.time < todayStart.time) {
            Log.d(TAG, "  → Listener spans previous day, loading yesterday's stable cache")
            loadStableEventsFromDay(
                babyId, listenerStartDay, firebaseCache, allEvents,
                // Load events that are now stable (created >6h ago)
                filterPredicate = { event ->
                    now - event.timestamp.time >= freshThresholdMs
                },
            )
        }

        // Load stable events from today if not already loaded in previous phases
        if (listenerStart > todayStart && !plan.missingDays.contains(todayStart)) {
            Log.d(TAG, "  → Loading today's stable cache")
            loadStableEventsFromDay(
                babyId, todayStart, firebaseCache, allEvents,
                filterPredicate = { event ->
                    event.timestamp.time < listenerStart.time &&
                            now - event.timestamp.time >= freshThresholdMs
                },
            )
        }
    }

    /**
     * Extract: Load stable events from a specific day within a time range
     */
    private suspend fun loadStableEventsFromDay(
        babyId: String,
        dayStart: Date,
        firebaseCache: FirebaseCache,
        allEvents: MutableMap<String, Event>,
        filterPredicate: (Event) -> Boolean,
    ) {
        val dayCache = firebaseCache.getCachedDayEvents(babyId, dayStart)
        if (dayCache == null) {
            Log.d(TAG, "  ⚠ No cache for $dayStart - will rely on listener")
            return
        }

        val relevantEvents = dayCache.events.filter(filterPredicate)

        relevantEvents.forEach { event ->
            allEvents[event.id] = event
        }

        if (relevantEvents.isNotEmpty()) {
            Log.d(TAG, "  ✓ Loaded ${relevantEvents.size} stable events from $dayStart")
        }
    }

    /**
     * Extract: Setup fresh real-time listener for the recent period
     */
    private suspend inline fun <reified T> FlowCollector<T>.setupFreshRealtimeListener(
        babyId: String,
        listenerStart: Date,
        todayEnd: Date,
        firebaseCache: FirebaseCache,
        allEvents: MutableMap<String, Event>,
        crossinline transform: (Map<String, Event>) -> T?
    ) {
        Log.d(TAG, "→ Starting real-time listener for FRESH period: $listenerStart → $todayEnd")

        db.collection(FirestoreConstants.Collections.EVENTS)
            .whereEqualTo(FirestoreConstants.Fields.BABY_ID, babyId)
            .whereGreaterThanOrEqualTo(FirestoreConstants.Fields.TIMESTAMP, listenerStart)
            .whereLessThan(FirestoreConstants.Fields.TIMESTAMP, todayEnd)
            .orderBy(FirestoreConstants.Fields.TIMESTAMP, Query.Direction.DESCENDING)
            .snapshots()
            .collect { snapshot ->
                processRealtimeSnapshot(
                    babyId, snapshot, listenerStart, todayEnd,
                    firebaseCache, allEvents, transform
                )
            }
    }

    /**
     * Extract: Process a single real-time snapshot
     */
    private suspend inline fun <reified T> FlowCollector<T>.processRealtimeSnapshot(
        babyId: String,
        snapshot: QuerySnapshot,
        listenerStart: Date,
        todayEnd: Date,
        firebaseCache: FirebaseCache,
        allEvents: MutableMap<String, Event>,
        crossinline transform: (Map<String, Event>) -> T?
    ) {
        val listenerEvents = snapshot.documents.mapNotNull { it.toEvent() }

        Log.d(TAG, "↓ Listener update: ${listenerEvents.size} events in FRESH window")

        // Update allEvents: remove old FRESH events, add new ones
        replaceFreshEvents(allEvents, listenerEvents, listenerStart, todayEnd)

        // Cache newly-stable events
        cacheNewlyStableEvents(babyId, listenerEvents, firebaseCache)

        // Emit update
        emitIfNotNull(allEvents, transform, "Real-time update: ${allEvents.size} total events")
    }

    /**
     * Extract: Replace FRESH window events in allEvents map
     */
    private fun replaceFreshEvents(
        allEvents: MutableMap<String, Event>,
        listenerEvents: List<Event>,
        listenerStart: Date,
        todayEnd: Date
    ) {
        val beforeSize = allEvents.size
        allEvents.values.removeAll { event ->
            event.timestamp.time >= listenerStart.time &&
                    event.timestamp.time < todayEnd.time
        }
        val removed = beforeSize - allEvents.size

        listenerEvents.forEach { event ->
            allEvents[event.id] = event
        }

        Log.d(
            TAG,
            "  → Replaced $removed FRESH events with ${listenerEvents.size} from listener"
        )
    }

    /**
     * Extract: Cache newly-stable events from listener (events older than 6h)
     */
    private suspend fun cacheNewlyStableEvents(
        babyId: String,
        listenerEvents: List<Event>,
        firebaseCache: FirebaseCache
    ) {
        if (listenerEvents.isEmpty()) return

        val now = System.currentTimeMillis()

        // Group events by day
        val eventsByDay = listenerEvents.groupBy { event ->
            getDayStart(Date(event.timestamp.time))
        }

        eventsByDay.forEach { (dayStart, eventsForDay) ->
            // Cache only events older than 6h (stable)
            val nowStableEvents = eventsForDay.filter { event ->
                now - event.timestamp.time >= CacheTTL.FRESH.ageThresholdMs
            }

            if (nowStableEvents.isNotEmpty()) {
                cacheStableEventsForDay(babyId, dayStart, nowStableEvents, firebaseCache)
            }
        }
    }

    /**
     * Extract: Cache stable events for a specific day (merge with existing cache)
     */
    private suspend fun cacheStableEventsForDay(
        babyId: String,
        dayStart: Date,
        nowStableEvents: List<Event>,
        firebaseCache: FirebaseCache
    ) {
        try {
            val existingCache = firebaseCache.getCachedDayEvents(babyId, dayStart)
            val eventsToCache = if (existingCache != null) {
                // Merge: keep existing + add new stables
                mergeEventLists(existingCache.events, nowStableEvents)
            } else {
                nowStableEvents
            }

            firebaseCache.cacheDayEvents(
                babyId, dayStart, eventsToCache,
                isComplete = false
            )
            Log.d(
                TAG,
                "  ✓ Cached ${nowStableEvents.size} newly-stable events for $dayStart"
            )
        } catch (e: Exception) {
            Log.w(TAG, "  ⚠ Failed to cache: ${e.message}")
        }
    }

    /**
     * Extract: Merge two event lists, avoiding duplicates
     */
    private fun mergeEventLists(
        existingEvents: List<Event>,
        newEvents: List<Event>
    ): List<Event> {
        val existingIds = existingEvents.map { it.id }.toSet()
        val merged = existingEvents.toMutableList()

        newEvents.forEach { event ->
            if (!existingIds.contains(event.id)) {
                merged.add(event)
            }
        }

        return merged
    }

    /**
     * Extract: Emit transformed data if not null
     */
    private suspend inline fun <reified T> FlowCollector<T>.emitIfNotNull(
        allEvents: Map<String, Event>,
        crossinline transform: (Map<String, Event>) -> T?,
        logMessage: String
    ) {
        val transformed = transform(allEvents)
        if (transformed != null) {
            emit(transformed)
            Log.d(TAG, "✓ $logMessage")
        }
    }
    @Deprecated("Use streamAnalysisMetrics instead")
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
            val sortedEvents = events.values.sortedByDescending { it.timestamp }

            val eventsByDate = sortedEvents.groupByDateSpanning()

            val analysisSnapshot = buildAnalysisSnapshot(
                startDate = startDate,
                endDate = endDate,
                babyId = babyId,
                events = sortedEvents,
                eventTypes = eventTypes
            )
            analysisSnapshot.copy(
                events = sortedEvents,
                eventsByDay = eventsByDate
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
                    },
                events = dayEvents
            )
        }

        return AnalysisSnapshot(
            dailyAnalysis = dailyAnalysis,
            dateRange = AnalysisFilter.DateRange(
                AnalysisRange.CUSTOM,
                startDate,
                endDate
            ),
            babyId = babyId
        )
    }

    suspend fun getLastGrowthEvent(babyId: String): Result<GrowthEvent?> = runCatching {
        FirebaseValidators.validateBabyId(babyId)
        getCurrentUserIdOrThrow()

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
