package com.kouloundissa.twinstracker.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kouloundissa.twinstracker.data.DiaperEvent
import com.kouloundissa.twinstracker.data.DiaperType
import com.kouloundissa.twinstracker.data.FirebaseRepository
import com.kouloundissa.twinstracker.data.PoopColor
import com.kouloundissa.twinstracker.data.PoopConsistency
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

    private val _timestamp = MutableStateFlow(Date())
    val timestamp: StateFlow<Date> = _timestamp.asStateFlow()

    private val _poopColor = MutableStateFlow<PoopColor?>(null)
    val poopColor: StateFlow<PoopColor?> = _poopColor.asStateFlow()

    private val _poopConsistency = MutableStateFlow<PoopConsistency?>(null)
    val poopConsistency: StateFlow<PoopConsistency?> = _poopConsistency.asStateFlow()


    // --- State for UI feedback ---
    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _saveSuccess = MutableStateFlow(false) // To signal UI to navigate or clear form
    val saveSuccess: StateFlow<Boolean> = _saveSuccess.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // --- Event Handlers for UI ---

    fun onDiaperTypeChanged(newType: DiaperType) {
        _diaperType.value = newType
        // Reset poop fields if not DIRTY or MIXED
        if (newType != DiaperType.DIRTY && newType != DiaperType.MIXED) {
            _poopColor.value = null
            _poopConsistency.value = null
        }
    }

    fun onNotesChanged(newNotes: String) {
        _notes.value = newNotes
    }

    fun onPoopColorChanged(color: PoopColor) {
        _poopColor.value = color
    }

    fun onPoopConsistencyChanged(consistency: PoopConsistency) {
        _poopConsistency.value = consistency
    }

    fun onTimestampChanged(newTimestamp: Date) {
        _timestamp.value = newTimestamp
    }

    fun saveDiaperEvent(babyId: String) {
        if (babyId.isBlank()) {
            _errorMessage.value = "Baby ID is missing."
            return
        }

        _isSaving.value = true
        _errorMessage.value = null
        _saveSuccess.value = false

        val event = DiaperEvent(
            babyId = babyId,
            timestamp = _timestamp.value,
            notes = _notes.value.takeIf { it.isNotBlank() },
            diaperType = _diaperType.value,
            poopColor = _poopColor.value,
            poopConsistency = _poopConsistency.value
        )

        viewModelScope.launch {

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
        _poopColor.value = null
        _poopConsistency.value = null
        _notes.value = ""
        _errorMessage.value = null
        _saveSuccess.value = false
    }
}