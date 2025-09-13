package com.example.babytracker.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.babytracker.data.Baby
import com.example.babytracker.data.FirebaseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.util.Log // For logging errors
import com.example.babytracker.data.Gender
import com.example.babytracker.data.User
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class BabyViewModel @Inject constructor(
    private val repository: FirebaseRepository
) : ViewModel() {

    val babies: StateFlow<List<Baby>> = repository
        .streamBabies()
        .onStart {
            _isLoading.value = true
            _errorMessage.value = null
        }
        .onEach {
            _isLoading.value = false
        }
        .catch { e ->
            _isLoading.value = false
            _errorMessage.value = "Failed to load babies: ${e.message}"
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )


    private val _selectedBaby = MutableStateFlow<Baby?>(null)
    val selectedBaby: StateFlow<Baby?> = _selectedBaby.asStateFlow()

    private val _defaultBaby = MutableStateFlow<Baby?>(null)
    val defaultBaby: StateFlow<Baby?> = _defaultBaby.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Optional: Add a StateFlow for error messages
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _collaborators = MutableStateFlow<List<User>>(emptyList())
    val collaborators: StateFlow<List<User>> = _collaborators.asStateFlow()

    fun loadCollaborators(babyId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                // Récupération sécurisée du bébé
                val baby = repository.getBabyById(babyId).getOrElse {
                    throw it
                } ?: throw IllegalStateException("Bébé introuvable")

                // Extraction des IDs et chargement des users
                val ids = baby.parentIds
                _collaborators.value =
                    if (ids.isEmpty()) emptyList()
                    else repository.getUsersByIds(ids)
            } catch (e: Exception) {
                _errorMessage.value = "Erreur chargement collaborateurs : ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    fun inviteCollaborator(babyId: String, email: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                repository.addCollaboratorToBaby(babyId, email)
                loadCollaborators(babyId)
            } catch (e: Exception) {
                _errorMessage.value = "Invitation échouée : ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    fun selectBaby(baby: Baby) {
        _selectedBaby.value = baby
    }

    fun addBaby(name: String, birthDate: Long, gender: Gender = Gender.UNKNOWN) {
        _isLoading.value = true
        viewModelScope.launch {
            // 1. Appel repository
            val result = repository.addOrUpdateBaby(
                Baby(name = name, birthDate = birthDate, gender = gender)
            )
            // 2. Vérifier échec
            if (result.isFailure) {
                // Extraire la cause
                val cause = result.exceptionOrNull()
                _errorMessage.value = cause?.message ?: "Échec inattendu"
            } else {
                _errorMessage.value = null
            }
            _isLoading.value = false
        }
    }

    fun updateBaby(
        id: String,
        name: String,
        birthDate: Long,
        gender: Gender = Gender.UNKNOWN
    ) {
        _isLoading.value = true
        _errorMessage.value = null
        viewModelScope.launch {
            try {
                val updatedBaby = Baby(id = id, name = name, birthDate = birthDate, gender = gender)
                repository.addOrUpdateBaby(updatedBaby)
            } catch (e: Exception) {
                Log.e("BabyViewModel", "Error updating baby: ${e.message}", e)
                _errorMessage.value = "Failed to update baby: ${e.localizedMessage}"
            } finally {
                _selectedBaby.value = _selectedBaby.value?.takeIf { it.id == id }
                    ?.copy(name = name, birthDate = birthDate, gender = gender)
                _isLoading.value = false
            }
        }
    }

    fun loadDefaultBaby(defaultBabyId: String?) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _defaultBaby.value = defaultBabyId?.let { id ->
                    repository.getBabyById(id).getOrThrow()
                }
            } catch (e: Exception) {
                _errorMessage.value = "Impossible de charger le bébé par défaut : ${e.message}"
                _defaultBaby.value = null
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun setDefaultBaby(baby: Baby) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Persister dans le document users.defaultBabyId
                repository.updateUserProfile(mapOf("defaultBabyId" to baby.id))
                _defaultBaby.value = baby
            } catch (e: Exception) {
                _errorMessage.value = "Échec du choix de bébé par défaut : ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteBaby(babyId: String) {
        _isLoading.value = true
        viewModelScope.launch {
            repository.deleteBabyAndEvents(babyId).onFailure {
                _errorMessage.value = "Échec de la suppression : ${it.message}"
            }
            if (_selectedBaby.value?.id == babyId) {
                _selectedBaby.value = null
            }
            _isLoading.value = false
        }
    }
}