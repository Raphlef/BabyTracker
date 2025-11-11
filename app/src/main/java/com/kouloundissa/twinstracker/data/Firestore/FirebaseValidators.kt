package com.kouloundissa.twinstracker.data.Firestore

import java.util.Date
import java.util.Locale

object FirebaseValidators {
    fun validateUserId(userId: String?): String =
        userId ?: throw IllegalStateException("User not authenticated")

    fun validateBabyId(babyId: String) {
        require(babyId.isNotBlank()) { "Baby ID cannot be empty" }
    }

    fun validateDateRange(startDate: Date, endDate: Date) {
        require(startDate.before(endDate)) { "Start date must be before end date" }
    }

    fun validateFamilyId(familyId: String) {
        require(familyId.isNotBlank()) { "Family ID cannot be empty" }
    }

    fun normalizeEmail(email: String): String =
        email.trim().lowercase(Locale.getDefault())

    fun validateEmail(email: String) {
        require(email.isNotBlank()) { "Email cannot be empty" }
    }
}
