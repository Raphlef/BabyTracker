package com.kouloundissa.twinstracker.data

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.google.firebase.firestore.IgnoreExtraProperties
import com.kouloundissa.twinstracker.R
import com.kouloundissa.twinstracker.data.Firestore.FirestoreConstants
import java.text.Normalizer
import java.util.Random
import java.util.UUID

/**
 * Represents a family unit that can contain multiple users and babies.
 * This allows for shared tracking and management of babies across family members.
 */
@IgnoreExtraProperties
data class Family(
    val id: String = UUID.randomUUID().toString(),   // Unique family identifier
    val name: String = "",                           // Family name or label (e.g., "Smith Family")
    val description: String? = null,                 // Optional family description
    val inviteCode: String = generateInviteCode(),   // Code for inviting new members
    val adminIds: List<String> = emptyList(),        // User IDs with admin privileges
    val memberIds: List<String> = emptyList(),       // All family member User IDs (includes admins)
    val viewerIds: List<String> = emptyList(),       // all just viewer user Ids
    val babyIds: List<String> = emptyList(),         // Baby IDs associated with this family
    val active: Boolean = true,                    // Whether the family is active
    val settings: FamilySettings = FamilySettings(), // Family-specific settings
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    constructor() : this(
        id = "",
        name = "",
        description = null,
        inviteCode = "",
        adminIds = emptyList(),
        memberIds = emptyList(),
        babyIds = emptyList(),
        active = true,
        settings = FamilySettings(),
        createdAt = 0L,
        updatedAt = 0L
    )

    companion object {
        /**
         * Generates a random 6-character invite code for the family.
         * Uses a limited character set to avoid visual ambiguity:
         * - Excludes: 0, O (zero vs letter O)
         * - Excludes: 1, I, L (one vs letter I vs letter L)
         * - Excludes: 5, S (five vs letter S)
         * - Excludes: 2, Z (two vs letter Z)
         * - Excludes: 8, B (eight vs letter B)
         * This results in a clear, unambiguous code that's easy to read and share.
         */
        private fun generateInviteCode(): String {
            // Character set optimized for readability (23 characters)
            // Uppercase letters: A C D E F G H J K M N P Q R T U V W X Y
            // Numbers: 3 4 6 7 9
            val chars = "ACDEFGHJKMNPQRTUVWXY3479"

            return (1..6)
                .map { chars.random() }
                .joinToString("")
        }

        /** Return a copy with a fresh inviteCode and updated timestamp */
        fun withNewInviteCode(family: Family): Family =
            family.copy(
                inviteCode = generateInviteCode(),
                updatedAt = System.currentTimeMillis()
            )
    }
}

/**
 * Settings specific to a family that can be configured by family admins
 */
@IgnoreExtraProperties
data class FamilySettings(
    val requireApprovalForNewMembers: Boolean = false, // Require admin approval for new members
    //val sharedNotifications: Boolean = true,         // Enable shared notifications for family events
    val defaultPrivacy: PrivacyLevel = PrivacyLevel.FAMILY_ONLY, // Default privacy for new entries
)

/**
 * Privacy levels for family data sharing
 */
enum class PrivacyLevel {
    PRIVATE,      // Noone can join
    FAMILY_ONLY,  // People with code can join
}

object PseudoGenerator {

    // Resource ID arrays (static, defined once)
     val prefixIds = intArrayOf(
        R.string.pseudo_prefix_1, R.string.pseudo_prefix_2, R.string.pseudo_prefix_3,
        R.string.pseudo_prefix_4, R.string.pseudo_prefix_5, R.string.pseudo_prefix_6,
        R.string.pseudo_prefix_7, R.string.pseudo_prefix_8, R.string.pseudo_prefix_9,
        R.string.pseudo_prefix_10, R.string.pseudo_prefix_11, R.string.pseudo_prefix_12,
        R.string.pseudo_prefix_13, R.string.pseudo_prefix_14, R.string.pseudo_prefix_15,
        R.string.pseudo_prefix_16, R.string.pseudo_prefix_17, R.string.pseudo_prefix_18,
        R.string.pseudo_prefix_19, R.string.pseudo_prefix_20, R.string.pseudo_prefix_21,
        R.string.pseudo_prefix_22, R.string.pseudo_prefix_23, R.string.pseudo_prefix_24
    )

     val suffixIds = intArrayOf(
        R.string.pseudo_suffix_1, R.string.pseudo_suffix_2, R.string.pseudo_suffix_3,
        R.string.pseudo_suffix_4, R.string.pseudo_suffix_5, R.string.pseudo_suffix_6,
        R.string.pseudo_suffix_7, R.string.pseudo_suffix_8, R.string.pseudo_suffix_9,
        R.string.pseudo_suffix_10, R.string.pseudo_suffix_11, R.string.pseudo_suffix_12,
        R.string.pseudo_suffix_13, R.string.pseudo_suffix_14, R.string.pseudo_suffix_15,
        R.string.pseudo_suffix_16, R.string.pseudo_suffix_17, R.string.pseudo_suffix_18,
        R.string.pseudo_suffix_19, R.string.pseudo_suffix_20, R.string.pseudo_suffix_21,
        R.string.pseudo_suffix_22, R.string.pseudo_suffix_23, R.string.pseudo_suffix_24
    )

