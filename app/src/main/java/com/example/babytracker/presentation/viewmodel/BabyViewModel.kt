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

@HiltViewModel
class BabyViewModel @Inject constructor(
    private val repository: FirebaseRepository
) : ViewModel() {

    private val _babies = MutableStateFlow<List<Baby>>(emptyList())
    val babies: StateFlow<List<Baby>> = _babies.asStateFlow()

    private val _selectedBaby = MutableStateFlow<Baby?>(null)
    val selectedBaby: StateFlow<Baby?> = _selectedBaby.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Optional: Add a StateFlow for error messages
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        loadBabies()
    }

    fun loadBabies() {
        _isLoading.value = true
        _errorMessage.value = null // Clear previous error
        viewModelScope.launch {

            val result: Result<List<Baby>> = repository.getBabies() // Get the Result object

            result.fold(
                onSuccess = { babiesList ->
                    _babies.value = babiesList
                    if (_selectedBaby.value == null && babiesList.isNotEmpty()) {
                        _selectedBaby.value = babiesList.first()
                    }
                },
                onFailure = { exception ->
                    // Handle the error appropriately
                    Log.e("BabyViewModel", "Error loading babies: ${exception.message}", exception)
                    _babies.value = emptyList() // Clear babies list on error or keep previous
                    _errorMessage.value = "Failed to load babies: ${exception.localizedMessage}"
                    // TODO: Gérer l'erreur (e.g., show a snackbar or message to the user)
                }
            )
            _isLoading.value = false // Set loading to false in both cases
        }
    }

    fun selectBaby(baby: Baby) {
        _selectedBaby.value = baby
    }

    fun addBaby(name: String, birthDate: Long, gender: Gender = Gender.UNKNOWN) {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val newBaby = Baby(name = name, birthDate = birthDate, gender = gender)
                repository.addOrUpdateBaby(newBaby)
                loadBabies() // Recharger la liste
            } catch (e: Exception) {
                _errorMessage.value = "Failed to add baby: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // TODO: Ajouter des méthodes pour gérer les événements (alimentation, couches, etc.)
}