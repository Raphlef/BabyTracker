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

    fun saveBaby(
        id: String?,                // null or blank means new baby
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
                val babyId: String

                if (id.isNullOrBlank()) {
                    // New baby: create without photo first
                    val baby = Baby(
                        id = "",
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
                        photoUri = null,
                        updatedAt = System.currentTimeMillis()
                    )
                    repository.addOrUpdateBaby(baby).getOrThrow()

                    // Retrieve created baby's ID (assumed unique by name+birthDate)
                    val createdBaby = repository.getBabies().getOrNull()
                        ?.find { it.name == name && it.birthDate == birthDate }
                        ?: throw IllegalStateException("Created baby not found")

                    babyId = createdBaby.id

                    // Upload photo if provided
                    photoUri?.let { uri ->
                        val photoUrl = uploadBabyPhoto(babyId, uri)
                        // Update baby with photoUrl field
                        val updatedBaby = createdBaby.copy(photoUri = photoUrl, updatedAt = System.currentTimeMillis())
                        repository.addOrUpdateBaby(updatedBaby).getOrThrow()
                        _selectedBaby.value = updatedBaby
                    } ?: run {
                        _selectedBaby.value = createdBaby
                    }

                } else {
                    // Existing baby: upload photo first if provided
                    val photoUrl = photoUri?.let { uri ->
                        uploadBabyPhoto(id, uri)
                    }

                    // Fetch current to preserve fields
                    val current = _selectedBaby.value
                        ?: repository.getBabyById(id).getOrThrow()

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
                    babyId = id
                }

            } catch (e: Exception) {
                Log.e("BabyViewModel", "Error saving baby: ${e.message}", e)
                _errorMessage.value = "Échec de la sauvegarde: ${e.localizedMessage}"
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