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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class BabyViewModel @Inject constructor(
    private val repository: FirebaseRepository
) : ViewModel() {

    val babies: StateFlow<List<Baby>> = repository
        .streamBabies()
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

    fun selectBaby(baby: Baby) {
        _selectedBaby.value = baby
    }

    fun addBaby(name: String, birthDate: Long, gender: Gender = Gender.UNKNOWN) {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val newBaby = Baby(name = name, birthDate = birthDate, gender = gender)
                repository.addOrUpdateBaby(newBaby)
            } catch (e: Exception) {
                _errorMessage.value = "Failed to add baby: ${e.message}"
            } finally {
                _isLoading.value = false
            }
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

    fun deleteBaby(babyId: String) {
        _isLoading.value = true
        viewModelScope.launch {
            repository.deleteBaby(babyId).onFailure {
                _errorMessage.value = "Échec de la suppression : ${it.message}"
            }
            if (_selectedBaby.value?.id == babyId) {
                _selectedBaby.value = null
            }
            _isLoading.value = false
        }
    }
}