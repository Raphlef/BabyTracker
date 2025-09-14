package com.example.babytracker.presentation.viewmodel

import android.graphics.Color
import android.util.Log
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.babytracker.data.FirebaseRepository
import com.example.babytracker.data.GrowthEvent
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import androidx.compose.ui.graphics.Color as ComposeColor

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

    fun setStartDate(date: Date, babyId: String) {
        _startDate.value = date
        loadGrowthEventsInRange(babyId)
    }

    /** Met à jour la date de fin et recharge les events */
    fun setEndDate(date: Date, babyId: String) {
        _endDate.value = date
        loadGrowthEventsInRange(babyId)
    }
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

    fun loadGrowthEventsInRange(babyId: String) {
        viewModelScope.launch {
            _isLoading.value = true

            // 1. Bornes normalisées
            val calStart = Calendar.getInstance().apply {
                time = _startDate.value
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            val calEnd = Calendar.getInstance().apply {
                time = _endDate.value
                set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
            }
            val start = calStart.time
            val end   = calEnd.time

            // 2. Calcul de la durée en jours
            val days = ((end.time - start.time) / (1000 * 60 * 60 * 24)).toInt().coerceAtLeast(0)

            val events: List<GrowthEvent> = when {
                days == 0 -> {
                    // Même jour → un seul point
                    repository.getGrowthEventsInRange(babyId, start, end)
                        .getOrNull().orEmpty().take(1)
                }
                days <= 30 -> {
                    // Période courte (≤1 mois) → tous les événements
                    repository.getGrowthEventsInRange(babyId, start, end)
                        .getOrNull().orEmpty()
                }
                else -> {
                    // Période longue (>2 semaines) → un point par semaine
                    val weeks = splitIntoWeeks(start, end)
                    weeks.mapNotNull { (weekStart, weekEnd) ->
                        repository.getOneGrowthEventInRange(babyId, weekStart, weekEnd)
                            .getOrNull()
                    }
                }
            }

            // 3. Mise à jour et tri pour le chart
            _growthEvents.value = events.sortedBy { it.timestamp }
            _isLoading.value = false
        }
    }

    fun getMultiLineData(): Pair<LineData, IndexAxisValueFormatter> {
        val events = _growthEvents.value.sortedBy { it.timestamp.time }
        Log.d("GrowthVM", "Display ${events.size} growth events for chart")
        // Axe X : liste de dates formatées
        val dateLabels = events.map { SimpleDateFormat("dd/MM", Locale.getDefault()).format(it.timestamp) }

        // Création des Entry pour chaque propriété
        val weightEntries = events.mapIndexed { i, e -> Entry(i.toFloat(), e.weightKg?.toFloat() ?: 0f) }
        val heightEntries = events.mapIndexed { i, e -> Entry(i.toFloat(), e.heightCm?.toFloat() ?: 0f) }
        val headEntries   = events.mapIndexed { i, e -> Entry(i.toFloat(), e.headCircumferenceCm?.toFloat() ?: 0f) }

        // DataSets
        val weightSet = LineDataSet(weightEntries, "Poids (kg)").apply {
            color = ComposeColor.Blue.toArgb()
            valueTextColor = ComposeColor.Blue.toArgb()
            lineWidth = 2f; circleRadius = 4f; setDrawCircles(true); setDrawValues(false)
        }
        val heightSet = LineDataSet(heightEntries, "Taille (cm)").apply {
            color = ComposeColor.Green.toArgb()
            valueTextColor = ComposeColor.Green.toArgb()
            lineWidth = 2f; circleRadius = 4f; setDrawCircles(true); setDrawValues(false)
        }
        val headSet = LineDataSet(headEntries, "Périmètre (cm)").apply {
            color = ComposeColor.Red.toArgb()
            valueTextColor = ComposeColor.Red.toArgb()
            lineWidth = 2f; circleRadius = 4f; setDrawCircles(true); setDrawValues(false)
        }

        // Concaténation et formatter pour l'axe X
        val data = LineData(weightSet, heightSet, headSet)
        val xFormatter = IndexAxisValueFormatter(dateLabels)
        return Pair(data, xFormatter)
    }

    /**
     * Découpe l’intervalle [startDate,endDate] en sous-intervalles d’une semaine.
     * Chaque Pair représente le début (lundi 00:00) et la fin (dimanche 23:59:59) d’une semaine.
     */
    private fun splitIntoWeeks(startDate: Date, endDate: Date): List<Pair<Date, Date>> {
        val weeks = mutableListOf<Pair<Date, Date>>()
        val calStart = Calendar.getInstance().apply {
            time = startDate
            // Ajuste au lundi de la semaine
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val calEnd = Calendar.getInstance().apply { time = endDate }
        while (calStart.time.before(endDate)) {
            val weekStart = calStart.time
            // Calcule le dimanche de cette semaine
            calStart.add(Calendar.DAY_OF_WEEK, 6)
            calStart.set(Calendar.HOUR_OF_DAY, 23); calStart.set(Calendar.MINUTE, 59)
            calStart.set(Calendar.SECOND, 59); calStart.set(Calendar.MILLISECOND, 999)
            val weekEnd = if (calStart.time.before(endDate)) calStart.time else endDate
            weeks += weekStart to weekEnd
            // Passe au lundi suivant
            calStart.time = weekEnd
            calStart.add(Calendar.DAY_OF_MONTH, 1)
            calStart.set(Calendar.HOUR_OF_DAY, 0); calStart.set(Calendar.MINUTE, 0)
            calStart.set(Calendar.SECOND, 0); calStart.set(Calendar.MILLISECOND, 0)
        }
        return weeks
    }

    fun saveGrowthEvent(babyId: String) {
        if (babyId.isBlank()) {
            _errorMessage.value = "Baby ID is missing."
            return
        }

        viewModelScope.launch {
            _isSaving.value = true

            val timestampDate = Date(_measurementTimestamp.value)

            // 1. Calculer le début et la fin de la journée sélectionnée
            val cal = Calendar.getInstance().apply { timeInMillis = _measurementTimestamp.value }
            cal.set(Calendar.HOUR_OF_DAY, 0);  cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0);       cal.set(Calendar.MILLISECOND, 0)
            val dayStart = cal.time
            cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59)
            cal.set(Calendar.SECOND, 59);      cal.set(Calendar.MILLISECOND, 999)
            val dayEnd = cal.time

            // 2. Rechercher un événement existant sur la même date
            val existingResult = repository.getGrowthEventsInRange(babyId, dayStart, dayEnd)
            val existingEvent = existingResult.getOrNull().orEmpty().firstOrNull()
            val newId = existingEvent?.id ?: UUID.randomUUID().toString()
            val event = GrowthEvent(
                id = newId,
                babyId = babyId,
                timestamp = timestampDate,
                weightKg = _weightKg.value,
                heightCm = _heightCm.value,
                headCircumferenceCm = _headCircumferenceCm.value,
                notes = _notes.value
            )

            val result = repository.addEvent(event)
            result.fold(
                onSuccess = {
                    Log.d("GrowthViewModel", "Growth event saved successfully.")
                    _saveSuccess.value = true
                    _errorMessage.value = null
                    loadGrowthEventsInRange(babyId)
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

    fun loadLastGrowth(babyId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            repository.getLastGrowthEvent(babyId)
                .onSuccess { event ->
                event?.let {
                    _measurementTimestamp.value = it.timestamp.time
                    _weightKg.value = it.weightKg ?: 0.0
                    _heightCm.value = it.heightCm ?: 0.0
                    _headCircumferenceCm.value = it.headCircumferenceCm ?: 0.0
                    _notes.value = it.notes.orEmpty()
                }
            }
                .onFailure { throwable ->
                    _errorMessage.value = "Erreur chargement historique: ${throwable.message}"
                    Log.e("GrowthVM", "loadLastGrowth failed", throwable)
                }
            _isLoading.value = false
        }
    }
}