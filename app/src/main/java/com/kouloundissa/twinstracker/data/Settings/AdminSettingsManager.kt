package com.kouloundissa.twinstracker.data.Settings


import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

private const val TAG = "AdminSettingsManager"

/**
 * Singleton manager for admin settings
 * Handles Firebase Remote Config + SharedPreferences caching
 *
 * Usage: AdminSettingsManager.getInstance(context).getSettings()
 */
class AdminSettingsManager private constructor(
    private val context: Context,
    private val remoteConfig: FirebaseRemoteConfig,
    private val sharedPreferences: SharedPreferences,
    private val gson: Gson
) {

    companion object {
        private const val PREFS_NAME = "admin_settings"
        private const val KEY_SETTINGS = "admin_settings_json"
        private const val CONFIG_KEY = "admin_settings_config"
        private const val FETCH_INTERVAL_SECONDS = 600L // 10 min

        @Volatile
        private var instance: AdminSettingsManager? = null

        fun getInstance(context: Context): AdminSettingsManager =
            instance ?: synchronized(this) {
                instance ?: AdminSettingsManager(
                    context = context.applicationContext,
                    remoteConfig = FirebaseRemoteConfig.getInstance(),
                    sharedPreferences = context.applicationContext.getSharedPreferences(
                        PREFS_NAME,
                        Context.MODE_PRIVATE
                    ),
                    gson = Gson()
                ).also { instance = it }
            }
    }

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private val _settings = MutableStateFlow<AdminSettings>(AdminSettings())
    val settings: StateFlow<AdminSettings> = _settings.asStateFlow()

    // In-memory cache for ultra-fast access
    private var cachedSettings: AdminSettings = AdminSettings()

    init {
        configureRemoteConfig()
        loadCachedSettings()
        initializeAutoSync()
    }

    /**
     * Configure Firebase Remote Config
     */
    private fun configureRemoteConfig() {
        try {
            val defaultsMap = mapOf(
                CONFIG_KEY to getDefaultSettingsJson()
            )
            // Set defaults from XML
            remoteConfig.setDefaultsAsync(defaultsMap)

            // Configure minimum fetch interval
            val configSettings = remoteConfigSettings {
                minimumFetchIntervalInSeconds = FETCH_INTERVAL_SECONDS
            }
            remoteConfig.setConfigSettingsAsync(configSettings)

            Log.d(TAG, "RemoteConfig initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize RemoteConfig", e)
        }
    }

    /**
     * Load settings from cache (SharedPreferences)
     * Called on initialization
     */
    private fun loadCachedSettings() {
        try {
            val settingsJson = sharedPreferences.getString(KEY_SETTINGS, null)
            if (!settingsJson.isNullOrEmpty()) {
                cachedSettings = gson.fromJson(settingsJson, AdminSettings::class.java)
                _settings.value = cachedSettings
                Log.d(TAG, "Settings loaded from cache")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load cached settings", e)
        }
    }

    /**
     * Get current settings (immediate, from cache)
     * No I/O blocking - safe to call anywhere
     */
    fun getSettings(): AdminSettings = cachedSettings

    /**
     * Fetch and update settings from Firebase
     * Automatically saves to cache
     */
    fun syncSettingsFromFirebase() {
        scope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "Syncing settings from Firebase...")

                val task = remoteConfig.fetchAndActivate()
                val wasUpdated = task.await()

                if (wasUpdated) {
                    val settingsJson = remoteConfig.getString(CONFIG_KEY)
                    val newSettings = gson.fromJson(settingsJson, AdminSettings::class.java)

                    // Update cache
                    cachedSettings = newSettings
                    saveToPreferences(newSettings)

                    // Update Flow on Main thread
                    withContext(Dispatchers.Main) {
                        _settings.value = newSettings
                        Log.d(TAG, "Settings updated from Firebase")
                    }
                } else {
                    Log.d(TAG, "No new settings from Firebase")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync settings from Firebase", e)
                // Settings remain unchanged - offline fallback works
            }
        }
    }

    /**
     * Update single setting value locally (for testing)
     * Note: For production, update via Firebase Console
     */
    fun updateSettingLocally(updater: (AdminSettings) -> AdminSettings) {
        try {
            val updated = updater(cachedSettings)
            cachedSettings = updated
            saveToPreferences(updated)
            _settings.value = updated
            Log.d(TAG, "Setting updated locally: ${updated.lastUpdated}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update setting locally", e)
        }
    }

    /**
     * Save settings to SharedPreferences (local cache)
     */
    private fun saveToPreferences(settings: AdminSettings) {
        try {
            val json = gson.toJson(settings)
            sharedPreferences.edit().apply {
                putString(KEY_SETTINGS, json)
                apply()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save settings to preferences", e)
        }
    }

    /**
     * Initialize automatic background sync
     * Syncs every hour (can be configured)
     */
    private fun initializeAutoSync() {
        scope.launch(Dispatchers.Default) {
            while (isActive) {
                try {
                    delay(FETCH_INTERVAL_SECONDS * 1000)
                    syncSettingsFromFirebase()
                } catch (e: Exception) {
                    Log.e(TAG, "Auto-sync failed", e)
                }
            }
        }
    }

    /**
     * Get default settings as JSON
     * Used as fallback in Firebase Remote Config
     */
    private fun getDefaultSettingsJson(): String {
        val defaults = AdminSettings()
        return gson.toJson(defaults)
    }

    /**
     * Cleanup resources
     * Call in Application.onTerminate() or Activity.onDestroy()
     */
    fun cleanup() {
        scope.cancel()
    }
}