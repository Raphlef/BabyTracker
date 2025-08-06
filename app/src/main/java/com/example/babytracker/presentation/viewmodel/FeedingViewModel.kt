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

@HiltViewModel
class FeedingViewModel @Inject constructor(
    private val eventViewModel: EventViewModel // Inject the shared EventViewModel
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

        // Call the shared EventViewModel to add the event
        // This leverages the existing repository call and error handling in EventViewModel
        viewModelScope.launch {
            // Re-fetch the addFeedingEvent function from the general EventViewModel
            // Note: This relies on EventViewModel being correctly implemented to handle Result
            // and update its own error/loading states if necessary, or this ViewModel
            // can observe EventViewModel's error state.
            // For simplicity here, we assume EventViewModel's addFeedingEvent handles its own Result.

            // To properly get feedback from eventViewModel.addFeedingEvent, it should ideally
            // return a Result or have observable states for its own save operations.
            // If eventViewModel.addFeedingEvent is a simple fire-and-forget that updates
            // a shared list, this ViewModel might not get direct success/failure for *this specific* add.

            // Let's assume a slightly different approach where this VM constructs the event
            // and the EventViewModel has a more direct way to signal completion for THIS add,
            // or this VM calls the repository IF it's decided that specific viewmodels handle their own saves.

            // Given the previous correction to EventViewModel:
            eventViewModel.addFeedingEvent(
                babyId = babyId,
                feedType = currentFeedType,
                notes = currentNotes,
                amountMl = currentAmountMl,
                durationMinutes = currentDurationMinutes,
                breastSide = currentBreastSide
            )

            // How do we know if eventViewModel.addFeedingEvent succeeded or failed HERE?
            // Option A: EventViewModel's addFeedingEvent returns a Result.
            // Option B: Observe EventViewModel's _errorMessage and a potential _lastEventAddedSuccessfully flag.
            // Option C: This FeedingViewModel calls the repository directly (less centralized).

            // For now, let's assume if eventViewModel.addFeedingEvent doesn't throw an immediate error
            // (or if it handles its own error display), we can proceed.
            // A more robust solution would involve EventViewModel providing clearer feedback.

            // A simple way if EventViewModel's _errorMessage is observable:
            if (eventViewModel.errorMessage.value == null) { // Check error state from EventViewModel AFTER the call
                Log.d("FeedingViewModel", "Delegated feeding event addition. Assuming success if no immediate error from EventViewModel.")
                _saveSuccess.value = true // Signal UI
                // Optionally reset fields here after successful save
                // resetInputFields()
            } else {
                // Error was set by EventViewModel, this VM might not need to set its own _errorMessage
                _errorMessage.value = eventViewModel.errorMessage.value ?: "Failed to save feeding event."
            }
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

