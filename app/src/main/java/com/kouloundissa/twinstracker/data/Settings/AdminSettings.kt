package com.kouloundissa.twinstracker.data.Settings

/**
 * Data class representing all admin settings
 * Immutable and thread-safe
 * Stored as JSON in Firebase Remote Config
 */
data class AdminSettings(
    // Read/Write authorization
    val allowRead: Boolean = true,
    val allowWrite: Boolean = true,

    // Ad level (stored as String for Firebase compatibility)
    val adLevel: String = AdLevel.MEDIUM.name,

    // Photo authorization
    val photoAuthorization: Boolean = true,

    val crashlyticsEnabled: Boolean = true,

    // Timestamp
    val lastUpdated: Long = System.currentTimeMillis()
) {
    /**
     * Get AdLevel enum from stored String
     */
    fun getAdLevel(): AdLevel = AdLevel.fromString(adLevel)
}

/**
 * Ad level permissions
 */
enum class AdLevel {
    NONE, MEDIUM, HIGH;

    companion object {
        fun fromString(value: String?): AdLevel {
            return try {
                valueOf(value ?: "MEDIUM")
            } catch (e: Exception) {
                AdLevel.MEDIUM
            }
        }
    }
}