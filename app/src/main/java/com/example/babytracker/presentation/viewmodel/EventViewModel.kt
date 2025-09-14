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

    // --- Event Data State ---
    private val _events = MutableStateFlow<Map<KClass<out Event>, List<Event>>>(emptyMap())
    val events: StateFlow<Map<KClass<out Event>, List<Event>>> = _events.asStateFlow()

    private val _eventsByType = MutableStateFlow<Map<KClass<out Event>, List<Event>>>(emptyMap())
    val eventsByType: StateFlow<Map<KClass<out Event>, List<Event>>> = _eventsByType.asStateFlow()

    // --- Date Range Filtering (like GrowthViewModel) ---
    private val _startDate = MutableStateFlow<Date>(Date().apply {
        time = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000 // Default: 7 days ago
    })
    val startDate: StateFlow<Date> = _startDate.asStateFlow()

    private val _endDate = MutableStateFlow<Date>(Date()) // Default: today
    val endDate: StateFlow<Date> = _endDate.asStateFlow()

    // --- Loading States ---

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    // --- Feedback States ---

    private val _saveSuccess = MutableStateFlow(false)
    val saveSuccess: StateFlow<Boolean> = _saveSuccess.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // --- Event-specific data ---
    private val _feedingEvents = MutableStateFlow<List<FeedingEvent>>(emptyList())
    val feedingEvents: StateFlow<List<FeedingEvent>> = _feedingEvents.asStateFlow()

    private val _diaperEvents = MutableStateFlow<List<DiaperEvent>>(emptyList())
    val diaperEvents: StateFlow<List<DiaperEvent>> = _diaperEvents.asStateFlow()

    private val _growthEvents = MutableStateFlow<List<GrowthEvent>>(emptyList())
    val growthEvents: StateFlow<List<GrowthEvent>> = _growthEvents.asStateFlow()

    private val _sleepEvents = MutableStateFlow<List<SleepEvent>>(emptyList())
    val sleepEvents: StateFlow<List<SleepEvent>> = _sleepEvents.asStateFlow()

    // --- Date Range Methods  ---
    fun setStartDate(date: Date, babyId: String) {
        _startDate.value = date
        loadEventsInRange(babyId)
    }

    fun setEndDate(date: Date, babyId: String) {
        _endDate.value = date
        loadEventsInRange(babyId)
    }

    // --- Reset methods (like DiaperViewModel) ---
    fun resetSaveSuccess() {
        _saveSuccess.value = false
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    // Update any field via a transform function
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
                is EventFormState.Diaper  -> saveDiaper(babyId, state)
                is EventFormState.Sleep   -> saveSleep(babyId, state)
                is EventFormState.Feeding -> saveFeeding(babyId, state)
                is EventFormState.Growth  -> saveGrowth(babyId, state)
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
            && s.poopColor == null && s.poopConsistency == null) {
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
            return Result.failure(IllegalArgumentException(
                "Please start and stop sleep before saving."
            ))
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

    private suspend fun saveFeeding(
        babyId: String,
        s: EventFormState.Feeding
    ): Result<Unit> {
        val amount = s.amountMl.toDoubleOrNull()
        val duration = s.durationMin.toIntOrNull()
        when (s.feedType) {
            FeedType.BREAST_MILK -> {
                if (duration == null && s.breastSide == null && amount == null) {
                    return Result.failure(IllegalArgumentException(
                        "Provide duration/side or amount for breast milk."
                    ))
                }
            }
            FeedType.FORMULA -> if (amount == null) {
                return Result.failure(IllegalArgumentException("Amount is required for formula."))
            }
            FeedType.SOLID -> if (s.notes.isBlank() && amount == null) {
                return Result.failure(IllegalArgumentException(
                    "Provide notes or amount for solids."
                ))
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

    private suspend fun saveGrowth(
        babyId: String,
        s: EventFormState.Growth
    ): Result<Unit> {
        val weight = s.weightKg.toDoubleOrNull()
        val height = s.heightCm.toDoubleOrNull()
        val head   = s.headCircumferenceCm.toDoubleOrNull()
        if (weight == null && height == null && head == null) {
            return Result.failure(IllegalArgumentException(
                "At least one measurement required."
            ))
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
                // Load events within date range
                val allEvents = loadEventsByDateRange(babyId, _startDate.value, _endDate.value)

                // Group events by type (like GrowthViewModel organizing data)
                val eventsByClass = allEvents.groupBy { it::class }
                _eventsByType.value = eventsByClass
                _events.value = eventsByClass

                // Separate by specific types (like GrowthViewModel storing growth events)
                _feedingEvents.value = eventsByClass[FeedingEvent::class]?.filterIsInstance<FeedingEvent>()?.sortedBy { it.timestamp } ?: emptyList()
                _diaperEvents.value = eventsByClass[DiaperEvent::class]?.filterIsInstance<DiaperEvent>()?.sortedBy { it.timestamp } ?: emptyList()
                _growthEvents.value = eventsByClass[GrowthEvent::class]?.filterIsInstance<GrowthEvent>()?.sortedBy { it.timestamp } ?: emptyList()
                _sleepEvents.value = eventsByClass[SleepEvent::class]?.filterIsInstance<SleepEvent>()?.sortedBy { it.timestamp } ?: emptyList()

                Log.d("EventViewModel", "Loaded ${allEvents.size} events in date range for baby $babyId")

            } catch (exception: Exception) {
                Log.e("EventViewModel", "Error loading events in range for baby $babyId: ${exception.message}", exception)
                _errorMessage.value = "Failed to load events: ${exception.localizedMessage}"
                clearAllEvents()
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun loadEventsByDateRange(babyId: String, startDate: Date, endDate: Date): List<Event> {
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
        val days = ((normalizedEnd.time - normalizedStart.time) / (1000 * 60 * 60 * 24)).toInt().coerceAtLeast(0)

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

    fun getEventCountByType(): Map<String, Int> {
        val events = _eventsByType.value
        return mapOf(
            "Feeding" to (events[FeedingEvent::class]?.size ?: 0),
            "Diaper" to (events[DiaperEvent::class]?.size ?: 0),
            "Growth" to (events[GrowthEvent::class]?.size ?: 0),
            "Sleep" to (events[SleepEvent::class]?.size ?: 0)
        )
    }

    fun getEventsForDateRange(eventType: KClass<out Event>): List<Event> {
        return _eventsByType.value[eventType] ?: emptyList()
    }

    fun getFormattedDateLabels(): List<String> {
        val formatter = SimpleDateFormat("dd/MM", Locale.getDefault())
        val allEvents = _eventsByType.value.values.flatten().sortedBy { it.timestamp }
        return allEvents.map { formatter.format(it.timestamp) }
    }

    private fun clearAllEvents() {
        _events.value = emptyMap()
        _eventsByType.value = emptyMap()
        _feedingEvents.value = emptyList()
        _diaperEvents.value = emptyList()
        _growthEvents.value = emptyList()
        _sleepEvents.value = emptyList()
    }


    fun addFeedingEvent(
        babyId: String,
        feedType: FeedType,
        amountMlText: String = "",    // String input from UI
        durationMinutesText: String = "", // String input from UI
        breastSide: BreastSide? = null,
        notes: String? = null,
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

        // Parse string inputs to numbers (like FeedingViewModel)
        val amountMl = amountMlText.toDoubleOrNull()
        val durationMinutes = durationMinutesText.toIntOrNull()
        val cleanNotes = notes?.takeIf { it.isNotBlank() }


        // Validation logic
        when (feedType) {
            FeedType.BREAST_MILK -> {
                if (durationMinutes == null && breastSide == null && amountMl == null) {
                    _errorMessage.value = "For breast milk, please provide duration/side or amount for pumped milk."
                    _isSaving.value = false
                    return
                }
            }
            FeedType.FORMULA -> {
                if (amountMl == null) {
                    _errorMessage.value = "Amount (ml) is required for formula."
                    _isSaving.value = false
                    return
                }
            }
            FeedType.SOLID -> {
                if (cleanNotes == null && amountMl == null) {
                    _errorMessage.value = "Please provide notes or amount for solid food."
                    _isSaving.value = false
                    return
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
                    loadEventsInRange(babyId)
                },
                onFailure = { exception ->
                    Log.e("EventViewModel", "Error adding feeding event: ${exception.message}", exception)
                    _errorMessage.value = "Failed to add feeding event: ${exception.localizedMessage}"
                }
            )
            _isSaving.value = false
        }
    }

    fun addGrowthEvent(
        babyId: String,
        weightKgText: String = "",
        heightCmText: String = "",
        headCircumferenceCmText: String = "",
        notes: String? = null,
        measurementDate: Date = Date()
    ) {
        if (babyId.isBlank()) {
            _errorMessage.value = "Baby ID is missing."
            return
        }

        _isSaving.value = true
        _errorMessage.value = null
        _saveSuccess.value = false

        val weightKg = weightKgText.toDoubleOrNull()
        val heightCm = heightCmText.toDoubleOrNull()
        val headCircumferenceCm = headCircumferenceCmText.toDoubleOrNull()
        val cleanNotes = notes?.takeIf { it.isNotBlank() }

        if (weightKg == null && heightCm == null && headCircumferenceCm == null) {
            _errorMessage.value = "Please provide at least one measurement (weight, height, or head circumference)."
            _isSaving.value = false
            return
        }

        viewModelScope.launch {
            // Advanced duplicate handling (like GrowthViewModel)
            val cal = Calendar.getInstance().apply { time = measurementDate }
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
            val dayStart = cal.time
            cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59)
            cal.set(Calendar.SECOND, 59); cal.set(Calendar.MILLISECOND, 999)
            val dayEnd = cal.time

            // Check for existing event on same date (like GrowthViewModel)
            val existingResult = repository.getGrowthEventsInRange(babyId, dayStart, dayEnd)
            val existingEvent = existingResult.getOrNull()?.firstOrNull()
            val eventId = existingEvent?.id ?: UUID.randomUUID().toString()

            val event = GrowthEvent(
                id = eventId,
                babyId = babyId,
                timestamp = measurementDate,
                notes = cleanNotes,
                weightKg = weightKg,
                heightCm = heightCm,
                headCircumferenceCm = headCircumferenceCm
            )

            val result = repository.addEvent(event)
            result.fold(
                onSuccess = {
                    Log.d("EventViewModel", "Growth event added successfully for baby $babyId")
                    _saveSuccess.value = true
                    loadEventsInRange(babyId)
                },
                onFailure = { exception ->
                    Log.e("EventViewModel", "Error adding growth event: ${exception.message}", exception)
                    _errorMessage.value = "Failed to add growth event: ${exception.localizedMessage}"
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
                    loadEventsInRange(babyId)
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
                    loadEventsInRange(babyId)
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
