package com.routepix.ui.auth

import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow


sealed class AuthState {
    data object Idle : AuthState()
    data object Loading : AuthState()
    data class Success(val uid: String) : AuthState()
    data class Error(val message: String) : AuthState()
}

class AuthViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    init {

        auth.currentUser?.let {
            _authState.value = AuthState.Success(it.uid)
        }
    }

    
    fun signInWithGoogle(idToken: String) {
        _authState.value = AuthState.Loading
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnSuccessListener { result ->
                val user = result.user
                val uid = user?.uid ?: ""

                if (uid.isNotEmpty()) {
                    val userData = hashMapOf(
                        "uid" to uid,
                        "displayName" to (user?.displayName ?: "Traveler")
                    )
                    firestore.collection("users").document(uid).set(userData)
                }
                _authState.value = AuthState.Success(uid)
            }
            .addOnFailureListener { e ->
                _authState.value = AuthState.Error("Authentication failed: ${e.message}")
            }
    }

    fun clearError() {
        _authState.value = AuthState.Idle
    }
}

