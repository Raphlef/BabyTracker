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

    private val _errorMessage = MutableStateFlow<String?>(null) // For error messages
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun loadEvents(babyId: String) {
        if (babyId.isBlank()) {
            Log.w("EventViewModel", "loadEvents called with blank babyId")
            _events.value = emptyMap()
            _isLoading.value = false
            return
        }
        _isLoading.value = true
        _errorMessage.value = null // Clear previous error
        viewModelScope.launch {
            val result: Result<List<Event>> = repository.getAllEventsForBaby(babyId)

            result.fold(
                onSuccess = { eventsList ->
                    // Group the events by their type.
                    // The 'it.type' depends on how 'type' is defined in your Event class.
                    // If Event is a sealed class:
                    //   - And you added an 'eventTypeString' in FirebaseRepository, you might group by that.
                    //   - Or you can group by the class type itself: it::class
                    // If Event has a property 'type: EventType' (enum):
                    //   - Then 'it.type' should work directly.

                    // Example: Grouping by class type (if Event is sealed)
                    _events.value = eventsList.groupBy { event ->
                        event::class // Groups by actual class (FeedingEvent, DiaperEvent, etc.)
                        // Or if you have a common 'type' property in your base Event class:
                        // event.type // This assumes 'type' exists and is suitable as a Map key
                    }
                    // TODO: Implémenter le chargement par type d'événement (if further filtering is needed after grouping)
                },
                onFailure = { exception ->
                    Log.e("EventViewModel", "Error loading events for baby $babyId: ${exception.message}", exception)
                    _events.value = emptyMap() // Clear events on error or keep previous
                    _errorMessage.value = "Failed to load events: ${exception.localizedMessage}"
                    // TODO: Gérer l'erreur (e.g., show a snackbar)
                }
            )
            _isLoading.value = false // Set loading to false after success or failure

        }
    }

    fun addFeedingEvent(
        babyId: String,
        feedType: FeedType,
        notes: String? = null, // Added optional notes
        amountMl: Double? = null,
        durationMinutes: Int? = null,
        breastSide: BreastSide? = null
    ) {
        if (babyId.isBlank()) {
            Log.w("EventViewModel", "addDiaperEvent called with blank babyId")
            _errorMessage.value = "Baby ID is missing."
            return
        }
        // Basic validation based on feed type (optional, but good practice)
        when (feedType) {
            FeedType.BREAST_MILK -> {
                if (durationMinutes == null && breastSide == null && amountMl == null) {
                    // For breast milk, typically expect duration/side for breastfeeding
                    // or amount if it's pumped milk.
                    // Log a warning or set an error, depending on how strict you want to be.
                    Log.w("EventViewModel", "Breast milk feeding event lacks typical details (duration/side or amount).")
                }
            }
            FeedType.FORMULA -> {
                if (amountMl == null) {
                    Log.w("EventViewModel", "Formula feeding event lacks amount.")
                    // _errorMessage.value = "Amount is required for formula feeding."
                    // return // Optionally stop if validation fails strictly
                }
            }
            FeedType.SOLID -> {
                // Solids might just have notes, or you might add a 'description' field to FeedingEvent later
                if (notes.isNullOrBlank() && amountMl == null) { // amountMl could be used for "grams" with a comment
                    Log.w("EventViewModel", "Solid feeding event lacks details (notes or amount).")
                }
            }
        }
        viewModelScope.launch {
            val event = FeedingEvent(
                babyId = babyId,
                feedType = feedType,
                notes = notes,
                amountMl = amountMl,
                durationMinutes = durationMinutes,
                breastSide = breastSide
                // id and timestamp will use default values from FeedingEvent data class
            )
            val result = repository.addEvent(event)
            result.fold(
                onSuccess = {
                    Log.d("EventViewModel", "Feeding event added successfully for baby $babyId")
                    loadEvents(babyId) // Refresh the events list
                },
                onFailure = { exception ->
                    Log.e("EventViewModel", "Error adding feeding event: ${exception.message}", exception)
                    _errorMessage.value = "Failed to add feeding event: ${exception.localizedMessage}"
                }
            )
            // TODO: Actualiser la liste des événements
        }
    }
    suspend fun addGrowthEvent(
        babyId: String,
        weightKg: Double?,
        heightCm: Double?,
        headCircumferenceCm: Double?,
        notes: String?,
        measurementDate: Date = Date() // Allow specifying measurement date, defaults to now
    ): Result<Unit> {
        if (babyId.isBlank()) {
            Log.w("EventViewModel", "addGrowthEvent called with blank babyId")
            return Result.failure(IllegalArgumentException("Baby ID cannot be blank for GrowthEvent."))
        }

        if (weightKg == null && heightCm == null && headCircumferenceCm == null) {
            Log.w("EventViewModel", "addGrowthEvent called with no measurement values.")
            // You might want to return Result.failure here if at least one measurement is required.
            // For now, allowing it, but it's probably not a useful event.
            // return Result.failure(IllegalArgumentException("At least one growth measurement (weight, height, or head circumference) is required."))
        }

        val event = GrowthEvent(
            babyId = babyId,
            timestamp = measurementDate, // Use provided measurementDate
            notes = notes,
            weightKg = weightKg,
            heightCm = heightCm,
            headCircumferenceCm = headCircumferenceCm
        )

        val repoResult = repository.addEvent(event)

        return repoResult.fold(
            onSuccess = {
                Log.d("EventViewModel", "Growth event added successfully for baby $babyId. Refreshing events.")
                loadEvents(babyId) // Refresh the main events list
                _errorMessage.value = null // Clear any previous general error on success
                Result.success(Unit)
            },
            onFailure = { exception ->
                Log.e("EventViewModel", "Error adding growth event for baby $babyId: ${exception.message}", exception)
                _errorMessage.value = "Failed to add growth event: ${exception.localizedMessage}"
                Result.failure(exception)
            }
        )
    }
    suspend fun addSleepEvent(
        babyId: String,
        isSleeping: Boolean,
        beginTime: Date?,
        endTime: Date?, // Nullable as per SleepEvent definition
        durationMinutes: Long?, // Nullable as per SleepEvent definition
        notes: String?,
    ): Result<Unit> { // Return Result<Unit> for success/failure feedback
        if (babyId.isBlank()) {
            Log.w("EventViewModel", "addSleepEvent called with blank babyId")
            // Not setting _errorMessage here as the caller (SleepViewModel) will handle Result.failure
            return Result.failure(IllegalArgumentException("Baby ID cannot be blank for SleepEvent."))
        }
        if (durationMinutes == null && endTime == null) {
            Log.w("EventViewModel", "addSleepEvent called with no duration and no end time.")
            // Decide if this is an error or just a warning. For now, let's allow it if an explicit
            // duration isn't required and endTime might be set later (though our current flow sets endTime).
        }


        val event = SleepEvent(
            babyId = babyId,
            timestamp = Date(),
            beginTime = beginTime,
            endTime = endTime,
            isSleeping= isSleeping,
            durationMinutes = durationMinutes,
            notes = notes,
        )

        // The actual call to the repository
        val repoResult = repository.addEvent(event) // Assuming addEvent handles any Event type

        return repoResult.fold(
            onSuccess = {
                Log.d("EventViewModel", "Sleep event added successfully for baby $babyId. Refreshing events.")
                loadEvents(babyId) // Refresh the main events list
                _errorMessage.value = null // Clear any previous general error on success
                Result.success(Unit)
            },
            onFailure = { exception ->
                Log.e("EventViewModel", "Error adding sleep event for baby $babyId: ${exception.message}", exception)
                _errorMessage.value = "Failed to add sleep event: ${exception.localizedMessage}" // Set general error
                Result.failure(exception)
            }
        )
    }
    fun addDiaperEvent(
        babyId: String,
        diaperType: DiaperType,
        notes: String? = null,
        color: String? = null,
        consistency: String? = null
    ) {
        if (babyId.isBlank()) {
        Log.w("EventViewModel", "addDiaperEvent called with blank babyId")
        _errorMessage.value = "Baby ID is missing."
        return
        }
        viewModelScope.launch {
            val event = DiaperEvent(
                babyId = babyId,
                diaperType = diaperType,
                notes = notes,
                color = color,
                consistency = consistency
                // id and timestamp will use default values from DiaperEvent data class
            )
            val result = repository.addEvent(event)
            result.fold(
                onSuccess = {
                    Log.d("EventViewModel", "Diaper event added successfully for baby $babyId")
                    loadEvents(babyId) // Refresh
                },
                onFailure = { exception ->
                    Log.e("EventViewModel", "Error adding diaper event: ${exception.message}", exception)
                    _errorMessage.value = "Failed to add diaper event: ${exception.localizedMessage}"
                }
            )
        }
    }

    // TODO: Ajouter des méthodes similaires pour les autres types d'événements
}