package com.example.babytracker.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.babytracker.data.Family
import com.example.babytracker.data.FirebaseRepository
import com.example.babytracker.data.addMemberToFamily
import com.example.babytracker.data.addOrUpdateFamily
import com.example.babytracker.data.deleteFamily
import com.example.babytracker.data.getFamilies
import com.example.babytracker.data.joinFamilyByCode
import com.example.babytracker.data.regenerateInviteCode
import com.example.babytracker.data.removeMemberFromFamily
import com.example.babytracker.data.streamFamilies
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FamilyViewModel @Inject constructor(
    private val repository: FirebaseRepository
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


    init {
        loadFamilies()
        observeFamilyUpdates()
        _currentUserId.value = repository.getCurrentUserId()
    }

    private fun loadFamilies() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            repository.getFamilies()
                .onSuccess { list ->
                    _families.value = list
                    _state.update { it.copy(isLoading = false) }
                }
                .onFailure { err ->
                    _state.update { it.copy(isLoading = false, error = err.message) }
                }
        }
    }

    private fun observeFamilyUpdates() {
        viewModelScope.launch {
            repository.streamFamilies().collect { list ->
                _families.value = list
            }
        }
    }

    fun selectFamily(family: Family?) {
        _selectedFamily.value = family
    }

    fun createOrUpdateFamily(family: Family) {
        _state.value = _state.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            // For new families, ensure current user is added as admin
            val familyToSave = if (family.id.isBlank()) {
                val currentUserId = repository.getCurrentUserId()
                    ?: return@launch _state.update {
                        it.copy(
                            isLoading = false,
                            error = "User not authenticated"
                        )
                    }
                family.copy(adminIds = listOf(currentUserId))
            } else {
                family
            }

            repository.addOrUpdateFamily(familyToSave)
                .onSuccess {
                    _state.value = _state.value.copy(isLoading = false)
                }
                .onFailure { err ->
                    _state.value = _state.value.copy(isLoading = false, error = err.message)
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
