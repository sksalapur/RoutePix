package com.routepix.data.remote

import android.content.Context
import android.util.Log
import com.routepix.BuildConfig
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Singleton manager for the TDLib client.
 *
 * Responsibilities:
 *  - Initialises the native TDLib [Client] exactly once.
 *  - Automatically responds to [TdApi.AuthorizationStateWaitTdlibParameters] using
 *    the API credentials injected via [BuildConfig] (read from `local.properties`).
 *  - Exposes the current [TdApi.AuthorizationState] as a [StateFlow] so that
 *    ViewModels and UI layers can observe changes reactively.
 *  - Provides a coroutine-friendly [send] wrapper around the callback-based
 *    [Client.send].
 *
 * Usage:
 * ```
 * // In Application.onCreate() or when the user triggers "Connect Telegram":
 * TelegramClientManager.initialize(applicationContext)
 *
 * // Observe state in a ViewModel:
 * TelegramClientManager.authorizationState.collect { state -> ... }
 * ```
 */
object TelegramClientManager {

    private const val TAG = "TelegramClientManager"

    // ── Exposed state ────────────────────────────────────────────────────
    private val _authorizationState = MutableStateFlow<TdApi.AuthorizationState?>(null)

    /** Current TDLib authorization state. Emits `null` until [initialize] is called. */
    val authorizationState: StateFlow<TdApi.AuthorizationState?> =
        _authorizationState.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<TdApi.Message>(extraBufferCapacity = 64)

    /** All incoming TDLib messages. Consumers (e.g. BotFather automation) filter by chat/sender. */
    val incomingMessages: SharedFlow<TdApi.Message> = _incomingMessages.asSharedFlow()

    // ── Internal state ───────────────────────────────────────────────────
    private var client: Client? = null
    private var databaseDirectory: String = ""
    private var appContext: Context? = null

    /** Whether [initialize] has already been called. */
    val isInitialized: Boolean get() = client != null

    // ── Initialization ───────────────────────────────────────────────────

    /**
     * Creates the TDLib [Client] and begins listening for authorization-state
     * updates. Safe to call multiple times — subsequent calls are no-ops.
     *
     * @param context Application context, used to derive the database directory
     *                (`<filesDir>/tdlib`).
     */
    @Synchronized
    fun initialize(context: Context) {
        appContext = context.applicationContext
        if (client != null) {
            Log.d(TAG, "Already initialised — skipping.")
            return
        }

        databaseDirectory = File(context.filesDir, "tdlib").absolutePath

        // Set TDLib log verbosity (1 = errors only; raise for debugging).
        Client.execute(TdApi.SetLogVerbosityLevel(1))

        createClient()
    }

    /**
     * Creates a fresh TDLib [Client] instance.
     * Called during initial [initialize] and after [TdApi.AuthorizationStateClosed]
     * to restart the auth flow.
     */
    @Synchronized
    private fun createClient() {
        client = Client.create(
            /* updateHandler  = */ ::handleUpdate,
            /* updateExceptionHandler = */ { e ->
                Log.e(TAG, "TDLib update exception", e)
            },
            /* defaultExceptionHandler = */ { e ->
                Log.e(TAG, "TDLib default exception", e)
            }
        )

        Log.i(TAG, "TDLib client created. Database dir: $databaseDirectory")
    }

    // ── Sending requests ─────────────────────────────────────────────────

    /**
     * Sends a [TdApi.Function] to TDLib and suspends until a result is
     * available.
     *
     * @return The [TdApi.Object] result.
     * @throws TdException if TDLib returns a [TdApi.Error].
     * @throws IllegalStateException if [initialize] has not been called.
     */
    suspend fun send(function: TdApi.Function<*>): TdApi.Object {
        val c = client ?: error("TelegramClientManager has not been initialised. Call initialize() first.")
        return suspendCancellableCoroutine { cont ->
            c.send(function) { result ->
                when (result) {
                    is TdApi.Error -> cont.resumeWithException(
                        TdException(result.code, result.message)
                    )
                    else -> cont.resume(result)
                }
            }
        }
    }

    // ── Update handler ───────────────────────────────────────────────────

    private fun handleUpdate(update: TdApi.Object) {
        when (update) {
            is TdApi.UpdateAuthorizationState -> {
                val newState = update.authorizationState
                Log.d(TAG, "Auth state → ${newState.javaClass.simpleName}")
                _authorizationState.value = newState
                onAuthorizationStateChanged(newState)
            }

            is TdApi.UpdateNewMessage -> {
                _incomingMessages.tryEmit(update.message)
            }
        }
    }

    /**
     * Reacts to authorization-state changes that can be handled automatically
     * (i.e. without user interaction). Interactive states like
     * [TdApi.AuthorizationStateWaitPhoneNumber] or
     * [TdApi.AuthorizationStateWaitCode] are left for the ViewModel / UI.
     */
    private fun onAuthorizationStateChanged(state: TdApi.AuthorizationState) {
        when (state) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> {
                val params = TdApi.SetTdlibParameters().apply {
                    apiId = BuildConfig.TG_API_ID
                    apiHash = BuildConfig.TG_API_HASH
                    databaseDirectory = this@TelegramClientManager.databaseDirectory
                    useMessageDatabase = true
                    useSecretChats = false
                    systemLanguageCode = "en"
                    deviceModel = "Android"
                    applicationVersion = BuildConfig.VERSION_NAME
                }
                client?.send(params) { result ->
                    if (result is TdApi.Error) {
                        Log.e(TAG, "SetTdlibParameters failed: ${result.code} ${result.message}")
                    }
                }
            }

            is TdApi.AuthorizationStateReady -> {
                Log.i(TAG, "TDLib authorised and ready.")
            }

            is TdApi.AuthorizationStateClosed -> {
                Log.i(TAG, "TDLib client closed. Re-creating client for fresh auth flow.")
                client = null
                // Immediately spin up a new client so the auth state
                // transitions back to WaitTdlibParameters -> WaitPhoneNumber.
                createClient()
            }

            // States that require user input — surfaced via authorizationState Flow.
            is TdApi.AuthorizationStateWaitPhoneNumber,
            is TdApi.AuthorizationStateWaitCode,
            is TdApi.AuthorizationStateWaitPassword -> {
                Log.d(TAG, "Awaiting user input: ${state.javaClass.simpleName}")
            }

            else -> {
                Log.d(TAG, "Unhandled auth state: ${state.javaClass.simpleName}")
            }
        }
    }

    // ── Exception type ───────────────────────────────────────────────────

    /**
     * Exception wrapping a [TdApi.Error] returned by TDLib.
     */
    class TdException(val code: Int, override val message: String) :
        Exception("TDLib error $code: $message")
}
