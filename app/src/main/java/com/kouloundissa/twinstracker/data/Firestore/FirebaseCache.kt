package com.kouloundissa.twinstracker.data.Firestore


import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Calendar
import java.util.Date

/**
 * Simple in-memory cache for events keyed by babyId + date string.
 * Applies expiry semantics based on event "age" relative to current day.
 */
class FirebaseCache {

    data class CacheEntry<T>(
        val data: T,
        val timestamp: Long // store insertion time in millis
    )

    private enum class AgeCategory {
        CURRENT_DAY, PREVIOUS_DAY, OLDER
    }

    // Cache storage: Map<Key, CacheEntry>. LinkedHashMap to preserve insertion order if needed
    private val cache = LinkedHashMap<String, CacheEntry<Any>>()

    private val cacheMutex = Mutex()

    /**
     * Determines the age category of a date relative to now.
     */
    private fun ageCategoryFor(date: Date): AgeCategory {
        val todayCalendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val today = todayCalendar.time

        val diffDays = ((today.time - date.time) / (1000 * 60 * 60 * 24)).toInt()
        return when {
            diffDays < 0 -> AgeCategory.CURRENT_DAY // Future dates treated as current?
            diffDays == 0 -> AgeCategory.CURRENT_DAY
            diffDays == 1 -> AgeCategory.PREVIOUS_DAY
            else -> AgeCategory.OLDER
        }
    }

    /**
     * Computes expiry duration in millis based on age category.
     */
    private fun expiryMillisFor(ageCategory: AgeCategory): Long = when(ageCategory) {
        AgeCategory.CURRENT_DAY -> 0L // no cache for current day
        AgeCategory.PREVIOUS_DAY -> 1 * 60 * 60 * 1000L // 1 hour expiry
        AgeCategory.OLDER -> 10 * 60 * 60 * 1000L // 10 hours expiry
    }

    /**
     * Builds a cache key based on babyId + range string.
     */
    private fun buildCacheKey(babyId: String, startDate: Date, endDate: Date): String {
        return "$babyId|${startDate.time}|${endDate.time}"
    }

    /**
     * Attempts to get a cached value if valid (not expired).
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun <T : Any> getCached(
        babyId: String,
        startDate: Date,
        endDate: Date
    ): T? = cacheMutex.withLock {
        val key = buildCacheKey(babyId, startDate, endDate)

        val entry = cache[key] ?: return null

        val ageCategory = ageCategoryFor(endDate)

        val expiryMillis = expiryMillisFor(ageCategory)

        if (expiryMillis == 0L) {
            // No caching for current day range
            cache.remove(key)
            return null
        }

        if ((System.currentTimeMillis() - entry.timestamp) > expiryMillis) {
            cache.remove(key)
            return null
        }

        return entry.data as? T
    }

    /**
     * Caches a value with timestamp now.
     */
    suspend fun <T : Any> putCache(
        babyId: String,
        startDate: Date,
        endDate: Date,
        data: T
    ) = cacheMutex.withLock {
        val key = buildCacheKey(babyId, startDate, endDate)
        cache[key] = CacheEntry(data, System.currentTimeMillis())
    }

    /**
     * Clears cache entry explicitly.
     */
    suspend fun clearCache(babyId: String, startDate: Date, endDate: Date) = cacheMutex.withLock {
        val key = buildCacheKey(babyId, startDate, endDate)
        cache.remove(key)
    }
}

