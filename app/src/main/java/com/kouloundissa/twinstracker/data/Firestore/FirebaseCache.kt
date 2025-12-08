package com.kouloundissa.twinstracker.data.Firestore


import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializer
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializer
import com.google.gson.JsonSyntaxException
import com.kouloundissa.twinstracker.data.Event
import com.kouloundissa.twinstracker.data.EventType
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.timeout
import java.util.Calendar
import java.util.Date
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

private const val CACHE_PREFERENCE_NAME = "baby_events_cache"
private val Context.cacheDataStore: DataStore<Preferences> by preferencesDataStore(name = CACHE_PREFERENCE_NAME)

data class CachedDayData(
    val babyId: String,
    val dayStartTimestamp: Long,  // Midnight of the day in UTC
    val events: List<Event>,
    val cachedAt: Long,
    val isComplete: Boolean = true  // true = full day queried, false = partial
)

/**
 * Serializable wrapper for CachedDayData (for JSON serialization)
 * Converts Event objects using their toMap() method for flexible serialization
 */
data class SerializableCachedDayData(
    val babyId: String,
    val dayStartTimestamp: Long,
    val events: List<Map<String, Any?>>,
    val cachedAt: Long,
    val isComplete: Boolean = true
) {
    companion object {
        fun fromCachedDayData(data: CachedDayData): SerializableCachedDayData {
            return SerializableCachedDayData(
                babyId = data.babyId,
                dayStartTimestamp = data.dayStartTimestamp,
                events = data.events.map { it.toMap() },
                cachedAt = data.cachedAt,
                isComplete = data.isComplete
            )
        }

        fun toCachedDayData(
            serializable: SerializableCachedDayData,
            context: Context
        ): CachedDayData {
            return CachedDayData(
                babyId = serializable.babyId,
                dayStartTimestamp = serializable.dayStartTimestamp,
                events = serializable.events.mapNotNull { eventMap ->
                    mapToEvent(eventMap, context)
                },
                cachedAt = serializable.cachedAt,
                isComplete = serializable.isComplete
            )
        }

        private fun mapToEvent(map: Map<String, Any?>, context: Context): Event? {
            return try {
                val typeName = map["eventTypeString"] as? String ?: return null
                val eventType = try {
                    EventType.valueOf(typeName)
                } catch (e: IllegalArgumentException) {
                    Log.w("FirebaseCache", "Unknown eventTypeString: $typeName")
                    return null
                }

                val json = GsonProvider.gson.toJson(map)
                val eventClass = eventType.eventClass.java
                GsonProvider.gson.fromJson(json, eventClass) as? Event
            } catch (e: Exception) {
                Log.w("FirebaseCache", "Error deserializing event from map: ${e.message}", e)
                null
            }
        }
    }
}


/**
 * Enum defining cache TTL based on data age
 * Age is calculated from the event's oldest timestamp in the cached range
 */
enum class CacheTTL(val ageThresholdMs: Long, val ttlMs: Long) {
    // 0-6 hours old: no cache (always fresh)
    FRESH(TimeUnit.HOURS.toMillis(6), 0L),

    // 6-24 hours old: 60 min TTL
    RECENT(TimeUnit.HOURS.toMillis(24), TimeUnit.MINUTES.toMillis(60)),

    // 24-48 hours old: 12 hour TTL
    MODERATE(TimeUnit.HOURS.toMillis(48), TimeUnit.HOURS.toMillis(12)),

    // 48 hours-7 days hours old: 2 days TTL
    OLD(TimeUnit.DAYS.toMillis(7), TimeUnit.DAYS.toMillis(2)),

    // 7 days + old: 1 month TTL
    VERYOLD(Long.MAX_VALUE, TimeUnit.DAYS.toMillis(30));

    companion object {
        private val sortedThresholds by lazy {
            entries.sortedBy { it.ageThresholdMs }
        }

        fun getTTL(ageMs: Long): CacheTTL {
            return sortedThresholds.firstOrNull { ageMs < it.ageThresholdMs } ?: VERYOLD
        }
    }
}

/**
 * Result of data retrieval planning
 * Returned by validateAndPlanDataRetrieval()
 */
