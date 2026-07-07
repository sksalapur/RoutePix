package com.routepix.ui.settings

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.routepix.data.remote.BotFatherAutomation
import com.routepix.data.remote.TelegramClientManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi

// ── UI State ─────────────────────────────────────────────────────────────

/**
 * Represents each step of the Telegram authentication flow visible to the UI.
 */
enum class TelegramAuthStep {
    /** Initial idle state — nothing in progress. */
    Idle,
    /** TDLib is initialising / waiting for parameters (automatic). */
    Initializing,
    /** Waiting for the user to enter their phone number. */
    WaitingForPhoneNumber,
    /** Phone number submitted, waiting for the OTP code. */
    WaitingForCode,
    /** OTP code submitted, waiting for 2FA password. */
    WaitingForPassword,
    /** Fully authenticated with Telegram. */
    Ready,
    /** Bot creation in progress via BotFather automation. */
    CreatingBot,
    /** Bot created successfully — token is available. */
    BotCreated,
    /** An error occurred at some point. */
    Error
}

data class TelegramAuthUiState(
    val step: TelegramAuthStep = TelegramAuthStep.Idle,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    /** Informational hint provided by TDLib for the code type (e.g. "SMS", "Telegram app"). */
    val codeHint: String? = null,
    /** Human-readable progress message during bot creation. */
    val botCreationStatus: String? = null,
    /** The extracted bot HTTP API token after successful creation. */
    val createdBotToken: String? = null,
    /** The authenticated user's Telegram chat ID. */
    val createdChatId: String? = null
)

// ── ViewModel ────────────────────────────────────────────────────────────

/**
 * Drives the Telegram authentication bottom-sheet UI.
 *
 * Observes [TelegramClientManager.authorizationState] and maps TDLib states
 * to a simplified [TelegramAuthUiState] that the Compose sheet can render.
 *
 * Provides [submitPhoneNumber] and [submitCode] to advance the auth flow,
 * and [createBot] to automate the BotFather conversation.
 */
class TelegramAuthViewModel(application: Application) : AndroidViewModel(application) {

    private companion object {
        const val TAG = "TelegramAuthVM"
    }

    private val _uiState = MutableStateFlow(TelegramAuthUiState())
    val uiState: StateFlow<TelegramAuthUiState> = _uiState.asStateFlow()

    init {
        // Initialize TDLib eagerly so we pick up an existing session.
        TelegramClientManager.initialize(getApplication())

        // Observe TDLib authorization state changes and translate to UI state.
        viewModelScope.launch {
            TelegramClientManager.authorizationState.collect { authState ->
                Log.d(TAG, "Collected auth state: ${authState?.javaClass?.simpleName}")
                mapAuthState(authState)
            }
        }
    }

    // ── Public actions ────────────────────────────────────────────────

    /**
     * Initialise TDLib (idempotent) and begin the auth flow.
     * Called when the user taps "Connect Telegram Automatically".
     */
    fun startAuth() {
        TelegramClientManager.initialize(getApplication())
        
        // Map whatever state TDLib is currently in so the UI reflects it
        // immediately (e.g. WaitPhoneNumber after a previous logout).
        val currentState = TelegramClientManager.authorizationState.value
        if (currentState != null) {
            mapAuthState(currentState)
        } else {
            _uiState.value = _uiState.value.copy(
                step = TelegramAuthStep.Initializing,
                isLoading = true,
                errorMessage = null
            )
        }
    }

    /**
     * Logs out the current Telegram account.
     */
    fun logout() {
        _uiState.value = _uiState.value.copy(
            step = TelegramAuthStep.Initializing,
            isLoading = true,
            errorMessage = null
        )
        viewModelScope.launch {
            try {
                TelegramClientManager.send(TdApi.LogOut())
            } catch (e: Exception) {
                Log.e(TAG, "Logout failed", e)
                _uiState.value = _uiState.value.copy(
                    step = TelegramAuthStep.Error,
                    isLoading = false,
                    errorMessage = e.localizedMessage ?: "Logout failed"
                )
            }
        }
    }

