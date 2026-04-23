package com.routepix.ui.join

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.routepix.data.repository.TripRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

sealed class JoinTripState {
    data object Idle : JoinTripState()
    data object Loading : JoinTripState()
    data class Success(val tripId: String) : JoinTripState()
    data class AlreadyMember(val tripId: String, val tripName: String) : JoinTripState()
    data class Error(val message: String) : JoinTripState()
}

class JoinTripViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val repository = TripRepository(
        firestore,
        auth
    )

    private val _state = MutableStateFlow<JoinTripState>(JoinTripState.Idle)
    val state: StateFlow<JoinTripState> = _state.asStateFlow()

    /** Called when arriving from a deep link — checks if already a member before showing join prompt. */
    fun checkMembershipByCode(inviteCode: String) {
        if (inviteCode.isBlank()) return
        _state.value = JoinTripState.Loading
        viewModelScope.launch {
            try {
                val uid = auth.currentUser?.uid ?: throw IllegalStateException("Not logged in")
                val inviteDoc = firestore.collection("invites").document(inviteCode).get()
                    .await()
                if (!inviteDoc.exists()) {
                    _state.value = JoinTripState.Idle // let normal join flow handle the error
                    return@launch
                }
                val tripId = inviteDoc.getString("tripId") ?: run {
                    _state.value = JoinTripState.Idle
                    return@launch
                }
                val tripDoc = firestore.collection("trips").document(tripId).get().await()
                @Suppress("UNCHECKED_CAST")
                val members = tripDoc.get("memberUids") as? List<String> ?: emptyList()
                val tripName = tripDoc.getString("name") ?: ""
                if (uid in members) {
                    _state.value = JoinTripState.AlreadyMember(tripId, tripName)
                } else {
                    _state.value = JoinTripState.Idle
                }
            } catch (e: Exception) {
                _state.value = JoinTripState.Idle // fall back silently; join flow handles errors
            }
        }
    }

    fun joinTrip(inviteCode: String) {
        _state.value = JoinTripState.Loading
        viewModelScope.launch {
            try {
                val tripId = repository.joinTrip(inviteCode)
                _state.value = JoinTripState.Success(tripId)
            } catch (e: Exception) {
                _state.value = JoinTripState.Error(e.message ?: "Failed to join trip")
            }
        }
    }

    fun clearError() {
        _state.value = JoinTripState.Idle
    }
}