data class DataRetrievalPlan(
    val cachedDays: Map<Long, CachedDayData> = emptyMap(),  // dayStart (ms) -> events
    val missingDays: List<Date> = emptyList(),  // dates needing fresh query
    val realtimeDate: Date? = null,  // today's date for real-time listener (or null)
    val realtime6hBeforeTimestamp: Long? = null // moment to start listening from
) {
    fun hasCachedData(): Boolean = cachedDays.isNotEmpty()
    fun hasMissingDays(): Boolean = missingDays.isNotEmpty()
    fun hasRealtimeListener(): Boolean = realtimeDate != null
}

/**
 * Represents a date range for database queries
 */
data class DateRange(
    val startDate: Date,
    val endDate: Date
)

/**
 * Helper to get start of day (midnight UTC)
 */
fun getDayStart(date: Date): Date {
    val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        time = date
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return calendar.time
}

/**
 * Helper to get end of day (23:59:59.999 UTC)
 */
fun getDayEnd(date: Date): Date {
    val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        time = date
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59)
        set(Calendar.MILLISECOND, 999)
    }
    return calendar.time
}

/**
 * FirebaseCache handles persistent caching of Firestore events with intelligent TTL management
 * Reduces Firestore read operations by:
 * - Maintaining persistent cache with age-based TTL
 * - Splitting queries to fetch only missing data ranges
 * - Merging cached and fresh data
 * - Logging read operation savings
 *
 * Uses Event.toMap() for serialization and Gson for JSON storage
 * No API changes needed - leverages existing Event serialization
 */
