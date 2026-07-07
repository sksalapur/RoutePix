package com.routepix.data.remote

import android.util.Log
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.drinkless.tdlib.TdApi

/**
 * Automates a conversation with @BotFather to create a new Telegram bot
 * and extract the resulting HTTP API token.
 *
 * Flow:
 *  1. Open private chat with BotFather (user ID 93372553).
 *  2. Send `/newbot`.
 *  3. Reply with the bot display name ("RoutePix Trip Album").
 *  4. Reply with a unique username (`routepix_<timestamp>_bot`).
 *  5. Extract the API token from BotFather's success message.
 *
 * Each reply from BotFather is awaited with a configurable timeout
 * (default 10 seconds) to avoid hanging indefinitely.
 */
object BotFatherAutomation {

    private const val TAG = "BotFatherAutomation"
    private const val BOTFATHER_USER_ID = 93372553L
    private const val REPLY_TIMEOUT_MS = 10_000L

    /** Regex to extract the HTTP API token from BotFather's success message. */
    private val TOKEN_REGEX = Regex("[0-9]{8,10}:[a-zA-Z0-9_-]{35}")

    /** Result of a successful bot creation. */
    data class BotCreationResult(val botToken: String, val chatId: String)

    /**
     * Runs the full BotFather conversation and returns the extracted bot token
     * along with the authenticated user's chat ID.
     *
     * @param onStatus Callback invoked with human-readable progress messages
     *                 for the UI to display.
     * @return [BotCreationResult] containing bot token and user chat ID.
     * @throws BotCreationException if any step fails or times out.
     */
    suspend fun createBot(onStatus: (String) -> Unit): BotCreationResult {
        try {
            // ── Step 1: Open chat with BotFather ─────────────────────
            onStatus("Opening chat with BotFather…")
            Log.d(TAG, "Searching for @BotFather public chat")
            val chat = TelegramClientManager.send(
                TdApi.SearchPublicChat("BotFather")
            ) as TdApi.Chat
            val chatId = chat.id
            Log.d(TAG, "BotFather chatId = $chatId")

            // Drain any pending welcome message BotFather may have sent
            // when we opened the chat, so it doesn't get captured as
            // the /newbot reply.
            kotlinx.coroutines.delay(1500)
            Log.d(TAG, "Drained welcome messages, proceeding with /newbot")

            // ── Step 2: Send /newbot and await name prompt ───────────
            onStatus("Sending /newbot command…")
            val namePrompt = sendAndAwaitReply(chatId, "/newbot")
            Log.d(TAG, "BotFather name prompt: ${namePrompt.take(80)}")

            // ── Step 3: Send bot name and await username prompt ──────
            onStatus("Setting bot name…")
            val usernamePrompt = sendAndAwaitReply(chatId, "RoutePix Trip Album")
            Log.d(TAG, "BotFather username prompt: ${usernamePrompt.take(80)}")

            // ── Step 4: Send unique username and await success ───────
            onStatus("Setting bot username…")
            val username = "routepix_${System.currentTimeMillis() / 1000}_bot"
            Log.d(TAG, "Sending username: $username")
            val successMessage = sendAndAwaitReply(chatId, username)
            Log.d(TAG, "BotFather response: $successMessage")

            // ── Step 5: Extract token ────────────────────────────────
            onStatus("Extracting bot token…")
            val token = TOKEN_REGEX.find(successMessage)?.value
                ?: throw BotCreationException(
                    "BotFather responded:\n\n\"${successMessage.take(300)}\"\n\n" +
                            "If you've created too many bots, open Telegram and send " +
                            "/deletebot to @BotFather to remove unused ones."
                )

            Log.i(TAG, "Bot created successfully. Token starts with: ${token.take(10)}…")

            // ── Step 6: Get current user's chat ID ───────────────────
            onStatus("Fetching your chat ID…")
            val me = TelegramClientManager.send(TdApi.GetMe()) as TdApi.User
            val userChatId = me.id.toString()
            Log.i(TAG, "User chat ID: $userChatId")

            // ── Step 7: Send /start to the newly created bot ─────────────
            onStatus("Initializing your new bot…")
            Log.d(TAG, "Searching for newly created bot public chat: $username")
            val newBotChat = TelegramClientManager.send(
                TdApi.SearchPublicChat(username)
            ) as TdApi.Chat
            Log.d(TAG, "Sending /start to new bot (chatId=${newBotChat.id})")
            sendTextMessage(newBotChat.id, "/start")

            onStatus("Bot created successfully!")
            return BotCreationResult(botToken = token, chatId = userChatId)

        } catch (e: BotCreationException) {
            throw e
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            throw BotCreationException(
                "BotFather did not reply within ${REPLY_TIMEOUT_MS / 1000} seconds. " +
                        "Please check your internet connection and try again."
            )
        } catch (e: TelegramClientManager.TdException) {
            throw BotCreationException("TDLib error: ${e.message}")
        } catch (e: Exception) {
            throw BotCreationException("Unexpected error: ${e.localizedMessage ?: e.toString()}")
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /**
     * Sends a text message to [chatId] and waits for BotFather's next reply.
     *
     * The collector starts **before** the message is sent to prevent race
     * conditions — if BotFather replies before we subscribe, the message
     * would be lost with a replay-0 SharedFlow.
     */
    private suspend fun sendAndAwaitReply(chatId: Long, text: String): String {
        return coroutineScope {
            // Start listening BEFORE sending to avoid missing the reply
            val replyDeferred = async {
                withTimeout(REPLY_TIMEOUT_MS) {
                    TelegramClientManager.incomingMessages
                        .filter { it.chatId == chatId && !it.isOutgoing }
                        .mapNotNull { msg ->
                            (msg.content as? TdApi.MessageText)?.text?.text
                        }
                        .first()
                }
            }

            // Yield to let the async collector subscribe to the SharedFlow
            yield()

            // Now send the message
            sendTextMessage(chatId, text)

            // Await BotFather's reply (or timeout)
            replyDeferred.await()
        }
    }

    /**
     * Sends a plain-text message to the given [chatId].
     */
    private suspend fun sendTextMessage(chatId: Long, text: String) {
        TelegramClientManager.send(
            TdApi.SendMessage().apply {
                this.chatId = chatId
                this.inputMessageContent = TdApi.InputMessageText().apply {
                    this.text = TdApi.FormattedText().apply {
                        this.text = text
                    }
                }
            }
        )
    }

    // ── Exception ────────────────────────────────────────────────────────

    /**
     * Exception thrown when bot creation fails at any step.
     */
    class BotCreationException(override val message: String) : Exception(message)
}
