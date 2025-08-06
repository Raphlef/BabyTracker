package com.example.babytracker.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.babytracker.data.DiaperType
import com.example.babytracker.data.SleepEvent
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

    private val _durationMinutes = MutableStateFlow(0)
    val durationMinutes: StateFlow<Int> = _durationMinutes.asStateFlow()
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

        viewModelScope.launch {
            try {
                val endTime = Calendar.getInstance().apply {
                    time = state.startTime!!
                    add(Calendar.MINUTE, state.duration)
                }.time

                repository.addEvent(
                    SleepEvent(
                        babyId = babyId,
                        timestamp = state.startTime!!,
                        endTime = endTime,
                        duration = state.duration
                    )
                )
                // Réinitialiser
                _state.value = SleepState()
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            } finally {
                _state.value = _state.value.copy(isLoading = false)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        timer?.cancel()
    }

    // TODO: Ajouter le chargement des sessions de sommeil passées
    // TODO: Calculer la moyenne du temps de sommeil
}