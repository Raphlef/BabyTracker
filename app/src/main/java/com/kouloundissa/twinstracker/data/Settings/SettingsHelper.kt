package com.kouloundissa.twinstracker.data.Settings

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow

private const val TAG = "SettingsHelper"

/**
 * Extension function for easy access throughout app
 * Usage: val settings = context.adminSettings().getSettings()
 */
fun Context.adminSettings(): AdminSettingsManager {
    return AdminSettingsManager.getInstance(this)
}

/**
 * Extension for ViewModels to access settings flow
 * Usage: viewModel.settingsFlow(context).collect { settings -> ... }
 */
fun ViewModel.settingsFlow(context: Context): StateFlow<AdminSettings> {
    return context.adminSettings().settings
}

/**
 * Helper object for permission checks and validation
 */
object SettingsValidator {

    /**
     * Check if read operations are allowed
     */
    fun canRead(settings: AdminSettings): Boolean {
        return settings.allowRead
    }

    /**
     * Check if write operations are allowed
     */
    fun canWrite(settings: AdminSettings): Boolean {
        return settings.allowWrite
    }

    /**
     * Check if specific ad level is allowed
     * @param settings Admin settings
     * @param requiredLevel Minimum required level
     */
    fun hasAdLevel(settings: AdminSettings, requiredLevel: AdLevel): Boolean {
        val currentLevel = settings.getAdLevel()
        return currentLevel.ordinal >= requiredLevel.ordinal
    }

    /**
     * Check if ads can be shown (ad level is not NONE)
     */
    fun canShowAds(settings: AdminSettings): Boolean {
        return settings.getAdLevel() != AdLevel.NONE
    }

    /**
     * Get current ad level
     */
    fun getAdLevel(settings: AdminSettings): AdLevel {
        return settings.getAdLevel()
    }

    /**
     * Check if photo authorization is enabled
     */
    fun photoAuthorizationEnabled(settings: AdminSettings): Boolean {
        return settings.photoAuthorization
    }
}

/**
 * Usage examples for the simplified settings
 */

// Example 1: Check read/write permissions
object PermissionExample {
    fun checkPermissions(context: Context) {
        val settings = context.adminSettings().getSettings()

        Log.d(TAG, "Read allowed: ${settings.allowRead}")
        Log.d(TAG, "Write allowed: ${settings.allowWrite}")

        if (SettingsValidator.canRead(settings)) {
            Log.d(TAG, "Can read data")
        }

        if (SettingsValidator.canWrite(settings)) {
            Log.d(TAG, "Can write data")
        }
    }
}

// Example 2: Ad level checking
object AdLevelExample {
    fun checkAdLevel(context: Context) {
        val settings = context.adminSettings().getSettings()
        val adLevel = settings.getAdLevel()

        Log.d(TAG, "Current ad level: $adLevel")

        when {
            SettingsValidator.hasAdLevel(settings, AdLevel.HIGH) -> {
                Log.d(TAG, "HIGH ad level - show premium ads")
            }

            SettingsValidator.hasAdLevel(settings, AdLevel.MEDIUM) -> {
                Log.d(TAG, "MEDIUM ad level - show standard ads")
            }

            else -> {
                Log.d(TAG, "NONE ad level - no ads shown")
            }
        }
    }
}

// Example 3: Photo authorization
object PhotoExample {
    fun checkPhotoAuthorization(context: Context) {
        val settings = context.adminSettings().getSettings()

        if (SettingsValidator.photoAuthorizationEnabled(settings)) {
            Log.d(TAG, "Photo upload is authorized")
        } else {
            Log.w(TAG, "Photo upload is not authorized")
        }
    }
}

fun isCrashlyticsEnabled(settings: AdminSettings): Boolean {
    return settings.crashlyticsEnabled
}