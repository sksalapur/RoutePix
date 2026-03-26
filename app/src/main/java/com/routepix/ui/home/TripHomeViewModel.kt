package com.routepix.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.routepix.data.model.Trip
import com.routepix.data.repository.TripRepository
import com.routepix.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class TripHomeUiState(
    val displayName: String = "",
    val trips: List<Trip> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

class TripHomeViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val repository = TripRepository(firestore, auth)
    private val userRepository = UserRepository(firestore, auth)

    private val _uiState = MutableStateFlow(TripHomeUiState())
    val uiState: StateFlow<TripHomeUiState> = _uiState.asStateFlow()

    init {
        loadUserProfile()
        observeTrips()
    }

    private fun loadUserProfile() {
        viewModelScope.launch {
            repository.syncLegacyInvites()
            val user = userRepository.getCurrentUser()
            _uiState.value = _uiState.value.copy(
                displayName = user?.displayName?.ifBlank { null } 
                    ?: auth.currentUser?.displayName 
                    ?: "Traveler"
            )
        }
    }

    private fun observeTrips() {
        viewModelScope.launch {
            repository.getTripsForCurrentUser()
                .catch { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = e.message
                    )
                }
                .collect { trips ->
                    _uiState.value = _uiState.value.copy(
                        trips = trips,
                        isLoading = false
                    )
                }
        }
    }

    fun signOut() {
        auth.signOut()
    }

    suspend fun renameTrip(tripId: String, newName: String) {
        if (newName.isBlank()) return
        try {
            FirebaseFirestore.getInstance().collection("trips").document(tripId)
                .update("name", newName)
                .await()
        } catch (_: Exception) { }
    }

    suspend fun resolveMemberNames(uids: List<String>): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for (uid in uids) {
            try {
                val doc = FirebaseFirestore.getInstance().collection("users").document(uid).get().await()
                result[uid] = doc.getString("displayName") ?: uid
            } catch (_: Exception) {
                result[uid] = uid
            }
        }
        return result
    }

    suspend fun removeMember(tripId: String, memberUid: String) {
        try {
            firestore.collection("trips").document(tripId)
                .update("memberUids", com.google.firebase.firestore.FieldValue.arrayRemove(memberUid))
                .await()
        } catch (_: Exception) { }
    }

    suspend fun exitTrip(tripId: String) {
        repository.exitTrip(tripId)
    }

    fun getCurrentUid(): String = auth.currentUser?.uid ?: ""
}

