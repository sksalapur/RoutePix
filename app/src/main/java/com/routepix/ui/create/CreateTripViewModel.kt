package com.routepix.ui.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.routepix.data.repository.TripRepository
import kotlinx.coroutines.flow.MutableStateFlow
import com.routepix.data.repository.UserRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

sealed class CreateTripState {
    data object Idle : CreateTripState()
    data object Loading : CreateTripState()
    data class Success(val tripId: String) : CreateTripState()
    data class Error(val message: String) : CreateTripState()
}

class CreateTripViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val repository = TripRepository(firestore, auth)
    private val userRepository = UserRepository(firestore, auth)

    private val _state = MutableStateFlow<CreateTripState>(CreateTripState.Idle)
    val state: StateFlow<CreateTripState> = _state.asStateFlow()

    private val _hasCredentials = MutableStateFlow(true)
    val hasCredentials: StateFlow<Boolean> = _hasCredentials.asStateFlow()

    init {
        checkCredentials()
    }

    private fun checkCredentials() {
        viewModelScope.launch {
            val user = userRepository.getCurrentUser()
            _hasCredentials.value = !user?.telegramBotToken.isNullOrBlank() && !user?.telegramChatId.isNullOrBlank()
        }
    }

    fun createTrip(name: String) {
        _state.value = CreateTripState.Loading
        viewModelScope.launch {
            try {
                val tripId = repository.createTrip(name)
                _state.value = CreateTripState.Success(tripId)
            } catch (e: Exception) {
                _state.value = CreateTripState.Error(e.message ?: "Failed to create trip")
            }
        }
    }

    fun clearError() {
        _state.value = CreateTripState.Idle
    }
}

