package com.kouloundissa.twinstracker.data

import com.google.firebase.firestore.Query
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.map
import com.google.firebase.firestore.snapshots
import java.util.UUID

// --- Family Methods ---
suspend fun FirebaseRepository.addOrUpdateFamily(family: Family): Result<Unit> = runCatching {
    val currentUserId = auth.currentUser?.uid
        ?: throw IllegalStateException("User not authenticated")

    val isNew = family.id.isBlank()
    val familyWithIds = family.copy(
        id = family.id.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
        adminIds = if (isNew) listOf(currentUserId) else family.adminIds,
        memberIds = (family.memberIds + currentUserId).distinct(),
        updatedAt = System.currentTimeMillis()
    )

    db.collection("families")
        .document(familyWithIds.id)
        .set(familyWithIds)
        .await()
}

fun FirebaseRepository.streamFamilies(): Flow<List<Family>> {
    val currentUserId = auth.currentUser?.uid
        ?: throw IllegalStateException("User not authenticated")

    return db.collection("families")
        .whereArrayContains("memberIds", currentUserId)
        .orderBy("createdAt", Query.Direction.DESCENDING)
        .snapshots()
        .map { it.toObjects(Family::class.java) }
}

suspend fun FirebaseRepository.getFamilies(): Result<List<Family>> = runCatching {
    val currentUserId = auth.currentUser?.uid
        ?: throw IllegalStateException("User not authenticated")

    db.collection("families")
        .whereArrayContains("memberIds", currentUserId)
        .orderBy("createdAt", Query.Direction.DESCENDING)
        .get()
        .await()
        .toObjects(Family::class.java)
}

suspend fun FirebaseRepository.getFamilyById(familyId: String): Result<Family?> = runCatching {
    db.collection("families")
        .document(familyId)
        .get()
        .await()
        .toObject(Family::class.java)
}

suspend fun FirebaseRepository.addMemberToFamily(
    familyId: String,
    userIdToAdd: String
): Result<Unit> = runCatching {
    val familyRef = db.collection("families").document(familyId)
    // 1. Add member ID to family
    db.runTransaction { tx ->
        val snap = tx.get(familyRef)
        val current = snap.get("memberIds") as? List<String> ?: emptyList()
        if (!current.contains(userIdToAdd)) {
            tx.update(familyRef, "memberIds", current + userIdToAdd, "updatedAt", System.currentTimeMillis())
        }
    }.await()

    // 2. Fetch babies linked to this family
    val babiesSnapshot = db.collection("babies")
        .whereArrayContains("familyIds", familyId)  // assume familyIds field on Baby
        .get()
        .await()

    // 3. For each baby, add new parentId
    babiesSnapshot.documents.forEach { babyDoc ->
        val babyId = babyDoc.id
        val parentList = (babyDoc.get("parentIds") as? List<String>).orEmpty()
        if (!parentList.contains(userIdToAdd)) {
            db.collection("babies").document(babyId)
                .update(
                    "parentIds", parentList + userIdToAdd,
                    "updatedAt", System.currentTimeMillis()
                )
                .await()
        }
    }
}

suspend fun FirebaseRepository.removeMemberFromFamily(
    familyId: String,
    userIdToRemove: String
): Result<Unit> = runCatching {
    val familyRef = db.collection("families").document(familyId)
    db.runTransaction { tx ->
        val snapshot = tx.get(familyRef)
        val current = snapshot.get("memberIds") as? List<String> ?: emptyList()
        tx.update(
            familyRef,
            "memberIds", current - userIdToRemove,
            "updatedAt", System.currentTimeMillis()
        )
    }.await()
}

suspend fun FirebaseRepository.deleteFamily(familyId: String): Result<Unit> = runCatching {
    db.collection("families").document(familyId).delete().await()
}
/** Regenerates and persists a new invite code */
suspend fun FirebaseRepository.regenerateInviteCode(family: Family): Result<String> = runCatching {
    val newFamily = Family.withNewInviteCode(family)
    db.collection("families")
        .document(family.id)
        .update(
            "inviteCode", newFamily.inviteCode,
            "updatedAt", newFamily.updatedAt
        ).await()
    newFamily.inviteCode
}

/** Adds the current user to the family identified by inviteCode */
suspend fun FirebaseRepository.joinFamilyByCode(code: String, userId: String): Result<Unit> = runCatching {
    val query = db.collection("families")
        .whereEqualTo("inviteCode", code)
        .get()
        .await()

    if (query.isEmpty) throw Exception("Code invalide")

    val doc = query.documents.first()
    val famId = doc.id

    val settings = doc.get("settings") as? Map<*, *>
        ?: throw Exception("Paramètres manquants")

    val allowInvites = settings["allowMemberInvites"] as? Boolean ?: true
    if (!allowInvites) throw Exception("Invitations désactivées")

    db.collection("families")
        .document(famId)
        .update("memberIds", FieldValue.arrayUnion(userId))
        .await()
}

