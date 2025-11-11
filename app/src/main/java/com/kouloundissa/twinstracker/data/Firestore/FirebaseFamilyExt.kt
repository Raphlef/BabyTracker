package com.kouloundissa.twinstracker.data.Firestore

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Filter
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.kouloundissa.twinstracker.data.Family
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await
import java.util.UUID

// --- Family Methods ---
suspend fun FirebaseRepository.addOrUpdateFamily(family: Family): Result<Family> = runCatching {
    val currentUserId = authHelper.getCurrentUserId()

    val isNew = family.id.isBlank()
    val familyWithIds = family.copy(
        id = family.id.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
        adminIds = if (isNew) listOf(currentUserId) else family.adminIds,
        memberIds = (family.memberIds + currentUserId).distinct(),
        updatedAt = FirestoreTimestampUtils.getCurrentTimestamp()
    )

    db.collection(FirestoreConstants.Collections.FAMILIES)
        .document(familyWithIds.id)
        .set(familyWithIds)
        .await()

    familyWithIds
}

fun FirebaseRepository.streamFamilies(): Flow<List<Family>> {
    val currentUserId = authHelper.getCurrentUserId()

    return callbackFlow {
        val listeners = mutableListOf<ListenerRegistration>()
        val familiesMap = mutableMapOf<String, Family>()

        // Build query with combined member/admin filter
        val memberQuery = db.collection(FirestoreConstants.Collections.FAMILIES)
            .whereArrayContains(FirestoreConstants.Fields.MEMBER_IDS, currentUserId)
            .orderBy(FirestoreConstants.Fields.CREATED_AT, Query.Direction.DESCENDING)

        val adminQuery = db.collection(FirestoreConstants.Collections.FAMILIES)
            .whereArrayContains(FirestoreConstants.Fields.ADMIN_IDS, currentUserId)
            .orderBy(FirestoreConstants.Fields.CREATED_AT, Query.Direction.DESCENDING)
        fun emitSortedFamilies() {
            val sortedFamilies = familiesMap.values.sortedByDescending { it.createdAt }
            trySend(sortedFamilies)
        }
        // Member families listener
        val memberListener = memberQuery.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }

            snapshot?.documents?.forEach { doc ->
                doc.toObjectSafely<Family>()?.let { family ->
                    familiesMap[family.id] = family
                }
            }

            emitSortedFamilies()
        }

        // Admin families listener
        val adminListener = adminQuery.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }

            snapshot?.documents?.forEach { doc ->
                doc.toObjectSafely<Family>()?.let { family ->
                    familiesMap[family.id] = family
                }
            }

            emitSortedFamilies()
        }

        listeners.add(memberListener)
        listeners.add(adminListener)

        awaitClose {
            listeners.forEach { it.remove() }
        }
    }.distinctUntilChanged()
}

suspend fun FirebaseRepository.getCurrentUserFamilies(): Result<List<Family>> = runCatching {
    val currentUserId = authHelper.getCurrentUserId()

    db.collection(FirestoreConstants.Collections.FAMILIES)
        .where(
            Filter.or(
                Filter.arrayContains(FirestoreConstants.Fields.MEMBER_IDS, currentUserId),
                Filter.arrayContains(FirestoreConstants.Fields.ADMIN_IDS, currentUserId)
            )
        )
        .get()
        .await()
        .toObjects(Family::class.java)
}

suspend fun FirebaseRepository.getFamilies(): Result<List<Family>> = runCatching {
    val currentUserId = authHelper.getCurrentUserId()

    db.collection(FirestoreConstants.Collections.FAMILIES)
        .whereArrayContains(FirestoreConstants.Fields.MEMBER_IDS, currentUserId)
        .orderBy(FirestoreConstants.Fields.CREATED_AT, Query.Direction.DESCENDING)
        .get()
        .await()
        .toObjects(Family::class.java)
}

suspend fun FirebaseRepository.getFamilyById(familyId: String): Result<Family?> = runCatching {
    FirebaseValidators.validateFamilyId(familyId)

    db.collection(FirestoreConstants.Collections.FAMILIES)
        .document(familyId)
        .get()
        .await()
        .toObjectSafely()
}

