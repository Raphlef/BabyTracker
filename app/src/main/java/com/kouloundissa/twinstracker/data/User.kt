package com.kouloundissa.twinstracker.data

import com.google.firebase.firestore.IgnoreExtraProperties
import java.util.UUID

@IgnoreExtraProperties
data class User(
    val id: String = UUID.randomUUID().toString(),   // UID Firebase Auth
    val email: String = "",                          // Email principal (identifiant)
    val displayName: String = "",                    // Nom complet ou pseudo
    val photoUrl: String? = null,                    // URL de l’avatar
    val theme: Theme = Theme.SYSTEM,                 // Préférence de thème
    val notificationsEnabled: Boolean = true,        // Activer les notifications
    val locale: String = "en",                       // Code langue ("en","fr",…)
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/** Thèmes supportés dans l’application. */
enum class Theme {
    LIGHT,
    DARK,
    SYSTEM
}