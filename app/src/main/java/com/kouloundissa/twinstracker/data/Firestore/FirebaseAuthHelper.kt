package com.kouloundissa.twinstracker.data.Firestore

import com.google.firebase.auth.FirebaseAuth

class FirebaseAuthHelper(private val auth: FirebaseAuth) {
    fun getCurrentUserId(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("User not authenticated")

    fun isLoggedIn(): Boolean = auth.currentUser != null

    fun getCurrentEmail(): String? = auth.currentUser?.email

    suspend fun logout() = auth.signOut()
}