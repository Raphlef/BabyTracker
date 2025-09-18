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
        id: String?,
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
        photoUrl: Uri? = null
    ) {
        _isLoading.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                if (id.isNullOrBlank()) {
                    // Create new baby
                    createNewBaby(
                        name, birthDate, gender, birthWeightKg, birthLengthCm,
                        birthHeadCircumferenceCm, birthTime, bloodType, allergies,
                        medicalConditions, pediatricianContact, notes, photoUrl
                    )
                } else {
                    // Update existing baby
                    updateExistingBaby(
                        id, name, birthDate, gender, birthWeightKg, birthLengthCm,
                        birthHeadCircumferenceCm, birthTime, bloodType, allergies,
                        medicalConditions, pediatricianContact, notes, photoUrl
                    )
                }
            } catch (e: Exception) {
                Log.e("BabyViewModel", "Error saving baby: ${e.message}", e)
                _errorMessage.value = "Échec de la sauvegarde: ${e.localizedMessage}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    private suspend fun updateExistingBaby(
        id: String, name: String, birthDate: Long, gender: Gender, birthWeightKg: Double?,
        birthLengthCm: Double?, birthHeadCircumferenceCm: Double?, birthTime: String?,
        bloodType: BloodType, allergies: List<String>, medicalConditions: List<String>,
        pediatricianContact: String?, notes: String?, photoUrl: Uri?
    ) {
        // Get current baby to preserve existing photo if no new photo provided
        val currentBaby = _selectedBaby.value ?: repository.getBabyById(id).getOrThrow()
        // Create updated baby with new data
        val updatedBaby = currentBaby?.copy(
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
            updatedAt = System.currentTimeMillis()
            // photoUrl will be handled separately if new photo provided
        )

        val finalBaby = updatedBaby?.let { handlePhotoUpload(it, photoUrl) }
        finalBaby?.let { repository.addOrUpdateBaby(it) }?.getOrThrow()
        _selectedBaby.value = finalBaby
    }
    private suspend fun createNewBaby(
        name: String, birthDate: Long, gender: Gender, birthWeightKg: Double?,
        birthLengthCm: Double?, birthHeadCircumferenceCm: Double?, birthTime: String?,
        bloodType: BloodType, allergies: List<String>, medicalConditions: List<String>,
        pediatricianContact: String?, notes: String?, photoUrl: Uri?
    ) {
        // Create baby without photo first
        val baby = Baby(
            // id will be auto-generated by UUID in data class
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
            photoUrl = null, // Will be updated after photo upload
            updatedAt = System.currentTimeMillis()
        )

        repository.addOrUpdateBaby(baby).getOrThrow()

        // Get the created baby with its ID
        val createdBaby = repository.getBabies().getOrNull()
            ?.find { it.name == name && it.birthDate == birthDate }
            ?: throw IllegalStateException("Created baby not found")

        // Handle photo upload if provided
        val finalBaby = handlePhotoUpload(createdBaby, photoUrl)
        _selectedBaby.value = finalBaby
    }
    private suspend fun handlePhotoUpload(baby: Baby, photoUrl: Uri?): Baby {
        return if (photoUrl != null) {
            val newPhotoUrl = uploadBabyPhoto(baby.id, photoUrl)
            val updatedBaby = baby.copy(
                photoUrl = newPhotoUrl,
                updatedAt = System.currentTimeMillis()
            )
            repository.addOrUpdateBaby(updatedBaby).getOrThrow()
            updatedBaby
        } else {
            baby
        }
    }
    private suspend fun uploadBabyPhoto(babyId: String, photoUrl: Uri): String? {
        return try {
            repository.addPhotoToEntity("babies", babyId, photoUrl)
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