class FirebaseCache(
    private val context: Context,
    private val db: FirebaseFirestore
) {
    private val TAG = "FirebaseCache"

    /**
     * Generate cache key for a baby's events within a date range
     */
    private fun generateDayCacheKey(babyId: String, dayStart: Long): String {
        return "events_${babyId}_day_${dayStart}"
    }

    private fun generateCacheKey(babyId: String, startDate: Date, endDate: Date): String {
        return "events_${babyId}_${startDate.time}_${endDate.time}"
    }

    /**
     * Safe way to get Preferences from DataStore Flow
     * Handles Flow cancellation and timeouts
     */
    @OptIn(FlowPreview::class)
    private suspend fun getPreferences(): Preferences? {
        return try {
            context.cacheDataStore.data
                .timeout(15.seconds)
                .first()
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Log.w(TAG, "✗ DataStore timeout")
            null
        } catch (e: Exception) {
            Log.w(TAG, "✗ Error accessing DataStore: ${e.message}")
            null
        }
    }

    /**
     * Store events for a specific day in persistent cache
     * Uses Event.toMap() for serialization
     *
     * @param babyId The baby whose events to cache
     * @param dayStart The start of day (midnight) to cache for
     * @param events The events to cache
     */
    suspend fun cacheDayEvents(
        babyId: String,
        dayStart: Date,
        events: List<Event>
    ) {
        val cacheKey = generateDayCacheKey(babyId, dayStart.time)
        val cachedData = CachedDayData(
            babyId = babyId,
            dayStartTimestamp = dayStart.time,
            events = events,
            cachedAt = System.currentTimeMillis(),
            isComplete = true
        )

        try {
            context.cacheDataStore.edit { preferences ->
                val json = serializeCachedDayData(cachedData)
                if (json.isBlank()) {
                    Log.e(TAG, "✗ Serialization failed for baby=$babyId, day=${dayStart.time}")
                    return@edit
                }
                preferences[stringPreferencesKey(cacheKey)] = json
            }
            Log.d(TAG, "✓ Cached ${events.size} events for baby=$babyId on day=${dayStart.time}")
        } catch (e: Exception) {
            Log.e(TAG, "✗ Error caching day events: ${e.message}", e)
        }
    }

    /**
     * Retrieve cached events for a specific day if cache is still valid
     * Validates TTL before returning
     *
     * @param babyId The baby whose events to retrieve
     * @param dayStart The start of day (midnight) to retrieve for
     * @return CachedDayData if valid cache exists, null otherwise
     */
    suspend fun getCachedDayEvents(
        babyId: String,
        dayStart: Date
    ): CachedDayData? {
        val cacheKey = generateDayCacheKey(babyId, dayStart.time)

        return try {
            val preferences = getPreferences() ?: return null.also {
                Log.d(TAG, "✗ DataStore unavailable for baby=$babyId")
            }

            val json = preferences[stringPreferencesKey(cacheKey)] ?: return null.also {
                Log.d(TAG, "ℹ No cached data for baby=$babyId on day=${dayStart.time}")
            }

            if (json.isBlank()) {
                Log.w(TAG, "✗ Cached JSON is blank")
                return null
            }

            val cachedData = deserializeCachedDayData(json) ?: return null
            val now = System.currentTimeMillis()

            // Calculate how long ago the cache was stored
            val cacheAge = now - cachedData.cachedAt

            // Calculate how old the actual data is (based on oldest event)
            val oldestEventTime = cachedData.events.minOfOrNull { it.timestamp.time } ?: now
            val dataAge = now - oldestEventTime

            val cacheTTL = CacheTTL.getTTL(dataAge)

            Log.d(
                TAG,
                "Cache analysis: dataAge=${dataAge}ms (${TimeUnit.MILLISECONDS.toHours(dataAge)}h), " +
                        "cacheAge=${cacheAge}ms (${TimeUnit.MILLISECONDS.toMinutes(cacheAge)}min), " +
                        "TTL stage=${cacheTTL.name}, TTL=${cacheTTL.ttlMs}ms"
            )

            // FRESH data (ttlMs == -1) should NOT be cached
            if (cacheTTL == CacheTTL.FRESH) {
                Log.d(TAG, "✗ Data is FRESH (0-6h old) - must re-query from DB")
                return null
            }

            // For other TTLs, check if cache has expired
            if (cacheAge > cacheTTL.ttlMs) {
                Log.d(TAG, "✗ Cache expired: age=${cacheAge}ms > TTL=${cacheTTL.ttlMs}ms")
                return null
            }

            Log.d(
                TAG,
                "✓ Cache valid for baby=$babyId: ${cachedData.events.size} events, dataAge=${dataAge}ms, TTL=$cacheTTL"
            )
            cachedData
        } catch (e: Exception) {
            Log.e(TAG, "✗ Error retrieving cached day: ${e.message}", e)
            null
        }
    }

    /**
     * Process:
     * 1. Iterate through each day in the requested range
     * 2. For past days: check cache → add to cachedDays or missingDays
     * 3. For today: mark for real-time listener (never cache today)
     * 4. Return plan with all three categories
     *
     * Returns DataRetrievalPlan containing:
     * - cachedDays: Map of days with valid cached data (ready to emit immediately)
     * - missingDays: List of days needing fresh queries (query once, cache result)
     * - realtimeDate: Today's date if included in range (setup real-time listener)
     */
    suspend fun validateAndPlanDataRetrieval(
        babyId: String,
        requestedStart: Date,
        requestedEnd: Date
    ): DataRetrievalPlan {
        val now = Date()
        val todayStart = getDayStart(now)


        val rangeIncludesToday = todayStart in requestedStart..<requestedEnd

        Log.d(
            TAG,
            "→ Planning retrieval for baby=$babyId: range=[${requestedStart.time}, ${requestedEnd.time}], " +
                    "today=${todayStart.time}, includestoday=$rangeIncludesToday"
        )

        val cachedDays = mutableMapOf<Long, CachedDayData>()  // dayStart -> events
        val missingDays = mutableListOf<Date>()  // dates to query

        var realtimeDate: Date? = null
        var realtime6hBeforeTimestamp: Long? = null
        if (rangeIncludesToday) {
            realtimeDate = todayStart  // ← Set ONCE: today's date
            val sixHoursAgo = now.time - (CacheTTL.FRESH.ageThresholdMs)  // 6 hours in milliseconds
            realtime6hBeforeTimestamp = sixHoursAgo
            Log.d(TAG, "  ✓ Range includes today - will setup real-time listener for $todayStart")
        }

        // Iterate through each day in the requested range
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            time = getDayStart(requestedStart)
        }
        val endCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            time = getDayStart(requestedEnd)
        }

        while (calendar.timeInMillis <= endCalendar.timeInMillis) {
            val currentDay = calendar.time
            val currentDayStart = getDayStart(currentDay).time

            if (realtimeDate != null && currentDay >= realtimeDate) {

                val cachedDay = getCachedDayEvents(babyId, currentDay)
                if (cachedDay != null) {
                    cachedDays[currentDayStart] = cachedDay
                    Log.d(TAG, "  ✓ Today cached: ${cachedDay.events.size} events")
                } else {
                    // ❌ Pas de cache aujourd'hui → ajouter à missingDays
                    // Mais seulement pour la période STABLE (avant listener)
                    missingDays.add(currentDay)
                    Log.d(TAG, "  → Today not cached: will query stable period in PHASE 3")
                }

                calendar.add(Calendar.DAY_OF_MONTH, 1)
                continue
            }

            // PAST DATES: Try cache first
            val cachedDay = getCachedDayEvents(babyId, currentDay)

            if (cachedDay != null) {
                cachedDays[currentDayStart] = cachedDay
                Log.d(
                    TAG,
                    "  ✓ Using cache for day ${currentDay.time}: ${cachedDay.events.size} events"
                )
            } else {
                missingDays.add(currentDay)
                Log.d(TAG, "  ✗ Missing cache for day ${currentDay.time}: will query DB")
            }

            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }

        Log.d(
            TAG,
            "✓ Plan ready: ${cachedDays.size} cached days, ${missingDays.size} missing days, " +
                    "realtimeToday=${realtimeDate != null}"
        )

        return DataRetrievalPlan(
            cachedDays = cachedDays,
            missingDays = missingDays,
            realtimeDate = realtimeDate,
            realtime6hBeforeTimestamp = realtime6hBeforeTimestamp
        )
    }

    /**
     * Serialize CachedDayData to JSON string for storage in DataStore
     * Uses Gson for reliable JSON serialization
     * Leverages Event.toMap() method for consistent serialization
     */
    private fun serializeCachedDayData(data: CachedDayData): String {
        return try {
            val serializable = SerializableCachedDayData.fromCachedDayData(data)
            GsonProvider.gson.toJson(serializable)
        } catch (e: Exception) {
            Log.e(TAG, "✗ Error serializing cache data: ${e.message}", e)
            ""
        }
    }

    /**
     * Deserialize JSON string back to CachedDayData
     * Uses Gson for reliable JSON deserialization
     * Handles deserialization errors gracefully
     */
    private fun deserializeCachedDayData(json: String): CachedDayData? {
        return try {
            if (json.isBlank()) return null

            val serializable =
                GsonProvider.gson.fromJson(json, SerializableCachedDayData::class.java)
                    ?: return null.also { Log.w(TAG, "✗ Gson returned null") }

            SerializableCachedDayData.toCachedDayData(serializable, context)
        } catch (e: JsonSyntaxException) {
            Log.w(TAG, "✗ Invalid JSON in cache (data may be corrupted): ${e.message}", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "✗ Unexpected error deserializing cache: ${e.message}", e)
            null
        }
    }

    /**
     * Invalidate cache for a specific day (call after mutations)
     * Use this after creating, updating, or deleting an event
     *
     * @param babyId The baby whose cache to invalidate
     * @param eventDate The date of the event that changed
     */
    suspend fun invalidateCacheDay(babyId: String, eventDate: Date) {
        try {
            val dayStart = getDayStart(eventDate)
            val cacheKey = generateDayCacheKey(babyId, dayStart.time)

            context.cacheDataStore.edit { preferences ->
                preferences.remove(stringPreferencesKey(cacheKey))
            }
            Log.d(TAG, "✓ Invalidated cache for baby=$babyId on day=${dayStart.time}")
        } catch (e: Exception) {
            Log.e(TAG, "✗ Error invalidating cache: ${e.message}", e)
        }
    }

    /**
     * Clear all cached events for a baby
     * Useful when user creates, updates, or deletes events for this baby
     */
    suspend fun clearCacheForBaby(babyId: String) {
        try {
            context.cacheDataStore.edit { preferences ->
                preferences.asMap()
                    .keys
                    .filter { it.name.startsWith("events_${babyId}_") }
                    .forEach { preferences.remove(it) }
            }
            Log.d(TAG, "Cleared cache for baby=$babyId")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing cache for baby=$babyId", e)
        }
    }

    /**
     * Clear all cached events (useful on logout or account deletion)
     */
    suspend fun clearAllCache() {
        try {
            context.cacheDataStore.edit { preferences ->
                preferences.clear()
            }
            Log.d(TAG, "Cleared all event cache")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing all cache", e)
        }
    }

    /**
     * Get cache size in bytes (for monitoring)
     * Note: This is approximate - actual size may vary
     */
    suspend fun getCacheSizeBytes(): Long {
        return try {
            val preferences = context.cacheDataStore.data.first() as? Preferences ?: return 0L
            preferences.asMap().values.sumOf {
                (it as? String)?.toByteArray()?.size?.toLong() ?: 0L
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating cache size", e)
            0L
        }
    }
}