suspend fun FirebaseRepository.addMemberToFamily(
    familyId: String,
    userIdToAdd: String
): Result<Unit> = runCatching {
    FirebaseValidators.validateFamilyId(familyId)
    require(userIdToAdd.isNotBlank()) { "User ID cannot be empty" }

    val familyRef = db.collection(FirestoreConstants.Collections.FAMILIES)
        .document(familyId)

    // 1. Add member to family
    queryHelper.updateArrayField(
        FirestoreConstants.Collections.FAMILIES,
        familyId,
        FirestoreConstants.Fields.MEMBER_IDS,
        userIdToAdd,
        ArrayOperation.Add
    )

    // 2. Fetch babies linked to this family
    val babiesSnapshot = db.collection(FirestoreConstants.Collections.BABIES)
        .whereArrayContains(FirestoreConstants.Fields.FAMILY_IDS, familyId)
        .get()
        .await()

    // 3. Add new parent ID to each baby
    val batch = db.batch()
    babiesSnapshot.documents.forEach { babyDoc ->
        val babyId = babyDoc.id
        val parentList = (babyDoc.get(FirestoreConstants.Fields.PARENT_IDS) as? List<String>)
            .orEmpty()

        if (!parentList.contains(userIdToAdd)) {
            batch.update(
                db.collection(FirestoreConstants.Collections.BABIES).document(babyId),
                FirestoreConstants.Fields.PARENT_IDS,
                parentList + userIdToAdd,
                FirestoreConstants.Fields.UPDATED_AT,
                FirestoreTimestampUtils.getCurrentTimestamp()
            )
        }
    }
    batch.commit().await()
}

suspend fun FirebaseRepository.removeMemberFromFamily(
    familyId: String,
    userIdToRemove: String
): Result<Unit> = runCatching {
    FirebaseValidators.validateFamilyId(familyId)
    require(userIdToRemove.isNotBlank()) { "User ID cannot be empty" }

    queryHelper.updateArrayField(
        FirestoreConstants.Collections.FAMILIES,
        familyId,
        FirestoreConstants.Fields.MEMBER_IDS,
        userIdToRemove,
        ArrayOperation.Remove
    )
}

suspend fun FirebaseRepository.deleteFamily(familyId: String): Result<Unit> = runCatching {
    FirebaseValidators.validateFamilyId(familyId)

    db.collection(FirestoreConstants.Collections.FAMILIES)
        .document(familyId)
        .delete()
        .await()
}

suspend fun FirebaseRepository.regenerateInviteCode(family: Family): Result<String> = runCatching {
    val newFamily = Family.withNewInviteCode(family)

    db.collection(FirestoreConstants.Collections.FAMILIES)
        .document(family.id)
        .update(
            FirestoreConstants.Fields.INVITE_CODE, newFamily.inviteCode,
            FirestoreConstants.Fields.UPDATED_AT, newFamily.updatedAt
        )
        .await()

    newFamily.inviteCode
}

suspend fun FirebaseRepository.joinFamilyByCode(
    code: String,
    userId: String
): Result<Unit> = runCatching {
    require(code.isNotBlank()) { "Invite code cannot be empty" }
    require(userId.isNotBlank()) { "User ID cannot be empty" }

    val query = db.collection(FirestoreConstants.Collections.FAMILIES)
        .whereEqualTo(FirestoreConstants.Fields.INVITE_CODE, code)
        .get()
        .await()

    if (query.isEmpty) throw Exception("Code invalide")

    val doc = query.documents.first()
    val famId = doc.id

    val settings = (doc.get(FirestoreConstants.Fields.SETTINGS) as? Map<*, *>)
        ?: throw Exception("Paramètres manquants")

    val allowInvites = settings[FirestoreConstants.Fields.ALLOW_MEMBER_INVITES] as? Boolean ?: true
    if (!allowInvites) throw Exception("Invitations désactivées")

    db.collection(FirestoreConstants.Collections.FAMILIES)
        .document(famId)
        .update(
            FirestoreConstants.Fields.MEMBER_IDS,
            FieldValue.arrayUnion(userId),
            FirestoreConstants.Fields.UPDATED_AT,
            FirestoreTimestampUtils.getCurrentTimestamp()
        )
        .await()
}

