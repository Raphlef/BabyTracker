package com.kouloundissa.twinstracker.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.kouloundissa.twinstracker.data.Family
import com.kouloundissa.twinstracker.data.FirebaseRepository
import com.kouloundissa.twinstracker.data.addMemberToFamily
import com.kouloundissa.twinstracker.data.addOrUpdateFamily
import com.kouloundissa.twinstracker.data.deleteFamily
import com.kouloundissa.twinstracker.data.getFamilies
import com.kouloundissa.twinstracker.data.joinFamilyByCode
import com.kouloundissa.twinstracker.data.regenerateInviteCode
import com.kouloundissa.twinstracker.data.removeMemberFromFamily
import com.kouloundissa.twinstracker.data.streamFamilies
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FamilyViewModel @Inject constructor(
    private val repository: FirebaseRepository,
    private val  firebaseAuth: FirebaseAuth
) : ViewModel() {

    private val _families = MutableStateFlow<List<Family>>(emptyList())
    val families: StateFlow<List<Family>> = _families.asStateFlow()

    private val _selectedFamily = MutableStateFlow<Family?>(null)
    val selectedFamily: StateFlow<Family?> = _selectedFamily.asStateFlow()

    private val _state = MutableStateFlow(FamilyState())
    val state: StateFlow<FamilyState> = _state.asStateFlow()

    private val _inviteResult = MutableSharedFlow<Result<Unit>>()
    val inviteResult: SharedFlow<Result<Unit>> = _inviteResult

    private val _newCode = MutableSharedFlow<String>()
    val newCode: SharedFlow<String> = _newCode


    // Backing StateFlow for currentUserId
    private val _currentUserId = MutableStateFlow<String?>(null)
    val currentUserId: StateFlow<String?> = _currentUserId.asStateFlow()

    private var familiesJob: Job? = null

    private val authStateFlow: Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            trySend(auth.currentUser)
        }
        firebaseAuth.addAuthStateListener(listener)
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }


    init {
        authStateFlow
            .map { it != null }
            .distinctUntilChanged()
            .onEach { isAuth ->
                if (isAuth) {
                    _currentUserId.value = repository.getCurrentUserId()
                    startObservingFamilyUpdates()
                } else {
                    stopObservingFamilyUpdates()
                    _currentUserId.value = null
                    _families.value = emptyList()
                    _state.value = FamilyState()  // reset loading/error
                }
            }
            .launchIn(viewModelScope)
    }


    private fun startObservingFamilyUpdates() {
        familiesJob?.cancel()
        familiesJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                repository.streamFamilies()
                    .collect { list ->
                        _families.value = list
                        _state.update { it.copy(isLoading = false, error = null) }
                    }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load families"
                    )
                }
            }
        }
    }

    private fun stopObservingFamilyUpdates() {
        familiesJob?.cancel()
        familiesJob = null
    }


    fun selectFamily(family: Family?) {
        _selectedFamily.value = family
    }

    fun createOrUpdateFamily(family: Family) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            try {
                val currentUserId = repository.getCurrentUserId()

                if (currentUserId.isNullOrBlank()) {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = "User not authenticated. Please sign in again."
                        )
                    }
                    return@launch
                }
                val existingFamily = _families.value.find { it.id == family.id }
                val isNewFamily = existingFamily == null
                // For new families, ensure current user is added as admin AND member
                val familyToSave = if (isNewFamily) {
                    family.copy(
                        adminIds = listOf(currentUserId),
                        memberIds = listOf(currentUserId) // Also add to members
                    )
                } else {
                    family
                }

                repository.addOrUpdateFamily(familyToSave)
                    .onSuccess { updatedFamily ->
                        // Update the selected family if it was the one being updated
                        _selectedFamily.value = (updatedFamily ?: familyToSave) as Family?
                        _state.update { it.copy(isLoading = false) }
                    }
                    .onFailure { err ->
                        _state.update {
                            it.copy(
                                isLoading = false,
                                error = err.message ?: "Failed to create family"
                            )
                        }
                    }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = "Error creating family: ${e.message}"
                    )
                }
            }
        }
    }


    fun deleteFamily(familyId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            repository.deleteFamily(familyId)
                .onSuccess {
                    _state.update { it.copy(isLoading = false) }
                }
                .onFailure { err ->
                    _state.update { it.copy(isLoading = false, error = err.message) }
                }
        }
    }

    fun addMember(familyId: String, userId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            repository.addMemberToFamily(familyId, userId)
                .onSuccess {
                    _state.update { it.copy(isLoading = false) }
                }
                .onFailure { err ->
                    _state.update { it.copy(isLoading = false, error = err.message) }
                }
        }
    }

    fun removeMember(familyId: String, userId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                // Get current family from cached families list
                val family = _families.value.firstOrNull { it.id == familyId }
                    ?: return@launch _state.update {
                        it.copy(
                            isLoading = false,
                            error = "Famille introuvable"
                        )
                    }

                // Safety check: prevent leaving if user is the only admin
                if (family?.adminIds?.contains(userId) == true && family.adminIds.size == 1) {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = "Vous ne pouvez pas quitter car vous êtes le seul administrateur. Nommez d'abord un autre admin."
                        )
                    }
                    return@launch
                }

                repository.removeMemberFromFamily(familyId, userId)
                    .onSuccess {
                        // Clear selection if user left the currently selected family
                        if (_selectedFamily.value?.id == familyId) {
                            _selectedFamily.value = null
                        }
                        _state.update { it.copy(isLoading = false) }
                    }
                    .onFailure { err ->
                        _state.update { it.copy(isLoading = false, error = err.message) }
                    }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    /** Regenerates invite code for the selected family */
    fun regenerateCode() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val fam = _selectedFamily.value
                ?: return@launch _state.update { it.copy(isLoading = false, error = "No family") }
            repository.regenerateInviteCode(fam)
                .onSuccess { code ->
                    // emit new code for UI
                    _newCode.emit(code)
                    // update selectedFamily
                    _selectedFamily.value = fam.copy(
                        inviteCode = code,
                        updatedAt = System.currentTimeMillis()
                    )
                    _state.update { it.copy(isLoading = false) }
                }
                .onFailure { err ->
                    _state.update { it.copy(isLoading = false, error = err.message) }
                }
        }
    }

    /** Joins a family via invite code */
    fun joinByCode(code: String) {
        viewModelScope.launch {
            val currentUserId = repository.getCurrentUserId()
                ?: return@launch _state.update {
                    it.copy(
                        isLoading = false,
                        error = "User not authenticated"
                    )
                }
            repository.joinFamilyByCode(code, currentUserId)
                .onSuccess {
                    _inviteResult.emit(Result.success(Unit))
                }
                .onFailure { ex ->
                    _inviteResult.emit(Result.failure(ex))
                }
        }
    }

}

data class FamilyState(
    val isLoading: Boolean = false,
    val error: String? = null
)
