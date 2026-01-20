package com.kouloundissa.twinstracker.data.Firestore

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.kouloundissa.twinstracker.data.Settings.AdminSettingsManager
import com.kouloundissa.twinstracker.data.Settings.SettingsValidator

/**
 * Authorization wrapper for FirebaseRepository
 * Controls read/write operations based on admin settings
 */
class FirebaseAuthorizationManager(
    private val context: Context,
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore
) {

    private val TAG = "FirebaseAuthorizationManager"
    private val settingsManager = AdminSettingsManager.getInstance(context)

    /**
     * Check if read operations are allowed
     */
    fun canRead(): Boolean {
        val settings = settingsManager.getSettings()
        val allowed = SettingsValidator.canRead(settings)

        if (!allowed) {
            Log.w(TAG, "Read operation BLOCKED: allowRead = ${settings.allowRead}")
        }
        return allowed
    }

    /**
     * Check if write operations are allowed
     */
    fun canWrite(): Boolean {
        val settings = settingsManager.getSettings()
        val allowed = SettingsValidator.canWrite(settings)

        if (!allowed) {
            Log.w(TAG, "Write operation BLOCKED: allowWrite = ${settings.allowWrite}")
        }
        return allowed
    }

    /**
     * Check if photo authorization is allowed
     */
    fun canUploadPhoto(): Boolean {
        val settings = settingsManager.getSettings()
        val allowed = settings.photoAuthorization

        if (!allowed) {
            Log.w(TAG, "Photo upload BLOCKED: photoAuthorization = ${settings.photoAuthorization}")
        }
        return allowed
    }

    /**
     * Get current authorization status
     */
    fun getAuthorizationStatus(): AuthorizationStatus {
        val settings = settingsManager.getSettings()
        return AuthorizationStatus(
            canRead = settings.allowRead,
            canWrite = settings.allowWrite,
            adLevel = settings.getAdLevel().name,
            photoAuthorization = settings.photoAuthorization
        )
    }

    /**
     * Throw exception if read not allowed
     */
    fun requireRead(): String {
        if (!canRead()) {
            val message = "Read operations are disabled by admin settings"
            Log.e(TAG, message)
            throw PermissionDeniedException(message)
        }
        return "Read allowed"
    }

    /**
     * Throw exception if write not allowed
     */
    fun requireWrite(): String {
        if (!canWrite()) {
            val message = "Write operations are disabled by admin settings"
            Log.e(TAG, message)
            throw PermissionDeniedException(message)
        }
        return "Write allowed"
    }


    /**
     * Throw exception if photo upload not allowed
     */
    fun requirePhotoUpload(): String {
        if (!canUploadPhoto()) {
            val message = "Photo upload is disabled by admin settings"
            Log.e(TAG, message)
            throw PermissionDeniedException(message)
        }
        return "Photo upload allowed"
    }
}

/**
 * Custom exception for permission denied
 */
class PermissionDeniedException(message: String) : Exception(message)

/**
 * Data class for authorization status
 */
data class AuthorizationStatus(
    val canRead: Boolean,
    val canWrite: Boolean,
    val adLevel: String,
    val photoAuthorization: Boolean
)