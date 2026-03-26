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

sealed class JoinTripState {
    data object Idle : JoinTripState()
    data object Loading : JoinTripState()
    data class Success(val tripId: String) : JoinTripState()
    data class Error(val message: String) : JoinTripState()
}

class JoinTripViewModel : ViewModel() {

    private val repository = TripRepository(
        FirebaseFirestore.getInstance(),
        FirebaseAuth.getInstance()
    )

    private val _state = MutableStateFlow<JoinTripState>(JoinTripState.Idle)
    val state: StateFlow<JoinTripState> = _state.asStateFlow()

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

