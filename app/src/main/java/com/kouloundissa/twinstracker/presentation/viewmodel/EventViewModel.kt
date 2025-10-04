package com.kouloundissa.twinstracker.presentation.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kouloundissa.twinstracker.data.FirebaseRepository
import com.kouloundissa.twinstracker.data.Event
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject
import android.util.Log
import com.kouloundissa.twinstracker.data.DiaperEvent
import com.kouloundissa.twinstracker.data.EventFormState
import com.kouloundissa.twinstracker.data.FeedingEvent
import com.kouloundissa.twinstracker.data.GrowthEvent
import com.kouloundissa.twinstracker.data.SleepEvent
import com.kouloundissa.twinstracker.data.setPhotoUrl
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
import androidx.core.net.toUri
import com.google.firebase.storage.StorageException
import com.kouloundissa.twinstracker.data.DrugsEvent
import com.kouloundissa.twinstracker.data.EventFormState.*
import com.kouloundissa.twinstracker.data.PumpingEvent

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


    // Track if we can load more (haven't reached the beginning of baby's data)
    private val _hasMoreHistory = MutableStateFlow(true)
    val hasMoreHistory: StateFlow<Boolean> = _hasMoreHistory.asStateFlow()
    private fun groupEventsByDay(allEvents: List<Event>) {
        val map = allEvents.groupBy { event ->
            // conversion java.util.Date -> java.time.LocalDate
            event.timestamp.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
        }
        _eventsByDay.value = map
    }
    //deleting state

    private val _isDeleting = MutableStateFlow(false)
    val isDeleting: StateFlow<Boolean> = _isDeleting.asStateFlow()

    private val _deleteSuccess = MutableStateFlow(false)
    val deleteSuccess: StateFlow<Boolean> = _deleteSuccess.asStateFlow()

    private val _deleteError = MutableStateFlow<String?>(null)
    val deleteError: StateFlow<String?> = _deleteError.asStateFlow()


    // --- Loading States ---
    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _saveSuccess = MutableStateFlow(false)
    val saveSuccess: StateFlow<Boolean> = _saveSuccess.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // --- Date Range Filtering ---
    private var currentDaysWindow = 30L
    private val maxDaysWindow = 365L // Maximum 1 year of history
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
                is EventFormState.Drugs -> state.copy(eventTimestamp = date)
            }
        }
    }

    fun loadMoreHistoricalEvents() {
        if (_isLoadingMore.value || !_hasMoreHistory.value) return

        _isLoadingMore.value = true

        // Extend the window by additional days (e.g., 30 more days)
        val additionalDays = 30L
        val newDaysWindow = (currentDaysWindow + additionalDays).coerceAtMost(maxDaysWindow)

        // Check if we've reached the maximum
        if (newDaysWindow >= maxDaysWindow) {
            _hasMoreHistory.value = false
        }

        // Update the date range
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val newStartDate = today.minusDays(newDaysWindow - 1).atStartOfDay(zone).toInstant()
        val endDate = today.atTime(23, 59, 59).atZone(zone).toInstant()

        currentDaysWindow = newDaysWindow
        _startDate.value = Date.from(newStartDate)
        _endDate.value = Date.from(endDate)

        // The streaming will automatically pick up the new date range
        // and update the events accordingly
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
            .onStart {
                if (!_isLoadingMore.value) {
                    _isLoading.value = true
                }
            }
            .catch { e ->
                _errorMessage.value = "Stream error: ${e.localizedMessage}"
                _isLoading.value = false
                _isLoadingMore.value = false
            }
            .onEach { filtered ->
                _eventsByType.value = filtered.groupBy { it::class }
                groupEventsByDay(filtered)
                _isLoading.value = false
                _isLoadingMore.value = false
            }
            .launchIn(viewModelScope)
    }

    /** Stops any active real-time listener. */
    fun stopStreaming() {
        streamJob?.cancel()
        streamJob = null
        _isLoading.value = false
        _isLoadingMore.value = false
    }

    // Update form
    fun updateForm(update: EventFormState.() -> EventFormState) {
        _formState.update { it.update() }
    }

    fun loadEventIntoForm(event: Event) {
        val id = event.id
        val timestamp = event.timestamp

        val state: EventFormState = when (event) {
            is DiaperEvent -> Diaper(
                eventId = id,
                eventTimestamp = timestamp,
                diaperType = event.diaperType,
                poopColor = event.poopColor,
                poopConsistency = event.poopConsistency,
                notes = event.notes.orEmpty(),
                photoUrl = event.photoUrl
            )

            is SleepEvent -> Sleep(
                eventId = id,
                eventTimestamp = timestamp,
                beginTime = event.beginTime,
                endTime = event.endTime,
                durationMinutes = event.durationMinutes,
                notes = event.notes.orEmpty(),
                photoUrl = event.photoUrl
            )

            is FeedingEvent -> Feeding(
                eventId = id,
                eventTimestamp = timestamp,
                feedType = event.feedType,
                amountMl = event.amountMl?.toString().orEmpty(),
                durationMin = event.durationMinutes?.toString().orEmpty(),
                breastSide = event.breastSide,
                notes = event.notes.orEmpty(),
                photoUrl = event.photoUrl
            )

            is GrowthEvent -> Growth(
                eventId = id,
                eventTimestamp = timestamp,
                weightKg = event.weightKg?.toString().orEmpty(),
                heightCm = event.heightCm?.toString().orEmpty(),
                headCircumferenceCm = event.headCircumferenceCm?.toString().orEmpty(),
                notes = event.notes.orEmpty(),
                photoUrl = event.photoUrl
            )

            is DrugsEvent -> Drugs(
                eventId = id,
                eventTimestamp = timestamp,
                drugType = event.drugType,
                dosage = event.dosage?.toString().orEmpty(),
                unit = event.unit,
                notes = event.notes.orEmpty(),
                photoUrl = event.photoUrl
            )

            is PumpingEvent -> Pumping(
                eventId = id,
                eventTimestamp = timestamp,
                amountMl = event.amountMl?.toString().orEmpty(),
                durationMin = event.durationMinutes?.toString().orEmpty(),
                notes = event.notes.orEmpty(),
                photoUrl = event.photoUrl
            )
        }

        _formState.value = state
    }

    // Entry-point to validate & save whichever event type is active
    fun SaveEvent(babyId: String) {
        if (babyId.isBlank()) {
            _errorMessage.value = "Baby ID is missing."
            return
        }
        _isSaving.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            val state = _formState.value
            state.validateAndToEvent(babyId).fold(
                onFailure = { err ->
                    _errorMessage.value = err.message
                    _isSaving.value = false
                },
                onSuccess = { event ->
                    if (state.eventId == null) {
                        // Creation branch
                        createEventWithPhoto(event, state)
                    } else {
                        // Update branch
                        updateEventWithPhoto(event, state)
                    }
                }
            )
        }
    }

    private suspend fun createEventWithPhoto(event: Event, state: EventFormState) {
        try {
            // 1. Handle photo upload (before event creation)
            val photoUrl =
                state.newPhotoUrl?.let { uploadEventPhoto(event.id, state.newPhotoUrl!!) }

            // 2. Create the event with photoUrl (if available)
            val eventWithPhoto = event.setPhotoUrl(photoUrl)
            repository.addEvent(eventWithPhoto).fold(
                onSuccess = { _saveSuccess.value = true },
                onFailure = { _errorMessage.value = it.message }
            )
        } catch (e: Exception) {
            _errorMessage.value = e.localizedMessage
        } finally {
            _isSaving.value = false
        }
    }

    private suspend fun updateEventWithPhoto(event: Event, state: EventFormState) {
        try {
            // 1. Handle photo upload or deletion (before event update)
            val photoUrl = when {
                state.newPhotoUrl != null -> uploadEventPhoto(event.id, state.newPhotoUrl!!)
                state.photoRemoved -> {
                    deleteEventPhoto(event.id); null
                }

                else -> event.photoUrl
            }

            // 2. Update the event with new photoUrl
            val eventWithPhoto = event.setPhotoUrl(photoUrl)
            repository.updateEvent(event.id, eventWithPhoto).fold(
                onSuccess = { _saveSuccess.value = true },
                onFailure = { _errorMessage.value = it.message }
            )
        } catch (e: Exception) {
            _errorMessage.value = e.localizedMessage
        } finally {
            _isSaving.value = false
        }
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

    private suspend fun uploadEventPhoto(eventId: String, photoUri: Uri): String? {
        return try {
            repository.addPhotoToEntity("events", eventId, photoUri)
        } catch (e: Exception) {
            Log.e("EventViewModel", "Event Photo upload failed: ${e.message}", e)
            _errorMessage.value = "Event Photo upload failed: ${e.localizedMessage}"
            null
        }
    }

    fun deleteEventPhoto(eventId: String) {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                // 1. Remove photo from storage & clear Firestore field
                try {
                    repository.deletePhotoFromEntity("events", eventId)
                } catch (e: Exception) {
                    // If it's a Firebase Storage 404, ignore it
                    if (e is StorageException && e.errorCode == StorageException.ERROR_OBJECT_NOT_FOUND) {
                        // no-op
                    } else {
                        throw e
                    }
                }

                // 2. Fetch current event from repository to get latest data
                val currentEvent = repository.getEventById(eventId).getOrThrow()

                // 3. Create updated Event without photo
                val updatedEvent = currentEvent!!.setPhotoUrl(photoUrl = null)


                // 4. Persist the change
                repository.updateEvent(eventId, updatedEvent).getOrThrow()

                // 5. Update local form state if editing this event
                if (_formState.value.eventId == eventId) {
                    updateForm {
                        when (this) {
                            is EventFormState.Diaper -> copy(photoRemoved = true, photoUrl = null)
                            is EventFormState.Feeding -> copy(photoRemoved = true, photoUrl = null)
                            is EventFormState.Sleep -> copy(photoRemoved = true, photoUrl = null)
                            is EventFormState.Growth -> copy(photoRemoved = true, photoUrl = null)
                            is EventFormState.Pumping -> copy(photoRemoved = true, photoUrl = null)
                            is EventFormState.Drugs -> copy(photoRemoved = true, photoUrl = null)
                        }
                    }
                }

            } catch (e: Exception) {
                _errorMessage.value = "Échec de la suppression de la photo : ${e.localizedMessage}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Deletes the event with the given ID.
     * Updates isDeleting, deleteSuccess, and deleteError accordingly.
     * After successful deletion, refreshes the current event stream.
     */
    fun deleteEvent(eventId: String, babyId: String) {
        _isDeleting.value = true
        _deleteError.value = null

        viewModelScope.launch {
            try {
                // 1. Delete photo from Storage & clear Firestore field
                try {
                    repository.deletePhotoFromEntity("events", eventId)
                } catch (e: Exception) {
                    if (e is StorageException && e.errorCode == StorageException.ERROR_OBJECT_NOT_FOUND) {
                        // Photo didn’t exist—ignore
                    } else {
                        throw e
                    }
                }

                // 2. Fetch current event to clear its photoUrl
                val currentEvent = repository.getEventById(eventId).getOrThrow()
                    ?: throw IllegalStateException("Event not found")

                val eventWithoutPhoto = currentEvent.setPhotoUrl(photoUrl = null)

                // 3. Persist Firestore update (remove photoUrl)
                repository.updateEvent(eventId, eventWithoutPhoto).getOrThrow()

                // 4. Now delete the event document itself
                repository.deleteEvent(eventId).fold(
                    onSuccess = {
                        _deleteSuccess.value = true
                        streamEventsInRangeForBaby(babyId)
                    },
                    onFailure = { throwable ->
                        throw throwable
                    }
                )
            } catch (throwable: Throwable) {
                _deleteError.value = throwable.localizedMessage
            } finally {
                _isDeleting.value = false
            }
        }
    }


    // Call this to clear any previous delete outcome
    fun resetDeleteState() {
        _deleteSuccess.value = false
        _deleteError.value = null
    }
    fun resetDateRangeAndHistory() {
        currentDaysWindow = 30L
        _hasMoreHistory.value = true
        _isLoadingMore.value = false
        setDateRangeForLastDays(30L)
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
