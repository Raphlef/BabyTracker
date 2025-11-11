package com.kouloundissa.twinstracker.data.Firestore

import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot

inline fun <reified T> DocumentSnapshot.toObjectSafely(tag: String = "Firestore"): T? {
    return try {
        this.toObject(T::class.java)
    } catch (e: Exception) {
        Log.e(tag, "Error deserializing document ${this.id}", e)
        null
    }
}

fun Map<String, Any?>.withUpdatedAt(): Map<String, Any?> =
    this + (FirestoreConstants.Fields.UPDATED_AT to FirestoreTimestampUtils.getCurrentTimestamp())

fun Map<String, Any?>.withUserIdAndTimestamp(userId: String): Map<String, Any?> =
    this.withUpdatedAt() + (FirestoreConstants.Fields.USER_ID to userId)