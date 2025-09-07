package com.example.babytracker.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.babytracker.data.FirebaseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repository: FirebaseRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AuthState())
    val state: StateFlow<AuthState> = _state.asStateFlow()

    fun onEmailChange(email: String) {
        _state.value = _state.value.copy(email = email)
    }

    fun onPasswordChange(password: String) {
        _state.value = _state.value.copy(password = password)
    }

    fun onRememberMeChanged(value: Boolean) {
        _state.value = _state.value.copy(rememberMe = value)
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
                _state.value = _state.value.copy(isAuthenticated = true)
            }  catch (e: Exception) {
                _state.value = _state.value.copy(
                    error = "Échec de la connexion : ${e.message}",
                    isLoading = false,
                    isAuthenticated = false
                )
            } finally {
                _state.value = _state.value.copy(isLoading = false)
            }
        }
    }

    fun register() {
        val email = _state.value.email.trim()
        val password = _state.value.password

        if (email.isEmpty()) {
            _state.value = _state.value.copy(error = "Email ne peut pas être vide")
            return
        }

        if (password.isEmpty()) {
            _state.value = _state.value.copy(error = "Mot de passe ne peut pas être vide")
            return
        }
        _state.value = _state.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            try {
                repository.register(_state.value.email, _state.value.password)
                _state.value = _state.value.copy(isAuthenticated = true)
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = "Échec de l'inscription: ${e.message}")
            } finally {
                _state.value = _state.value.copy(isLoading = false)
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
    val isUserLoggedIn: Boolean = false,
    val error: String? = null
)