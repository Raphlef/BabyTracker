package com.kouloundissa.twinstracker.presentation.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.storage.StorageException
import com.kouloundissa.twinstracker.R
import com.kouloundissa.twinstracker.Service.NotificationService
import com.kouloundissa.twinstracker.data.AnalysisRange
import com.kouloundissa.twinstracker.data.AnalysisSnapshot
import com.kouloundissa.twinstracker.data.DiaperEvent
import com.kouloundissa.twinstracker.data.DrugsEvent
import com.kouloundissa.twinstracker.data.Event
import com.kouloundissa.twinstracker.data.EventFormState
import com.kouloundissa.twinstracker.data.EventFormState.Diaper
import com.kouloundissa.twinstracker.data.EventFormState.Drugs
import com.kouloundissa.twinstracker.data.EventFormState.Feeding
import com.kouloundissa.twinstracker.data.EventFormState.Growth
import com.kouloundissa.twinstracker.data.EventFormState.Pumping
import com.kouloundissa.twinstracker.data.EventFormState.Sleep
import com.kouloundissa.twinstracker.data.EventType
import com.kouloundissa.twinstracker.data.FeedingEvent
import com.kouloundissa.twinstracker.data.Firestore.FirebaseRepository
import com.kouloundissa.twinstracker.data.GrowthEvent
import com.kouloundissa.twinstracker.data.PumpingEvent
import com.kouloundissa.twinstracker.data.SleepEvent
import com.kouloundissa.twinstracker.data.User
import com.kouloundissa.twinstracker.data.setPhotoUrl
import com.kouloundissa.twinstracker.ui.components.AnalysisFilter
import com.kouloundissa.twinstracker.ui.components.AnalysisFilters
import com.kouloundissa.twinstracker.ui.components.calculateRange
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.reflect.KClass

