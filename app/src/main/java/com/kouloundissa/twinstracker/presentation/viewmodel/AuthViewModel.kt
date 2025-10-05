package com.kouloundissa.twinstracker.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.kouloundissa.twinstracker.data.FirebaseRepository
import com.kouloundissa.twinstracker.data.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repository: FirebaseRepository
) : ViewModel() {

    private val _userProfile = MutableStateFlow<User?>(null)
    val userProfile: StateFlow<User?> = _userProfile.asStateFlow()

    private val _state = MutableStateFlow(
        AuthState(
            email = repository.getCurrentUserEmail().orEmpty()
        )
    )
    val state: StateFlow<AuthState> = _state.asStateFlow()

    private val _oneTimeEvent = MutableSharedFlow<AuthEvent>(replay = 1)
    val oneTimeEventFlow: SharedFlow<AuthEvent> = _oneTimeEvent.asSharedFlow()

    init {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)

            // Pré-remplir l’email si déjà connecté
            repository.getCurrentUserEmail()?.let { email ->
                _state.update { it.copy(email = email) }
            }

            val remembered = repository.isRemembered()
            _state.update { it.copy(rememberMe = remembered) }

            if (remembered && repository.isUserLoggedIn()) {
                performPostAuthSetup()
            }
            _state.value = _state.value.copy(isLoading = false)
        }
    }
    // Set an error message in state
    fun onError(message: String) {
        _state.update { it.copy(error = message, isLoading = false) }
    }

    // Clear the error (after UI displays it)
    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun loginWithGoogle() {
        viewModelScope.launch {
            _oneTimeEvent.emit(AuthEvent.StartGoogleSignIn)
        }
    }

    // Call this from your Activity once Google returns an ID token
    fun handleGoogleSignInResult(idToken: String) {
        _state.value = _state.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            try {
                repository.signInWithGoogle(idToken)
                if (_state.value.rememberMe) repository.saveUserSession()
                performPostAuthSetup()
            } catch (e: Exception) {
                _state.update {
                    it.copy(isLoading = false, error = "Google sign-in failed: ${e.message}")
                }
            }
        }
    }


    fun onEmailChange(email: String) {
        _state.value = _state.value.copy(email = email)
    }

    fun onPasswordChange(password: String) {
        _state.value = _state.value.copy(password = password)
    }

    fun onRememberMeChanged(value: Boolean) {
        _state.value = _state.value.copy(rememberMe = value)
    }

    fun getCurrentUserEmail(): String? {
        return repository.getCurrentUserEmail()
    }

    fun login() {
        _state.value = _state.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            try {
                repository.login(_state.value.email, _state.value.password)
                // Si Remember Me est activé, stocker la session/token via repo ou DataStore
                if (_state.value.rememberMe) {
                    repository.saveUserSession()
                }
                performPostAuthSetup()
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        isAuthenticated = false,
                        error = "Échec de la connexion : ${e.message}"
                    )
                }
            }
        }
    }

    fun register() {
        val email = _state.value.email.trim()
        val password = _state.value.password

        // Validations simples
        if (email.isEmpty() || password.isEmpty()) {
            val msg = when {
                email.isEmpty() -> "Email ne peut pas être vide"
                password.isEmpty() -> "Mot de passe ne peut pas être vide"
                else -> null
            }
            _state.update { it.copy(error = msg) }
            return
        }

        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                repository.register(email, password)
                performPostAuthSetup()
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        isAuthenticated = false,
                        error = "Échec de l'inscription: ${e.message}"
                    )
                }
            }
        }
    }

    fun loadUserProfile() {
        viewModelScope.launch {
            try {
                val profile = repository.getCurrentUserProfile()
                _userProfile.value = profile
            } catch (_: Exception) {
            }
        }
    }

    fun updateUserProfile(updates: Map<String, Any?>) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                repository.updateUserProfile(updates)
                // Recharger le profil
                val refreshed = repository.getCurrentUserProfile()
                _state.update {
                    it.copy(
                        isLoading = false,
                        userProfile = refreshed
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = "Échec de l'update: ${e.message}"
                    )
                }
            }
        }
    }

    private suspend fun performPostAuthSetup() {
        try {
            // Charger le profil Firestore
            val profile = repository.getCurrentUserProfile()

            // Mettre à jour l’état
            _state.update {
                it.copy(
                    isLoading = false,
                    isAuthenticated = true,
                    userProfile = profile,
                    error = null
                )
            }
        } catch (e: Exception) {
            Log.e("Auth", "Erreur de profil utilisateur", e)
            _state.update {
                it.copy(
                    isLoading = false,
                    isAuthenticated = false,
                    error = "Erreur de profil utilisateur : ${e.message}"
                )
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.clearUserSession()
            _state.value = AuthState() // Reset complet de l’état, déconnecté
        }
    }
}

data class AuthState(
    val email: String = "",
    val password: String = "",
    val rememberMe: Boolean = false,
    val isLoading: Boolean = false,
    val isAuthenticated: Boolean = false,
    val userProfile: User? = null,
    val error: String? = null
)
sealed class AuthEvent {
    object StartGoogleSignIn : AuthEvent()
    object Logout : AuthEvent()
}