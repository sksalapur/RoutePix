@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)

package com.routepix.ui.timeline

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Help
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.AssistChip
import androidx.compose.material.icons.filled.Folder
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.lifecycle.viewmodel.compose.viewModel
import com.routepix.data.model.PhotoMeta
import com.routepix.ui.picker.PhotoPickerViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TimelineScreen(
    tripId: String,
    onBack: () -> Unit,
    timelineViewModel: TimelineViewModel = viewModel(),
    photoPickerViewModel: PhotoPickerViewModel = viewModel()
) {
    val activeTrip by timelineViewModel.activeTrip.collectAsState()
    val sortMode by timelineViewModel.sortMode.collectAsState()
    val grouped by timelineViewModel.groupedPhotos.collectAsState()
    val photos by timelineViewModel.photos.collectAsState()
    val userNames by timelineViewModel.userNames.collectAsState()
    val availableTags by timelineViewModel.availableTags.collectAsState()
    val uploadProgress by timelineViewModel.uploadProgress.collectAsState()

    var showTagSheet by remember { mutableStateOf(false) }
    var pendingUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var pendingFolderUri by remember { mutableStateOf<Uri?>(null) }
    var tagText by remember { mutableStateOf("") }
    var selectedTag by remember { mutableStateOf<String?>(null) }
    var selectedGroupKey by remember { mutableStateOf<String?>(null) }
    var selectedPhotoIndex by remember { mutableStateOf<Int?>(null) }
    val context = LocalContext.current
    val auth = remember { com.google.firebase.auth.FirebaseAuth.getInstance() }
    val isAdmin = activeTrip?.adminUid == auth.currentUser?.uid

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            pendingUris = uris
            pendingFolderUri = null
            showTagSheet = true
        }
    }

    val folderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            pendingFolderUri = uri
            pendingUris = emptyList()
            showTagSheet = true
        }
    }

    BackHandler(enabled = selectedGroupKey != null || selectedPhotoIndex != null) {
        if (selectedPhotoIndex != null) {
            selectedPhotoIndex = null
        } else if (selectedGroupKey != null) {
            selectedGroupKey = null
        }
    }

    var showTagHelp by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = selectedGroupKey ?: activeTrip?.name ?: "Timeline",
                        fontWeight = FontWeight.SemiBold
                    )
                },

                actions = {
                    if (selectedGroupKey != null) {
                        val albumPhotos = grouped[selectedGroupKey] ?: emptyList()
                        IconButton(onClick = { 
                            timelineViewModel.downloadAlbum(context, albumPhotos, selectedGroupKey!!) 
                        }) {
                            Icon(Icons.Default.Download, contentDescription = "Download Album")
                        }
                        if (isAdmin) {
                            IconButton(onClick = { 
                                timelineViewModel.deleteAlbum(albumPhotos)
                                selectedGroupKey = null
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Album")
                            }
                        }
                    } else {
                        SortModeSelector(
                            currentMode = sortMode,
                            onModeSelected = {
                                timelineViewModel.setSortMode(it)
                                selectedGroupKey = null
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            var expanded by remember { mutableStateOf(false) }

            Box(contentAlignment = Alignment.BottomEnd) {
                AnimatedVisibility(
                    visible = expanded,
                    modifier = Modifier.padding(bottom = 72.dp)
                ) {
                    Column(horizontalAlignment = Alignment.End) {
                        FloatingActionButton(
                            onClick = {
                                expanded = false
                                folderLauncher.launch(null)
                            },
                            modifier = Modifier.padding(bottom = 8.dp),
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Icon(Icons.Default.Folder, contentDescription = "Select Folder")
                        }
                        FloatingActionButton(
                            onClick = {
                                expanded = false
                                pickerLauncher.launch(arrayOf("image/*"))
                            },
                            modifier = Modifier.padding(bottom = 8.dp),
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = "Select Photos")
                        }
                    }
                }
                
                FloatingActionButton(
                    onClick = { expanded = !expanded },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(if (expanded) Icons.Default.Close else Icons.Default.Add, contentDescription = "Add")
                }
            }
        },
        bottomBar = {
            if (uploadProgress.isActive) {
                UploadProgressBar(progress = uploadProgress)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {

            
            if (photos.isEmpty()) {

                EmptyTimelineState(modifier = Modifier.fillMaxSize())
            } else if (selectedGroupKey != null && grouped.containsKey(selectedGroupKey)) {

                val albumPhotos = grouped[selectedGroupKey] ?: emptyList()
                Column(modifier = Modifier.fillMaxSize()) {

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(albumPhotos.size) { index ->
                            val photo = albumPhotos[index]
                            PhotoRow(
                                photo = photo,
                                timelineViewModel = timelineViewModel,
                                displayName = userNames[photo.uploaderUid] ?: photo.uploaderUid,
                                onClick = { selectedPhotoIndex = index }
                            )
                        }
                        item { Spacer(modifier = Modifier.height(80.dp)) }
                    }
                }
            } else {

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 80.dp)
                ) {
                    items(grouped.keys.toList()) { groupKey ->
                        val groupPhotos = grouped[groupKey] ?: emptyList()
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedGroupKey = groupKey }
                                .padding(8.dp)
                        ) {
                            AlbumCollageThumbnail(
                                photos = groupPhotos,
                                timelineViewModel = timelineViewModel,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = groupKey,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${groupPhotos.size} photos",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    if (selectedPhotoIndex != null && selectedGroupKey != null) {
        val albumPhotos = grouped[selectedGroupKey] ?: emptyList()
        if (albumPhotos.isNotEmpty()) {
            PhotoPagerOverlay(
                initialIndex = selectedPhotoIndex!!,
                photoList = albumPhotos,
                timelineViewModel = timelineViewModel,
                isAdmin = isAdmin,
                onClose = { selectedPhotoIndex = null }
            )
        }
    }

    if (showTagSheet) {
        TagBottomSheet(
            tagText = tagText,
            availableTags = availableTags,
            onTagChange = { tagText = it },
            onConfirm = { finalTag ->
                if (pendingFolderUri != null) {
                    photoPickerViewModel.enqueueFolder(
                        tripId = tripId,
                        treeUri = pendingFolderUri!!,
                        tag = finalTag
                    )
                } else if (pendingUris.isNotEmpty()) {
                    photoPickerViewModel.enqueuePhotos(
                        tripId = tripId,
                        uris = pendingUris,
                        tag = finalTag
                    )
                }
                showTagSheet = false
                pendingUris = emptyList()
                pendingFolderUri = null
                tagText = ""
                selectedTag = null
            },
            onHighlightHelp = { showTagHelp = true },
            onDismiss = {
                showTagSheet = false
                pendingUris = emptyList()
                pendingFolderUri = null
                tagText = ""
                selectedTag = null
            }
        )
    }

    if (showTagHelp) {
        AlertDialog(
            onDismissRequest = { showTagHelp = false },
            confirmButton = {
                TextButton(onClick = { showTagHelp = false }) { Text("Got it") }
            },
            title = { Text("What is a Tag?") },
            text = {
                Text("Tags help you organize your photos. You can use them for:\n\n• Location (e.g., Paris, Beach)\n• Place Name (e.g., Eiffel Tower)\n• Activity (e.g., Dinner, Hiking)\n• People (e.g., Friends, Family)\n\nPhotos with the same tag are grouped into beautiful albums automatically!")
            },
            icon = { Icon(Icons.Default.PhotoLibrary, null) }
        )
    }
}

@Composable
private fun UploadProgressBar(progress: UploadProgress) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { progress.finished.toFloat() / progress.total },
                    modifier = Modifier.size(36.dp),
                    strokeWidth = 3.dp,
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Text(
                    text = "${progress.percentage}%",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = "Uploading Photos",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${progress.finished} of ${progress.total} completed",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun SortModeSelector(
    currentMode: SortMode,
    onModeSelected: (SortMode) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val modes = listOf(SortMode.ByDate, SortMode.ByUploader, SortMode.ByTag)

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.FilterList, contentDescription = "Sort Mode")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            modes.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(mode.label) },
                    onClick = {
                        onModeSelected(mode)
                        expanded = false
                    },
                    leadingIcon = {
                        if (currentMode::class == mode::class) {
                            Icon(Icons.Default.Check, contentDescription = null)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun AlbumCard(
    title: String,
    photoCount: Int,
    coverPhoto: PhotoMeta?,
    botToken: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        label = "album_scale",
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessLow
        )
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.foundation.LocalIndication.current,
                onClick = onClick
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (coverPhoto != null) {


            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "$photoCount photos",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PhotoRow(
    photo: PhotoMeta,
    timelineViewModel: TimelineViewModel,
    displayName: String = photo.uploaderUid,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TelegramAsyncImage(
                photo = photo,
                timelineViewModel = timelineViewModel,
                contentDescription = null,
                modifier = Modifier.size(80.dp).clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )

                photo.placeName?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                photo.tag?.let { tag ->
                    Spacer(modifier = Modifier.height(4.dp))
                    AssistChip(
                        onClick = {},
                        label = { Text(tag, style = MaterialTheme.typography.labelSmall) },
                        shape = RoundedCornerShape(16.dp)
                    )
                }

                Text(
                    text = formatTimestamp(photo.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
private fun AlbumCollageThumbnail(
    photos: List<PhotoMeta>,
    timelineViewModel: TimelineViewModel,
    modifier: Modifier = Modifier
) {
    val displayPhotos = photos.take(4)
    Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        when {
            displayPhotos.isEmpty() -> {
                Icon(
                    Icons.Default.PhotoLibrary,
                    contentDescription = null,
                    modifier = Modifier.align(Alignment.Center).size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
            displayPhotos.size == 1 -> {
                TelegramAsyncImage(
                    photo = displayPhotos[0],
                    timelineViewModel = timelineViewModel,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            else -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(modifier = Modifier.weight(1f)) {
                        TelegramAsyncImage(
                            photo = displayPhotos[0],
                            timelineViewModel = timelineViewModel,
                            contentDescription = null,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            contentScale = ContentScale.Crop
                        )
                        if (displayPhotos.size >= 2) {
                            Spacer(modifier = Modifier.width(1.dp))
                            TelegramAsyncImage(
                                photo = displayPhotos[1],
                                timelineViewModel = timelineViewModel,
                                contentDescription = null,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                    if (displayPhotos.size >= 3) {
                        Spacer(modifier = Modifier.height(1.dp))
                        Row(modifier = Modifier.weight(1f)) {
                            TelegramAsyncImage(
                                photo = displayPhotos[2],
                                timelineViewModel = timelineViewModel,
                                contentDescription = null,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                contentScale = ContentScale.Crop
                            )
                            if (displayPhotos.size >= 4) {
                                Spacer(modifier = Modifier.width(1.dp))
                                TelegramAsyncImage(
                                    photo = displayPhotos[3],
                                    timelineViewModel = timelineViewModel,
                                    contentDescription = null,
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(modifier = Modifier.weight(1f).fillMaxHeight())
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TelegramAsyncImage(
    photo: PhotoMeta,
    timelineViewModel: TimelineViewModel,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit
) {
    val url by timelineViewModel.resolveImageUrl(photo).collectAsState(initial = null)
    
    AsyncImage(
        model = url,
        contentDescription = contentDescription,
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentScale = contentScale,
        placeholder = null,
        error = null
    )
}

@Composable
private fun EmptyTimelineState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.outlineVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No photos yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Tap + to add your first trip photo",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagBottomSheet(
    tagText: String,
    availableTags: List<String>,
    onTagChange: (String) -> Unit,
    onConfirm: (String?) -> Unit,
    onHighlightHelp: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    var selectedTag by remember { mutableStateOf<String?>(null) }
    var isCreatingNew by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Select a tag",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onHighlightHelp) {
                    Icon(Icons.Default.Help, contentDescription = "What is a tag?", tint = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                availableTags.forEach { tag ->
                    FilterChip(
                        selected = selectedTag == tag,
                        onClick = { 
                            selectedTag = if (selectedTag == tag) null else tag
                            isCreatingNew = false
                        },
                        label = { Text(tag) }
                    )
                }
                FilterChip(
                    selected = isCreatingNew,
                    onClick = { 
                        isCreatingNew = !isCreatingNew
                        selectedTag = null
                    },
                    label = { Text("+ New Tag") },
                    leadingIcon = { Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp)) }
                )
            }

            if (isCreatingNew) {
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = tagText,
                    onValueChange = onTagChange,
                    label = { Text("New Tag Name") },
                    placeholder = { Text("e.g. Beach, Sunset") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = {
                    val finalTag = if (isCreatingNew) tagText.ifBlank { null } else selectedTag
                    onConfirm(finalTag)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                enabled = (selectedTag != null || (isCreatingNew && tagText.isNotBlank()) || (!isCreatingNew && selectedTag == null))
            ) {
                Text("Upload Photos")
            }
        }
    }
}

@Composable
private fun PhotoPagerOverlay(
    initialIndex: Int,
    photoList: List<PhotoMeta>,
    timelineViewModel: TimelineViewModel,
    isAdmin: Boolean,
    onClose: () -> Unit
) {
    val pagerState = rememberPagerState { photoList.size }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(initialIndex) {
        pagerState.scrollToPage(initialIndex)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(enabled = false) {}
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            pageSpacing = 16.dp
        ) { page ->
            TelegramAsyncImage(
                photo = photoList[page],
                timelineViewModel = timelineViewModel,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
            
            Row {
                IconButton(
                    onClick = { 
                        timelineViewModel.downloadPhoto(context, photoList[pagerState.currentPage]) 
                    },
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
                ) {
                    Icon(Icons.Default.Download, contentDescription = "Download", tint = Color.White)
                }
                if (isAdmin) {
                    IconButton(
                        onClick = { 
                        timelineViewModel.deletePhoto(photoList[pagerState.currentPage].photoId)
                        if (photoList.size <= 1) onClose()
                        },
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.White)
                    }
                }
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp),
            color = Color.Black.copy(alpha = 0.7f)
        ) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(photoList.size) { index ->
                    val isSelected = pagerState.currentPage == index
                    val photo = photoList[index]
                    
                    Box(
                        modifier = Modifier
                            .size(if (isSelected) 56.dp else 44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(
                                width = if (isSelected) 2.dp else 0.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable {
                                scope.launch { pagerState.animateScrollToPage(index) }
                            }
                    ) {
                        TelegramAsyncImage(
                            photo = photo,
                            timelineViewModel = timelineViewModel,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
        }
    }
}


private fun formatTimestamp(timestamp: Long): String {
    return SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
        .format(Date(timestamp))
}

