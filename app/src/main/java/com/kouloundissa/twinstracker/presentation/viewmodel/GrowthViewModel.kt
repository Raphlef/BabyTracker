package com.kouloundissa.twinstracker.presentation.viewmodel

import androidx.lifecycle.ViewModel
import com.github.mikephil.charting.data.LineData
import com.kouloundissa.twinstracker.data.Firestore.FirebaseRepository
import com.kouloundissa.twinstracker.data.GrowthEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class GrowthViewModel @Inject constructor(
    private val repository: FirebaseRepository
) : ViewModel() {

    private val _startDate = MutableStateFlow<Date>(Date().apply {
        time = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000 // 7 jours par défaut
    })
    val startDate: StateFlow<Date> = _startDate.asStateFlow()

    private val _endDate = MutableStateFlow<Date>(Date())
    val endDate: StateFlow<Date> = _endDate.asStateFlow()
    private val _measurementTimestamp = MutableStateFlow(System.currentTimeMillis())
    val measurementTimestamp: StateFlow<Long> = _measurementTimestamp.asStateFlow()

    private val _chartData = MutableStateFlow<LineData?>(null)
    val chartData: StateFlow<LineData?> = _chartData.asStateFlow()
    // --- UI State for Input Fields ---
    private val _notes = MutableStateFlow("")
    val notes: StateFlow<String> = _notes.asStateFlow()

    private val _weightKg = MutableStateFlow(0.0)
    val weightKg: StateFlow<Double?> = _weightKg.asStateFlow()

    private val _heightCm = MutableStateFlow(0.0)
    val heightCm: StateFlow<Double?> = _heightCm.asStateFlow()

    private val _headCircumferenceCm = MutableStateFlow(0.0)
    val headCircumferenceCm: StateFlow<Double?> = _headCircumferenceCm.asStateFlow()

    private val _growthEvents = MutableStateFlow<List<GrowthEvent>>(emptyList())
    val growthEvents: StateFlow<List<GrowthEvent>> = _growthEvents.asStateFlow()

    // --- State for UI feedback ---
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
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

    fun setMeasurementTimestamp(ms: Long) {
        _measurementTimestamp.value = ms
    }
    fun resetSaveSuccess() {
        _saveSuccess.value = false
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }


}