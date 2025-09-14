package com.example.babytracker.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.babytracker.data.DiaperType
import com.example.babytracker.data.FeedType
import com.example.babytracker.data.FirebaseRepository
import com.example.babytracker.data.SleepEvent
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
    private val repository: FirebaseRepository
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

    // Necessary local var
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
            val event = SleepEvent(
                babyId = babyId,
                notes = currentNotes,
                beginTime = currentBeginTime,
                endTime = currentEndTime,
                durationMinutes = currentDuratioNMinute,
                isSleeping = currentIsSleeping,
                timestamp = Date()
            )
            val result = repository.addEvent(event)
            result.fold(
                onSuccess = {
                    Log.d("SleepViewModel", "Sleep event saved successfully.")
                    _saveSuccess.value = true
                    _errorMessage.value = null
                },
                onFailure = {
                    _errorMessage.value = "Failed to save sleep event: ${it.localizedMessage}"
                }
            )
            _isSaving.value = false
        }
    }

    override fun onCleared() {
        super.onCleared()
        timer?.cancel()
    }
    fun resetSaveSuccess() {
        _saveSuccess.value = false
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    // Optional: Function to reset all input fields
    fun resetInputFields() {
        _isSleeping.value = false
        _beginTime.value = null
        _endTime.value = null
        _durationMinutes.value = 0L
        _notes.value = ""
        _errorMessage.value = null
        _saveSuccess.value = false
    }
    // TODO: Ajouter le chargement des sessions de sommeil passées
    // TODO: Calculer la moyenne du temps de sommeil
}