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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
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
                Column {
                    Text("1. Search for @BotFather in Telegram.")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("2. Send /newbot and follow prompts to get your Bot Token.")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("3. Search for @userinfobot and send any message to get your Chat ID.")
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
                title = "Telegram Bot Configuration",
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
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = botToken,
                    onValueChange = { botToken = it },
                    label = { Text("Bot Token") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    readOnly = !isEditing,
                    trailingIcon = { Icon(Icons.Default.Lock, null) }
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = chatId,
                    onValueChange = { chatId = it },
                    label = { Text("Chat ID") },
                    modifier = Modifier.fillMaxWidth(),
                    readOnly = !isEditing,
                    shape = RoundedCornerShape(12.dp)
                )


            }

            Spacer(modifier = Modifier.height(16.dp))

            SettingsCard(title = "App Preferences") {
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
                        onCheckedChange = { showInGallery = it },
                        enabled = isEditing
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

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

