package com.kouloundissa.twinstracker.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.kouloundissa.twinstracker.data.Firestore.FirebaseRepository
import com.kouloundissa.twinstracker.data.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repository: FirebaseRepository
) : ViewModel() {

    private val _state = MutableStateFlow(
        AuthState(
            email = "",
            currentStep = AuthStep.IdleForm
        )
    )
    val state: StateFlow<AuthState> = _state.asStateFlow()

    private val _oneTimeEvent = MutableSharedFlow<AuthEvent>(replay = 1)
    val oneTimeEventFlow: SharedFlow<AuthEvent> = _oneTimeEvent.asSharedFlow()

    private var verificationCheckJob: Job? = null

    init {
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(currentStep = AuthStep.LoadingProfile)

                // Pré-remplir l’email si déjà connecté
                repository.getCurrentUserEmail()?.let { email ->
                    _state.update { it.copy(email = email) }
                }

                val remembered = repository.isRemembered()
                _state.update { it.copy(rememberMe = remembered) }

                if (remembered && repository.isUserLoggedIn()) {
                    performPostAuthSetup()
                } else {
                    _state.value = _state.value.copy(currentStep = AuthStep.IdleForm)
                }
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Init error", e)
                _state.value = _state.value.copy(
                    currentStep = AuthStep.Error("Erreur d'initialisation")
                )
            }
        }
    }

    // Set an error message in state
    fun onError(message: String) {
        _state.update { it.copy(error = message) }
    }
    fun retryAfterError() {
        _state.update {
            it.copy(
                currentStep = AuthStep.IdleForm,
                error = null,
                password = ""
            )
        }
    }
    // Clear the error (after UI displays it)
    fun clearError() {
        _state.update {
            it.copy(
                currentStep = AuthStep.IdleForm,
                error = null
            )
        }
    }
    fun navigateToForgotPassword() {
        _state.update {
            it.copy(
                currentStep = AuthStep.ForgotPassword,
                error = null
            )
        }
    }
    fun loginWithGoogle() {
        viewModelScope.launch {
            _state.value = _state.value.copy(rememberMe = true)
            _oneTimeEvent.emit(AuthEvent.StartGoogleSignIn)
        }
    }

    // Call this from your Activity once Google returns an ID token
    fun handleGoogleSignInResult(idToken: String) {
        _state.value = _state.value.copy(currentStep = AuthStep.Authenticating, error = null)
        viewModelScope.launch {
            try {
                repository.signInWithGoogle(idToken)
                if (_state.value.rememberMe) repository.saveUserSession()
                performPostAuthSetup()
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        currentStep = AuthStep.Error("Google sign-in failed: ${e.message}"),
                        error = "Google sign-in failed: ${e.message}"
                    )
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
        _state.value = _state.value.copy(
            currentStep = AuthStep.Authenticating,
            error = null
        )
        viewModelScope.launch {
            try {
                repository.login(_state.value.email, _state.value.password)

                // If Remember Me is enabled, store session
                if (_state.value.rememberMe) {
                    repository.saveUserSession()
                }
                performPostAuthSetup()
            } catch (e: Exception) {
                val errorMsg = when (e) {
                    is FirebaseAuthInvalidUserException -> "[translate:Utilisateur non trouvé]"
                    is FirebaseAuthInvalidCredentialsException -> "[translate:Email ou mot de passe incorrect]"
                    is FirebaseNetworkException -> "[translate:Erreur réseau]"
                    else -> "[translate:Échec de la connexion : ${e.message}]"
                }

                _state.update {
                    it.copy(
                        currentStep = AuthStep.Error(errorMsg),
                        error = errorMsg,
                        password = ""
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
            _state.update {
                it.copy(
                    error = msg,
                    currentStep = AuthStep.Error(msg ?: "[translate:Erreur]")
                )
            }
            return
        }

        _state.update {
            it.copy(
                currentStep = AuthStep.Authenticating,
                error = null
            )
        }
        viewModelScope.launch {
            try {
                repository.register(email, password)
                _state.update {
                    it.copy(
                        currentStep = AuthStep.EmailVerification,
                        email = email,
                        password = "",
                        showEmailVerificationFlow = true,
                        error = null,
                        emailVerificationState = EmailVerificationState.Initial
                    )
                }

                startAutoVerificationCheck()
            } catch (e: Exception) {
                val errorMsg = when (e) {
                    is FirebaseAuthWeakPasswordException ->
                        "[translate:Mot de passe trop faible (min 6 caractères)]"
                    is FirebaseAuthInvalidCredentialsException ->
                        "[translate:Email invalide]"
                    is FirebaseAuthUserCollisionException ->
                        "[translate:Cet email est déjà utilisé]"
                    is IllegalArgumentException -> e.message ?: "[translate:Erreur validation]"
                    else -> "[translate:Échec inscription: ${e.message}]"
                }

                _state.update {
                    it.copy(
                        currentStep = AuthStep.Error(errorMsg),
                        password = "",
                        error = errorMsg
                    )
                }
            }
        }
    }

    private fun startAutoVerificationCheck() {
        verificationCheckJob?.cancel()

        verificationCheckJob = viewModelScope.launch {
            repeat(60) { // 60 tentatives = ~5 minutes avec délai
                delay(5000) // Vérifier chaque 5 secondes

                try {
                    if (repository.checkEmailVerification()) {
                        _state.update {
                            it.copy(
                                showEmailVerificationFlow = false,
                                emailVerificationState = EmailVerificationState.Verified,
                                currentStep = AuthStep.LoadingProfile
                            )
                        }
                        performPostAuthSetup()
                        return@launch
                    }
                } catch (e: Exception) {
                    Log.e("EmailVerification", "Erreur vérification", e)
                }
            }
            _state.update {
                it.copy(
                    currentStep = AuthStep.EmailVerification,
                    emailVerificationState = EmailVerificationState.Error(
                        "[translate:Vérification d'email échouée après 5 minutes]"
                    ),
                    error = "[translate:Vérification d'email échouée après 5 minutes]"
                )
            }
        }
    }

    fun resendVerificationEmail() {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    emailVerificationState = EmailVerificationState.Sending
                )
            }

            repository.resendVerificationEmail().fold(
                onSuccess = {
                    _state.update {
                        it.copy(
                            emailVerificationState = EmailVerificationState.Sent("Email renvoyé"), error = null
                        )
                    }
                },
                onFailure = { error ->
                    val errorMsg = error.message ?: "[translate:Erreur]"
                    _state.update {
                        it.copy(
                            emailVerificationState = EmailVerificationState.Error(errorMsg),
                            error = errorMsg
                        )
                    }
                }
            )
        }
    }

    fun initiatePasswordReset(email: String) {
        if (email.isEmpty()) {
            _state.update {
                it.copy(
                    currentStep = AuthStep.Error("Email ne peut pas être vide"),
                    error = "Email ne peut pas être vide"
                )
            }
            return
        }

        _state.update {
            it.copy(
                currentStep = AuthStep.Authenticating,
                error = null
            )
        }

        viewModelScope.launch {
            try {
                repository.sendPasswordReset(email)

                _state.update {
                    it.copy(
                        currentStep = AuthStep.IdleForm,
                        error = null,
                        email = ""  // Clear email after sending
                    )
                }

                // Show success message
                _state.update {
                    it.copy(
                        error = "Email de réinitialisation envoyé à $email"
                    )
                }
            } catch (e: Exception) {
                val errorMsg = when (e) {
                    is FirebaseAuthInvalidUserException -> "Utilisateur non trouvé"
                    is FirebaseAuthInvalidCredentialsException -> "Email invalide"
                    is FirebaseNetworkException -> "Erreur réseau"
                    else -> "Erreur lors de l'envoi : ${e.message}"
                }

                _state.update {
                    it.copy(
                        currentStep = AuthStep.Error(errorMsg),
                        error = errorMsg
                    )
                }
            }
        }
    }
    fun loadUserProfile() {
        viewModelScope.launch {
            try {
                val profile = repository.getCurrentUserProfile()
                _state.update {
                    it.copy(
                        userProfile = profile
                    )
                }
            } catch (e: Exception) {
                Log.e("Profile", "Error loading profile", e)
            }
        }
    }

    fun updateUserProfile(updates: Map<String, Any?>) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                currentStep = AuthStep.LoadingProfile,
                error = null
            )
            try {
                repository.updateUserProfile(updates)
                // Recharger le profil
                val refreshed = repository.getCurrentUserProfile()
                _state.update {
                    it.copy(
                        currentStep = AuthStep.Success,
                        userProfile = refreshed
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        currentStep = AuthStep.Error("[translate:Échec de l'update: ${e.message}]"),
                        error = "[translate:Échec de l'update: ${e.message}]"
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
                    currentStep = AuthStep.Success,
                    userProfile = profile,
                    error = null,
                    password = ""
                )
            }
        } catch (e: Exception) {
            Log.e("Auth", "Erreur de profil utilisateur", e)
            _state.update {
                it.copy(
                    currentStep = AuthStep.Error("[translate:Erreur de profil utilisateur : ${e.message}]"),
                    error = "[translate:Erreur de profil utilisateur : ${e.message}]"
                )
            }
        }
    }


    fun logout() {
        viewModelScope.launch {
            verificationCheckJob?.cancel()

            repository.clearUserSession()
            _state.value = AuthState(currentStep = AuthStep.IdleForm)
        }
    }

    fun clearVerificationFlow() {
        _state.update { it.copy(showEmailVerificationFlow = false,
            currentStep = AuthStep.IdleForm) }
    }

    fun resetVerificationState() {
        _state.update { it.copy(emailVerificationState = EmailVerificationState.Initial,
            error = null) }
    }

    override fun onCleared() {
        super.onCleared()
        verificationCheckJob?.cancel()
    }
}


sealed class AuthStep {
    object IdleForm : AuthStep()  // User at login/register form
    object Authenticating : AuthStep()  // Firebase auth in progress
    object LoadingProfile : AuthStep()  // Firestore profile loading
    object EmailVerification : AuthStep()  // Waiting for email verification
    object ForgotPassword : AuthStep()
    object Success : AuthStep()  // Ready to navigate
    data class Error(val message: String) : AuthStep()  // Fatal error state
}
data class AuthState(
    val email: String = "",
    val password: String = "",
    val rememberMe: Boolean = false,

    val currentStep: AuthStep = AuthStep.IdleForm,

    val userProfile: User? = null,
    val error: String? = null,
    val showEmailVerificationFlow: Boolean = false,
    val emailVerificationState: EmailVerificationState = EmailVerificationState.Initial
)

sealed class AuthEvent {
    object StartGoogleSignIn : AuthEvent()
    object Logout : AuthEvent()
}

sealed class EmailVerificationState {
    object Initial : EmailVerificationState()
    object Sending : EmailVerificationState()
    data class Sent(val message: String) : EmailVerificationState()
    data class Error(val message: String) : EmailVerificationState()
    object Verified : EmailVerificationState()
}