    /**
     * Submit the user's phone number (with country code, e.g. "+1234567890").
     */
    fun submitPhoneNumber(phoneNumber: String) {
        if (phoneNumber.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Phone number cannot be empty.")
            return
        }
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            try {
                val settings = TdApi.PhoneNumberAuthenticationSettings().apply {
                    allowFlashCall = false
                    allowMissedCall = false
                    isCurrentPhoneNumber = false
                }
                TelegramClientManager.send(
                    TdApi.SetAuthenticationPhoneNumber(phoneNumber, settings)
                )
                // State change will arrive via the authorizationState collector.
            } catch (e: TelegramClientManager.TdException) {
                Log.e(TAG, "submitPhoneNumber failed", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message
                )
            } catch (e: Exception) {
                Log.e(TAG, "submitPhoneNumber failed", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.localizedMessage ?: "Unknown error"
                )
            }
        }
    }

    /**
     * Submit the OTP code received by the user.
     */
    fun submitCode(code: String) {
        if (code.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Code cannot be empty.")
            return
        }
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            try {
                TelegramClientManager.send(TdApi.CheckAuthenticationCode(code))
            } catch (e: TelegramClientManager.TdException) {
                Log.e(TAG, "submitCode failed", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message
                )
            } catch (e: Exception) {
                Log.e(TAG, "submitCode failed", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.localizedMessage ?: "Unknown error"
                )
            }
        }
    }

    /**
     * Automate a conversation with @BotFather to create a new bot and
     * extract its HTTP API token.
     *
     * Should only be called after [TelegramAuthStep.Ready].
     */
    fun createBot() {
        _uiState.value = _uiState.value.copy(
            step = TelegramAuthStep.CreatingBot,
            isLoading = true,
            errorMessage = null,
            botCreationStatus = "Starting bot creation…"
        )
        viewModelScope.launch {
            try {
                val result = BotFatherAutomation.createBot { status ->
                    _uiState.value = _uiState.value.copy(botCreationStatus = status)
                }
                _uiState.value = _uiState.value.copy(
                    step = TelegramAuthStep.BotCreated,
                    isLoading = false,
                    createdBotToken = result.botToken,
                    createdChatId = result.chatId,
                    botCreationStatus = "Bot created successfully!"
                )
            } catch (e: BotFatherAutomation.BotCreationException) {
                Log.e(TAG, "Bot creation failed", e)
                _uiState.value = _uiState.value.copy(
                    step = TelegramAuthStep.Error,
                    isLoading = false,
                    errorMessage = e.message
                )
            } catch (e: Exception) {
                Log.e(TAG, "Bot creation failed", e)
                _uiState.value = _uiState.value.copy(
                    step = TelegramAuthStep.Error,
                    isLoading = false,
                    errorMessage = e.localizedMessage ?: "Unknown error during bot creation"
                )
            }
        }
    }

    /**
     * Reset the UI state back to idle (e.g. when dismissing the bottom sheet).
     */
    fun resetState() {
        _uiState.value = TelegramAuthUiState()
    }

    // ── Internal mapping ─────────────────────────────────────────────

    private fun mapAuthState(authState: TdApi.AuthorizationState?) {
        // Don't overwrite CreatingBot/BotCreated steps with auth state changes.
        val currentStep = _uiState.value.step
        if (currentStep == TelegramAuthStep.CreatingBot ||
            currentStep == TelegramAuthStep.BotCreated
        ) return

        when (authState) {
            null -> {
                // TDLib not yet initialised — keep current state.
            }

            is TdApi.AuthorizationStateWaitTdlibParameters -> {
                // Handled automatically by TelegramClientManager.
                _uiState.value = _uiState.value.copy(
                    step = TelegramAuthStep.Initializing,
                    isLoading = true
                )
            }

            is TdApi.AuthorizationStateWaitPhoneNumber -> {
                _uiState.value = _uiState.value.copy(
                    step = TelegramAuthStep.WaitingForPhoneNumber,
                    isLoading = false,
                    errorMessage = null
                )
            }

            is TdApi.AuthorizationStateWaitCode -> {
                val hint = authState.codeInfo?.type?.let { type ->
                    when (type) {
                        is TdApi.AuthenticationCodeTypeTelegramMessage ->
                            "Code sent to your Telegram app (${type.length} digits)"
                        is TdApi.AuthenticationCodeTypeSms ->
                            "SMS code sent (${type.length} digits)"
                        is TdApi.AuthenticationCodeTypeFragment ->
                            "Code sent via Fragment (${type.length} digits)"
                        is TdApi.AuthenticationCodeTypeCall ->
                            "You will receive a phone call (${type.length} digits)"
                        else -> null
                    }
                }
                _uiState.value = _uiState.value.copy(
                    step = TelegramAuthStep.WaitingForCode,
                    isLoading = false,
                    errorMessage = null,
                    codeHint = hint
                )
            }

            is TdApi.AuthorizationStateWaitPassword -> {
                _uiState.value = _uiState.value.copy(
                    step = TelegramAuthStep.WaitingForPassword,
                    isLoading = false,
                    errorMessage = null
                )
            }

            is TdApi.AuthorizationStateReady -> {
                _uiState.value = _uiState.value.copy(
                    step = TelegramAuthStep.Ready,
                    isLoading = false,
                    errorMessage = null
                )
            }

            is TdApi.AuthorizationStateClosed,
            is TdApi.AuthorizationStateClosing -> {
                _uiState.value = TelegramAuthUiState()
            }

            else -> {
                Log.d(TAG, "Unhandled auth state in VM: ${authState.javaClass.simpleName}")
            }
        }
    }
}
