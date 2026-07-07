package com.routepix.ui.settings

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import android.content.Intent
import android.net.Uri
import com.routepix.R
import com.routepix.data.model.User
import kotlinx.coroutines.launch
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import com.routepix.ui.components.GlassTopBar
import com.routepix.ui.components.RoutepixLoader
import com.routepix.data.remote.TelegramClientManager
import org.drinkless.tdlib.TdApi
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onLogout: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var displayName by remember(uiState.user) { mutableStateOf(uiState.user?.displayName ?: "") }
    var botToken by remember(uiState.user) { mutableStateOf(uiState.user?.telegramBotToken ?: "") }
    var chatId by remember(uiState.user) { mutableStateOf(uiState.user?.telegramChatId ?: "") }
    var showInGallery by remember(uiState.user) { mutableStateOf(uiState.user?.showDownloadedPhotosInGallery ?: false) }
    
    var isEditing by remember { mutableStateOf(false) }
    var showTelegramGuide by remember { mutableStateOf(false) }
    var botTokenVisible by remember { mutableStateOf(false) }
    var chatIdVisible by remember { mutableStateOf(false) }
    var showTelegramAuthSheet by remember { mutableStateOf(false) }
    val telegramAuthViewModel: TelegramAuthViewModel = viewModel()
    val telegramAuthState by telegramAuthViewModel.uiState.collectAsState()
    // Direct observation of TDLib auth state — single source of truth for connection status
    val tdlibAuthState by TelegramClientManager.authorizationState.collectAsState()
    val isTelegramConnected = tdlibAuthState is TdApi.AuthorizationStateReady
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Screen Entry Animation
    val entryProgress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        entryProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
        )
    }

    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            snackbarHostState.showSnackbar("Settings saved successfully")
            if (uiState.syncRequired) {
                scope.launch {
                    com.routepix.util.ImageDownloadManager.syncSavedPhotosToGallery(context)
                }
            }
            isEditing = false
            viewModel.resetSaveSuccess()
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
        }
    }

    if (showTelegramGuide) {
        AlertDialog(
            onDismissRequest = { showTelegramGuide = false },
            title = { Text("How to get Telegram Credentials") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    // --- Section: Get Bot Token ---
                    Text(
                        "Get Bot Token",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("1. Open Telegram and search for @BotFather.")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("2. Send /newbot and follow the prompts to create a new bot.")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("3. Copy the Bot Token provided by BotFather.")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("4. Paste the Bot Token into RoutePix.")

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(14.dp))

                    // --- Section: Get Chat ID ---
                    Text(
                        "Get Chat ID",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("5. Open the newly created bot and send any message (e.g. \"Hello\").")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("6. Search for @userinfobot in Telegram.")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("7. Send any message to @userinfobot.")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("8. Copy the numeric Chat ID shown in the reply.")

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(14.dp))

                    // --- Section: Finish ---
                    Text(
                        "Finish",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("9. Paste the Chat ID into RoutePix.")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("10. Save your credentials.")
                }
            },
            confirmButton = {
                TextButton(onClick = { showTelegramGuide = false }) {
                    Text("Got it")
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            GlassTopBar(
                title = { Text("Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { isEditing = !isEditing },
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        if (isEditing) {
                            Text("Cancel", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        } else {
                            Text("Edit Profile", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = entryProgress.value
                    translationY = (1f - entryProgress.value) * 40.dp.toPx()
                }
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Spacer(modifier = Modifier.height(32.dp))

            SettingsCard(title = "Profile Information") {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Display Name") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    readOnly = !isEditing
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            SettingsCard(
                title = "Recommended: Automatic Configuration",
                action = {
                    IconButton(onClick = { showTelegramGuide = true }) {
                        Icon(Icons.Default.Info, contentDescription = "Help")
                    }
                }
            ) {
                Text(
                    "These credentials will be used for all your trips.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                val hasCredentials = botToken.isNotEmpty() && chatId.isNotEmpty()
                
                // --- Button 1: Telegram Connection ---
                Button(
                    onClick = {
                        if (isTelegramConnected) {
                            telegramAuthViewModel.logout()
                        } else {
                            telegramAuthViewModel.startAuth()
                        }
                        showTelegramAuthSheet = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_telegram),
                        contentDescription = "Telegram",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (isTelegramConnected) "Change Linked Telegram Account" else "Connect Telegram",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // --- Button 2: Bot Credentials ---
                Button(
                    onClick = {
                        telegramAuthViewModel.createBot()
                        showTelegramAuthSheet = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = isTelegramConnected
                ) {
                    Text(
                        if (hasCredentials) "Create a new bot and get Credentials" else "Get Bot Credentials",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                // --- OR divider ---
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f))
                    Text(
                        " OR ",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                    HorizontalDivider(modifier = Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(16.dp))

                // --- Advanced: Manual Configuration ---
                Text(
                    "Advanced: Manual Configuration",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(
                    onClick = { showTelegramGuide = true },
                    modifier = Modifier.padding(vertical = 2.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Guide",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "How to get credentials manually?",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))

                OutlinedTextField(
                    value = botToken,
                    onValueChange = { botToken = it },
                    label = { Text("Bot Token") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    readOnly = !isEditing,
                    visualTransformation = if (botTokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { botTokenVisible = !botTokenVisible }) {
                            Icon(
                                imageVector = if (botTokenVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (botTokenVisible) "Hide Bot Token" else "Show Bot Token"
                            )
                        }
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = chatId,
                    onValueChange = { chatId = it },
                    label = { Text("Chat ID") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    readOnly = !isEditing,
                    visualTransformation = if (chatIdVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { chatIdVisible = !chatIdVisible }) {
                            Icon(
                                imageVector = if (chatIdVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (chatIdVisible) "Hide Chat ID" else "Show Chat ID"
                            )
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            SettingsCard(title = "App Preferences") {
                var showSyncDialog by remember { mutableStateOf(false) }
                var showRemoveDialog by remember { mutableStateOf(false) }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Show Saved Photos in Gallery", fontWeight = FontWeight.Bold)
                        Text(
                            "If disabled, downloaded photos will only be visible within RoutePix's internal Saved section.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = showInGallery,
                        onCheckedChange = { newValue ->
                            if (newValue) {
                                showSyncDialog = true
                            } else {
                                showRemoveDialog = true
                            }
                        }
                    )
                }

                if (showSyncDialog) {
                    var isSyncing by remember { mutableStateOf(false) }
                    AlertDialog(
                        onDismissRequest = { if (!isSyncing) showSyncDialog = false },
                        title = { Text("Show in Gallery") },
                        text = {
                            if (isSyncing) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(12.dp))
                                    Text("Syncing photos to gallery…")
                                }
                            } else {
                                Text("Also show already saved photos in the gallery?")
                            }
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    showInGallery = true
                                    viewModel.updateGalleryPreference(true)
                                    isSyncing = true
                                    scope.launch {
                                        com.routepix.util.ImageDownloadManager.syncSavedPhotosToGallery(context)
                                        isSyncing = false
                                        showSyncDialog = false
                                        android.widget.Toast.makeText(context, "Photos synced to gallery", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                },
                                enabled = !isSyncing
                            ) {
                                Text("Yes, sync all")
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    showInGallery = true
                                    viewModel.updateGalleryPreference(true)
                                    showSyncDialog = false
                                },
                                enabled = !isSyncing
                            ) {
                                Text("No, just going forward")
                            }
                        }
                    )
                }

                if (showRemoveDialog) {
                    AlertDialog(
                        onDismissRequest = { showRemoveDialog = false },
                        title = { Text("Hide from Gallery") },
                        text = { Text("Also remove already saved photos from the gallery? (The photos will still be available in RoutePix's Saved section.)") },
                        confirmButton = {
                            TextButton(onClick = {
                                showInGallery = false
                                viewModel.updateGalleryPreference(false)
                                scope.launch {
                                    com.routepix.util.ImageDownloadManager.removeSavedPhotosFromGallery(context)
                                }
                                showRemoveDialog = false
                            }) {
                                Text("Yes, remove from gallery")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = {
                                showInGallery = false
                                viewModel.updateGalleryPreference(false)
                                showRemoveDialog = false
                            }) {
                                Text("No, just going forward")
                            }
                        }
                    )
                }
            }



            if (isEditing) {
                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()
                val buttonScale by animateFloatAsState(
                    targetValue = if (isPressed) 0.96f else 1f,
                    label = "save_scale"
                )

                Button(
                    onClick = { viewModel.saveSettings(displayName, botToken, chatId, showInGallery) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .graphicsLayer {
                            scaleX = buttonScale
                            scaleY = buttonScale
                        },
                    interactionSource = interactionSource,
                    shape = RoundedCornerShape(16.dp),
                    enabled = !uiState.isSaving
                ) {
                    if (uiState.isSaving) {
                        RoutepixLoader(modifier = Modifier.size(24.dp), speed = 1800)
                    } else {
                        Text("Save Changes", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            val logoutInteractionSource = remember { MutableInteractionSource() }
            val isLogoutPressed by logoutInteractionSource.collectIsPressedAsState()
            val logoutScale by animateFloatAsState(
                targetValue = if (isLogoutPressed) 0.96f else 1f,
                label = "logout_scale"
            )

            OutlinedButton(
                onClick = onLogout,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .graphicsLayer {
                        scaleX = logoutScale
                        scaleY = logoutScale
                    },
                interactionSource = logoutInteractionSource,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Default.ExitToApp, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Logout", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
            
            Spacer(modifier = Modifier.weight(1f))

            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Made with ❤️ by the RoutePix Team.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                IconButton(onClick = {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse("https://github.com/sksalapur/RoutePix")
                    }
                    context.startActivity(intent)
                }) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_github),
                        contentDescription = "GitHub Repository",
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    // ── Auto-fill bot token + chat ID when BotFather automation completes, and persist ──
    LaunchedEffect(telegramAuthState.createdBotToken, telegramAuthState.createdChatId) {
        val token = telegramAuthState.createdBotToken
        val id = telegramAuthState.createdChatId
        if (token != null && id != null) {
            botToken = token
            chatId = id
            // Auto-save so credentials survive navigation
            viewModel.saveSettings(displayName, token, id, showInGallery)
        }
    }

    // ── Telegram Auth Bottom Sheet ─────────────────────────────────────
    if (showTelegramAuthSheet) {
        TelegramAuthBottomSheet(
            uiState = telegramAuthState,
            onSubmitPhoneNumber = { telegramAuthViewModel.submitPhoneNumber(it) },
            onSubmitCode = { telegramAuthViewModel.submitCode(it) },
            onCreateBot = { telegramAuthViewModel.createBot() },
            onDismiss = {
                showTelegramAuthSheet = false
                telegramAuthViewModel.resetState()
            }
        )
    }
}

@Composable
private fun SettingsCard(
    title: String,
    action: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                action?.invoke()
            }
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