object GsonProvider {
    val gson: Gson = GsonBuilder()
        // : Date Serializer - handles Firebase Timestamp properly
        .registerTypeAdapter(Date::class.java, JsonSerializer<Date> { src, _, _ ->
            // When toMap() converts Date to Timestamp, we need to handle it
            // But we want to serialize as Long for cache storage
            if (src != null) {
                // Serialize as Long timestamp (not Timestamp object)
                JsonPrimitive(src.time)
            } else {
                null
            }
        })

        //  Date Deserializer - handles both formats
        .registerTypeAdapter(Date::class.java, JsonDeserializer { json, _, _ ->
            try {
                when {
                    json == null || json.isJsonNull -> null

                    // Format 1: Direct Long (old cache or serialized)
                    json.isJsonPrimitive && json.asJsonPrimitive.isNumber -> {
                        try {
                            Date(json.asJsonPrimitive.asLong)
                        } catch (e: Exception) {
                            // Handle scientific notation or other formats
                            Date(json.asJsonPrimitive.asDouble.toLong())
                        }
                    }

                    // Format 2: Firebase Timestamp object from toMap()
                    // {"nanoseconds": 973000000, "seconds": 1762741680}
                    json.isJsonObject -> {
                        val obj = json.asJsonObject
                        val seconds = obj.get("seconds")?.asLong ?: 0L
                        val nanoseconds = obj.get("nanoseconds")?.asLong ?: 0L

                        // Convert to milliseconds: (seconds * 1000) + (nanoseconds / 1000000)
                        val milliseconds = (seconds * 1000L) + (nanoseconds / 1_000_000L)
                        Date(milliseconds)
                    }

                    // Format 3: String (fallback)
                    json.isJsonPrimitive && json.asJsonPrimitive.isString -> {
                        try {
                            Date(json.asJsonPrimitive.asString.toLong())
                        } catch (e: Exception) {
                            null
                        }
                    }

                    else -> null
                }
            } catch (e: Exception) {
                null
            }
        })