     val familyIds = intArrayOf(
        R.string.pseudo_family_1, R.string.pseudo_family_2, R.string.pseudo_family_3,
        R.string.pseudo_family_4, R.string.pseudo_family_5, R.string.pseudo_family_6,
        R.string.pseudo_family_7, R.string.pseudo_family_8, R.string.pseudo_family_9,
        R.string.pseudo_family_10, R.string.pseudo_family_11, R.string.pseudo_family_12,
        R.string.pseudo_family_13, R.string.pseudo_family_14, R.string.pseudo_family_15,
        R.string.pseudo_family_16, R.string.pseudo_family_17, R.string.pseudo_family_18,
        R.string.pseudo_family_19, R.string.pseudo_family_20, R.string.pseudo_family_21,
        R.string.pseudo_family_22, R.string.pseudo_family_23, R.string.pseudo_family_24
    )
    fun generateCoolPseudo(context: Context, email: String): String {
        val seed = email.lowercase().hashCode().toLong()
        val random = Random(seed)

        // Load localized arrays automatically based on app language
        val prefix = context.getString(prefixIds[random.nextInt(prefixIds.size)])
        val suffix = context.getString(suffixIds[random.nextInt(suffixIds.size)])
        val family = context.getString(familyIds[random.nextInt(familyIds.size)])


        if (!email.contains("@")) {
            return "$family$suffix"
        }

        val localPart = email
            .substringBefore("@")
            .lowercase()
            .normalizeUnicode()
            .replace(Regex("[._\\-+]"), " ")

        val nameParts = localPart
            .split(" ")
            .filter { it.isNotEmpty() && it.length > 1 }

        if (nameParts.isEmpty()) {
            return "$family$suffix"
        }

        val firstName = nameParts[0].replaceFirstChar { it.uppercase() }

        // Multiple combination strategies for variety
        val strategies = listOf(
            { "$firstName $suffix" },
            { "$prefix $firstName" },
            { "$family $firstName" },
            { "$firstName $family" },
            { "$prefix $firstName $suffix" },
            { "$firstName $family $suffix" },
            { "$firstName $family" }
        )

        val strategyIndex = random.nextInt(strategies.size)
        return strategies[strategyIndex].invoke()
    }

    /**
     * Normalize unicode characters (remove accents) for better compatibility.
     */
    private fun String.normalizeUnicode(): String {
        return Normalizer.normalize(this, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}"), "")
    }
}

@IgnoreExtraProperties
data class FamilyUser(
    val user: User,
    val role: FamilyRole = FamilyRole.MEMBER
) {
    // Convenience properties to access User fields
    val userId: String get() = user.id
    val email: String get() = user.email
    val displayName: String get() = user.displayName
    val photoUrl: String? get() = user.photoUrl

    val displayNameOrEmail: String
        get() = user.displayName.ifBlank { user.email }
}

/**
 * Roles within a family structure
 */
enum class FamilyRole(
    val label: String,
    val icon: ImageVector,
    val color: Color
) {
    ADMIN(
        label = "Admin",
        icon = Icons.Default.Shield,
        color = Color(0xFFffa500)      // orange for administrators
    ),
    MEMBER(
        label = "Membre",
        icon = Icons.Default.Person,
        color = Color(0xFF1976D2)      // Blue for regular members
    ),
    VIEWER(
        label = "Lecteur",
        icon = Icons.Default.Visibility,
        color = Color(0xFF388E3C)      // Green for viewers
    );
}

/**
 * Validates if a family can accept new members based on privacy settings
 * @throws IllegalArgumentException if privacy settings forbid joining
 */
fun Family.validateCanAcceptMembers() {
    when (settings.defaultPrivacy) {
        PrivacyLevel.PRIVATE ->
            throw IllegalArgumentException("Cette famille est privée et n'accepte pas de nouveaux membres")

        PrivacyLevel.FAMILY_ONLY -> {} // Allow
    }
}

/**
 * Validates if a family is available for joining (not deleted, etc.)
 * @throws IllegalArgumentException if family cannot be joined
 */
fun Family.validateAvailableForJoining() {
    if (!active) {
        throw IllegalArgumentException("Cette famille n'existe plus ou a été désactivée")
    }
}

/**
 * Checks if user is already a member or viewer of this family
 * @throws IllegalArgumentException if user already joined
 */
fun Family.validateUserNotAlreadyMember(userId: String) {
    if (adminIds.contains(userId) || memberIds.contains(userId) || viewerIds.contains(userId)) {
        throw IllegalArgumentException("Vous êtes déjà membre de cette famille")
    }
}

/**
 * Determines the role for a new member based on family settings
 */
fun Family.determineRoleForNewMember(): FamilyRole {
    return if (settings.requireApprovalForNewMembers) {
        FamilyRole.VIEWER  // Pending approval, add as VIEWER
    } else {
        FamilyRole.MEMBER  // Auto-approve, add as MEMBER
    }
}

/**
 * Gets the field name and list for updating based on role
 */
fun getRoleUpdateInfo(role: FamilyRole): Pair<String, String> {
    return when (role) {
        FamilyRole.VIEWER ->
            Pair(FirestoreConstants.Fields.VIEWER_IDS, "viewer")

        FamilyRole.MEMBER ->
            Pair(FirestoreConstants.Fields.MEMBER_IDS, "member")

        else ->
            throw IllegalArgumentException("Invalid role: $role")
    }
}
