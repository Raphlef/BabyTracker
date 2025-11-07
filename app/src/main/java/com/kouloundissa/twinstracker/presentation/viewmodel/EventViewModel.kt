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
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.ZoneId
import kotlin.reflect.KClass
import com.google.firebase.storage.StorageException
import com.kouloundissa.twinstracker.Service.NotificationService
import com.kouloundissa.twinstracker.data.Baby
import com.kouloundissa.twinstracker.data.DrugsEvent
import com.kouloundissa.twinstracker.data.EventFormState.*
import com.kouloundissa.twinstracker.data.PumpingEvent
import com.kouloundissa.twinstracker.data.User
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.time.temporal.ChronoUnit

@HiltViewModel
class EventViewModel @Inject constructor(
    private val repository: FirebaseRepository,
    private val notificationService: NotificationService
) : ViewModel() {

    // Current form values
    private val _formState = MutableStateFlow<EventFormState>(EventFormState.Diaper())
    val formState: StateFlow<EventFormState> = _formState.asStateFlow()

    // Loaded events for calendar
    private val _eventsByType = MutableStateFlow<Map<KClass<out Event>, List<Event>>>(emptyMap())
    val eventsByType: StateFlow<Map<KClass<out Event>, List<Event>>> = _eventsByType.asStateFlow()

    private val _eventsByDay = MutableStateFlow<Map<java.time.LocalDate, List<Event>>>(emptyMap())
    val eventsByDay: StateFlow<Map<java.time.LocalDate, List<Event>>> = _eventsByDay

    private val _eventCountsByDay = MutableStateFlow<Map<String, FirebaseRepository.EventDayCount>>(emptyMap())
    val eventCountsByDay: StateFlow<Map<String, FirebaseRepository.EventDayCount>> = _eventCountsByDay.asStateFlow()

    private val _events = MutableStateFlow<List<Event>>(emptyList())
    val events: StateFlow<List<Event>> = _events

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

    private val _notificationEvent = MutableStateFlow<Event?>(null)
    val notificationEvent: StateFlow<Event?> = _notificationEvent.asStateFlow()
    private val _currentUserId = MutableStateFlow<String?>(null)
    private val currentUserId: StateFlow<String?> = _currentUserId.asStateFlow()

    // Cache for user profiles to avoid repeated fetches
    private val userProfileCache = mutableMapOf<String, User>()

    init {
        // Get current user ID on initialization
        viewModelScope.launch {
            _currentUserId.value = repository.getCurrentUserId()
        }
    }

    // --- Date Range Filtering ---
    data class DateRangeParams(
        val startDate: Date,
        val endDate: Date
    )

    sealed class DateRangeStrategy {
        data class LastDays(val days: Long) : DateRangeStrategy()
        data class Custom(val dateRange: DateRangeParams) : DateRangeStrategy()
    }

    fun calculateRange(
        strategy: DateRangeStrategy,
        zone: ZoneId = ZoneId.systemDefault()
    ): DateRangeParams {
        val today = LocalDate.now()
        return when (strategy) {
            is DateRangeStrategy.LastDays -> {
                val startDate = today.minusDays(strategy.days - 1)
                    .atStartOfDay(zone).toInstant()
                val endDate = today.atTime(23, 59, 59).atZone(zone).toInstant()
                val result = DateRangeParams(
                    Date.from(startDate),
                    Date.from(endDate)
                )
                Log.d("DateRange", "LastDays result: ${result.startDate} → ${result.endDate} (${strategy.days} days)")
                result
            }

            is DateRangeStrategy.Custom -> {
                val daysBetween = ChronoUnit.DAYS.between(
                    strategy.dateRange.startDate.toInstant(),
                    strategy.dateRange.endDate.toInstant()
                ) + 1 // +1 to include both start and end dates
                Log.d(
                    "DateRange",
                    "Custom range: ${strategy.dateRange.startDate} → ${strategy.dateRange.endDate} ($daysBetween days)"
                )
                strategy.dateRange
            }
        }
    }

    data class EventStreamRequest(
        val babyId: String,
        val dateRange: DateRangeParams
    )

    /**
     * Convenience method for last N days
     */
    fun refreshWithLastDays(babyId: String, days: Long = 1L) {
        resetDateRangeAndHistory()
        startStreaming(babyId, DateRangeStrategy.LastDays(days))
    }
    /**
     * Convenience method for custom range
     */
    fun refreshWithCustomRange(babyId: String, startDate: Date, endDate: Date) {
        startStreaming(babyId, DateRangeStrategy.Custom(DateRangeParams(startDate, endDate)))
    }

    fun refreshCountWithLastDays(babyId: String, days: Long = 1L) {
        startCountStreaming(babyId, DateRangeStrategy.LastDays(days))
    }

    fun refreshCountWithCustomRange(babyId: String, startDate: Date, endDate: Date) {
        startCountStreaming(babyId, DateRangeStrategy.Custom(DateRangeParams(startDate, endDate)))
    }

    private val _streamRequest = MutableStateFlow<EventStreamRequest?>(null)
    private val _countStreamRequest = MutableStateFlow<EventStreamRequest?>(null)
    private var streamJob: Job? = null
    private var countsStreamJob: Job? = null
    private var currentDaysWindow = 1L
    private val maxDaysWindow = 30L // Maximum 1 month of history

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

    // Called when notification is tapped
    fun onNotificationClicked(eventId: String) {
        viewModelScope.launch {
            // Try to find in current events cache
            val found = _events.value.firstOrNull { it.id == eventId }
                ?: repository.getEventById(eventId) // implement this if needed

            _notificationEvent.value = found as Event
        }
    }

    fun clearEditingEvent() {
        _notificationEvent.value = null
    }

    /**
     * Safe date range expansion for loading more history
     * Calculates new range without race conditions
     */
    fun loadMoreHistoricalEvents() {
        if (_isLoadingMore.value || !_hasMoreHistory.value) return

        _isLoadingMore.value = true

        val currentRequest = _streamRequest.value ?: run {
            _isLoadingMore.value = false
            return
        }

        val additionalDays = 1L
        val newDaysWindow = (currentDaysWindow + additionalDays).coerceAtMost(maxDaysWindow)

        if (newDaysWindow >= maxDaysWindow) {
            _hasMoreHistory.value = false
        }

        currentDaysWindow = newDaysWindow

        // Recalculate range based on new window
        val newStrategy = DateRangeStrategy.LastDays(newDaysWindow)
        Log.d("CalculateRange", "from load more")
        val newDateRange = calculateRange(newStrategy)

        // Update stream request atomically
        _streamRequest.value = EventStreamRequest(currentRequest.babyId, newDateRange)
    }

    /**
     * Centralized stream setup - only called once in init
     * Responds to changes in _streamRequest only
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun setupStreamListener() {
        // Only set up the listener once
        if (streamJob != null) return

        streamJob?.cancel()
        streamJob = _streamRequest
            .onEach { request ->
                request?.let {
                    val daysBetween = ChronoUnit.DAYS.between(
                        it.dateRange.startDate.toInstant(),
                        it.dateRange.endDate.toInstant()
                    ) + 1 // +1 to include both start and end dates
                    Log.d(
                        "EventStream",
                        "BabyId: ${it.babyId}, DateRange: ${it.dateRange.startDate} to ${it.dateRange.endDate} ($daysBetween days)"
                    )
                } ?: Log.d("EventStream", "StreamRequest is null")
            }
            .filterNotNull() // Only process valid requests
            .distinctUntilChanged() // Prevent duplicate requests
            .onEach { request ->
                Log.d("EventStream", "Processing valid request for babyId: ${request.babyId}")
            }
            .flatMapLatest { request ->
                repository.streamEventsForBaby(
                    request.babyId,
                    request.dateRange.startDate,
                    request.dateRange.endDate
                )
            }
            .onStart {
                Log.d("EventStream", "Stream started")
                if (!_isLoadingMore.value) {
                    _isLoading.value = true
                }
            }
            .catch { e ->
                Log.e("EventStream", "Stream error occurred", e)
                _errorMessage.value = "Stream error: ${e.localizedMessage}"
                _isLoading.value = false
                _isLoadingMore.value = false
            }
            .onEach { filtered ->
                Log.d("EventStream", "Received ${filtered.size} events")
                checkForNewEvents(filtered)
                _eventsByType.value = filtered.groupBy { it::class }
                groupEventsByDay(filtered)
                _events.value = filtered
                _isLoading.value = false
                _isLoadingMore.value = false
            }
            .launchIn(viewModelScope)
    }
    /**
     * Configure le stream pour les compteurs d'événements par jour
     * Permet d'afficher les indicateurs de jours avec événements dans le calendrier
     * sans charger tous les détails des événements
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun setupEventCountsStreamListener() {
        // Only set up the listener once
        if (countsStreamJob != null) return

        countsStreamJob?.cancel()
        countsStreamJob = _countStreamRequest // Utilise la nouvelle request
            .onEach { request ->
                request?.let {
                    val daysBetween = ChronoUnit.DAYS.between(
                        it.dateRange.startDate.toInstant(),
                        it.dateRange.endDate.toInstant()
                    ) + 1
                    Log.d(
                        "EventCountStream",
                        "BabyId: ${it.babyId}, DateRange: ${it.dateRange.startDate} to ${it.dateRange.endDate} ($daysBetween days)"
                    )
                } ?: Log.d("EventCountStream", "CountStreamRequest is null")
            }
            .filterNotNull()
            .distinctUntilChanged()
            .onEach { request ->
                Log.d("EventCountStream", "Processing count request for babyId: ${request.babyId}")
            }
            .flatMapLatest { request ->
                repository.streamEventCountsByDayTyped(
                    request.babyId,
                    request.dateRange.startDate,
                    request.dateRange.endDate
                )
            }
            .onStart {
                Log.d("EventCountStream", "Count stream started")
            }
            .catch { e ->
                Log.e("EventCountStream", "Count stream error occurred", e)
                // Ne pas affecter l'UI principale
            }
            .onEach { counts ->
                Log.d("EventCountStream", "Received counts for ${counts.size} days")
                _eventCountsByDay.value = counts
            }
            .launchIn(viewModelScope)
    }
    /**
     * Démarre le streaming des comptages uniquement (léger, pour calendrier)
     */
    fun startCountStreaming(
        babyId: String,
        strategy: DateRangeStrategy
    ) {
        if (babyId.isEmpty()) return

        setupEventCountsStreamListener()

        Log.d("CalculateRange", "from startCountStreaming")
        val dateRange = calculateRange(strategy)
        val request = EventStreamRequest(babyId, dateRange)

        if (_countStreamRequest.value != request) {
            Log.i("EventViewModel", "✓ Count StreamRequest UPDATED")
            _countStreamRequest.value = request
        } else {
            Log.i("EventViewModel", "✗ Count StreamRequest UNCHANGED - skipped")
        }
    }

    /**
     * Single entry point for all event stream requests
     * Ensures babyId and dateRange are always in sync
     */
    fun startStreaming(
        babyId: String,
        strategy: DateRangeStrategy
    ) {
        if (babyId.isEmpty()) return

        _isLoading.value = true
        setupStreamListener();

        Log.d("CalculateRange", "from startStreaming")
        val dateRange = calculateRange(strategy)
        val request = EventStreamRequest(babyId, dateRange)
        Log.d("EventViewModel", "Range: ${dateRange.startDate} → ${dateRange.endDate}")
        // Only update if request actually changed
        if (_streamRequest.value != request) {
            Log.i("EventViewModel", "✓ StreamRequest UPDATED")
            _streamRequest.value = request
        } else {
            Log.i("EventViewModel", "✗ StreamRequest UNCHANGED - skipped")
            _isLoading.value = false
        }
    }

    fun resetDateRangeAndHistory() {
        currentDaysWindow = 1L
        _hasMoreHistory.value = true
        _isLoadingMore.value = false
        // setDateRangeForLastDays(currentDaysWindow)
    }
    /** Stops any active real-time listener. */
    fun stopStreaming() {
        Log.d("EventViewModel", "Stopping stream")

        streamJob?.cancel()
        streamJob = null

        countsStreamJob?.cancel()
        countsStreamJob = null

        _streamRequest.value = null

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
                isSleeping = event.isSleeping,
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
                otherDrugName = event.otherDrugName.orEmpty(),
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
                breastSide = event.breastSide,
                notes = event.notes.orEmpty(),
                photoUrl = event.photoUrl
            )
        }

        _formState.value = state
    }

    private var previousEvents: List<Event> = emptyList()

    private suspend fun checkForNewEvents(newEvents: List<Event>) {
        val currentUser = currentUserId.value
        if (currentUser == null) return

        // Find events that are new compared to previous state
        val newlyAddedEvents = newEvents.filter { newEvent ->
            // Event is new if it wasn't in previous events
            previousEvents.none { it.id == newEvent.id } &&
                    // Ensure userId exists
                    newEvent.userId.isNotEmpty() &&
                    // Event is not from current user
                    newEvent.userId != currentUser &&
                    // Event was created recently (within last 5 minutes to avoid old events)
                    System.currentTimeMillis() - newEvent.timestamp.time < 5 * 60 * 1000
        }

        // Process notifications for new events
        newlyAddedEvents.forEach { event ->
            processEventNotification(event)
        }

        // Update previous events for next comparison
        previousEvents = newEvents
    }

    private suspend fun processEventNotification(event: Event) {
        if (!notificationService.hasNotificationPermission()) return

        try {
            // Get user profile for the event author
            val userProfile = getUserProfile(event.userId)
            val authorName = userProfile?.displayName ?: "Someone"

            // Show notification
            notificationService.showEventNotification(event, authorName)
        } catch (e: Exception) {
            Log.e("EventViewModel", "Error processing notification", e)
        }
    }

    private suspend fun getUserProfile(userId: String): User? {
        // Check cache first
        userProfileCache[userId]?.let { return it }

        // Fetch from repository
        val profile = repository.getUserProfileById(userId)
        profile.let {
            userProfileCache[userId] = it
        }
        return profile
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
            val userId = repository.getCurrentUserProfile().id
            val state = _formState.value
            state.validateAndToEvent(babyId, userId).fold(
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

    fun loadLastGrowth(babyId: String) {
        viewModelScope.launch {
            repository.getLastGrowthEvent(babyId)
                .onSuccess { event ->
                    event?.let {
                        // Only run this once—guarded by LaunchedEffect
                        updateForm {
                            (this as EventFormState.Growth).copy(
                                weightKg = it.weightKg?.toString().orEmpty(),
                                heightCm = it.heightCm?.toString().orEmpty(),
                                headCircumferenceCm = it.headCircumferenceCm?.toString().orEmpty()
                                // notes and other fields remain untouched
                            )
                        }
                    }
                }
                .onFailure { throwable ->
                    Log.e("EventVM", "Failed loading last growth", throwable)
                    _errorMessage.value = throwable.message
                }
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


    /**
     * Returns the list of events for the given Event subclass,
     * e.g. FeedingEvent::class, DiaperEvent::class, etc.
     */

    fun <T : Event> getEventsOfTypeAsFlow(type: KClass<T>): Flow<List<T>> {
        return _eventsByType.map { map ->
            val list = map[type] ?: emptyList()
            @Suppress("UNCHECKED_CAST")
            list as List<T>
        }.distinctUntilChanged()
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
    /**
     * Vérifie si un jour spécifique a des événements
     */
    fun hasEventsOnDay(date: Date): Boolean {
        val dateKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date)
        return _eventCountsByDay.value[dateKey]?.hasEvents ?: false
    }

    /**
     * Obtient le nombre d'événements pour un jour spécifique
     */
    fun getEventCountForDay(date: Date): Int {
        val dateKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date)
        return _eventCountsByDay.value[dateKey]?.count ?: 0
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
