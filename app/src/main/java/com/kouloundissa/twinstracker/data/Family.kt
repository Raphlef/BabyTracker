package com.kouloundissa.twinstracker.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.google.firebase.firestore.IgnoreExtraProperties
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
         * Generates a random 6-character invite code for the family
         */
        private fun generateInviteCode(): String {
            val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
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
    val allowMemberInvites: Boolean = true,          // Allow non-admin members to send invites
    val requireApprovalForNewMembers: Boolean = false, // Require admin approval for new members
    val sharedNotifications: Boolean = true,         // Enable shared notifications for family events
    val defaultPrivacy: PrivacyLevel = PrivacyLevel.FAMILY_ONLY, // Default privacy for new entries
)

/**
 * Privacy levels for family data sharing
 */
enum class PrivacyLevel {
    PRIVATE,      // Only the creator can see
    FAMILY_ONLY,  // All family members can see
    PUBLIC        // Can be shared outside the family
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
        color = Color(0xFFD32F2F)      // Red for administrators
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
