package com.kouloundissa.twinstracker.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.kouloundissa.twinstracker.data.Family
import com.kouloundissa.twinstracker.data.FamilyRole
import com.kouloundissa.twinstracker.data.FamilyUser
import com.kouloundissa.twinstracker.data.Firestore.FirebaseRepository
import com.kouloundissa.twinstracker.data.Firestore.addMemberToFamily
import com.kouloundissa.twinstracker.data.Firestore.addOrUpdateFamily
import com.kouloundissa.twinstracker.data.Firestore.deleteFamily
import com.kouloundissa.twinstracker.data.Firestore.joinFamilyByCode
import com.kouloundissa.twinstracker.data.Firestore.regenerateInviteCode
import com.kouloundissa.twinstracker.data.Firestore.removeUserFromAllRoles
import com.kouloundissa.twinstracker.data.Firestore.streamFamilies
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FamilyViewModel @Inject constructor(
    private val repository: FirebaseRepository,
    private val firebaseAuth: FirebaseAuth
) : ViewModel() {

    private val _families = MutableStateFlow<List<Family>>(emptyList())
    val families: StateFlow<List<Family>> = _families.asStateFlow()

    val selectedFamily: StateFlow<Family?> = repository.selectedFamily

    private val _state = MutableStateFlow(FamilyState())
    val state: StateFlow<FamilyState> = _state.asStateFlow()

    private val _inviteResult = MutableSharedFlow<Result<Unit>>()
    val inviteResult: SharedFlow<Result<Unit>> = _inviteResult

    private val _newCode = MutableSharedFlow<String>()
    val newCode: SharedFlow<String> = _newCode

    private val _familyUsers = MutableStateFlow<List<FamilyUser>>(emptyList())
    val familyUsers: StateFlow<List<FamilyUser>> = _familyUsers.asStateFlow()

    // Backing StateFlow for currentUserId
    private val _currentUserId = MutableStateFlow<String?>(null)
    val currentUserId: StateFlow<String?> = _currentUserId.asStateFlow()

    private var familiesJob: Job? = null

    private val authStateFlow: Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            trySend(auth.currentUser)
        }
        firebaseAuth.addAuthStateListener(listener)
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }

    init {
        authStateFlow
            .map { it != null }
            .distinctUntilChanged()
            .onEach { isAuth ->
                if (isAuth) {
                    _currentUserId.value = repository.getCurrentUserId()
                    startObservingFamilyUpdates()

                    viewModelScope.launch {
                        val lastFamilyId = repository.getLastSelectedFamilyId().first()
                        families
                            .filter { it.isNotEmpty() }
                            .first()
                            .let { familiesList ->
                                val familyToSelect = familiesList.find { it.id == lastFamilyId }
                                    ?: familiesList.firstOrNull()  // ✅ Default to first

                                familyToSelect?.let { selectFamily(it) }
                            }
                    }
                } else {
                    stopObservingFamilyUpdates()
                    _currentUserId.value = null
                    _families.value = emptyList()
                    _state.value = FamilyState()  // reset loading/error
                }
            }
            .launchIn(viewModelScope)
    }

    fun selectFamily(family: Family?) {
        repository.setSelectedFamily(family)
        family?.let { loadFamilyUsers(it) }

        viewModelScope.launch {
            repository.saveLastSelectedFamilyId(family?.id)
        }
    }

    fun loadFamilyUsers(family: Family) {
        viewModelScope.launch {
            setLoading(true)
            try {
                repository.runCatching {
                    // Fetch all users by their IDs
                    val users =
                        repository.getUsersByIds(family.memberIds + family.adminIds + family.viewerIds)
                    users.map { user ->
                        // Determine all roles for this user
                        val role = when {
                            family.adminIds.contains(user.id) -> FamilyRole.ADMIN      // Check admin first
                            family.memberIds.contains(user.id) -> FamilyRole.MEMBER    // Then member
                            family.viewerIds.contains(user.id) -> FamilyRole.VIEWER    // Then viewer
                            else -> FamilyRole.VIEWER                                   // Fallback
                        }

                        // Return a FamilyUser with all role
                        FamilyUser(
                            user = user,
                            role = role
                        )
                    }
                }.onSuccess { users ->
                    _familyUsers.value = users
                    clearError()
                    setLoading(false)
                }.onFailure { e ->
                    handleError(e as Exception, "Failed to load family members")
                }
            } catch (e: Exception) {
                handleError(e, "Failed to load family members")
            }
        }
    }


    fun updateUserRole(family: Family, userId: String, newRole: FamilyRole) {
        viewModelScope.launch {
            setLoading(true)
            try {
                val currentId = _currentUserId.value
                // Only admins can change roles
                if (currentId == null || !family.adminIds.contains(currentId)) {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = "Seuls les administrateurs peuvent modifier les rôles."
                        )
                    }
                    return@launch
                }
                val updatedFamily = family.copy(
                    adminIds = when (newRole) {
                        FamilyRole.ADMIN -> (family.adminIds + userId).distinct()
                        else -> family.adminIds - userId
                    },
                    memberIds = when (newRole) {
                        FamilyRole.MEMBER -> (family.memberIds + userId).distinct()
                        else -> family.memberIds - userId
                    },
                    viewerIds = when (newRole) {
                        FamilyRole.VIEWER -> (family.viewerIds + userId).distinct()
                        else -> family.viewerIds - userId
                    },
                )
                repository.addOrUpdateFamily(updatedFamily)
                    .onSuccess {
                        selectFamily(updatedFamily)
                        loadFamilyUsers(updatedFamily)
                        clearError()
                        setLoading(false)
                    }
                    .onFailure { e ->
                        handleError(e as Exception, "Failed to update user role")
                    }
            } catch (e: Exception) {
                handleError(e, "Failed to update user role")
            }
        }
    }


    fun removeUserFromFamily(family: Family, userId: String) {
        viewModelScope.launch {
            setLoading(true)
            try {
                // Prevent removing sole admin
                if (family.adminIds.contains(userId) && family.adminIds.size == 1) {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = "Assign another admin before removing this user."
                        )
                    }
                    return@launch
                }
                val updatedFamily = family.copy(
                    adminIds = family.adminIds - userId,
                    memberIds = family.memberIds - userId
                )
                repository.addOrUpdateFamily(updatedFamily)
                    .onSuccess {
                        if (_currentUserId.value == userId) {
                            selectFamily(null)
                        } else {
                            selectFamily(updatedFamily)
                        }
                        loadFamilyUsers(updatedFamily)
                        clearError()
                        setLoading(false)
                    }
                    .onFailure { e ->
                        handleError(e as Exception, "Failed to remove user from family")
                    }
            } catch (e: Exception) {
                handleError(e, "Failed to remove user from family")
            }
        }
    }

    fun createOrUpdateFamily(family: Family) {
        viewModelScope.launch {
            setLoading(true)

            try {
                val currentUserId = repository.getCurrentUserId()
                if (currentUserId.isNullOrBlank()) {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = "User not authenticated. Please sign in again."
                        )
                    }
                    return@launch
                }
                val existingFamily = _families.value.find { it.id == family.id }
                val isNewFamily = existingFamily == null
                // For new families, ensure current user is added as admin AND member
                val familyToSave = if (isNewFamily) {
                    family.copy(
                        adminIds = listOf(currentUserId),
                        memberIds = listOf(currentUserId) // Also add to members
                    )
                } else {
                    family
                }

                repository.addOrUpdateFamily(familyToSave)
                    .onSuccess { updatedFamily ->
                        // Update the selected family if it was the one being updated
                        selectFamily(updatedFamily as Family?)
                        clearError()
                        setLoading(false)
                    }
                    .onFailure { err ->
                        handleError(err as Exception, "Failed to create family")
                    }
            } catch (e: Exception) {
                handleError(e, "Failed to create family")
            }
        }
    }

    fun deleteFamily(familyId: String) {
        viewModelScope.launch {
            setLoading(true)
            try {
                repository.deleteFamily(familyId)
                    .onSuccess {
                        clearError()
                        setLoading(false)
                    }
                    .onFailure { err ->
                        handleError(err as Exception, "Failed to delete family")
                    }
            } catch (e: Exception) {
                handleError(e, "Failed to delete family")
            }
        }
    }

    fun addMember(familyId: String, userId: String) {
        viewModelScope.launch {
            setLoading(true)
            try {
                repository.addMemberToFamily(familyId, userId)
                    .onSuccess {
                        // Reload family users if this is the selected family
                        selectedFamily.value?.takeIf { it.id == familyId }?.let {
                            loadFamilyUsers(it)
                        }
                        clearError()
                        setLoading(false)
                    }
                    .onFailure { err ->
                        handleError(err as Exception, "Failed to add member")
                    }
            } catch (e: Exception) {
                handleError(e, "Failed to add member")
            }
        }
    }

    fun removeMember(familyId: String, userId: String) {
        viewModelScope.launch {
            setLoading(true)
            try {
                // Get current family from cached families list
                val family = _families.value.firstOrNull { it.id == familyId }
                    ?: return@launch handleError(
                        Exception("Family not found"),
                        "Family not found"
                    )

                // Safety check: prevent leaving if user is the only admin
                if (family.adminIds.contains(userId) && family.adminIds.size == 1) {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = "You cannot leave as you are the only administrator. Assign another admin first."
                        )
                    }
                    return@launch
                }

                repository.removeUserFromAllRoles(familyId, userId)
                    .onSuccess {
                        // Clear selection if user left the currently selected family
                        if (selectedFamily.value?.id == familyId) {
                            selectFamily(null)
                        }
                        clearError()
                        setLoading(false)
                        startObservingFamilyUpdates()
                    }
                    .onFailure { err ->
                        handleError(err as Exception, "Failed to remove member")
                    }
            } catch (e: Exception) {
                handleError(e, "Failed to remove member")
            }
        }
    }

    /** Regenerates invite code for the selected family */
    fun regenerateCode() {
        viewModelScope.launch {
            setLoading(true)
            try {
                val fam = selectedFamily.value
                    ?: return@launch handleError(
                        Exception("No family selected"),
                        "No family selected"
                    )
                repository.regenerateInviteCode(fam)
                    .onSuccess { code ->
                        // emit new code for UI
                        _newCode.emit(code)
                        // update selectedFamily
                        selectFamily(
                            fam.copy(
                                inviteCode = code,
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                        clearError()
                        setLoading(false)
                    }
                    .onFailure { exception ->
                        handleError(exception as Exception, "Failed to regenerate invite code")
                    }
            } catch (e: Exception) {
                handleError(e, "Failed to regenerate invite code")
            }
        }
    }

    /** Joins a family via invite code */
    fun joinByCode(code: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            try {
                val currentUserId = repository.getCurrentUserId()
                    ?: return@launch _state.update {
                        it.copy(
                            isLoading = false,
                            error = "User not authenticated"
                        )
                    }
                repository.joinFamilyByCode(code, currentUserId)
                    .onSuccess {
                        _inviteResult.emit(Result.success(Unit))
                        _state.update { it.copy(isLoading = false) }
                        startObservingFamilyUpdates()
                    }
                    .onFailure { ex ->
                        _state.update {
                            it.copy(
                                isLoading = false,
                                error = ex.message ?: "Failed to join family"
                            )
                        }
                        _inviteResult.emit(Result.failure(ex))
                    }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to join family"
                    )
                }
            }
        }
    }

    private fun startObservingFamilyUpdates() {
        familiesJob?.cancel()
        familiesJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                repository.streamFamilies()
                    .collect { list ->
                        _families.value = list
                        _state.update { it.copy(isLoading = false, error = null) }
                    }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load families"
                    )
                }
            }
        }
    }

    private fun stopObservingFamilyUpdates() {
        familiesJob?.cancel()
        familiesJob = null
    }

    /**
     * Utility: Set loading state
     */
    private fun setLoading(isLoading: Boolean) {
        _state.update { it.copy(isLoading = isLoading) }
    }

    /**
     * Utility: Clear error
     */
    private fun clearError() {
        _state.update { it.copy(error = null) }
    }

    private fun handleError(exception: Exception, defaultMessage: String = "An error occurred") {
        _state.update {
            it.copy(
                isLoading = false,
                error = exception.message ?: defaultMessage
            )
        }
    }

}

data class FamilyState(
    val isLoading: Boolean = false,
    val error: String? = null
)