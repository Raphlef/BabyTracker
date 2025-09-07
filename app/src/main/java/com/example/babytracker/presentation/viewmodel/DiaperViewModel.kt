package com.example.babytracker.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.babytracker.data.DiaperType
import com.example.babytracker.data.FeedType
import com.example.babytracker.data.event.DiaperEvent
import com.example.babytracker.data.FirebaseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class DiaperViewModel @Inject constructor(
    private val repository: FirebaseRepository
) : ViewModel() {

    // --- UI State for Input Fields ---
    private val _diaperType = MutableStateFlow(DiaperType.DRY)
    val diaperType: StateFlow<DiaperType> = _diaperType.asStateFlow()

    private val _notes = MutableStateFlow("")
    val notes: StateFlow<String> = _notes.asStateFlow()

    private val _color = MutableStateFlow("")
    val color: StateFlow<String> = _color.asStateFlow()

    private val _consistency = MutableStateFlow("")
    val consistency: StateFlow<String> = _consistency.asStateFlow()

    // --- State for UI feedback ---
    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _saveSuccess = MutableStateFlow(false) // To signal UI to navigate or clear form
    val saveSuccess: StateFlow<Boolean> = _saveSuccess.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // --- Event Handlers for UI ---

    fun onDiaperTypeChanged(newDiaperType: DiaperType) {
        _diaperType.value = newDiaperType
    }

    fun onNotesChanged(newNotes: String) {
        _notes.value = newNotes
    }

    fun onColorChanged(newColor: String) {
        _color.value = newColor
    }

    fun onConsistencyChanged(newConsistency: String) {
        _consistency.value = newConsistency
    }
    fun saveDiaperEvent(babyId: String) {
        if (babyId.isBlank()) {
            _errorMessage.value = "Baby ID is missing."
            return
        }

        _isSaving.value = true
        _errorMessage.value = null
        _saveSuccess.value = false

        val currentDiaperType = _diaperType.value
        val currentNotes = _notes.value.takeIf { it.isNotBlank() }
        val currentColor = _color.value.takeIf { it.isNotBlank() }
        val currentConsistency = _consistency.value.takeIf { it.isNotBlank() }
        viewModelScope.launch {
            val event = DiaperEvent(
                babyId = babyId,
                diaperType = currentDiaperType,
                notes = currentNotes,
                color = currentColor,
                consistency = currentConsistency
            )
            val result = repository.addEvent(event)
            result.fold(
                onSuccess = {
                    Log.d("DiaperViewModel", "Diaper event saved successfully.")
                    _saveSuccess.value = true
                    _errorMessage.value = null
                },
                onFailure = {
                    _errorMessage.value = "Failed to save diaper event: ${it.localizedMessage}"
                }
            )
            _isSaving.value = false
        }

    }

    fun resetSaveSuccess() {
        _saveSuccess.value = false
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    // Optional: Function to reset all input fields
    fun resetInputFields() {
        _diaperType.value = DiaperType.DRY
        _color.value = ""
        _consistency.value = ""
        _notes.value = ""
        _errorMessage.value = null
        _saveSuccess.value = false
    }
}