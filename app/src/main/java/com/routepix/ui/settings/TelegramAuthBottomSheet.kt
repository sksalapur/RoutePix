package com.routepix.ui.settings

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.routepix.R
import com.routepix.ui.components.RoutepixLoader

/**
 * A [ModalBottomSheet] that drives the Telegram authentication flow and
 * optional BotFather bot creation.
 *
 * Renders different content depending on [TelegramAuthStep]:
 *  - **Initializing**: A spinner while TDLib boots.
 *  - **WaitingForPhoneNumber**: Text field for the phone number.
 *  - **WaitingForCode**: Text field for the OTP code.
 *  - **WaitingForPassword**: Informational message (2FA — future).
 *  - **Ready**: Success + "Create Bot" button.
 *  - **CreatingBot**: Progress spinner with live status text.
 *  - **BotCreated**: Token display + "Done".
 *  - **Error**: Error message.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelegramAuthBottomSheet(
    uiState: TelegramAuthUiState,
    onSubmitPhoneNumber: (String) -> Unit,
    onSubmitCode: (String) -> Unit,
    onCreateBot: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Header ────────────────────────────────────────────
            Icon(
                painter = painterResource(id = R.drawable.ic_telegram),
                contentDescription = "Telegram",
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Connect Telegram",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stepSubtitle(uiState.step),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))

            // ── Step content (animated crossfade) ─────────────────
            AnimatedContent(
                targetState = uiState.step,
                transitionSpec = {
                    fadeIn(animationSpec = tween(300)) togetherWith
                            fadeOut(animationSpec = tween(200))
                },
                label = "auth_step_transition"
            ) { step ->
                when (step) {
                    TelegramAuthStep.Idle,
                    TelegramAuthStep.Initializing -> {
                        InitializingContent()
                    }

                    TelegramAuthStep.WaitingForPhoneNumber -> {
                        PhoneNumberContent(
                            isLoading = uiState.isLoading,
                            errorMessage = uiState.errorMessage,
                            onSubmit = onSubmitPhoneNumber
                        )
                    }

                    TelegramAuthStep.WaitingForCode -> {
                        CodeContent(
                            isLoading = uiState.isLoading,
                            errorMessage = uiState.errorMessage,
                            codeHint = uiState.codeHint,
                            onSubmit = onSubmitCode
                        )
                    }

                    TelegramAuthStep.WaitingForPassword -> {
                        PasswordContent()
                    }

                    TelegramAuthStep.Ready -> {
                        ReadyContent(
                            onCreateBot = onCreateBot,
                            onSkip = onDismiss
                        )
                    }

                    TelegramAuthStep.CreatingBot -> {
                        CreatingBotContent(
                            statusMessage = uiState.botCreationStatus
                        )
                    }

                    TelegramAuthStep.BotCreated -> {
                        BotCreatedContent(
                            token = uiState.createdBotToken,
                            onDismiss = onDismiss
                        )
                    }

                    TelegramAuthStep.Error -> {
                        ErrorContent(errorMessage = uiState.errorMessage)
                    }
                }
            }
        }
    }
}

// ── Step subtitle ────────────────────────────────────────────────────────

private fun stepSubtitle(step: TelegramAuthStep): String = when (step) {
    TelegramAuthStep.Idle,
    TelegramAuthStep.Initializing -> "Setting up a secure connection…"
    TelegramAuthStep.WaitingForPhoneNumber -> "Enter your Telegram phone number"
    TelegramAuthStep.WaitingForCode -> "Enter the verification code"
    TelegramAuthStep.WaitingForPassword -> "Two-factor authentication required"
    TelegramAuthStep.Ready -> "Successfully connected!"
    TelegramAuthStep.CreatingBot -> "Creating your Telegram bot…"
    TelegramAuthStep.BotCreated -> "Your bot is ready!"
    TelegramAuthStep.Error -> "Something went wrong"
}

// ── Initializing ─────────────────────────────────────────────────────────

@Composable
private fun InitializingContent() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        RoutepixLoader(modifier = Modifier.size(48.dp), speed = 1800)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Connecting to Telegram servers…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}

// ── Phone Number ─────────────────────────────────────────────────────────

@Composable
private fun PhoneNumberContent(
    isLoading: Boolean,
    errorMessage: String?,
    onSubmit: (String) -> Unit
) {
    var phoneNumber by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = phoneNumber,
            onValueChange = { phoneNumber = it },
            label = { Text("Phone Number") },
            placeholder = { Text("+1 234 567 8901") },
            leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Phone,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { if (!isLoading && phoneNumber.isNotBlank()) onSubmit(phoneNumber) }
            ),
            enabled = !isLoading,
            isError = errorMessage != null
        )

        AnimatedVisibility(visible = errorMessage != null) {
            errorMessage?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Include country code (e.g. +1 for US, +91 for India)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { onSubmit(phoneNumber) },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp),
            enabled = !isLoading && phoneNumber.isNotBlank()
        ) {
            if (isLoading) {
                RoutepixLoader(modifier = Modifier.size(24.dp), speed = 1800)
            } else {
                Text("Send Code", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ── OTP Code ─────────────────────────────────────────────────────────────

@Composable
private fun CodeContent(
    isLoading: Boolean,
    errorMessage: String?,
    codeHint: String?,
    onSubmit: (String) -> Unit
) {
    var code by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxWidth()) {
        codeHint?.let {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                )
            ) {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(12.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        OutlinedTextField(
            value = code,
            onValueChange = { code = it },
            label = { Text("Verification Code") },
            placeholder = { Text("12345") },
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { if (!isLoading && code.isNotBlank()) onSubmit(code) }
            ),
            enabled = !isLoading,
            isError = errorMessage != null
        )

        AnimatedVisibility(visible = errorMessage != null) {
            errorMessage?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { onSubmit(code) },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp),
            enabled = !isLoading && code.isNotBlank()
        ) {
            if (isLoading) {
                RoutepixLoader(modifier = Modifier.size(24.dp), speed = 1800)
            } else {
                Text("Verify", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ── 2FA Password (placeholder) ───────────────────────────────────────────

@Composable
private fun PasswordContent() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.Lock,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "Your Telegram account has two-factor authentication enabled. " +
                    "This is not yet supported in the automatic flow. " +
                    "Please use manual configuration instead.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ── Ready (auth complete — offer bot creation) ───────────────────────────

@Composable
private fun ReadyContent(
    onCreateBot: () -> Unit,
    onSkip: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.Check,
            contentDescription = "Success",
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "Telegram connected successfully!",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Now let's create a bot to manage your trip photos. " +
                    "This will be done automatically via @BotFather.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = onCreateBot,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_telegram),
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Create Bot Now", fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onSkip) {
            Text("Skip for now")
        }
    }
}

// ── Creating Bot (in progress) ───────────────────────────────────────────

@Composable
private fun CreatingBotContent(statusMessage: String?) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        RoutepixLoader(modifier = Modifier.size(48.dp), speed = 1800)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Talking to @BotFather…",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        AnimatedContent(
            targetState = statusMessage,
            transitionSpec = {
                fadeIn(animationSpec = tween(200)) togetherWith
                        fadeOut(animationSpec = tween(150))
            },
            label = "bot_status_transition"
        ) { status ->
            Text(
                text = status ?: "Please wait…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

// ── Bot Created (success) ────────────────────────────────────────────────

@Composable
private fun BotCreatedContent(
    token: String?,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.Check,
            contentDescription = "Bot Created",
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "Bot created successfully!",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Your bot token has been auto-filled into the settings. " +
                    "You're all set to start sharing photos!",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        if (token != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "Bot Token",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${token.take(12)}••••••••••••",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Done", fontWeight = FontWeight.Bold)
        }
    }
}

// ── Error ─────────────────────────────────────────────────────────────────

@Composable
private fun ErrorContent(errorMessage: String?) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            errorMessage ?: "An unexpected error occurred.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )
    }
}
