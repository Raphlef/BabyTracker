package com.kouloundissa.twinstracker.presentation.viewmodel

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.storage.StorageException
import com.kouloundissa.twinstracker.data.Baby
import com.kouloundissa.twinstracker.data.BloodType
import com.kouloundissa.twinstracker.data.Family
import com.kouloundissa.twinstracker.data.Firestore.FirebaseRepository
import com.kouloundissa.twinstracker.data.Gender
import com.kouloundissa.twinstracker.data.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BabyViewModel @Inject constructor(
    private val repository: FirebaseRepository,
) : ViewModel() {

    private val TAG = "BabyViewModel"

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val babies: StateFlow<List<Baby>> = repository.selectedFamily
        .flatMapLatest { selectedFamily ->
            selectedFamily?.let { family ->
                repository.streamFamilyBabyIds(family.id)
                    .flatMapLatest { babyIds ->
                        repository.streamBabiesByIds(babyIds)
                    }
            } ?: flowOf(emptyList())
        }
        .onStart {
            _isLoading.value = true
            _errorMessage.value = null
            _selectedBaby.value = null
        }
        .onEach {
            _isLoading.value = false
        }
        .catch { e ->
            _isLoading.value = false
            _errorMessage.value = "Failed to load babies: ${e.localizedMessage}"
            emit(emptyList())
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList()
        )

    private val _selectedBaby = MutableStateFlow<Baby?>(null)
    val selectedBaby: StateFlow<Baby?> = _selectedBaby.asStateFlow()
    fun selectNextBaby(): Baby? {
        val currentBabies = babies.value
        if (currentBabies.isEmpty()) return null

        val currentBaby = _selectedBaby.value
        val currentIndex = currentBabies.indexOfFirst { it.id == currentBaby?.id }

        val nextIndex = if (currentIndex == -1 || currentIndex >= currentBabies.size - 1) {
            0 // Go to first baby if no selection or at the end
        } else {
            currentIndex + 1
        }

        val nextBaby = currentBabies[nextIndex]
        _selectedBaby.value = nextBaby

        return nextBaby
    }

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
                _errorMessage.value = "Erreur chargement parents : ${e.localizedMessage}"
            } finally {
                _isLoading.value = false
            }
        }
    }


    fun selectBaby(baby: Baby) {
        _selectedBaby.value = baby
    }

    /**
     * Crée plusieurs bébés à partir d'une liste de noms uniquement.
     * Tous les autres paramètres utilisent les valeurs par défaut (birthDate=0, gender=UNKNOWN, etc.)
     * Réutilise la logique de saveBaby pour éviter la duplication de code.
     */
    fun saveMultipleBabiesFromNames(
        family: Family,
        babyNames: List<String>,
        familyViewModel: FamilyViewModel,
        onComplete: (List<Baby>) -> Unit = {}
    ) {
        _isLoading.value = true
        _errorMessage.value = null

        if (!familyViewModel.canUserSaveBaby()) {
            _isLoading.value = false
            _errorMessage.value =
                "You don't have permission to save babies. Only members and admins can save."
            return
        }

        if (babyNames.isEmpty()) {
            _isLoading.value = false
            _errorMessage.value = "Aucun nom de bébé fourni."
            return
        }

        Log.d(TAG, "saveMultipleBabiesFromNames: Creating ${babyNames.size} babies")

        viewModelScope.launch {
            val savedBabies = mutableListOf<Baby>()
            var hasError = false

            try {
                var _currentFamily = family
                babyNames.forEachIndexed { index, name ->
                    if (hasError) return@forEachIndexed
                    try {
                        val createdBaby = saveBabyInternal(
                            family = _currentFamily,
                            id = null,
                            name = name,
                            familyViewModel = familyViewModel,
                        ) { updatedFamily ->
                            _currentFamily = updatedFamily
                        }

                        if (createdBaby != null && createdBaby.name == name) {
                            savedBabies.add(createdBaby)
                            Log.d(TAG, "Baby created: $name (id=${createdBaby.id})")
                        }

                    } catch (e: Exception) {
                        Log.e(TAG, "Error creating baby '$name': ${e.message}", e)
                        hasError = true
                        _errorMessage.value = "Erreur création bébé '$name': ${e.localizedMessage}"
                    }
                }

                if (!hasError) {
                    Log.i(
                        TAG,
                        "saveMultipleBabiesFromNames: Successfully created ${savedBabies.size} babies"
                    )
                    _selectedBaby.value = savedBabies.last()
                    onComplete(savedBabies)
                }

            } catch (e: Exception) {
                Log.e(TAG, "saveMultipleBabiesFromNames error: ${e.message}", e)
                _errorMessage.value = "Échec création multiples: ${e.localizedMessage}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun saveBaby(
        family: Family,
        id: String?,
        name: String,
        familyViewModel: FamilyViewModel,
        birthDate: Long = 0L,
        gender: Gender = Gender.UNKNOWN,
        birthWeightKg: Double? = null,
        birthLengthCm: Double? = null,
        birthHeadCircumferenceCm: Double? = null,
        birthTime: String? = null,
        bloodType: BloodType = BloodType.UNKNOWN,
        allergies: List<String> = emptyList(),
        medicalConditions: List<String> = emptyList(),
        pediatricianName: String? = null,
        pediatricianPhone: String? = null,
        notes: String? = null,
        existingPhotoUrl: String? = null,
        newPhotoUri: Uri? = null,
        photoRemoved: Boolean = false,
        onSuccess: ((Family) -> Unit)? = null
    ) {
        _isLoading.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                val finalBaby = saveBabyInternal(
                    family = family,
                    id = id,
                    name = name,
                    familyViewModel = familyViewModel,
                    birthDate = birthDate,
                    gender = gender,
                    birthWeightKg = birthWeightKg,
                    birthLengthCm = birthLengthCm,
                    birthHeadCircumferenceCm = birthHeadCircumferenceCm,
                    birthTime = birthTime,
                    bloodType = bloodType,
                    allergies = allergies,
                    medicalConditions = medicalConditions,
                    pediatricianName = pediatricianName,
                    pediatricianPhone = pediatricianPhone,
                    notes = notes,
                    existingPhotoUrl = existingPhotoUrl,
                    newPhotoUri = newPhotoUri,
                    photoRemoved = photoRemoved,
                    onSuccess = onSuccess
                )

                _selectedBaby.value = finalBaby

            } catch (e: Exception) {
                Log.e(TAG, "Error saving baby: ${e.message}", e)
                _errorMessage.value = "Échec de la sauvegarde: ${e.localizedMessage}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun saveBabyInternal(
        family: Family,
        id: String?,
        name: String,
        familyViewModel: FamilyViewModel,
        birthDate: Long = 0L,
        gender: Gender = Gender.UNKNOWN,
        birthWeightKg: Double? = null,
        birthLengthCm: Double? = null,
        birthHeadCircumferenceCm: Double? = null,
        birthTime: String? = null,
        bloodType: BloodType = BloodType.UNKNOWN,
        allergies: List<String> = emptyList(),
        medicalConditions: List<String> = emptyList(),
        pediatricianName: String? = null,
        pediatricianPhone: String? = null,
        notes: String? = null,
        existingPhotoUrl: String? = null,
        newPhotoUri: Uri? = null,
        photoRemoved: Boolean = false,
        onSuccess: ((Family) -> Unit)? = null
    ): Baby {
        if (!familyViewModel.canUserSaveBaby()) {
            throw IllegalStateException("You don't have permission to save baby. Only members and admins can save.")
        }
        val isUpdate = !id.isNullOrBlank()
        Log.d(TAG, "saveBaby: ${if (isUpdate) "UPDATE" else "CREATE"} - id=$id, name=$name")

        // Step 1: Prepare the baby data
        val babyData = prepareBabyData(
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
            pediatricianName = pediatricianName,
            pediatricianPhone = pediatricianPhone,
            notes = notes,
            existingPhotoUrl = existingPhotoUrl,
            photoRemoved = photoRemoved
        )

        // Step 2: Save baby to repository
        val result = repository.addOrUpdateBaby(babyData, family).getOrThrow()
        val savedBaby = result.first
        var updatedFamily = result.second
        Log.i(TAG, "saveBaby: Baby saved to repository - id=${savedBaby.id}")

        // Step 3: Handle photo upload if new photo provided
        val finalBaby = if (newPhotoUri != null) {
            val photoUrl = uploadBabyPhoto(savedBaby.id, newPhotoUri)
            if (photoUrl != null) {
                val babyWithPhoto = savedBaby.copy(
                    photoUrl = photoUrl,
                    updatedAt = System.currentTimeMillis()
                )
                val result = repository.addOrUpdateBaby(babyWithPhoto, family).getOrThrow()
                updatedFamily = result.second
                result.first

            } else {
                savedBaby
            }
        } else {
            savedBaby
        }

        Log.i(
            TAG,
            "saveBaby: Success - id=${finalBaby.id}, hasPhoto=${finalBaby.photoUrl != null}"
        )
        onSuccess?.invoke(updatedFamily)
        return finalBaby
    }


    /**
     * Prepares baby data object from provided parameters.
     * Handles both creation (new baby) and update (existing baby) scenarios.
     */
    private suspend fun prepareBabyData(
        id: String?,
        name: String,
        birthDate: Long,
        gender: Gender,
        birthWeightKg: Double?,
        birthLengthCm: Double?,
        birthHeadCircumferenceCm: Double?,
        birthTime: String?,
        bloodType: BloodType,
        allergies: List<String>,
        medicalConditions: List<String>,
        pediatricianName: String?,
        pediatricianPhone: String?,
        notes: String?,
        existingPhotoUrl: String?,
        photoRemoved: Boolean
    ): Baby {
        val isUpdate = !id.isNullOrBlank()

        // Determine final photo URL
        val finalPhotoUrl = when {
            photoRemoved -> null
            existingPhotoUrl != null -> existingPhotoUrl
            else -> null
        }

        // Get current user ID for parentIds
        val currentUserId = repository.getCurrentUserIdOrThrow()

        return if (isUpdate) {
            // Update: Get existing baby and update fields
            val existingBaby = _selectedBaby.value ?: repository.getBabyById(id).getOrThrow()
            existingBaby!!.copy(
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
                pediatricianName = pediatricianName,
                pediatricianPhone = pediatricianPhone,
                notes = notes,
                photoUrl = finalPhotoUrl,
                updatedAt = System.currentTimeMillis()
            )
        } else {
            // Create: New baby with generated ID
            Baby(
                id = id ?: "",
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
                pediatricianName = pediatricianName,
                pediatricianPhone = pediatricianPhone,
                notes = notes,
                photoUrl = finalPhotoUrl,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        }
    }

    private suspend fun uploadBabyPhoto(babyId: String, photoUrl: Uri): String? {
        return try {
            Log.d(TAG, "uploadBabyPhoto: Uploading for babyId=$babyId")
            val url = repository.addPhotoToEntity("babies", babyId, photoUrl)
            Log.i(TAG, "uploadBabyPhoto: Success - url=$url")
            url
        } catch (e: Exception) {
            Log.e(TAG, "Baby Photo upload failed: ${e.message}", e)
            _errorMessage.value = "Baby Photo upload failed: ${e.localizedMessage}"
            null
        }
    }

    fun deleteBaby(babyId: String, family: Family, familyViewModel: FamilyViewModel) {
        if (!familyViewModel.canUserDeleteBaby()) {
            _errorMessage.value =
                "You don't have permission to delete baby. Only members and admins can save."
            return
        }
        _isLoading.value = true
        viewModelScope.launch {
            try {
                // 1. Delete photo from Firebase Storage and Firestore field
                repository.deletePhotoFromEntity("babies", babyId)

                // 2. Delete Firestore baby document and related events
                repository.deleteBabyAndEvents(babyId).getOrThrow()

                // 3. Remove babyId from all families
               repository.removeBabyFromFamily(family.id, babyId)

                // 4. Clear selected baby if it was deleted
                if (_selectedBaby.value?.id == babyId ||_selectedBaby.value == null) {
                    selectNextBaby()
                }

            } catch (e: Exception) {
                _errorMessage.value = "Échec de la suppression : ${e.localizedMessage}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteBabyPhoto(babyId: String, family: Family, familyViewModel: FamilyViewModel) {
        if (!familyViewModel.canUserEditBaby()) {
            _errorMessage.value =
                "You don't have permission to edit baby. Only members and admins can save."
            return
        }
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
                var finalBaby = repository.addOrUpdateBaby(updated, family).getOrThrow()

                // 5. Update selected baby
                _selectedBaby.value = finalBaby.first

            } catch (e: Exception) {
                _errorMessage.value = "Échec de la suppression de la photo : ${e.localizedMessage}"
            } finally {
                _isLoading.value = false
            }
        }
    }
}