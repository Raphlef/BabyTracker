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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.timeout
import java.util.Calendar
import java.util.Date
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

private const val CACHE_PREFERENCE_NAME = "baby_events_cache"
private val Context.cacheDataStore: DataStore<Preferences> by preferencesDataStore(name = CACHE_PREFERENCE_NAME)

/**
 * Data class representing cached event data with metadata
 */
data class CachedEventData(
    val events: List<Event>,
    val cachedAt: Long,
    val startDate: Long,
    val endDate: Long
)

/**
 * Serializable wrapper for CachedEventData (for JSON serialization)
 * Converts Event objects using their toMap() method for flexible serialization
 */
data class SerializableCachedEventData(
    val events: List<Map<String, Any?>>,
    val cachedAt: Long,
    val startDate: Long,
    val endDate: Long
) {
    companion object {
        fun fromCachedEventData(data: CachedEventData): SerializableCachedEventData {
            return SerializableCachedEventData(
                events = data.events.map { it.toMap() },
                cachedAt = data.cachedAt,
                startDate = data.startDate,
                endDate = data.endDate
            )
        }

        fun toCachedEventData(
            serializable: SerializableCachedEventData,
            context: Context
        ): CachedEventData {
            return CachedEventData(
                events = serializable.events.mapNotNull { eventMap ->
                    // Use Firestore's deserialization through the Event companion
                    // This leverages your existing DocumentSnapshot.toEvent() logic
                    mapToEvent(eventMap, context)
                },
                cachedAt = serializable.cachedAt,
                startDate = serializable.startDate,
                endDate = serializable.endDate
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
    FRESH(TimeUnit.HOURS.toMillis(6), 0),

    // 6-24 hours old: 15 min TTL
    RECENT(TimeUnit.HOURS.toMillis(24), TimeUnit.MINUTES.toMillis(15)),

    // 24-48 hours old: 1 hour TTL
    MODERATE(TimeUnit.HOURS.toMillis(48), TimeUnit.HOURS.toMillis(1)),

    // 48+ hours old: 1 week TTL
    OLD(Long.MAX_VALUE, TimeUnit.DAYS.toMillis(7));

    companion object {
        fun getTTL(ageMs: Long): CacheTTL {
            return when {
                ageMs < FRESH.ageThresholdMs -> FRESH
                ageMs < RECENT.ageThresholdMs -> RECENT
                ageMs < MODERATE.ageThresholdMs -> MODERATE
                else -> OLD
            }
        }
    }
}

/**
 * Result of cache validation determining whether to use cache and what DB queries are needed
 */
data class CacheValidationResult(
    val useCachedData: Boolean,
    val cachedEvents: List<Event> = emptyList(),
    val queryRanges: List<DateRange> = emptyList(),
    val readOperationsSaved: Int = 0
)

/**
 * Represents a date range for database queries
 */
data class DateRange(
    val startDate: Date,
    val endDate: Date
)
/**
 * Helper to get start of today (midnight)
 */
fun getStartOfToday(): Date {
    val calendar = Calendar.getInstance()
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    return calendar.time
}
/**
 * Helper to get end of yesterday (23:59:59)
 */
fun getEndOfYesterday(): Date {
    val calendar = Calendar.getInstance()
    calendar.add(Calendar.DAY_OF_MONTH, -1)
    calendar.set(Calendar.HOUR_OF_DAY, 23)
    calendar.set(Calendar.MINUTE, 59)
    calendar.set(Calendar.SECOND, 59)
    calendar.set(Calendar.MILLISECOND, 999)
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
    private fun generateCacheKey(babyId: String, startDate: Date, endDate: Date): String {
        return "events_${babyId}_${startDate.time}_${endDate.time}"
    }
    /**
     * Safe way to get Preferences from DataStore Flow
     * Handles Flow cancellation and timeouts
     */
    private suspend fun getPreferences(): Preferences? {
        return try {
            context.cacheDataStore.data
                .timeout(5.seconds)
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
     * Store events in persistent cache with metadata
     * Uses Event.toMap() for serialization
     */
    suspend fun cacheEvents(
        babyId: String,
        startDate: Date,
        endDate: Date,
        events: List<Event>
    ) {
        val cacheKey = generateCacheKey(babyId, startDate, endDate)
        val cachedData = CachedEventData(
            events = events,
            cachedAt = System.currentTimeMillis(),
            startDate = startDate.time,
            endDate = endDate.time
        )

        try {
            context.cacheDataStore.edit { preferences ->
                val json = serializeCachedEventData(cachedData)
                if (json.isBlank()) {
                    Log.e(TAG, "✗ Serialization produced empty JSON for baby=$babyId")
                    return@edit
                }
                preferences[stringPreferencesKey(cacheKey)] = json
            }
            Log.d(
                TAG,
                "Cached ${events.size} events for baby=$babyId, range=[${startDate.time}, ${endDate.time}]"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error caching events for baby=$babyId", e)
        }
    }

    /**
     * Retrieve cached events if valid, otherwise null
     */
    suspend fun getCachedEvents(
        babyId: String,
        startDate: Date,
        endDate: Date
    ): CachedEventData? {
        val cacheKey = generateCacheKey(babyId, startDate, endDate)

        return try {
            val preferences =
                getPreferences()?: return null.also {
                    Log.d(TAG, "✗ Failed to get preferences for baby=$babyId")
                }

            val json = preferences[stringPreferencesKey(cacheKey)]
            if (json == null) {
                Log.d(TAG, "✗ No cached data found for baby=$babyId")
                return null
            }

            if (json.isBlank()) {
                Log.w(TAG, "✗ Cached JSON is blank for baby=$babyId")
                return null
            }

            val result = deserializeCachedEventData(json)
            if (result != null) {
                Log.d(TAG, "✓ Retrieved ${result.events.size} cached events for baby=$babyId")
            } else {
                Log.w(TAG, "✗ Deserialization returned null for baby=$babyId")
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "✗ Error retrieving cached events for baby=$babyId: ${e.message}", e)
            null
        }
    }

    /**
     * Validate cache and determine query strategy
     * Returns which cached data is still valid and which date ranges need fresh DB queries
     */
    suspend fun validateAndPlanQueries(
        babyId: String,
        requestedStart: Date,
        requestedEnd: Date
    ): CacheValidationResult {
        val now = Date()
        val startOfToday = getStartOfToday()
        val endOfYesterday = getEndOfYesterday()

        val rangeIncludesToday = requestedStart <= startOfToday && requestedEnd >= now

        Log.d(TAG, "→ Validation: requested=[${requestedStart.time}, ${requestedEnd.time}], " +
                "today=[${startOfToday.time}, ${now.time}], includestoday=$rangeIncludesToday")

        // ✅ SMART FIX: If range includes today, split the strategy
        if (rangeIncludesToday) {
            Log.d(TAG, "✓ Range includes TODAY → Hybrid strategy: cache old + query today")

            val queryRanges = mutableListOf<DateRange>()
            var cachedEvents = emptyList<Event>()
            var readOpsSaved = 0

            // Part 1: Try to use cache for dates BEFORE today
            if (requestedStart < startOfToday) {
                Log.d(TAG, "  → Checking cache for old dates: [${requestedStart.time}, ${endOfYesterday.time}]")

                val cachedData = getCachedEvents(babyId, requestedStart, endOfYesterday)

                if (cachedData != null) {
                    // Validate cache is still good
                    val cacheAge = System.currentTimeMillis() - cachedData.cachedAt
                    val oldestEventTime = cachedData.events.minOfOrNull { it.timestamp.time }
                        ?: System.currentTimeMillis()
                    val dataAge = System.currentTimeMillis() - oldestEventTime
                    val cacheTTL = CacheTTL.getTTL(dataAge)

                    Log.d(TAG, "  → Cache found: age=${cacheAge}ms, dataAge=${dataAge}ms, TTL=$cacheTTL")

                    // Use cache only if not expired and not fresh
                    if (cacheTTL != CacheTTL.FRESH && (cacheAge <= cacheTTL.ttlMs || cacheTTL.ttlMs == 0L)) {
                        Log.d(TAG, "  ✓ Cache VALID for old dates, using ${cachedData.events.size} cached events")
                        cachedEvents = cachedData.events
                        readOpsSaved = calculateReadOperationsSaved(cachedData.events.size)
                    } else {
                        Log.d(TAG, "✗ Cache expired: age=${cacheAge}ms > TTL=${cacheTTL.ttlMs}ms, need to query old dates")
                        queryRanges.add(DateRange(requestedStart, endOfYesterday))
                    }
                } else {
                    Log.d(TAG, "  ✗ No cache found for old dates, will query DB")
                    queryRanges.add(DateRange(requestedStart, endOfYesterday))
                }
            }

            // Part 2: Always query for TODAY to catch new events
            Log.d(TAG, "  → Always querying today: [${startOfToday.time}, ${requestedEnd.time}]")
            queryRanges.add(DateRange(startOfToday, requestedEnd))

            return CacheValidationResult(
                useCachedData = cachedEvents.isNotEmpty(),
                cachedEvents = cachedEvents,
                queryRanges = queryRanges,
                readOperationsSaved = readOpsSaved
            )
        }

        // For OLD dates (not including today), can fully use cache
        Log.d(TAG, "✓ Range is all PAST dates → Can use cache fully")

        val cachedData = getCachedEvents(babyId, requestedStart, requestedEnd)

        if (cachedData == null) {
            Log.d(TAG, "✗ No cache found → Query full range from DB")
            return CacheValidationResult(
                useCachedData = false,
                queryRanges = listOf(DateRange(requestedStart, requestedEnd)),
                readOperationsSaved = 0
            )
        }

        // Check if cached data is still valid
        val cacheAge = System.currentTimeMillis() - cachedData.cachedAt
        val oldestEventTime = cachedData.events.minOfOrNull { it.timestamp.time }
            ?: System.currentTimeMillis()
        val dataAge = System.currentTimeMillis() - oldestEventTime
        val cacheTTL = CacheTTL.getTTL(dataAge)

        Log.d(TAG, "Cache analysis: dataAge=${dataAge}ms, cacheAge=${cacheAge}ms, TTL=$cacheTTL")

        if (cacheTTL == CacheTTL.FRESH) {
            Log.d(TAG, "✗ Data is FRESH → Query DB for latest")
            return CacheValidationResult(
                useCachedData = false,
                queryRanges = listOf(DateRange(requestedStart, requestedEnd)),
                readOperationsSaved = 0
            )
        }

        if (cacheAge > cacheTTL.ttlMs && cacheTTL.ttlMs > 0) {
            Log.d(TAG, "✗ Cache expired: age=${cacheAge}ms > TTL=${cacheTTL.ttlMs}ms")
            return CacheValidationResult(
                useCachedData = false,
                queryRanges = listOf(DateRange(requestedStart, requestedEnd)),
                readOperationsSaved = 0
            )
        }

        val queryRanges = splitQueryRanges(
            requestedStart,
            requestedEnd,
            cachedData.startDate,
            cachedData.endDate
        )

        val readOpsSaved = calculateReadOperationsSaved(cachedData.events.size)

        Log.d(TAG, "✓ Cache VALID for past dates: ${cachedData.events.size} events, readOpsSaved=$readOpsSaved")

        return CacheValidationResult(
            useCachedData = true,
            cachedEvents = cachedData.events,
            queryRanges = queryRanges,
            readOperationsSaved = readOpsSaved
        )
    }
    /**
     * Split requested date range into missing ranges that need DB queries
     * Handles gaps and misalignments between requested and cached ranges
     */
    private fun splitQueryRanges(
        requestedStart: Date,
        requestedEnd: Date,
        cachedStart: Long,
        cachedEnd: Long
    ): List<DateRange> {
        val ranges = mutableListOf<DateRange>()

        // Before cached range
        if (requestedStart.time < cachedStart) {
            ranges.add(
                DateRange(
                    startDate = requestedStart,
                    endDate = Date(cachedStart)
                )
            )
        }

        // After cached range
        if (requestedEnd.time > cachedEnd) {
            ranges.add(
                DateRange(
                    startDate = Date(cachedEnd),
                    endDate = requestedEnd
                )
            )
        }

        return ranges
    }

    /**
     * Calculate approximate Firestore read operations saved by using cache
     * Each document read from Firestore = 1 read operation that is charged
     * Cached documents save their corresponding read operations
     */
    private fun calculateReadOperationsSaved(cachedEventCount: Int): Int {
        // Firestore billing model: 1 read operation per document read
        // By using cache, we avoid N read operations where N = number of cached events
        return cachedEventCount
    }
    /**
     * Invalidate cache entries that contain events on or after the given date
     * Call this when an event is CREATED, UPDATED, or DELETED
     *
     * @param babyId: The baby whose cache should be invalidated
     * @param eventTimestamp: The timestamp of the event that changed
     *
     * Example usage:
     *   - Event created: invalidateCacheFromEventTimestamp(babyId, newEvent.timestamp)
     *   - Event updated: invalidateCacheFromEventTimestamp(babyId, updatedEvent.timestamp)
     *   - Event deleted: invalidateCacheFromEventTimestamp(babyId, deletedEvent.timestamp)
     */
    suspend fun invalidateCacheFromEventTimestamp(
        babyId: String,
        eventTimestamp: Date
    ) {
        try {
            val preferences = context.cacheDataStore.data.first()
            val eventTimeMs = eventTimestamp.time

            // Find all cache keys that contain this event's date
            val keysToInvalidate = preferences.asMap()
                .keys
                .filter { key ->
                    // Cache key format: "events_${babyId}_${startDate}_${endDate}"
                    val keyName = key.name
                    if (!keyName.startsWith("events_${babyId}_")) return@filter false

                    // Parse the key to get startDate and endDate
                    val parts = keyName.substringAfter("events_${babyId}_").split("_")
                    if (parts.size < 2) return@filter false

                    try {
                        val cacheStartDate = parts[0].toLong()
                        val cacheEndDate = parts[1].toLong()

                        // Invalidate if event timestamp falls within this cache range
                        // OR if event is after this cache range (might affect today's data)
                        eventTimeMs >= cacheStartDate && eventTimeMs <= cacheEndDate + 86400000 // +1 day
                    } catch (e: Exception) {
                        Log.w(TAG, "Error parsing cache key: ${key.name}")
                        false
                    }
                }

            // Remove all affected cache entries
            if (keysToInvalidate.isNotEmpty()) {
                context.cacheDataStore.edit { preferences ->
                    keysToInvalidate.forEach { key ->
                        preferences.remove(key)
                        Log.d(TAG, "✗ Invalidated cache: ${key.name}")
                    }
                }
                Log.d(TAG, "✓ Invalidated ${keysToInvalidate.size} cache entries for baby=$babyId " +
                        "affected by event at ${Date(eventTimeMs)}")
            } else {
                Log.d(TAG, "ℹ No cache entries to invalidate for event at ${Date(eventTimeMs)}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "✗ Error invalidating cache: ${e.message}", e)
        }
    }
    /**
     * Serialize CachedEventData to JSON string for storage in DataStore
     * Uses Gson for reliable JSON serialization
     * Leverages Event.toMap() method for consistent serialization
     */
    private fun serializeCachedEventData(data: CachedEventData): String {
        return try {
            val serializable = SerializableCachedEventData.fromCachedEventData(data)
            GsonProvider.gson.toJson(serializable)
        } catch (e: Exception) {
            Log.e(TAG, "Error serializing cache data", e)
            ""
        }
    }

    /**
     * Deserialize JSON string back to CachedEventData
     * Uses Gson for reliable JSON deserialization
     * Handles deserialization errors gracefully
     */
    private fun deserializeCachedEventData(json: String): CachedEventData? {
        return try {
            if (json.isBlank()) {
                Log.w(TAG, "Attempted to deserialize empty JSON")
                return null
            }

            val serializable = GsonProvider.gson.fromJson(json, SerializableCachedEventData::class.java)
                ?: return null.also { Log.w(TAG, "Gson returned null for valid JSON") }

            SerializableCachedEventData.toCachedEventData(serializable, context)
        } catch (e: JsonSyntaxException) {
            Log.w(TAG, "Invalid JSON in cache (data may be corrupted): ${e.message}", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error deserializing cache data: ${e.message}", e)
            null
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

    /**
     * Get number of cached event ranges
     * Useful for monitoring cache effectiveness
     */
    suspend fun getCachedRangesCount(): Int {
        return try {
            val preferences = context.cacheDataStore.data.first() as? Preferences ?: return 0
            preferences.asMap().keys.count { it.name.startsWith("events_") }
        } catch (e: Exception) {
            Log.e(TAG, "Error counting cached ranges", e)
            0
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


