package com.kouloundissa.twinstracker.data.Firestore

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.kouloundissa.twinstracker.data.Firestore.FirebaseStorageUtils.getPhotoReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class FirebasePhotoHelper(
    private val storage: FirebaseStorage,
    private val db: FirebaseFirestore,
    private val context: Context,
    private val repo: FirebaseRepository
) {
    suspend fun uploadPhoto(
        entityType: String,
        entityId: String,
        uri: Uri
    ): String = withContext(Dispatchers.IO) {
        val userId = repo.getCurrentUserId()
        val photoPath = FirebaseStorageUtils.buildPhotoStoragePath(entityType, entityId)

        Log.d("PhotoUpload", "Starting upload: $photoPath for userId=$userId")

        // Grant persistable URI permission
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            Log.d("PhotoUpload", "Persistable permission granted for $uri")
        } catch (e: SecurityException) {
            Log.w("PhotoUpload", "Persistable permission failed: ${e.message}")
        }

        // Upload file
        val storageRef = storage.getPhotoReference(entityType, entityId)
        context.contentResolver.openInputStream(uri).use { stream ->
            requireNotNull(stream) { "Unable to open image URI: $uri" }
            storageRef.putStream(stream).await()
            Log.d("PhotoUpload", "Upload complete")
        }

        // Get download URL
        val downloadUrl = storageRef.downloadUrl.await().toString()
        Log.d("PhotoUpload", "Download URL: $downloadUrl")

        downloadUrl
    }

    suspend fun deletePhoto(
        entityType: String,
        entityId: String
    ) = withContext(Dispatchers.IO) {
        repo.getCurrentUserId() // Verify auth
        val photoRef = storage.getPhotoReference(entityType, entityId)

        try {
            photoRef.delete().await()
            Log.d("PhotoDelete", "Photo deleted: $entityType/$entityId")
        } catch (e: StorageException) {
            if (e.errorCode != StorageException.ERROR_OBJECT_NOT_FOUND) throw e
            Log.d("PhotoDelete", "Photo not found, skipping")
        }

        // Remove from Firestore
        try {
            db.collection(entityType)
                .document(entityId)
                .update(
                    FirestoreConstants.Fields.PHOTO_URL,
                    FieldValue.delete(),
                    FirestoreConstants.Fields.UPDATED_AT,
                    FirestoreTimestampUtils.getCurrentTimestamp()
                )
                .await()
        } catch (e: FirebaseFirestoreException) {
            if (e.code != FirebaseFirestoreException.Code.NOT_FOUND) throw e
            Log.d("PhotoDelete", "Document not found")
        }
    }
}