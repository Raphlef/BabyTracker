package com.kouloundissa.twinstracker.data.Firestore

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.kouloundissa.twinstracker.data.Family
import com.kouloundissa.twinstracker.data.FamilyRole
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await
import java.util.UUID

// --- Family Methods ---
suspend fun FirebaseRepository.addOrUpdateFamily(family: Family): Result<Family> = runCatching {
    val currentUserId = getCurrentUserId()

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
    val currentUserId = getCurrentUserId()

    return callbackFlow {
        val listeners = mutableListOf<ListenerRegistration>()
        val familiesMap = mutableMapOf<String, Family>()

        // Define roles and their corresponding field names
        val roles = listOf(
            FirestoreConstants.Fields.MEMBER_IDS to "member",
            FirestoreConstants.Fields.ADMIN_IDS to "admin",
            FirestoreConstants.Fields.VIEWER_IDS to "viewer"
        )

        // Reusable listener creation function
        fun createFamilyListener(fieldName: String) = db.collection(FirestoreConstants.Collections.FAMILIES)
            .whereArrayContains(fieldName, currentUserId)
            .orderBy(FirestoreConstants.Fields.CREATED_AT, Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                snapshot?.documents?.forEach { doc ->
                    doc.toObjectSafely<Family>()?.let { family ->
                        familiesMap[family.id] = family
                    }
                }
                val sortedFamilies = familiesMap.values.sortedByDescending { it.createdAt }
                trySend(sortedFamilies)
            }

        // Add listeners for all roles
        roles.forEach { (fieldName, _) ->
            listeners.add(createFamilyListener(fieldName))
        }

        awaitClose {
            listeners.forEach { it.remove() }
        }
    }.distinctUntilChanged()
}


suspend fun FirebaseRepository.addMemberToFamily(
    familyId: String,
    userIdToAdd: String
): Result<Unit> = runCatching {
    FirebaseValidators.validateFamilyId(familyId)
    require(userIdToAdd.isNotBlank()) { "User ID cannot be empty" }

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

suspend fun FirebaseRepository.removeUserFromAllRoles(familyId: String, userId: String): Result<Unit> {
    return runCatching {
        FirebaseValidators.validateFamilyId(familyId)
        require(userId.isNotBlank()) { "User ID cannot be empty" }

        val collection = FirestoreConstants.Collections.FAMILIES

        // Remove user from MEMBER_IDS
        queryHelper.updateArrayField(
            collection,
            familyId,
            FirestoreConstants.Fields.MEMBER_IDS,
            userId,
            ArrayOperation.Remove
        )

        // Remove user from ADMIN_IDS
        queryHelper.updateArrayField(
            collection,
            familyId,
            FirestoreConstants.Fields.ADMIN_IDS,
            userId,
            ArrayOperation.Remove
        )

        // Remove user from VIEWER_IDS
        queryHelper.updateArrayField(
            collection,
            familyId,
            FirestoreConstants.Fields.VIEWER_IDS,
            userId,
            ArrayOperation.Remove
        )
    }
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
    val family = doc.toObject(Family::class.java)
        ?: throw Exception("Family data corrupted")

    // ✅ Check if already a member
    if (family.memberIds.contains(userId) || family.viewerIds.contains(userId)) {
        throw Exception("Vous êtes déjà membre de cette famille")
    }

    // ✅ Determine role based on settings
    val newRole = if (family.settings.requireApprovalForNewMembers) {
        FamilyRole.VIEWER  // Pending approval, add as VIEWER
    } else {
        FamilyRole.MEMBER  // Auto-approve, add as MEMBER
    }

    // ✅ Update family with new user in appropriate list
    val updatedFamily = when (newRole) {
        FamilyRole.VIEWER -> family.copy(
            viewerIds = (family.viewerIds + userId).distinct(),
            updatedAt = System.currentTimeMillis()
        )
        FamilyRole.MEMBER -> family.copy(
            memberIds = (family.memberIds + userId).distinct(),
            updatedAt = System.currentTimeMillis()
        )
        else -> throw Exception("Invalid role for joining")
    }

    db.collection(FirestoreConstants.Collections.FAMILIES)
        .document(family.id)
        .update(
            when (newRole) {
                FamilyRole.VIEWER -> FirestoreConstants.Fields.VIEWER_IDS
                FamilyRole.MEMBER -> FirestoreConstants.Fields.MEMBER_IDS
                else -> throw Exception("Invalid role")
            },
            FieldValue.arrayUnion(userId),
            FirestoreConstants.Fields.UPDATED_AT,
            FirestoreTimestampUtils.getCurrentTimestamp()
        )
        .await()

    updatedFamily
}
