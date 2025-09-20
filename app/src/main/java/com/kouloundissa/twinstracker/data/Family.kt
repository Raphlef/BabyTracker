package com.kouloundissa.twinstracker.data

import java.util.UUID

/**
 * Represents a family unit that can contain multiple users and babies.
 * This allows for shared tracking and management of babies across family members.
 */
data class Family(
    val id: String = UUID.randomUUID().toString(),   // Unique family identifier
    val name: String = "",                           // Family name or label (e.g., "Smith Family")
    val description: String? = null,                 // Optional family description
    val inviteCode: String = generateInviteCode(),   // Code for inviting new members
    val adminIds: List<String> = emptyList(),        // User IDs with admin privileges
    val memberIds: List<String> = emptyList(),       // All family member User IDs (includes admins)
    val babyIds: List<String> = emptyList(),         // Baby IDs associated with this family
    val active: Boolean = true,                    // Whether the family is active
    val settings: FamilySettings = FamilySettings(), // Family-specific settings
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
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

/**
 * Roles within a family structure
 */
enum class FamilyRole {
    ADMIN,        // Can manage family settings, add/remove members
    MEMBER,       // Can view and contribute to family data
    VIEWER        // Can only view family data
}