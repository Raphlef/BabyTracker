package com.kouloundissa.twinstracker.presentation.viewmodel

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.storage.StorageException
import com.kouloundissa.twinstracker.data.Baby
import com.kouloundissa.twinstracker.data.BloodType
import com.kouloundissa.twinstracker.data.Firestore.FirebaseRepository
import com.kouloundissa.twinstracker.data.Gender
import com.kouloundissa.twinstracker.data.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BabyViewModel @Inject constructor(
    private val repository: FirebaseRepository
) : ViewModel() {

    private val TAG = "BabyViewModel"

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
        existingPhotoUrl: String?,
        photoUrl: Uri? = null,
        photoRemoved: Boolean = false
    ) {
        Log.d(TAG, "saveBaby: Starting - id=$id, name=$name, photoUrl=$photoUrl, photoRemoved=$photoRemoved")

        _isLoading.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                val operation = if (id.isNullOrBlank()) "CREATE" else "UPDATE"
                Log.i(TAG, "saveBaby: Operation=$operation")

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
                        id,
                        name,
                        birthDate,
                        gender,
                        birthWeightKg,
                        birthLengthCm,
                        birthHeadCircumferenceCm,
                        birthTime,
                        bloodType,
                        allergies,
                        medicalConditions,
                        pediatricianContact,
                        notes,
                        existingPhotoUrl,
                        photoUrl,
                        photoRemoved
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving baby: ${e.message}", e)
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
        pediatricianContact: String?, notes: String?, existingPhotoUrl: String?,
        newPhotoUrl: Uri?,
        photoRemoved: Boolean
    ) {
        Log.d(TAG, "updateExistingBaby: Starting - id=$id, name=$name")
        // Get current baby to preserve existing photo if no new photo provided
        val currentBaby = _selectedBaby.value ?: repository.getBabyById(id).getOrThrow()

        val finalPhotoUrl: String? = when {
            newPhotoUrl != null -> uploadBabyPhoto(id, newPhotoUrl)
            photoRemoved -> null
            else -> existingPhotoUrl
        }

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
            photoUrl = finalPhotoUrl,
            updatedAt = System.currentTimeMillis()
        )

        Log.d(TAG, "updateExistingBaby: Saving updated baby - finalPhotoUrl=$finalPhotoUrl")

        updatedBaby?.let { repository.addOrUpdateBaby(it) }?.getOrThrow()
        Log.i(TAG, "updateExistingBaby: Successfully updated baby - id=$id")
        _selectedBaby.value = updatedBaby
    }

    private suspend fun createNewBaby(
        name: String, birthDate: Long, gender: Gender, birthWeightKg: Double?,
        birthLengthCm: Double?, birthHeadCircumferenceCm: Double?, birthTime: String?,
        bloodType: BloodType, allergies: List<String>, medicalConditions: List<String>,
        pediatricianContact: String?, notes: String?, photoUrl: Uri?
    ) {
        Log.d(TAG, "createNewBaby: Starting - name=$name, hasPhoto=${photoUrl != null}")
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
        Log.d(TAG, "createNewBaby: Baby object created - id=${baby.id}")
        val createdBaby = repository.addOrUpdateBaby(baby).getOrThrow()
        Log.i(TAG, "createNewBaby: Baby created in repository - id=${createdBaby.id}")
        // Handle photo upload if provided
        val finalBaby = handlePhotoUpload(createdBaby, photoUrl)
        Log.i(TAG, "createNewBaby: Successfully created baby with photo handling - id=${finalBaby.id}")
        _selectedBaby.value = finalBaby
    }

    private suspend fun handlePhotoUpload(baby: Baby, photoUrl: Uri?): Baby {
        return if (photoUrl != null) {
            val newPhotoUrl = uploadBabyPhoto(baby.id, photoUrl)
            val updatedBaby = baby.copy(
                photoUrl = newPhotoUrl,
                updatedAt = System.currentTimeMillis()
            )
            val finalBaby = repository.addOrUpdateBaby(updatedBaby).getOrThrow()
            finalBaby
        } else {
            baby
        }
    }

    private suspend fun uploadBabyPhoto(babyId: String, photoUrl: Uri): String? {
        return try {
            repository.addPhotoToEntity("babies", babyId, photoUrl)
        } catch (e: Exception) {
            Log.e(TAG, "Baby Photo upload failed: ${e.message}", e)
            _errorMessage.value = "Baby Photo upload failed: ${e.localizedMessage}"
            null
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

                // 3. Remove babyId from all families
                repository.removeBabyFromAllFamilies(babyId)

                // 4. Clear selected baby if it was deleted
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

    fun deleteBabyPhoto(babyId: String) {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                // 1. Remove photo from storage & clear Firestore field
                try {
                    repository.deletePhotoFromEntity("babies", babyId)
                } catch (e: Exception) {
                    // If it’s a Firebase Storage 404, ignore it
                    if (e is StorageException && e.errorCode == StorageException.ERROR_OBJECT_NOT_FOUND) {
                        // no-op
                    } else {
                        throw e
                    }
                }

                // 2. Fetch current baby to update local state
                val current = _selectedBaby.value
                    ?: repository.getBabyById(babyId).getOrThrow()

                // 3. Create updated Baby without photo
                val updated = current!!.copy(
                    photoUrl = null,
                    updatedAt = System.currentTimeMillis()
                )

                // 4. Persist the change
                var finalBaby = repository.addOrUpdateBaby(updated).getOrThrow()

                // 5. Update selected baby
                _selectedBaby.value = finalBaby

            } catch (e: Exception) {
                _errorMessage.value = "Échec de la suppression de la photo : ${e.localizedMessage}"
            } finally {
                _isLoading.value = false
            }
        }
    }
}