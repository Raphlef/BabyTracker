package com.kouloundissa.twinstracker.data.Firestore


import android.util.Log
import com.kouloundissa.twinstracker.data.Event
import java.util.Date

object FirebaseCache {
    private const val TAG = "FirebaseCache"

    // Cache store: cacheKey -> CachedValue
    val eventCache = HashMap<String, CachedEventData>()

    // Cache expiration for age (in hours)
    private const val EXPIRE_PREVIOUS_DAY_HOURS = 1L
    private const val EXPIRE_OLDER_DAY_HOURS = 10L

    data class CachedEventData(
        val data: List<Event>,
        val lastFetched: Long // epoch millis
    )

    /**
     * Generate a cache key for babyId + date range (dates formatted as yyyy-MM-dd)
     */
    fun cacheKey(babyId: String, startDate: Date, endDate: Date): String =
        "$babyId:${startDate.time}-${endDate.time}"

    /**
     * Return whether to use cache based on event age and time since last fetch
     */
    fun shouldUseCache(
        startDate: Date,
        endDate: Date,
        now: Date = Date(),
        lastFetched: Long
    ): Boolean {
        val oneDayMillis = 24 * 60 * 60 * 1000L
        val ageStart = now.time - startDate.time
        val ageEnd = now.time - endDate.time

        val ageStartDays = ageStart / oneDayMillis
        val ageEndDays = ageEnd / oneDayMillis
        val hoursSinceFetched = (now.time - lastFetched) / (60 * 60 * 1000L)

        return when {
            // no cache for current day or approaching current day
            ageEnd < oneDayMillis -> false

            // previous day cache expires after 1h
            ageEnd in oneDayMillis..(2 * oneDayMillis) -> hoursSinceFetched < EXPIRE_PREVIOUS_DAY_HOURS

            // older than previous day cache expires after 10h
            ageEnd > (2 * oneDayMillis) -> hoursSinceFetched < EXPIRE_OLDER_DAY_HOURS

            else -> false
        }
    }

    /**
     * Check cache to retrieve old data if valid
     */
    fun getCachedEventsIfValid(babyId: String, startDate: Date, endDate: Date): List<Event>? {
        val key = cacheKey(babyId, startDate, endDate)
        val cached = eventCache[key]
        return if (cached != null && shouldUseCache(startDate, endDate, Date(), cached.lastFetched)) {
            Log.i(TAG, "Cache hit for $key - saved read operation")
            cached.data
        } else {
            Log.i(TAG, "Cache miss or expired for $key")
            null
        }
    }

    /**
     * Update cache after fetching fresh data
     */
    fun updateCache(babyId: String, startDate: Date, endDate: Date, data: List<Event>) {
        val key = cacheKey(babyId, startDate, endDate)
        eventCache[key] = CachedEventData(data, System.currentTimeMillis())
    }
}



