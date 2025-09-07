package com.example.babytracker.presentation.feeding // Or your ViewModel package

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.babytracker.data.BreastSide
import com.example.babytracker.data.FeedType
// Assuming FeedingEvent is in com.example.babytracker.data.event
import com.example.babytracker.data.event.FeedingEvent // [2]
import com.example.babytracker.presentation.viewmodel.EventViewModel // To delegate saving
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.util.Log
import com.example.babytracker.data.FirebaseRepository

@HiltViewModel
class FeedingViewModel @Inject constructor(
    private val repository: FirebaseRepository
) : ViewModel() {

    // --- UI State for Input Fields ---
    private val _feedType = MutableStateFlow(FeedType.BREAST_MILK) // Default to one type
    val feedType: StateFlow<FeedType> = _feedType.asStateFlow()

    private val _amountMl = MutableStateFlow<String>("") // Store as String for TextField
    val amountMl: StateFlow<String> = _amountMl.asStateFlow()

    private val _durationMinutes = MutableStateFlow<String>("") // Store as String
    val durationMinutes: StateFlow<String> = _durationMinutes.asStateFlow()

    private val _breastSide = MutableStateFlow<BreastSide?>(null)
    val breastSide: StateFlow<BreastSide?> = _breastSide.asStateFlow()

    private val _notes = MutableStateFlow("")
    val notes: StateFlow<String> = _notes.asStateFlow()

    // --- State for UI feedback ---
    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _saveSuccess = MutableStateFlow(false) // To signal UI to navigate or clear form
    val saveSuccess: StateFlow<Boolean> = _saveSuccess.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // --- Event Handlers for UI ---
    fun onFeedTypeChanged(newFeedType: FeedType) {
        _feedType.value = newFeedType
        // Optionally clear/reset other fields when feed type changes
        if (newFeedType == FeedType.FORMULA || newFeedType == FeedType.SOLID) {
            _durationMinutes.value = ""
            _breastSide.value = null
        }
        if (newFeedType == FeedType.BREAST_MILK && _breastSide.value == null) {
            // For breastfeeding, default to one side or prompt user
            _breastSide.value = BreastSide.LEFT
        }
    }

    fun onAmountMlChanged(newAmount: String) {
        _amountMl.value = newAmount
    }

    fun onDurationMinutesChanged(newDuration: String) {
        _durationMinutes.value = newDuration
    }

    fun onBreastSideChanged(newBreastSide: BreastSide?) {
        _breastSide.value = newBreastSide
    }

    fun onNotesChanged(newNotes: String) {
        _notes.value = newNotes
    }

    fun saveFeedingEvent(babyId: String) {
        if (babyId.isBlank()) {
            _errorMessage.value = "Baby ID is missing."
            return
        }

        _isSaving.value = true
        _errorMessage.value = null
        _saveSuccess.value = false

        val currentFeedType = _feedType.value
        val currentAmountMl = _amountMl.value.toDoubleOrNull()
        val currentDurationMinutes = _durationMinutes.value.toIntOrNull()
        val currentBreastSide = _breastSide.value
        val currentNotes = _notes.value.takeIf { it.isNotBlank() }

        // Perform validation based on feed type
        when (currentFeedType) {
            FeedType.BREAST_MILK -> {
                if (currentDurationMinutes == null && currentBreastSide == null && currentAmountMl == null) {
                    _errorMessage.value = "For breast milk, please provide duration/side or amount for pumped milk."
                    _isSaving.value = false
                    return
                }
            }
            FeedType.FORMULA -> {
                if (currentAmountMl == null) {
                    _errorMessage.value = "Amount (ml) is required for formula."
                    _isSaving.value = false
                    return
                }
            }
            FeedType.SOLID -> {
                if (currentNotes == null && currentAmountMl == null) { // amountMl could be used for grams
                    _errorMessage.value = "Please provide notes or amount for solid food."
                    _isSaving.value = false
                    return
                }
            }
        }

        viewModelScope.launch {
            val event = FeedingEvent(
                babyId = babyId,
                feedType = currentFeedType,
                notes = currentNotes,
                amountMl = currentAmountMl,
                durationMinutes = currentDurationMinutes,
                breastSide = currentBreastSide
            )
            val result = repository.addEvent(event)
            result.fold(
                onSuccess = {
                    Log.d("FeedingViewModel", "Feeding event saved successfully.")
                    _saveSuccess.value = true
                },
                onFailure = {
                    _errorMessage.value = "Failed to save feeding event: ${it.localizedMessage}"
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
        _feedType.value = FeedType.BREAST_MILK
        _amountMl.value = ""
        _durationMinutes.value = ""
        _breastSide.value = null
        _notes.value = ""
        _errorMessage.value = null
        _saveSuccess.value = false
    }
}

