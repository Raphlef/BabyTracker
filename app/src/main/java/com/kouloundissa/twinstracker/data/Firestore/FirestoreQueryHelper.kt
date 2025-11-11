package com.kouloundissa.twinstracker.data.Firestore

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await

class FirestoreQueryHelper(val db: FirebaseFirestore) {

    /**
     * Executes a Firestore query and handles chunking for whereIn operations.
     * Firestore limits whereIn to 10 items, so this splits into chunks.
     */
    suspend inline fun <reified T> queryByIds(
        collectionPath: String,
        ids: List<String>
    ): List<T> {
        if (ids.isEmpty()) return emptyList()

        return ids.chunked(10).flatMap { chunk ->
            db.collection(collectionPath)
                .whereIn(FieldPath.documentId(), chunk)
                .get()
                .await()
                .toObjects(T::class.java)
        }
    }

    /**
     * Streams data from Firestore using snapshot listeners for multiple chunks.
     */
    fun <T> streamByIds(
        collectionPath: String,
        ids: List<String>,
        mapFn: (DocumentSnapshot) -> T?
    ): Flow<List<T>> {
        if (ids.isEmpty()) return flowOf(emptyList())

        return callbackFlow {
            val listeners = mutableListOf<ListenerRegistration>()
            val itemsMap = mutableMapOf<String, T>()

            ids.chunked(10).forEach { chunk ->
                val listener = db.collection(collectionPath)
                    .whereIn(FieldPath.documentId(), chunk)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            close(error)
                            return@addSnapshotListener
                        }

                        snapshot?.documents?.forEach { doc ->
                            mapFn(doc)?.let { item ->
                                itemsMap[doc.id] = item
                            }
                        }

                        val result = itemsMap.values
                            .filter { doc -> doc.toString() in chunk }
                            .toList()

                        trySend(result)
                    }
                listeners.add(listener)
            }

            awaitClose {
                listeners.forEach { it.remove() }
            }
        }
    }

    /**
     * Performs array operations (add/remove) safely using transactions.
     */
    suspend fun updateArrayField(
        collectionPath: String,
        documentId: String,
        fieldName: String,
        value: Any,
        operation: ArrayOperation
    ) {
        val docRef = db.collection(collectionPath).document(documentId)

        db.runTransaction { tx ->
            val snapshot = tx.get(docRef)
            val currentList = ((snapshot.get(fieldName) as? List<*>) ?: emptyList<Any>())
                .toMutableList() as MutableList<Any>

            when (operation) {
                is ArrayOperation.Add -> {
                    if (!currentList.contains(value)) {
                        currentList.add(element = value)
                    }
                }
                is ArrayOperation.Remove -> {
                    currentList.remove(value)
                }
            }

            tx.update(docRef, fieldName, currentList, FirestoreConstants.Fields.UPDATED_AT, FirestoreTimestampUtils.getCurrentTimestamp())
        }.await()
    }
}

sealed class ArrayOperation {
    object Add : ArrayOperation()
    object Remove : ArrayOperation()
}
