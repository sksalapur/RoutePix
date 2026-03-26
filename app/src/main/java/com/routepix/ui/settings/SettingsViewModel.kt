package com.routepix.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.routepix.data.model.User
import com.routepix.data.repository.UserRepository
import com.routepix.data.repository.TripRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class SettingsUiState(
    val user: User? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val error: String? = null
)

class SettingsViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val userRepository = UserRepository(firestore, auth)
    private val tripRepository = TripRepository(firestore, auth)

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadUser()
    }

    private fun loadUser() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val user = userRepository.getCurrentUser()
            _uiState.value = _uiState.value.copy(user = user, isLoading = false)
        }
    }

    fun saveSettings(displayName: String, botToken: String, chatId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, saveSuccess = false, error = null)
            try {
                userRepository.updateProfile(
                    displayName = displayName,
                    botToken = botToken,
                    chatId = chatId
                )

                tripRepository.syncCredentialsToTrips(botToken, chatId)
                _uiState.value = _uiState.value.copy(saveSuccess = true)
                loadUser()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.localizedMessage)
            } finally {
                _uiState.value = _uiState.value.copy(isSaving = false)
            }
        }
    }

    fun resetSaveSuccess() {
        _uiState.value = _uiState.value.copy(saveSuccess = false)
    }

    fun signOut() {
        auth.signOut()
    }
}

