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
import androidx.compose.ui.graphics.Brush
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.firebase.auth.FirebaseAuth
import com.routepix.data.cache.ThumbnailCache
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
    
    val downloadProgressMap by tripHomeViewModel.downloadProgress.collectAsState()
    val coverPhotoFileIds by tripHomeViewModel.coverPhotoFileIds.collectAsState()
    val resolvedUrls by ThumbnailCache.resolvedUrls.collectAsState()
    val context = LocalContext.current
    val isBuildingCache by ThumbnailCache.isPrefetching.collectAsState()

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
                Spacer(modifier = Modifier.height(12.dp))

                // Banner: only shown during initial thumbnail prefetch after login
                androidx.compose.animation.AnimatedVisibility(
                    visible = isBuildingCache,
                    enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically(),
                    exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkVertically()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "Building thumbnail cache — images may load slowly until complete. This will take some time depending on the number of trips you have joined",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

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
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        filled = true
                    )
                    ActionCard(
                        title = "Join Trip",
                        icon = Icons.Default.Share,
                        onClick = onJoinTrip,
                        modifier = Modifier.weight(1f),
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        filled = false
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
                // Prefer HD-resolved URLs; fall back to thumbnail cache lookup
                val hdCoverUrls = tripHomeViewModel.coverPhotoUrls.collectAsState().value[trip.tripId]
                val coverPhotoUrls = if (!hdCoverUrls.isNullOrEmpty()) {
                    hdCoverUrls
                } else {
                    val coverFileIds = coverPhotoFileIds[trip.tripId] ?: emptyList()
                    coverFileIds.mapNotNull { resolvedUrls[it] }
                }
                TripListItem(
                    trip = trip,
                    currentUid = tripHomeViewModel.getCurrentUid() ?: "",
                    coverPhotoUrls = coverPhotoUrls,
                    onClick = { onTripClick(trip) },
                    onEditClick = { tripToRename = trip },
                    onMembersClick = { tripToShowMembers = trip },
                    onExitClick = { tripToExit = trip },
                    onDownloadClick = { tripHomeViewModel.downloadTripAlbum(trip, context) },
                    downloadProgress = downloadProgressMap[trip.tripId]
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
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    filled: Boolean = true
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
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        border = if (!filled) BorderStroke(
            width = 1.5.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
        ) else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(26.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun TripListItem(
    trip: Trip,
    currentUid: String,
    coverPhotoUrls: List<String>,
    onClick: () -> Unit,
    onEditClick: () -> Unit,
    onMembersClick: () -> Unit,
    onExitClick: () -> Unit,
    onDownloadClick: () -> Unit = {},
    downloadProgress: String? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (isPressed) 0.96f else 1f, label = "card_scale")
    val context = LocalContext.current
    var showDownloadConfirm by remember { mutableStateOf(false) }

    val hasCover = coverPhotoUrls.isNotEmpty()
    // Icon tint: white when over a photo, primary when over the fallback surface
    val iconTint = if (hasCover) Color.White else MaterialTheme.colorScheme.primary
    val exitIconTint = if (hasCover) Color(0xFFFF8A80) else MaterialTheme.colorScheme.error

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.foundation.LocalIndication.current,
                onClick = onClick
            ),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // ── Cover photo: collage, single, or fallback gradient ──
            when {
                coverPhotoUrls.size >= 2 -> {
                    // 2×2 collage grid
                    val displayUrls = coverPhotoUrls.take(4)
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(displayUrls[0]).crossfade(true).build(),
                                contentDescription = null,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                contentScale = ContentScale.Crop
                            )
                            if (displayUrls.size > 1) {
                                Spacer(modifier = Modifier.width(1.5.dp))
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(displayUrls[1]).crossfade(true).build(),
                                    contentDescription = null,
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                        if (displayUrls.size > 2) {
                            Spacer(modifier = Modifier.height(1.5.dp))
                            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(displayUrls[2]).crossfade(true).build(),
                                    contentDescription = null,
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                    contentScale = ContentScale.Crop
                                )
                                if (displayUrls.size > 3) {
                                    Spacer(modifier = Modifier.width(1.5.dp))
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(displayUrls[3]).crossfade(true).build(),
                                        contentDescription = null,
                                        modifier = Modifier.weight(1f).fillMaxHeight(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                        }
                    }
                }
                coverPhotoUrls.size == 1 -> {
                    // Single full-bleed cover
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(coverPhotoUrls[0])
                            .crossfade(true)
                            .build(),
                        contentDescription = "Cover photo for ${trip.name}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                else -> {
                    // Fallback: subtle gradient for trips with no photos
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        MaterialTheme.colorScheme.surface
                                    )
                                )
                            )
                    )
                }
            }

            // ── Gradient overlay for readability ──
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to Color.Transparent,
                                0.25f to Color.Transparent,
                                1f to Color.Black.copy(alpha = if (hasCover) 0.9f else 0.4f)
                            )
                        )
                    )
            )

            // ── Content overlaid on the gradient ──
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 16.dp, end = 12.dp, top = 16.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.Bottom
            ) {
                // Trip name
                Text(
                    text = trip.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        shadow = androidx.compose.ui.graphics.Shadow(
                            color = if (hasCover) Color.Black.copy(alpha = 0.6f) else Color.Transparent,
                            blurRadius = 8f
                        )
                    ),
                    fontWeight = FontWeight.Bold,
                    color = if (hasCover) Color.White else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Meta info row
                Text(
                    text = "Code: ${trip.inviteCode}  •  ${trip.memberUids.size} members",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (hasCover) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Action icons row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Share
                    IconButton(onClick = {
                        val encodedName = java.net.URLEncoder.encode(trip.name, "UTF-8")
                        val joinLink = "https://sksalapur.github.io/RoutePix/join?code=${trip.inviteCode}&name=$encodedName"
                        val shareText = "Hey! Join me on \"${trip.name}\" trip on RoutePix!\n\n$joinLink"
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, shareText)
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, null))
                    }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Share, contentDescription = "Share", tint = iconTint, modifier = Modifier.size(20.dp))
                    }

                    // Download album
                    IconButton(onClick = { showDownloadConfirm = true }, modifier = Modifier.size(36.dp)) {
                        if (downloadProgress != null) {
                            Text(
                                text = downloadProgress,
                                style = MaterialTheme.typography.labelSmall,
                                color = iconTint,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Icon(Icons.Default.Download, contentDescription = "Download Album", tint = iconTint, modifier = Modifier.size(20.dp))
                        }
                    }

                    // Edit (admin only)
                    if (trip.adminUid == currentUid) {
                        IconButton(onClick = onEditClick, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit", tint = iconTint, modifier = Modifier.size(20.dp))
                        }
                    }

                    // Members
                    IconButton(onClick = onMembersClick, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Person, contentDescription = "Members", tint = iconTint, modifier = Modifier.size(20.dp))
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Exit
                    IconButton(onClick = onExitClick, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Exit Trip", tint = exitIconTint, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }

    if (showDownloadConfirm) {
        AlertDialog(
            onDismissRequest = { showDownloadConfirm = false },
            title = { Text("Download Album") },
            text = { Text("Download all photos from \"${trip.name}\" to your gallery? Photos will be organized by their tags into folders.") },
            confirmButton = {
                TextButton(onClick = {
                    showDownloadConfirm = false
                    onDownloadClick()
                }) {
                    Text("Download")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDownloadConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

