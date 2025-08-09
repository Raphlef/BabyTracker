package com.example.babytracker.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.babytracker.data.DiaperType
import com.example.babytracker.data.event.SleepEvent
import com.example.babytracker.data.FirebaseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject
import kotlin.concurrent.timer

@HiltViewModel
class SleepViewModel @Inject constructor(
    private val eventViewModel: EventViewModel
) : ViewModel() {

    // --- UI State for Input Fields ---
    private val _notes = MutableStateFlow("")
    val notes: StateFlow<String> = _notes.asStateFlow()
    private val _isSleeping = MutableStateFlow(false)
    val isSleeping: StateFlow<Boolean> = _isSleeping.asStateFlow()

    private val _beginTime = MutableStateFlow(null as Date?)
    val beginTime: StateFlow<Date?> = _beginTime.asStateFlow()

    private val _endTime = MutableStateFlow(null as Date?)
    val endTime: StateFlow<Date?> = _endTime.asStateFlow()

    private val _durationMinutes = MutableStateFlow(0L)
    val durationMinutes: StateFlow<Long> = _durationMinutes.asStateFlow()
    // --- State for UI feedback ---
    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _saveSuccess = MutableStateFlow(false) // To signal UI to navigate or clear form
    val saveSuccess: StateFlow<Boolean> = _saveSuccess.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var timer: Timer? = null

    // --- Event Handlers for UI ---

    fun onNotesChanged(newNotes: String) {
        _notes.value = newNotes
    }
    fun startSleep() {
        startTimer()
        _isSleeping.value = true
        _beginTime.value=Date()
    }

    fun stopSleep() {
        timer?.cancel()
        _isSleeping.value = false
        _endTime.value=Date()
    }

    private fun startTimer() {
        timer = timer(period = 60000) { // Toutes les minutes
            _durationMinutes.value= _durationMinutes.value+1
        }
    }

    fun saveSleepEvent(babyId: String) {
        if (babyId.isBlank()) {
            _errorMessage.value = "Baby ID is missing."
            return
        }

        _isSaving.value = true
        _errorMessage.value = null
        _saveSuccess.value = false

        val currentBeginTime = _beginTime.value
        val currentEndTime = _endTime.value
        val currentDuratioNMinute = _durationMinutes.value
        val currentIsSleeping = _isSleeping.value
        val currentNotes = _notes.value.takeIf { it.isNotBlank() }

        viewModelScope.launch {
            eventViewModel.addSleepEvent(
                babyId = babyId,
                notes = currentNotes,
                beginTime = currentBeginTime,
                endTime = currentEndTime,
                durationMinutes = currentDuratioNMinute,
                isSleeping = currentIsSleeping
            )
            // A simple way if EventViewModel's _errorMessage is observable:
            if (eventViewModel.errorMessage.value == null) { // Check error state from EventViewModel AFTER the call
                Log.d("DiaperViewModel", "Delegated diaper event addition. Assuming success if no immediate error from EventViewModel.")
                _saveSuccess.value = true // Signal UI
                // Optionally reset fields here after successful save
                // resetInputFields()
            } else {
                // Error was set by EventViewModel, this VM might not need to set its own _errorMessage
                _errorMessage.value = eventViewModel.errorMessage.value ?: "Failed to save diaper event."
            }
            _isSaving.value = false
        }
    }

    override fun onCleared() {
        super.onCleared()
        timer?.cancel()
    }

    // TODO: Ajouter le chargement des sessions de sommeil passées
    // TODO: Calculer la moyenne du temps de sommeil
}