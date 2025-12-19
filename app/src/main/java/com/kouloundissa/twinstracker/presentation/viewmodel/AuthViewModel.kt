package com.kouloundissa.twinstracker.presentation.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.kouloundissa.twinstracker.R
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
    private val repository: FirebaseRepository,
    private val context: Context // For accessing string resources
) : ViewModel() {

    private val _state = MutableStateFlow(
        AuthState(
            email = "",
            currentStep = AuthStep.Initial
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

                // Pré-remplir l'email si déjà connecté
                repository.getCurrentUserEmail()?.let { email ->
                    _state.update { it.copy(email = email) }
                }

                val remembered = repository.isRemembered()
                _state.update { it.copy(rememberMe = remembered) }

                if (remembered && repository.isUserLoggedIn()) {
                    performPostAuthSetup()
                } else {
                    _state.value = _state.value.copy(currentStep = AuthStep.Initial)
                }
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Init error", e)
                _state.value = _state.value.copy(
                    currentStep = AuthStep.Error(context.getString(R.string.init_error))
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
                currentStep = AuthStep.Initial,
                error = null,
                password = ""
            )
        }
    }

    // Clear the error (after UI displays it)
    fun clearError() {
        _state.update {
            it.copy(
                currentStep = AuthStep.Initial,
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
                val errorMsg = context.getString(R.string.google_signin_failed)
                _state.update {
                    it.copy(
                        currentStep = AuthStep.Error("$errorMsg: ${e.localizedMessage}"),
                        error = "$errorMsg: ${e.localizedMessage}"
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
                    is FirebaseAuthInvalidUserException -> context.getString(R.string.user_not_found)
                    is FirebaseAuthInvalidCredentialsException -> context.getString(R.string.invalid_email_password)
                    is FirebaseNetworkException -> context.getString(R.string.network_error)
                    else -> "${context.getString(R.string.login_failed)}: ${e.localizedMessage}"
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
                email.isEmpty() -> context.getString(R.string.email_empty)
                password.isEmpty() -> context.getString(R.string.password_empty)
                else -> null
            }
            _state.update {
                it.copy(
                    error = msg,
                    currentStep = AuthStep.Error(msg ?: context.getString(R.string.error_label))
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
                        context.getString(R.string.weak_password)
                    is FirebaseAuthInvalidCredentialsException ->
                        context.getString(R.string.invalid_email)
                    is FirebaseAuthUserCollisionException ->
                        context.getString(R.string.email_already_used)
                    is IllegalArgumentException -> e.localizedMessage ?: context.getString(R.string.validation_error)
                    else -> "${context.getString(R.string.register_failed)}: ${e.message}"
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
            repeat(60) { // 60 attempts = ~5 minutes with delay
                delay(5000) // Check every 5 seconds

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
                    Log.e("EmailVerification", context.getString(R.string.email_verification_check_error), e)
                }
            }
            _state.update {
                it.copy(
                    currentStep = AuthStep.EmailVerification,
                    emailVerificationState = EmailVerificationState.Error(
                        context.getString(R.string.email_verification_timeout)
                    ),
                    error = context.getString(R.string.email_verification_timeout)
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
                            emailVerificationState = EmailVerificationState.Sent(
                                context.getString(R.string.email_resent)
                            ),
                            error = null
                        )
                    }
                    startAutoVerificationCheck()
                },
                onFailure = { error ->
                    val errorMsg = error.localizedMessage ?: context.getString(R.string.error_label)
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
                    currentStep = AuthStep.Error(context.getString(R.string.email_empty)),
                    error = context.getString(R.string.email_empty)
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
                        error = context.getString(R.string.password_reset_success, email)
                    )
                }
            } catch (e: Exception) {
                val errorMsg = when (e) {
                    is FirebaseAuthInvalidUserException -> context.getString(R.string.user_not_found)
                    is FirebaseAuthInvalidCredentialsException -> context.getString(R.string.invalid_email)
                    is FirebaseNetworkException -> context.getString(R.string.network_error)
                    else -> "${context.getString(R.string.password_reset_error)}: ${e.localizedMessage}"
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
                Log.e("Profile", context.getString(R.string.profile_load_error), e)
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
                val errorMsg = "${context.getString(R.string.profile_update_failed)}: ${e.localizedMessage}"
                _state.update {
                    it.copy(
                        currentStep = AuthStep.Error(errorMsg),
                        error = errorMsg
                    )
                }
            }
        }
    }

    private suspend fun performPostAuthSetup() {
        try {
            // Charger le profil Firestore
            val profile = repository.getCurrentUserProfile()

            // Mettre à jour l'état
            _state.update {
                it.copy(
                    currentStep = AuthStep.Success,
                    userProfile = profile,
                    error = null,
                    password = ""
                )
            }
        } catch (e: Exception) {
            Log.e("Auth", context.getString(R.string.profile_load_error), e)
            val errorMsg = "${context.getString(R.string.profile_load_error)}: ${e.localizedMessage}"
            _state.update {
                it.copy(
                    currentStep = AuthStep.Error(errorMsg),
                    error = errorMsg
                )
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            verificationCheckJob?.cancel()

            repository.clearUserSession()
            _state.value = AuthState(currentStep = AuthStep.Initial)
        }
    }

    fun clearVerificationFlow() {
        _state.update {
            it.copy(
                showEmailVerificationFlow = false,
                currentStep = AuthStep.IdleForm
            )
        }
    }
    fun showLoginForm() {
        _state.value = _state.value.copy(
            currentStep = AuthStep.IdleForm,
            isLoginMode = true,
            email = "",
            password = "",
            error = null
        )
    }

    fun showRegisterForm() {
        _state.value = _state.value.copy(
            currentStep = AuthStep.IdleForm,
            isLoginMode = false,
            email = "",
            password = "",
            error = null
        )
    }

    fun backToInitial() {
        _state.value = _state.value.copy(
            currentStep = AuthStep.Initial,
            email = "",
            password = "",
            error = null
        )
    }
    fun resetVerificationState() {
        _state.update {
            it.copy(
                emailVerificationState = EmailVerificationState.Initial,
                error = null
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        verificationCheckJob?.cancel()
    }
}

// ============================================================================
// STATE CLASSES
// ============================================================================

sealed class AuthStep {
    object Initial : AuthStep()
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
    val currentStep: AuthStep = AuthStep.Initial,
    val userProfile: User? = null,
    val error: String? = null,
    val showEmailVerificationFlow: Boolean = false,
    val emailVerificationState: EmailVerificationState = EmailVerificationState.Initial,
    val isLoginMode: Boolean = true
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
