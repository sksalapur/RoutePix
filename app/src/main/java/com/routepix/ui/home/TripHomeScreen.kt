@file:OptIn(ExperimentalMaterial3Api::class)

package com.routepix.ui.home

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.routepix.data.model.Trip
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.firebase.auth.FirebaseAuth
import com.routepix.ui.components.GlassTopBar
import com.routepix.ui.components.RoutepixLoader
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer

@Composable
fun TripHomeScreen(
    onCreateTrip: () -> Unit,
    onJoinTrip: () -> Unit,
    onSettingsClick: () -> Unit,
    onTripClick: (Trip) -> Unit,
    onViewSavedPhotos: () -> Unit,
    tripHomeViewModel: TripHomeViewModel = viewModel()
) {
    val uiState by tripHomeViewModel.uiState.collectAsState()
    var tripToRename by remember { mutableStateOf<Trip?>(null) }
    var tripToShowMembers by remember { mutableStateOf<Trip?>(null) }
    var tripToExit by remember { mutableStateOf<Trip?>(null) }
    val scope = rememberCoroutineScope()
    val entryProgress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        entryProgress.animateTo(1f, animationSpec = tween(600, easing = FastOutSlowInEasing))
    }

    Scaffold(
        topBar = {
            GlassTopBar(
                title = {
                    Text("RoutePix", fontWeight = FontWeight.ExtraBold)
                },
                navigationIcon = {},
                actions = {
                    Surface(
                        onClick = onViewSavedPhotos,
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier.height(36.dp).padding(end = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = "Saved Photos", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Saved", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onTertiaryContainer, fontWeight = FontWeight.Bold)
                        }
                    }
                    
                    val photoUrl = FirebaseAuth.getInstance().currentUser?.photoUrl
                    IconButton(onClick = onSettingsClick) {
                        if (photoUrl != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(photoUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Profile",
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = "Profile",
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                                    .padding(6.dp)
                            )
                        }
                    }
                },
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Hello, ${uiState.displayName}!",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Ready for a new adventure?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(20.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ActionCard(
                        title = "Create Trip",
                        icon = Icons.Default.Add,
                        onClick = onCreateTrip,
                        modifier = Modifier.weight(1f),
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                    ActionCard(
                        title = "Join Trip",
                        icon = Icons.Default.Share,
                        onClick = onJoinTrip,
                        modifier = Modifier.weight(1f),
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "My Trips",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    if (uiState.trips.isNotEmpty()) {
                        Text(
                            text = "${uiState.trips.size} total",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (uiState.isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        RoutepixLoader(modifier = Modifier.size(48.dp), speed = 1800)
                    }
                }
            }

            if (!uiState.isLoading && uiState.trips.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No trips yet. Create or join one!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            items(uiState.trips, key = { it.tripId }) { trip ->
                TripListItem(
                    trip = trip,
                    currentUid = tripHomeViewModel.getCurrentUid() ?: "",
                    onClick = { onTripClick(trip) },
                    onEditClick = { tripToRename = trip },
                    onMembersClick = { tripToShowMembers = trip },
                    onExitClick = { tripToExit = trip }
                )
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    if (tripToExit != null) {
        AlertDialog(
            onDismissRequest = { tripToExit = null },
            title = { Text("Exit Trip") },
            text = { Text("Are you sure you want to exit '${tripToExit!!.name}'?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val tid = tripToExit!!.tripId
                        scope.launch {
                            tripHomeViewModel.exitTrip(tid)
                            tripToExit = null
                        }
                    }
                ) {
                    Text("Exit", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { tripToExit = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (tripToRename != null) {
        var newName by remember { mutableStateOf(tripToRename!!.name) }
        AlertDialog(
            onDismissRequest = { tripToRename = null },
            title = { Text("Rename Trip") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val trip = tripToRename!!
                    tripToRename = null
                    scope.launch {
                        tripHomeViewModel.renameTrip(trip.tripId, newName)
                    }
                }) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { tripToRename = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (tripToShowMembers != null) {
        val trip = tripToShowMembers!!
        var memberNames by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
        LaunchedEffect(trip) {
            memberNames = tripHomeViewModel.resolveMemberNames(trip.memberUids)
        }

        AlertDialog(
            onDismissRequest = { tripToShowMembers = null },
            title = { Text("Trip Members") },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(trip.memberUids) { uid ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = memberNames[uid] ?: "Loading...")
                            if (trip.adminUid == tripHomeViewModel.getCurrentUid() && uid != trip.adminUid) {
                                TextButton(onClick = {
                                    scope.launch {
                                        tripHomeViewModel.removeMember(trip.tripId, uid)

                                        tripToShowMembers = tripToShowMembers?.copy(
                                            memberUids = tripToShowMembers!!.memberUids - uid
                                        )
                                    }
                                }) {
                                    Text("Remove", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { tripToShowMembers = null }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun ActionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (isPressed) 0.95f else 1f, label = "card_scale")

    Card(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = modifier.height(100.dp).graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun TripListItem(
    trip: Trip,
    currentUid: String,
    onClick: () -> Unit,
    onEditClick: () -> Unit,
    onMembersClick: () -> Unit,
    onExitClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (isPressed) 0.96f else 1f, label = "card_scale")
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(24.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.foundation.LocalIndication.current,
                onClick = onClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = trip.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                
                Row {
                    IconButton(onClick = {
                        val shareText = "Hey! Join the trip ${trip.name} on RoutePix!\n\nDownload the app: https://github.com/sksalapur/RoutePix/releases/latest\n\nInvite code: ${trip.inviteCode}"
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, shareText)
                            type = "text/plain"
                        }
                        val shareIntent = Intent.createChooser(sendIntent, null)
                        context.startActivity(shareIntent)
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Share", tint = MaterialTheme.colorScheme.primary)
                    }
                    if (trip.adminUid == currentUid) {
                        IconButton(onClick = onEditClick) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    IconButton(onClick = onMembersClick) {
                        Icon(Icons.Default.Person, contentDescription = "Members", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onExitClick) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Exit Trip", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row {
                Text(
                    text = "Code: ${trip.inviteCode}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "${trip.memberUids.size} members",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

