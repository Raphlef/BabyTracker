package com.example.babytracker.presentation.viewmodel

import android.net.Uri
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
import androidx.room.util.copy
import com.example.babytracker.data.BloodType
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

    private val _parents = MutableStateFlow<List<User>>(emptyList())
    val parents: StateFlow<List<User>> = _parents.asStateFlow()

    fun loadParents(babyId: String) {
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
                _parents.value =
                    if (ids.isEmpty()) emptyList()
                    else repository.getUsersByIds(ids)
            } catch (e: Exception) {
                _errorMessage.value = "Erreur chargement parents : ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun inviteParent(babyId: String, email: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                repository.addParentToBaby(babyId, email)
                loadParents(babyId)
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

    fun addBaby(
        name: String,
        birthDate: Long,
        gender: Gender = Gender.UNKNOWN,
        photoUri: Uri? = null
    ) {
        _isLoading.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                // 1. Create baby without photo first
                val baby = Baby(name = name, birthDate = birthDate, gender = gender)
                val result = repository.addOrUpdateBaby(baby)

                if (result.isFailure) {
                    _errorMessage.value = result.exceptionOrNull()?.message ?: "Échec inattendu"
                    return@launch
                }

                // 2. Upload photo if provided
                photoUri?.let { uri ->
                    // Find the newly created baby to get its ID
                    val createdBaby = repository.getBabies().getOrNull()
                        ?.find { it.name == name && it.birthDate == birthDate }

                    createdBaby?.let { baby ->
                        uploadBabyPhoto(baby.id, uri)
                    }
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Unexpected error"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateBaby(
        id: String,
        name: String,
        birthDate: Long,
        gender: Gender = Gender.UNKNOWN,
        birthWeightKg: Double? = null,
        birthLengthCm: Double? = null,
        birthHeadCircumferenceCm: Double? = null,
        birthTime: String? = null,
        bloodType: BloodType = BloodType.UNKNOWN,
        allergies: List<String> = emptyList(),
        medicalConditions: List<String> = emptyList(),
        pediatricianContact: String? = null,
        notes: String? = null,
        photoUri: Uri? = null
    ) {
        _isLoading.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                // 1. Upload photo first if provided
                val photoUrl = photoUri?.let { uri ->
                    uploadBabyPhoto(id, uri)
                }

                // 2. Get current baby
                val current = _selectedBaby.value
                    ?: repository.getBabyById(id).getOrThrow()

                // 3. Update baby with new photo URL or keep existing
                val updated = Baby(
                    id = id,
                    name = name,
                    birthDate = birthDate,
                    gender = gender,
                    birthWeightKg = birthWeightKg,
                    birthLengthCm = birthLengthCm,
                    birthHeadCircumferenceCm = birthHeadCircumferenceCm,
                    birthTime = birthTime,
                    bloodType = bloodType,
                    allergies = allergies,
                    medicalConditions = medicalConditions,
                    pediatricianContact = pediatricianContact,
                    notes = notes,
                    photoUri = photoUrl ?: current!!.photoUri,
                    updatedAt = System.currentTimeMillis()
                )

                repository.addOrUpdateBaby(updated).getOrThrow()
                _selectedBaby.value = updated

            } catch (e: Exception) {
                Log.e("BabyViewModel", "Error updating baby with photo: ${e.message}", e)
                _errorMessage.value = "Échec de la mise à jour: ${e.localizedMessage}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    private suspend fun uploadBabyPhoto(babyId: String, photoUri: Uri): String? {
        return try {
            repository.addPhotoToEntity("babies", babyId, photoUri)
        } catch (e: Exception) {
            Log.e("BabyViewModel", "Photo upload failed: ${e.message}", e)
            _errorMessage.value = "Photo upload failed: ${e.localizedMessage}"
            null
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
            try {
                // 1. Delete photo from Firebase Storage and Firestore field
                repository.deletePhotoFromEntity("babies", babyId)

                // 2. Delete Firestore baby document and related events
                repository.deleteBabyAndEvents(babyId).getOrThrow()

                // 3. Clear selected baby if it was deleted
                if (_selectedBaby.value?.id == babyId) {
                    _selectedBaby.value = null
                }

            } catch (e: Exception) {
                _errorMessage.value = "Échec de la suppression : ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
}