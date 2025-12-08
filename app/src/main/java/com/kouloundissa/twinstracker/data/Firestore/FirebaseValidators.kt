package com.kouloundissa.twinstracker.data.Firestore

import android.util.Patterns
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
        val trimmed = email.trim()

        when {
            trimmed.isEmpty() ->
                throw IllegalArgumentException("Email ne peut pas être vide")
            trimmed.length > 254 ->
                throw IllegalArgumentException("Email trop long")
            !Patterns.EMAIL_ADDRESS.matcher(trimmed).matches() ->
                throw IllegalArgumentException("Format email invalide")
        }
    }
    fun validateEmailWithMessage(email: String): String? = try {
        validateEmail(email)
        null
    } catch (e: IllegalArgumentException) {
        e.localizedMessage
    }

}
