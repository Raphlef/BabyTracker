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
import com.example.babytracker.data.event.EventFormState
import com.example.babytracker.data.event.GrowthEvent
import com.example.babytracker.data.event.SleepEvent
import java.text.SimpleDateFormat
import kotlin.reflect.KClass

@HiltViewModel
class EventViewModel @Inject constructor(
    private val repository: FirebaseRepository
) : ViewModel() {

    // Current form values
    private val _formState = MutableStateFlow<EventFormState>(EventFormState.Diaper())
    val formState: StateFlow<EventFormState> = _formState.asStateFlow()

    // Loaded events for calendar
    private val _eventsByType = MutableStateFlow<Map<KClass<out Event>, List<Event>>>(emptyMap())
    val eventsByType: StateFlow<Map<KClass<out Event>, List<Event>>> = _eventsByType.asStateFlow()

    // --- Loading States ---

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _saveSuccess = MutableStateFlow(false)
    val saveSuccess: StateFlow<Boolean> = _saveSuccess.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // --- Date Range Filtering ---
    private val _startDate = MutableStateFlow<Date>(Date().apply {
        time = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000 // Default: 30 days ago
    })
    val startDate: StateFlow<Date> = _startDate.asStateFlow()

    private val _endDate = MutableStateFlow<Date>(Date()) // Default: today
    val endDate: StateFlow<Date> = _endDate.asStateFlow()

    // Update form
    fun updateForm(update: EventFormState.() -> EventFormState) {
        _formState.value = _formState.value.update()
    }

    // Entry-point to validate & save whichever event type is active
    fun validateAndSave(babyId: String) {
        if (babyId.isBlank()) {
            _errorMessage.value = "Baby ID is missing."
            return
        }
        _isSaving.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            val result = when (val state = _formState.value) {
                is EventFormState.Diaper -> saveDiaper(babyId, state)
                is EventFormState.Sleep -> saveSleep(babyId, state)
                is EventFormState.Feeding -> saveFeeding(babyId, state)
                is EventFormState.Growth -> saveGrowth(babyId, state)
            }

            result.fold(
                onSuccess = { _saveSuccess.value = true },
                onFailure = { _errorMessage.value = it.message }
            )
            _isSaving.value = false
        }
    }

    private suspend fun saveDiaper(babyId: String, s: EventFormState.Diaper): Result<Unit> {
        // Validation for dirty or mixed diapers
        if ((s.diaperType == DiaperType.DIRTY || s.diaperType == DiaperType.MIXED)
            && s.poopColor == null && s.poopConsistency == null
        ) {
            return Result.failure(
                IllegalArgumentException("For dirty diapers, specify color or consistency.")
            )
        }

        // Construct the event
        val event = DiaperEvent(
            babyId = babyId,
            diaperType = s.diaperType,
            poopColor = s.poopColor,
            poopConsistency = s.poopConsistency,
            notes = s.notes.takeIf(String::isNotBlank)
        )

        // Persist via repository
        return repository.addEvent(event)
    }

    private suspend fun saveSleep(babyId: String, s: EventFormState.Sleep): Result<Unit> {
        if (!s.isSleeping && s.beginTime == null && s.endTime == null) {
            return Result.failure(
                IllegalArgumentException(
                    "Please start and stop sleep before saving."
                )
            )
        }
        val event = SleepEvent(
            babyId = babyId,
            isSleeping = s.isSleeping,
            beginTime = s.beginTime,
            endTime = s.endTime,
            durationMinutes = s.durationMinutes,
            notes = s.notes.takeIf(String::isNotBlank)
        )
        return repository.addEvent(event)
    }

    private suspend fun saveFeeding(babyId: String, s: EventFormState.Feeding): Result<Unit> {
        val amount = s.amountMl.toDoubleOrNull()
        val duration = s.durationMin.toIntOrNull()
        when (s.feedType) {
            FeedType.BREAST_MILK -> {
                if (duration == null && s.breastSide == null && amount == null) {
                    return Result.failure(
                        IllegalArgumentException(
                            "Provide duration/side or amount for breast milk."
                        )
                    )
                }
            }

            FeedType.FORMULA -> if (amount == null) {
                return Result.failure(IllegalArgumentException("Amount is required for formula."))
            }

            FeedType.SOLID -> if (s.notes.isBlank() && amount == null) {
                return Result.failure(
                    IllegalArgumentException(
                        "Provide notes or amount for solids."
                    )
                )
            }
        }
        val event = FeedingEvent(
            babyId = babyId,
            feedType = s.feedType,
            amountMl = amount,
            durationMinutes = duration,
            breastSide = s.breastSide,
            notes = s.notes.takeIf(String::isNotBlank)
        )
        return repository.addEvent(event)
    }

    private suspend fun saveGrowth(babyId: String, s: EventFormState.Growth): Result<Unit> {
        val weight = s.weightKg.toDoubleOrNull()
        val height = s.heightCm.toDoubleOrNull()
        val head = s.headCircumferenceCm.toDoubleOrNull()
        if (weight == null && height == null && head == null) {
            return Result.failure(
                IllegalArgumentException(
                    "At least one measurement required."
                )
            )
        }
        val event = GrowthEvent(
            babyId = babyId,
            weightKg = weight,
            heightCm = height,
            headCircumferenceCm = head,
            notes = s.notes.takeIf(String::isNotBlank)
        )
        return repository.addEvent(event)
    }

    fun loadEventsInRange(babyId: String) {
        if (babyId.isBlank()) {
            Log.w("EventViewModel", "loadEventsInRange called with blank babyId")
            clearAllEvents()
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                // 1. Fetch events in date window
                val allEvents = loadEventsByDateRange(babyId, _startDate.value, _endDate.value)

                // 2. Group by event class
                val grouped: Map<KClass<out Event>, List<Event>> =
                    allEvents.groupBy { it::class }

                // 3. Publish grouped map
                _eventsByType.value = grouped

                Log.d("EventViewModel", "Loaded ${allEvents.size} events for baby $babyId")

            } catch (e: Exception) {
                Log.e("EventViewModel", "Error loading events: ${e.message}", e)
                _errorMessage.value = "Failed to load events: ${e.localizedMessage}"
                clearAllEvents()
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun loadEventsByDateRange(
        babyId: String,
        startDate: Date,
        endDate: Date
    ): List<Event> {
        // Normalize date boundaries (like GrowthViewModel)
        val calStart = Calendar.getInstance().apply {
            time = startDate
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val calEnd = Calendar.getInstance().apply {
            time = endDate
            set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
        }

        val normalizedStart = calStart.time
        val normalizedEnd = calEnd.time

        // Calculate duration in days (like GrowthViewModel)
        val days = ((normalizedEnd.time - normalizedStart.time) / (1000 * 60 * 60 * 24)).toInt()
            .coerceAtLeast(0)

        return when {
            days == 0 -> {
                // Same day → get all events for that day
                repository.getAllEventsForBaby(babyId, limit = -1) // No limit
                    .getOrNull()?.filter { event ->
                        event.timestamp.time >= normalizedStart.time && event.timestamp.time <= normalizedEnd.time
                    } ?: emptyList()
            }

            days <= 30 -> {
                // Short period (≤1 month) → get all events
                repository.getAllEventsForBaby(babyId, limit = -1)
                    .getOrNull()?.filter { event ->
                        event.timestamp.time >= normalizedStart.time && event.timestamp.time <= normalizedEnd.time
                    } ?: emptyList()
            }

            else -> {
                // Long period (>1 month) → sample events or get recent ones
                repository.getAllEventsForBaby(babyId, limit = 100) // Limited sample
                    .getOrNull()?.filter { event ->
                        event.timestamp.time >= normalizedStart.time && event.timestamp.time <= normalizedEnd.time
                    } ?: emptyList()
            }
        }
    }

    /**
     * Returns the list of events for the given Event subclass,
     * e.g. FeedingEvent::class, DiaperEvent::class, etc.
     */
    fun <T : Event> getEventsOfType(type: KClass<T>): List<T> {
        val list = _eventsByType.value[type] ?: emptyList()
        @Suppress("UNCHECKED_CAST")
        return list as List<T>
    }


    /**
     * Returns a map of displayName → event count for all loaded types.
     * Useful to drive calendar badges or summary views.
     */
    fun getEventCounts(): Map<String, Int> {
        return _eventsByType.value.mapKeys { (type, _) ->
            // Simple human‐readable key; you could customize per type.
            type.simpleName ?: "Unknown"
        }.mapValues { (_, list) ->
            list.size
        }
    }

    private fun clearAllEvents() {
        _eventsByType.value = emptyMap()
    }
}
