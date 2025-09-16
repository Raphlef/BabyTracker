package com.example.babytracker.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.babytracker.data.FirebaseRepository
import com.example.babytracker.data.Event
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject
import android.util.Log
import com.example.babytracker.data.DiaperEvent
import com.example.babytracker.data.DiaperType
import com.example.babytracker.data.EventFormState
import com.example.babytracker.data.FeedType
import com.example.babytracker.data.FeedingEvent
import com.example.babytracker.data.GrowthEvent
import com.example.babytracker.data.SleepEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.ZoneId
import kotlin.reflect.KClass

@HiltViewModel
class EventViewModel @Inject constructor(
    private val repository: FirebaseRepository
) : ViewModel() {

    private var streamJob: Job? = null

    // Current form values
    private val _formState = MutableStateFlow<EventFormState>(EventFormState.Diaper())
    val formState: StateFlow<EventFormState> = _formState.asStateFlow()

    // Loaded events for calendar
    private val _eventsByType = MutableStateFlow<Map<KClass<out Event>, List<Event>>>(emptyMap())
    val eventsByType: StateFlow<Map<KClass<out Event>, List<Event>>> = _eventsByType.asStateFlow()

    private val _eventsByDay = MutableStateFlow<Map<java.time.LocalDate, List<Event>>>(emptyMap())
    val eventsByDay: StateFlow<Map<java.time.LocalDate, List<Event>>> = _eventsByDay

    private fun groupEventsByDay(allEvents: List<Event>) {
        val map = allEvents.groupBy { event ->
            // conversion java.util.Date -> java.time.LocalDate
            event.timestamp.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
        }
        _eventsByDay.value = map
    }
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

    fun setDateRangeForLastDays(days: Long) {
        // use existing _startDate/_endDate vars
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val startDate = today.minusDays(days - 1).atStartOfDay(zone).toInstant()
        val endDate = today.atTime(23, 59, 59).atZone(zone).toInstant()
        _startDate.value = Date.from(startDate)
        _endDate.value = Date.from(endDate)
    }

    fun setDateRangeForMonth(month: LocalDate) {
        val first = month.withDayOfMonth(1)
        val last = month.withDayOfMonth(month.lengthOfMonth())
        _startDate.value = Date.from(first.atStartOfDay(ZoneId.systemDefault()).toInstant())
        _endDate.value =
            Date.from(last.atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant())
    }

    fun updateEventTimestamp(date: Date) {
        _formState.update { state ->
            when (state) {
                is EventFormState.Diaper -> state.copy(eventTimestamp = date)
                is EventFormState.Sleep -> state.copy(eventTimestamp = date)
                is EventFormState.Feeding -> state.copy(eventTimestamp = date)
                is EventFormState.Growth -> state.copy(eventTimestamp = date)
                is EventFormState.Pumping -> state.copy(eventTimestamp = date)
            }
        }
    }

    fun streamEventsInRangeForBaby(babyId: String) {
        streamJob?.cancel()
        streamJob = repository.streamEventsForBaby(babyId)
            .map { allEvents ->
                // apply your existing date‐range filter
                allEvents.filter { e ->
                    val ts = e.timestamp
                    ts in _startDate.value.._endDate.value
                }
            }
            .onStart { _isLoading.value = true }
            .catch { e ->
                _errorMessage.value = "Stream error: ${e.localizedMessage}"
                _isLoading.value = false
            }
            .onEach { filtered ->
                _eventsByType.value = filtered.groupBy { it::class }
                groupEventsByDay(filtered)
                _isLoading.value = false
            }
            .launchIn(viewModelScope)
    }

    /** Stops any active real-time listener. */
    fun stopStreaming() {
        streamJob?.cancel()
        streamJob = null
    }

    // Update form
    fun updateForm(update: EventFormState.() -> EventFormState) {
        _formState.update { it.update() }
    }

    fun loadEventIntoForm(event: Event) {
        val id = event.id
        val timestamp = event.timestamp

        val state: EventFormState = when (event) {
            is DiaperEvent -> EventFormState.Diaper(
                eventId = id,
                eventTimestamp = timestamp,
                diaperType = event.diaperType,
                poopColor = event.poopColor,
                poopConsistency = event.poopConsistency,
                notes = event.notes.orEmpty()
            )

            is SleepEvent -> EventFormState.Sleep(
                eventId = id,
                eventTimestamp = timestamp,
                beginTime = event.beginTime,
                endTime = event.endTime,
                durationMinutes = event.durationMinutes,
                notes = event.notes.orEmpty()
            )

            is FeedingEvent -> EventFormState.Feeding(
                eventId = id,
                eventTimestamp = timestamp,
                feedType = event.feedType,
                amountMl = event.amountMl?.toString().orEmpty(),
                durationMin = event.durationMinutes?.toString().orEmpty(),
                breastSide = event.breastSide,
                notes = event.notes.orEmpty()
            )

            is GrowthEvent -> EventFormState.Growth(
                eventId = id,
                eventTimestamp = timestamp,
                weightKg = event.weightKg?.toString().orEmpty(),
                heightCm = event.heightCm?.toString().orEmpty(),
                headCircumferenceCm = event.headCircumferenceCm?.toString().orEmpty(),
                notes = event.notes.orEmpty()
            )

            else -> EventFormState.Diaper()
        }

        _formState.value = state
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
            val state = _formState.value
            val result: Result<Unit> = if (state.eventId != null) {
                // EDITING
                when (state) {
                    is EventFormState.Diaper -> repository.updateEvent(
                        eventId = state.eventId!!,
                        event = DiaperEvent(
                            id = state.eventId,
                            babyId = babyId,
                            diaperType = state.diaperType,
                            poopColor = state.poopColor,
                            poopConsistency = state.poopConsistency,
                            notes = state.notes.takeIf(String::isNotBlank)
                        )
                    )

                    is EventFormState.Sleep -> repository.updateEvent(
                        eventId = state.eventId!!,
                        event = SleepEvent(
                            id = state.eventId,
                            babyId = babyId,
                            isSleeping = state.isSleeping,
                            beginTime = state.beginTime,
                            endTime = state.endTime,
                            durationMinutes = state.durationMinutes,
                            notes = state.notes.takeIf(String::isNotBlank)
                        )
                    )

                    is EventFormState.Feeding -> repository.updateEvent(
                        eventId = state.eventId!!,
                        event = FeedingEvent(
                            id = state.eventId,
                            babyId = babyId,
                            feedType = state.feedType,
                            amountMl = state.amountMl.toDoubleOrNull(),
                            durationMinutes = state.durationMin.toIntOrNull(),
                            breastSide = state.breastSide,
                            notes = state.notes.takeIf(String::isNotBlank)
                        )
                    )

                    is EventFormState.Growth -> repository.updateEvent(
                        eventId = state.eventId!!,
                        event = GrowthEvent(
                            id = state.eventId,
                            babyId = babyId,
                            weightKg = state.weightKg.toDoubleOrNull(),
                            heightCm = state.heightCm.toDoubleOrNull(),
                            headCircumferenceCm = state.headCircumferenceCm.toDoubleOrNull(),
                            notes = state.notes.takeIf(String::isNotBlank)
                        )
                    )

                    is EventFormState.Pumping -> {
                        // Fallback to create for now or implement updatePump
                        Result.failure(Exception("Updating pumping events not implemented"))
                    }
                }
            } else {
                // CREATING
                when (state) {
                    is EventFormState.Diaper -> saveDiaper(babyId, state)
                    is EventFormState.Sleep -> saveSleep(babyId, state)
                    is EventFormState.Feeding -> saveFeeding(babyId, state)
                    is EventFormState.Growth -> saveGrowth(babyId, state)
                    is EventFormState.Pumping -> Result.failure(Exception("Pumping event save not yet implemented"))
                }
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
                groupEventsByDay(allEvents)

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
        // Normalize day boundaries
        val start = Calendar.getInstance().apply {
            time = startDate
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.time
        val end = Calendar.getInstance().apply {
            time = endDate
            set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
        }.time

        val days = ((end.time - start.time) / (1000 * 60 * 60 * 24)).toInt().coerceAtLeast(0)

        // Appel du repository sans écraser l’erreur
        val result = if (days <= 30) {
            repository.getAllEventsForBaby(babyId)
        } else {
            repository.getAllEventsForBaby(babyId, limit = 100)
        }

        return result.fold(
            onSuccess = { events ->
                events.filter { it.timestamp in start..end }
            },
            onFailure = { exception ->
                _errorMessage.value = "Erreur chargement : ${exception.message.orEmpty()}"
                throw exception
            }
        )
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
        _eventsByDay.value = emptyMap()
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    fun resetFormState() {
        _formState.value = EventFormState.Diaper()
    }

    fun resetSaveSuccess() {
        _saveSuccess.value = false
    }
}
