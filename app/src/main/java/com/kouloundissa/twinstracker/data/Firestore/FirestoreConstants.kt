package com.kouloundissa.twinstracker.data.Firestore

object FirestoreConstants {
    object Collections {
        const val USERS = "users"
        const val BABIES = "babies"
        const val EVENTS = "events"
        const val FAMILIES = "families"
    }

    object Fields {
        // Common fields
        const val ID = "id"
        const val USER_ID = "userId"
        const val UPDATED_AT = "updatedAt"
        const val CREATED_AT = "createdAt"
        const val PHOTO_URL = "photoUrl"

        // Family fields
        const val ADMIN_IDS = "adminIds"
        const val MEMBER_IDS = "memberIds"
        const val VIEWER_IDS = "viewerIds"
        const val BABY_IDS = "babyIds"
        const val INVITE_CODE = "inviteCode"
        const val SETTINGS = "settings"

        // Baby fields
        const val PARENT_IDS = "parentIds"
        const val NAME = "name"
        const val FAMILY_IDS = "familyIds"

        // Event fields
        const val BABY_ID = "babyId"
        const val TIMESTAMP = "timestamp"
        const val EVENT_TYPE_STRING = "eventTypeString"

        // Settings fields
        const val ALLOW_MEMBER_INVITES = "allowMemberInvites"

        // User fields
        const val DISPLAY_NAME = "displayName"
        const val EMAIL = "email"
    }

    object StoragePaths {
        const val PHOTOS = "photos"
        const val PHOTO_EXTENSION = ".jpg"
    }
    object Cache {
        const val ONE_HOUR_MILLIS = 60 * 60 * 1000L
        const val TEN_HOURS_MILLIS = 10 * ONE_HOUR_MILLIS
    }
}