        // ✅ FIXED: Timestamp Serializer - convert Firestore Timestamp to Long
        .registerTypeAdapter(Timestamp::class.java, JsonSerializer<Timestamp> { src, _, _ ->
            if (src != null) {
                // Convert Timestamp to milliseconds Long
                val milliseconds = (src.seconds * 1000L) + (src.nanoseconds / 1_000_000L)
                JsonPrimitive(milliseconds)
            } else {
                null
            }
        })

        // ✅ FIXED: Timestamp Deserializer - handle both formats
        .registerTypeAdapter(Timestamp::class.java, JsonDeserializer { json, _, _ ->
            try {
                when {
                    json == null || json.isJsonNull -> null

                    // Format 1: Already a Timestamp object
                    json.isJsonObject -> {
                        val obj = json.asJsonObject
                        val seconds = obj.get("seconds")?.asLong ?: 0L
                        val nanoseconds = obj.get("nanoseconds")?.asLong ?: 0L
                        Timestamp(seconds, nanoseconds.toInt())
                    }

                    // Format 2: Long milliseconds
                    json.isJsonPrimitive && json.asJsonPrimitive.isNumber -> {
                        val millis = json.asJsonPrimitive.asLong
                        val seconds = millis / 1000
                        val nanos = ((millis % 1000) * 1_000_000).toInt()
                        Timestamp(seconds, nanos)
                    }

                    else -> null
                }
            } catch (e: Exception) {
                null
            }
        })

        .create()
}


