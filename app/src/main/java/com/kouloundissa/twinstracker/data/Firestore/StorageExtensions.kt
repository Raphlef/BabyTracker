package com.kouloundissa.twinstracker.data.Firestore

import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference

object FirebaseStorageUtils {
    fun buildPhotoStoragePath(
        entityType: String,
        entityId: String
    ): String = "${FirestoreConstants.StoragePaths.PHOTOS}/$entityType/$entityId${FirestoreConstants.StoragePaths.PHOTO_EXTENSION}"

    fun FirebaseStorage.getPhotoReference(
        entityType: String,
        entityId: String
    ): StorageReference = this.reference.child(buildPhotoStoragePath(entityType, entityId))
}