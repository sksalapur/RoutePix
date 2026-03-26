package com.routepix.ui.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import kotlinx.coroutines.launch

@Composable
fun PermissionsScreen(
    onAllGranted: () -> Unit
) {
    val context = LocalContext.current
    val powerManager = remember { context.getSystemService(Context.POWER_SERVICE) as PowerManager }
    
    var hasNotificationPermission by remember { 
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }
    
    var hasStoragePermission by remember {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        mutableStateOf(ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED)
    }
    
    var isIgnoringBatteryOptimizations by remember {
        mutableStateOf(powerManager.isIgnoringBatteryOptimizations(context.packageName))
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasNotificationPermission = granted }

    val storageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasStoragePermission = results.values.all { it }
    }

    LaunchedEffect(hasNotificationPermission, hasStoragePermission, isIgnoringBatteryOptimizations) {
        if (hasNotificationPermission && hasStoragePermission && isIgnoringBatteryOptimizations) {
            onAllGranted()
        }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    // Refresh battery state when returning to app
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                 isIgnoringBatteryOptimizations = powerManager.isIgnoringBatteryOptimizations(context.packageName)
                 
                 hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                     ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                 } else true
                 
                 val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                     Manifest.permission.READ_MEDIA_IMAGES
                 } else {
                     Manifest.permission.READ_EXTERNAL_STORAGE
                 }
                 hasStoragePermission = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
                 
                 scope.launch {
                     kotlinx.coroutines.delay(800)
                     isIgnoringBatteryOptimizations = powerManager.isIgnoringBatteryOptimizations(context.packageName)
                 }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = "Welcome to RoutePix",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "To ensure your trip photos upload reliably in the background, we need a few permissions.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(48.dp))

            PermissionToggle(
                title = "Notifications",
                description = "Shows upload progress and alerts you when a new app version is available.",
                icon = Icons.Default.Notifications,
                checked = hasNotificationPermission,
                onToggle = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            )

            PermissionToggle(
                title = "Photos & Videos",
                description = "Allows RoutePix to read and upload your spectacular trip memories.",
                icon = Icons.Default.PhotoLibrary,
                checked = hasStoragePermission,
                onToggle = {
                    val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
                    } else {
                        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                    }
                    storageLauncher.launch(perms)
                }
            )

            PermissionToggle(
                title = "Background Activity",
                description = "Disables battery optimization so uploads don't stall when you lock your screen.",
                icon = Icons.Default.BatteryFull,
                checked = isIgnoringBatteryOptimizations,
                onToggle = {
                    if (!isIgnoringBatteryOptimizations) {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    }
                }
            )

            Spacer(modifier = Modifier.weight(1f))
            
            Button(
                onClick = onAllGranted,
                modifier = Modifier.fillMaxWidth(),
                enabled = hasStoragePermission // Mandatory
            ) {
                Text(if (hasStoragePermission && hasNotificationPermission && isIgnoringBatteryOptimizations) "Get Started" else "Continue with basic access")
            }
        }
    }
}

@Composable
private fun PermissionToggle(
    title: String,
    description: String,
    icon: ImageVector,
    checked: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        onClick = onToggle,
        shape = MaterialTheme.shapes.large,
        color = if (checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.padding(bottom = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = { onToggle() }
            )
        }
    }
}