@HiltViewModel
class EventViewModel @Inject constructor(
    private val repository: FirebaseRepository,
    private val notificationService: NotificationService
) : ViewModel() {

    // Current form values
    private val _formState = MutableStateFlow<EventFormState>(EventFormState.Diaper())
    val formState: StateFlow<EventFormState> = _formState.asStateFlow()


    private val _analysisSnapshot = MutableStateFlow(
        AnalysisSnapshot(
            dailyAnalysis = emptyList(),
            events = emptyList(),
            eventsByDay = emptyMap(),
            dateRange = AnalysisFilter.DateRange(AnalysisRange.CUSTOM),
            babyId = ""
        )
    )
    val analysisSnapshot: StateFlow<AnalysisSnapshot> = _analysisSnapshot.asStateFlow()

    private val _events = MutableStateFlow<List<Event>>(emptyList())

    @Deprecated("Use analysisSnapshot instead")
    val events: StateFlow<List<Event>> = _events

    @Deprecated("Use analysisSnapshot instead")
    val eventsByType: StateFlow<Map<KClass<out Event>, List<Event>>> = _events
        .map { eventList -> eventList.groupBy { it::class } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )

    @Deprecated("Use analysisSnapshot instead")
    val eventsByDay: StateFlow<Map<LocalDate, List<Event>>> = _events
        .map { events ->
            val result = mutableMapOf<LocalDate, MutableList<Event>>()
            val systemZone = ZoneId.systemDefault()

            events.forEach { event ->
                val eventStartDate = event.timestamp.toInstant()
                    .atZone(systemZone)
                    .toLocalDate()

                val eventEndDate = when (event) {
                    is SleepEvent -> event.endTime?.toInstant()
                        ?.atZone(systemZone)
                        ?.toLocalDate() ?: eventStartDate

                    else -> eventStartDate
                }

                // Add event to ALL dates it spans
                var currentDate = eventStartDate
                while (!currentDate.isAfter(eventEndDate)) {
                    result.getOrPut(currentDate) { mutableListOf() }.add(event)
                    currentDate = currentDate.plusDays(1)
                }
            }
            result
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )

    private val _lastGrowthEvent = MutableStateFlow<GrowthEvent?>(null)
    val lastGrowthEvent: StateFlow<GrowthEvent?> = _lastGrowthEvent.asStateFlow()

    // Track if we can load more (haven't reached the beginning of baby's data)
    private val _hasMoreHistory = MutableStateFlow(true)
    val hasMoreHistory: StateFlow<Boolean> = _hasMoreHistory.asStateFlow()

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
    private val _favoriteEventTypes = MutableStateFlow<Set<EventType>>(emptySet())
    val favoriteEventTypes: StateFlow<Set<EventType>> = _favoriteEventTypes

    fun toggleFavorite(eventType: EventType) {
        viewModelScope.launch {
            repository.toggleFavoriteEventType(eventType)
        }
    }

    fun clearBabyCache(babyId: String) {
        viewModelScope.launch {
            repository.clearBabyCache(babyId)
        }
    }

    // Helper to get sorted event types (favorites first)
    fun getSortedEventTypes(favorites: Set<EventType>): List<EventType> {
        val favoritesList = EventType.entries.filter { it in favorites }
        val nonFavoritesList = EventType.entries.filter { it !in favorites }
        return favoritesList + nonFavoritesList
    }

    init {
        // Get current user ID on initialization
        viewModelScope.launch {
            try {
                _currentUserId.value = repository.getCurrentUserIdOrThrow()
            } catch (e: Exception) {
                //can failed when no user log (first start)
            }
            repository.getFavoriteEventTypes().collect { favorites ->
                _favoriteEventTypes.value = favorites
            }
            _lastNewEventTimestamp.value = repository.getLastNewEventTimestamp()
        }
    }

    // --- Date Range Filtering ---
    data class DateRangeParams(
        val startDate: Date,
        val endDate: Date
    )

    data class EventStreamRequest(
        val babyId: String,
        val dateRange: DateRangeParams,
        val eventTypes: Set<EventType> = EventType.entries.toSet()
    )

    fun refreshWithFilters(
        filters: AnalysisFilters
    ) {
        val dateRange = filters.dateRange
        val babyId = filters.babyFilter.selectedBabies.firstOrNull()
        val selectedTypes = filters.eventTypeFilter.selectedTypes
        resetLoadMore()
        babyId?.let { baby ->
            startAnalysisStreaming(baby.id, dateRange, eventTypes = selectedTypes)
        }
    }

    /**
     * Convenience method for custom range
     */
    @Deprecated("Use refreshWithFilters() instead")
    fun refreshWithCustomRange(babyId: String,dateRangeParams:DateRangeParams) {
        resetLoadMore()
        startStreaming(babyId, dateRangeParams)
    }
    @Deprecated("Use _analysisStreamRequest() instead")
    private val _streamRequest = MutableStateFlow<EventStreamRequest?>(null)

    private val _analysisStreamRequest = MutableStateFlow<EventStreamRequest?>(null)
    @Deprecated("Use analysisStreamJob instead")
    private var streamJob: Job? = null

    private var analysisStreamJob: Job? = null

    //private val maxDaysWindow = 365L // Maximum 1 year of history

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
                ?: repository.getEvent(eventId) // implement this if needed

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
    fun loadMoreEvents() {
        if (_isLoadingMore.value || !_hasMoreHistory.value) return

        _isLoadingMore.value = true

        val currentRequest = _analysisStreamRequest.value ?: run {
            Log.d("LoadMore", "No current analysis request, aborting")
            _isLoadingMore.value = false
            return
        }

        val additionalDays = AnalysisRange.THREE_DAYS.days

        val newStartDate = Date(currentRequest.dateRange.startDate.time - TimeUnit.DAYS.toMillis(additionalDays.toLong()))

        Log.d("LoadMore", "Extending from ${currentRequest.dateRange.startDate} to $newStartDate")

        val maxDaysWindow = AnalysisRange.entries
            .filter { it.days > 0 }
            .maxOfOrNull { it.days } ?: 365

        val maxWindowMillis = TimeUnit.DAYS.toMillis(maxDaysWindow.toLong())

        if (newStartDate.time <= currentRequest.dateRange.startDate.time - maxWindowMillis) {
            Log.d("LoadMore", "Reached max limit, no more history")
            _hasMoreHistory.value = false
        }

        val newDateRange = DateRangeParams(newStartDate, currentRequest.dateRange.endDate)
        _analysisStreamRequest.value = EventStreamRequest(currentRequest.babyId, newDateRange)
    }
    /**
     * Centralized stream setup - only called once in init
     * Responds to changes in _streamRequest only
     */
    @Deprecated("Use setupAnalysisStreamListener() instead")
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
                _events.value = filtered
                _isLoading.value = false
                _isLoadingMore.value = false
            }
            .launchIn(viewModelScope)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun setupAnalysisStreamListener() {
        if (analysisStreamJob != null) return

        analysisStreamJob?.cancel()
        analysisStreamJob = _analysisStreamRequest
            .onEach { request ->
                request?.let {
                    val daysBetween = ChronoUnit.DAYS.between(
                        it.dateRange.startDate.toInstant(),
                        it.dateRange.endDate.toInstant()
                    ) + 1
                    Log.d(
                        "AnalysisStream",
                        "BabyId: ${it.babyId}, DateRange: ${it.dateRange.startDate} to ${it.dateRange.endDate} ($daysBetween days)"
                    )
                } ?: Log.d("AnalysisStream", "AnalysisStreamRequest is null")
            }
            .filterNotNull()
            .distinctUntilChanged()
            .onEach { request ->
                Log.d("AnalysisStream", "Processing analysis request for babyId: ${request.babyId}")
            }
            .flatMapLatest { request ->
                repository.streamAnalysisMetrics(
                    request.babyId,
                    request.dateRange.startDate,
                    request.dateRange.endDate,
                    eventTypes = request.eventTypes
                )
            }
            .onStart {
                Log.d("AnalysisStream", "Analysis stream started")
                if (!_isLoadingMore.value) {
                    _isLoading.value = true
                }
            }
            .catch { e ->
                Log.e("AnalysisStream", "Analysis stream error occurred", e)
                _isLoading.value = false
            }
            .onEach { snapshot ->
                val state = snapshot.loadingState
                Log.d(
                    "AnalysisStream",
                    "Received complete snapshot: ${snapshot.dailyAnalysis.size} days, ${snapshot.events.size} events, ${snapshot.eventsByDay.size} day counts"
                )
                checkForNewEvents(snapshot.events)
                _analysisSnapshot.value = snapshot
                if (state.isFullyReady) {
                    _isLoading.value = false
                    _isLoadingMore.value = false
                }
            }
            .launchIn(viewModelScope)
    }

    fun startAnalysisStreaming(
        babyId: String,
        dateRange: AnalysisFilter.DateRange,
        eventTypes: Set<EventType> = EventType.entries.toSet()
    ) {
        if (babyId.isEmpty()) return

        _isLoading.value = true
        setupAnalysisStreamListener()

        val dateRange = calculateRange(dateRange)
        val request = EventStreamRequest(
            babyId, dateRange,
            eventTypes = eventTypes
        )

        if (_analysisStreamRequest.value != request) {
            Log.i("EventViewModel", "✓ Analysis StreamRequest UPDATED")
            _analysisStreamRequest.value = request
        } else {
            Log.i("EventViewModel", "✗ Analysis StreamRequest UNCHANGED - skipped")
            //_isLoading.value = false
        }
    }

    /**
     * Single entry point for all event stream requests
     * Ensures babyId and dateRange are always in sync
     */
    @Deprecated("Use startAnalysisStreaming() instead")
    fun startStreaming(
        babyId: String,
        dateRange:DateRangeParams
    ) {
        if (babyId.isEmpty()) return

        _isLoading.value = true
        setupStreamListener();

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

    private fun resetLoadMore() {
        _hasMoreHistory.value = true
        _isLoadingMore.value = false
    }

    /** Stops any active real-time listener. */
    fun stopStreaming() {
        Log.d("EventViewModel", "Stopping stream")

        streamJob?.cancel()
        streamJob = null

        analysisStreamJob?.cancel()
        analysisStreamJob = null

        _streamRequest.value = null
        _analysisStreamRequest.value = null

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
                event = event,
                eventId = id,
                eventTimestamp = timestamp,
                diaperType = event.diaperType,
                poopColor = event.poopColor,
                poopConsistency = event.poopConsistency,
                notes = event.notes.orEmpty(),
                photoUrl = event.photoUrl
            )

            is SleepEvent -> Sleep(
                event = event,
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
                event = event,
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
                event = event,
                eventId = id,
                eventTimestamp = timestamp,
                weightKg = event.weightKg?.toString().orEmpty(),
                heightCm = event.heightCm?.toString().orEmpty(),
                headCircumferenceCm = event.headCircumferenceCm?.toString().orEmpty(),
                notes = event.notes.orEmpty(),
                photoUrl = event.photoUrl
            )

            is DrugsEvent -> Drugs(
                event = event,
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
                event = event,
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

    private val _lastNewEventTimestamp = MutableStateFlow<Long>(0L)
    val lastNewEventTimestamp: StateFlow<Long> = _lastNewEventTimestamp.asStateFlow()

    private suspend fun checkForNewEvents(newEvents: List<Event>) {
        val currentUser = currentUserId.value
        if (currentUser == null) return

        val lastTimestamp = _lastNewEventTimestamp.value

        // Find events newer than last known timestamp
        val newlyAddedEvents = newEvents.filter { newEvent ->
            newEvent.timestamp.time > lastTimestamp &&  // Simpler check!
                    newEvent.userId.isNotEmpty() &&
                    newEvent.userId != currentUser &&
                    System.currentTimeMillis() - newEvent.timestamp.time < 5 * 60 * 1000
        }

        // Process notifications for new events
        newlyAddedEvents.forEach { event ->
            processEventNotification(event)
        }

        // Save the most recent timestamp
        if (newlyAddedEvents.isNotEmpty()) {
            val mostRecentTimestamp = newlyAddedEvents.maxOf { it.timestamp.time }
            _lastNewEventTimestamp.value = mostRecentTimestamp
            repository.saveLastNewEventTimestamp(mostRecentTimestamp)

            Log.d(
                "CheckForNewEvents",
                "Found ${newlyAddedEvents.size} new events, " +
                        "updated timestamp to $mostRecentTimestamp"
            )
        }
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
    fun SaveEvent(babyId: String, familyViewModel: FamilyViewModel, context: Context) {
        if (babyId.isBlank()) {
            _errorMessage.value = "Baby ID is missing."
            return
        }

        if (!familyViewModel.canUserSaveEvent()) {
            _errorMessage.value = context.getString(R.string.event_error_permission_denied)
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
                        updateEventWithPhoto(event, state, familyViewModel)
                    }
                }
            )
        }
    }

    fun loadLastGrowth(babyId: String) {
        viewModelScope.launch {
            repository.getLastGrowthEvent(babyId)
                .onSuccess { event ->
                    _lastGrowthEvent.value = event
                }
                .onFailure { throwable ->
                    Log.e("EventVM", "Failed loading last growth", throwable)
                    _errorMessage.value = throwable.localizedMessage
                    _lastGrowthEvent.value = null
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
                onSuccess = {
                    _saveSuccess.value = true

                    // Mise à jour immédiate : ajoute ou met à jour l'event
                    val existingIndex = _events.value.indexOfFirst { it.id == eventWithPhoto.id }
                    _events.value = if (existingIndex >= 0) {
                        // Update si l'event existe déjà (cas rare mais possible)
                        _events.value.toMutableList().apply {
                            set(existingIndex, eventWithPhoto)
                        }
                    } else {
                        // Ajoute en tête si nouvel event (triée par timestamp DESC)
                        listOf(eventWithPhoto) + _events.value
                    }
                },
                onFailure = { _errorMessage.value = it.message }
            )
        } catch (e: Exception) {
            _errorMessage.value = e.localizedMessage
        } finally {
            _isSaving.value = false
        }
    }

    private suspend fun updateEventWithPhoto(
        event: Event,
        state: EventFormState,
        familyViewModel: FamilyViewModel
    ) {
        try {
            // 1. Handle photo upload or deletion (before event update)
            val photoUrl = when {
                state.newPhotoUrl != null -> uploadEventPhoto(event.id, state.newPhotoUrl!!)
                state.photoRemoved -> {
                    deleteEventPhoto(event.id, familyViewModel); null
                }

                else -> event.photoUrl
            }

            // 2. Update the event with new photoUrl
            val eventWithPhoto = event.setPhotoUrl(photoUrl)
            repository.updateEvent(event.id, eventWithPhoto).fold(
                onSuccess = {
                    _saveSuccess.value = true
                    _events.value = _events.value.map {
                        if (it.id == event.id) event else it
                    }
                },
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

    fun deleteEventPhoto(eventId: String, familyViewModel: FamilyViewModel) {
        if (!familyViewModel.canUserEditEvent()) {
            _errorMessage.value =
                "You don't have permission to edit events. Only members and admins can save."
            return
        }
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
                val currentEvent = repository.getEvent(eventId).getOrThrow()

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
    fun deleteEvent(event: Event, familyViewModel: FamilyViewModel) {
        if (!familyViewModel.canUserDeleteEvent()) {
            _errorMessage.value =
                "You don't have permission to delete events. Only members and admins can save."
            return
        }
        _isDeleting.value = true
        _deleteError.value = null

        viewModelScope.launch {
            try {
                //0. force invalidate cache,
                repository.invalidateCacheDay(event.babyId, event.timestamp)
                // 1. Delete photo from Storage & clear Firestore field
                try {
                    repository.deletePhotoFromEntity("events", event.id)
                } catch (e: Exception) {
                    if (e is StorageException && e.errorCode == StorageException.ERROR_OBJECT_NOT_FOUND) {
                        // Photo didn’t exist—ignore
                    } else {
                        throw e
                    }
                }

                // 2. Fetch current event to clear its photoUrl
                val currentEvent = repository.getEvent(event.id).getOrThrow()
                    ?: throw IllegalStateException("Event not found")

                val eventWithoutPhoto = currentEvent.setPhotoUrl(photoUrl = null)

                // 3. Persist Firestore update (remove photoUrl)
                repository.updateEvent(event.id, eventWithoutPhoto).getOrThrow()

                // 4. Now delete the event document itself
                repository.deleteEvent(event).fold(
                    onSuccess = {
                        _deleteSuccess.value = true
                        _events.value = _events.value.filterNot { it.id == event.id }
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
     * Returns a map of displayName → event count for all loaded types.
     * Useful to drive calendar badges or summary views.
     */
    fun getEventCounts(): Map<String, Int> {
        return eventsByType.value.mapKeys { (type, _) ->
            // Simple human‐readable key; you could customize per type.
            type.simpleName ?: "Unknown"
        }.mapValues { (_, list) ->
            list.size
        }
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
