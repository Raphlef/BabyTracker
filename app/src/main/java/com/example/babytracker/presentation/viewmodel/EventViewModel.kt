package com.example.babytracker.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.babytracker.data.FirebaseRepository
import com.example.babytracker.data.event.DiaperEvent
import com.example.babytracker.data.event.Event
import com.example.babytracker.data.event.FeedingEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject
import android.util.Log
import com.example.babytracker.data.BreastSide
import com.example.babytracker.data.DiaperType
import com.example.babytracker.data.FeedType
import com.example.babytracker.data.PoopColor
import com.example.babytracker.data.PoopConsistency
import com.example.babytracker.data.event.GrowthEvent
import com.example.babytracker.data.event.SleepEvent

@HiltViewModel
class EventViewModel @Inject constructor(
    private val repository: FirebaseRepository
) : ViewModel() {

    private val _events = MutableStateFlow<Map<Any, List<Event>>>(emptyMap())
    val events: StateFlow<Map<Any, List<Event>>> = _events.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _saveSuccess = MutableStateFlow(false)
    val saveSuccess: StateFlow<Boolean> = _saveSuccess.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // --- Reset methods (like DiaperViewModel) ---
    fun resetSaveSuccess() {
        _saveSuccess.value = false
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    fun loadEvents(babyId: String) {
        if (babyId.isBlank()) {
            Log.w("EventViewModel", "loadEvents called with blank babyId")
            _events.value = emptyMap()
            _isLoading.value = false
            return
        }
        _isLoading.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            val result: Result<List<Event>> = repository.getAllEventsForBaby(babyId)
            result.fold(
                onSuccess = { eventsList ->
                    _events.value = eventsList.groupBy { event ->
                        event::class // Groups by actual class (FeedingEvent, DiaperEvent, etc.)
                    }
                },
                onFailure = { exception ->
                    Log.e(
                        "EventViewModel",
                        "Error loading events for baby $babyId: ${exception.message}",
                        exception
                    )
                    _events.value = emptyMap()
                    _errorMessage.value = "Failed to load events: ${exception.localizedMessage}"
                }
            )
            _isLoading.value = false
        }
    }

    fun addFeedingEvent(
        babyId: String,
        feedType: FeedType,
        notes: String? = null,
        amountMl: Double? = null,
        durationMinutes: Int? = null,
        breastSide: BreastSide? = null,
        timestamp: Date = Date()
    ) {
        if (babyId.isBlank()) {
            Log.w("EventViewModel", "addFeedingEvent called with blank babyId")
            _errorMessage.value = "Baby ID is missing."
            return
        }

        _isSaving.value = true
        _errorMessage.value = null
        _saveSuccess.value = false

        // Validation logic
        when (feedType) {
            FeedType.BREAST_MILK -> {
                if (durationMinutes == null && breastSide == null && amountMl == null) {
                    Log.w("EventViewModel", "Breast milk feeding event lacks typical details.")
                }
            }

            FeedType.FORMULA -> {
                if (amountMl == null) {
                    Log.w("EventViewModel", "Formula feeding event lacks amount.")
                }
            }

            FeedType.SOLID -> {
                if (notes.isNullOrBlank() && amountMl == null) {
                    Log.w("EventViewModel", "Solid feeding event lacks details.")
                }
            }
        }

        val event = FeedingEvent(
            babyId = babyId,
            feedType = feedType,
            notes = notes,
            amountMl = amountMl,
            durationMinutes = durationMinutes,
            breastSide = breastSide,
            timestamp = timestamp
        )

        viewModelScope.launch {
            val result = repository.addEvent(event)
            result.fold(
                onSuccess = {
                    Log.d("EventViewModel", "Feeding event added successfully for baby $babyId")
                    _saveSuccess.value = true
                    loadEvents(babyId)
                },
                onFailure = { exception ->
                    Log.e(
                        "EventViewModel",
                        "Error adding feeding event: ${exception.message}",
                        exception
                    )
                    _errorMessage.value =
                        "Failed to add feeding event: ${exception.localizedMessage}"
                }
            )
            _isSaving.value = false
        }
    }

    fun addGrowthEvent(
        babyId: String,
        weightKg: Double?,
        heightCm: Double?,
        headCircumferenceCm: Double?,
        notes: String?,
        measurementDate: Date = Date()
    ) {
        if (babyId.isBlank()) {
            Log.w("EventViewModel", "addGrowthEvent called with blank babyId")
            _errorMessage.value = "Baby ID is missing."
            return
        }

        _isSaving.value = true
        _errorMessage.value = null
        _saveSuccess.value = false

        if (weightKg == null && heightCm == null && headCircumferenceCm == null) {
            Log.w("EventViewModel", "addGrowthEvent called with no measurement values.")
        }

        val event = GrowthEvent(
            babyId = babyId,
            timestamp = measurementDate,
            notes = notes,
            weightKg = weightKg,
            heightCm = heightCm,
            headCircumferenceCm = headCircumferenceCm
        )

        viewModelScope.launch {
            val result = repository.addEvent(event)
            result.fold(
                onSuccess = {
                    Log.d("EventViewModel", "Growth event added successfully for baby $babyId")
                    _saveSuccess.value = true
                    loadEvents(babyId)
                },
                onFailure = { exception ->
                    Log.e(
                        "EventViewModel",
                        "Error adding growth event: ${exception.message}",
                        exception
                    )
                    _errorMessage.value =
                        "Failed to add growth event: ${exception.localizedMessage}"
                }
            )
            _isSaving.value = false
        }
    }

    fun addSleepEvent(
        babyId: String,
        isSleeping: Boolean,
        beginTime: Date?,
        endTime: Date?,
        durationMinutes: Long?,
        notes: String?,
        timestamp: Date = Date()
    ) {
        if (babyId.isBlank()) {
            Log.w("EventViewModel", "addSleepEvent called with blank babyId")
            _errorMessage.value = "Baby ID is missing."
            return
        }

        _isSaving.value = true
        _errorMessage.value = null
        _saveSuccess.value = false

        if (durationMinutes == null && endTime == null) {
            Log.w("EventViewModel", "addSleepEvent called with no duration and no end time.")
        }

        val event = SleepEvent(
            babyId = babyId,
            timestamp = timestamp,
            beginTime = beginTime,
            endTime = endTime,
            isSleeping = isSleeping,
            durationMinutes = durationMinutes,
            notes = notes
        )

        viewModelScope.launch {
            val result = repository.addEvent(event)
            result.fold(
                onSuccess = {
                    Log.d("EventViewModel", "Sleep event added successfully for baby $babyId")
                    _saveSuccess.value = true
                    loadEvents(babyId)
                },
                onFailure = { exception ->
                    Log.e(
                        "EventViewModel",
                        "Error adding sleep event: ${exception.message}",
                        exception
                    )
                    _errorMessage.value = "Failed to add sleep event: ${exception.localizedMessage}"
                }
            )
            _isSaving.value = false
        }
    }

    fun addDiaperEvent(
        babyId: String,
        diaperType: DiaperType,
        notes: String? = null,
        poopColor: PoopColor? = null,
        poopConsistency: PoopConsistency? = null,
        timestamp: Date = Date()
    ) {
        if (babyId.isBlank()) {
            Log.w("EventViewModel", "addDiaperEvent called with blank babyId")
            _errorMessage.value = "Baby ID is missing."
            return
        }

        _isSaving.value = true
        _errorMessage.value = null
        _saveSuccess.value = false

        val event = DiaperEvent(
            babyId = babyId,
            diaperType = diaperType,
            notes = notes,
            poopColor = poopColor,
            poopConsistency = poopConsistency,
            timestamp = timestamp
        )

        viewModelScope.launch {
            val result = repository.addEvent(event)
            result.fold(
                onSuccess = {
                    Log.d("EventViewModel", "Diaper event added successfully for baby $babyId")
                    _saveSuccess.value = true
                    loadEvents(babyId)
                },
                onFailure = { exception ->
                    Log.e(
                        "EventViewModel",
                        "Error adding diaper event: ${exception.message}",
                        exception
                    )
                    _errorMessage.value =
                        "Failed to add diaper event: ${exception.localizedMessage}"
                }
            )
            _isSaving.value = false
        }
    }
}
