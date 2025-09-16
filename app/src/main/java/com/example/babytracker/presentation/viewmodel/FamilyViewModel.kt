package com.example.babytracker.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.babytracker.data.Family
import com.example.babytracker.data.FirebaseRepository
import com.example.babytracker.data.addMemberToFamily
import com.example.babytracker.data.addOrUpdateFamily
import com.example.babytracker.data.deleteFamily
import com.example.babytracker.data.getFamilies
import com.example.babytracker.data.removeMemberFromFamily
import com.example.babytracker.data.streamFamilies
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
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

    init {
        loadFamilies()
    }

    fun loadFamilies() {
        viewModelScope.launch {
            repository.getFamilies()
                .onSuccess { list -> _families.value = list }
                .onFailure { err -> _state.update { it.copy(error = err.message) } }

            repository.streamFamilies().collect { list ->
                _families.value = list
            }
        }
    }

    fun selectFamily(family: Family) {
        _selectedFamily.value = family
    }

    fun createOrUpdateFamily(family: Family) {
        _state.value = _state.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            repository.addOrUpdateFamily(family)
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
            repository.deleteFamily(familyId)
                .onFailure { err ->
                    _state.update { it.copy(error = err.message) }
                }
        }
    }

    fun addMember(familyId: String, userId: String) {
        viewModelScope.launch {
            repository.addMemberToFamily(familyId, userId)
                .onFailure { err ->
                    _state.update { it.copy(error = err.message) }
                }
        }
    }

    fun removeMember(familyId: String, userId: String) {
        viewModelScope.launch {
            repository.removeMemberFromFamily(familyId, userId)
                .onFailure { err ->
                    _state.update { it.copy(error = err.message) }
                }
        }
    }
}

data class FamilyState(
    val isLoading: Boolean = false,
    val error: String? = null
)
