package com.example.babytracker.presentation.viewmodel

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.babytracker.data.event.GrowthEvent
import com.example.babytracker.data.FirebaseRepository
import com.github.mikephil.charting.data.LineData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class GrowthViewModel @Inject constructor(
    private val repository: FirebaseRepository
) : ViewModel() {

    private val _measurementDate = MutableStateFlow(LocalDate.now())
    val measurementDate: StateFlow<LocalDate> = _measurementDate.asStateFlow()
    // --- UI State for Input Fields ---
    private val _notes = MutableStateFlow("")
    val notes: StateFlow<String> = _notes.asStateFlow()

    private val _weightKg = MutableStateFlow(0.0)
    val weightKg: StateFlow<Double?> = _weightKg.asStateFlow()

    private val _heightCm = MutableStateFlow(0.0)
    val heightCm: StateFlow<Double?> = _heightCm.asStateFlow()

    private val _headCircumferenceCm = MutableStateFlow(0.0)
    val headCircumferenceCm: StateFlow<Double?> = _headCircumferenceCm.asStateFlow()

    // --- State for UI feedback ---
    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _saveSuccess = MutableStateFlow(false) // To signal UI to navigate or clear form
    val saveSuccess: StateFlow<Boolean> = _saveSuccess.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // --- Event Handlers for UI ---

    fun onNotesChanged(newNotes: String) {
        _notes.value = newNotes
    }

    fun setWeight(newWeight: Double) {
        _weightKg.value = newWeight
    }

    fun setHeight(newHeight: Double) {
        _heightCm.value = newHeight
    }

    fun setHeadCircumferenceCm(newHeadCircumferenceCm: Double) {
        _headCircumferenceCm.value = newHeadCircumferenceCm
    }

    fun setMeasurementDate(date: LocalDate) {
        _measurementDate.value = date
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun saveGrowthEvent(babyId: String) {
        if (babyId.isBlank()) {
            _errorMessage.value = "Baby ID is missing."
            return
        }

        _isSaving.value = true
        _errorMessage.value = null
        _saveSuccess.value = false

        val currentHeightCm = _heightCm.value
        val currentWeightKg = _weightKg.value
        val currentHeadCircumferenceCm = _headCircumferenceCm.value
        val currentNotes = _notes.value.takeIf { it.isNotBlank() }
        val localDate = measurementDate.value
        val instant = localDate.atStartOfDay(ZoneId.systemDefault()).toInstant()
        val timestampDate = Date.from(instant)
        viewModelScope.launch {
            val event = GrowthEvent(
                babyId = babyId,
                heightCm = currentHeightCm,
                weightKg = currentWeightKg,
                headCircumferenceCm = currentHeadCircumferenceCm,
                timestamp = timestampDate,
                notes = currentNotes
            )
            val result = repository.addEvent(event)
            result.fold(
                onSuccess = {
                    Log.d("GrowthViewModel", "Growth event saved successfully.")
                    _saveSuccess.value = true
                    _errorMessage.value = null
                },
                onFailure = {
                    _errorMessage.value = "Failed to save growth event: ${it.localizedMessage}"
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
        _heightCm.value = 0.0
        _weightKg.value = 0.0
        _headCircumferenceCm.value = 0.0
        _notes.value = ""
        _errorMessage.value = null
        _saveSuccess.value = false
    }


    // TODO: Ajouter le calcul des percentiles selon les courbes OMS
    // TODO: Implémenter la génération des courbes de croissance
}