@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)

package com.routepix.ui.timeline

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.border
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.*
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
    val selectedPhotoIds by timelineViewModel.selectedPhotoIds.collectAsState()
    val viewMode by timelineViewModel.viewMode.collectAsState()
    val pendingUpload by photoPickerViewModel.pendingUpload.collectAsState()
    
    var selectedGroupKey by remember { mutableStateOf<String?>(null) }
    var showTagEditDialog by remember { mutableStateOf(false) }
    var tagToEdit by remember { mutableStateOf("") }

    if (showTagEditDialog) {
        AlertDialog(
            onDismissRequest = { showTagEditDialog = false },
            title = { Text("Edit Tag") },
            text = {
                OutlinedTextField(
                    value = tagToEdit,
                    onValueChange = { tagToEdit = it },
                    label = { Text("New Tag (e.g. Day 1, Scenery)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val finalTag = if (tagToEdit.isBlank()) null else tagToEdit
                    timelineViewModel.updatePhotoTags(selectedPhotoIds.toList(), finalTag)
                    showTagEditDialog = false
                    tagToEdit = ""
                    timelineViewModel.clearSelection()
                    selectedGroupKey = null
                }) {
                    Text("Update")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTagEditDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (pendingUpload != null) {
        AlertDialog(
            onDismissRequest = { photoPickerViewModel.resolvePendingUpload(false) },
            title = { Text("Already Uploaded") },
            text = { Text("${pendingUpload!!.conflictingUris.size} of the selected photos are already uploaded under a different tag. Would you like to add them to '${pendingUpload!!.tag ?: "Untagged"}' as well?") },
            confirmButton = {
                TextButton(onClick = { photoPickerViewModel.resolvePendingUpload(true) }) {
                    Text("Add Anyway")
                }
            },
            dismissButton = {
                TextButton(onClick = { photoPickerViewModel.resolvePendingUpload(false) }) {
                    Text("Skip Duplicates")
                }
            }
        )
    }

    var showTagSheet by remember { mutableStateOf(false) }
    var pendingUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var pendingFolderUri by remember { mutableStateOf<Uri?>(null) }
    var tagText by remember { mutableStateOf("") }
    var selectedTag by remember { mutableStateOf<String?>(null) }
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

    BackHandler(enabled = selectedPhotoIds.isNotEmpty() || selectedGroupKey != null || selectedPhotoIndex != null) {
        if (selectedPhotoIds.isNotEmpty()) {
            timelineViewModel.clearSelection()
        } else if (selectedPhotoIndex != null) {
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
                    if (selectedPhotoIds.isEmpty()) {
                        Text(
                            text = selectedGroupKey ?: activeTrip?.name ?: "Timeline",
                            fontWeight = FontWeight.SemiBold
                        )
                    } else {
                        Text(
                            text = "${selectedPhotoIds.size} selected",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                navigationIcon = {
                    if (selectedPhotoIds.isNotEmpty()) {
                        IconButton(onClick = { timelineViewModel.clearSelection() }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear Selection")
                        }
                    } else {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (selectedPhotoIds.isNotEmpty()) {
                        if (isAdmin) {
                            IconButton(onClick = { showTagEditDialog = true }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit Tag")
                            }
                            IconButton(onClick = { timelineViewModel.deletePhotos(selectedPhotoIds.toList()) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Selected")
                            }
                        }
                    } else if (selectedGroupKey != null) {
                        val albumPhotos = grouped[selectedGroupKey] ?: emptyList()
                        IconButton(onClick = { 
                            timelineViewModel.setViewMode(
                                if (viewMode == ViewMode.DETAILED) ViewMode.GRID else ViewMode.DETAILED
                            ) 
                        }) {
                            Icon(
                                imageVector = if (viewMode == ViewMode.DETAILED) Icons.Default.GridView else Icons.Default.ViewList,
                                contentDescription = "Toggle View"
                            )
                        }
                        IconButton(onClick = { 
                            timelineViewModel.downloadAlbum(context, albumPhotos, selectedGroupKey!!) 
                        }) {
                            Icon(Icons.Default.Download, contentDescription = "Download Album")
                        }
                        if (isAdmin) {
                            IconButton(onClick = {
                                tagToEdit = if (selectedGroupKey == "Untagged") "" else selectedGroupKey!!
                                albumPhotos.forEach { timelineViewModel.toggleSelection(it.photoId) }
                                showTagEditDialog = true
                            }) {
                                Icon(Icons.Default.Edit, contentDescription = "Rename Tag")
                            }
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
                UploadProgressBar(
                    progress = uploadProgress,
                    onCancel = { timelineViewModel.cancelUpload() }
                )
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
                    if (viewMode == ViewMode.GRID) {
                        var gridColumns by remember { mutableStateOf(3) }
                        var scale by remember { mutableStateOf(1f) }
        
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(gridColumns),
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectTransformGestures { _, _, zoom, _ ->
                                        scale *= zoom
                                        if (scale > 1.3f && gridColumns > 2) {
                                            gridColumns--
                                            scale = 1f
                                        } else if (scale < 0.7f && gridColumns < 6) {
                                            gridColumns++
                                            scale = 1f
                                        }
                                    }
                                },
                            contentPadding = PaddingValues(bottom = 80.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            items(albumPhotos) { photo ->
                                val isSelected = photo.photoId in selectedPhotoIds
                                Box(
                                    modifier = Modifier
                                        .aspectRatio(1f)
                                        .combinedClickable(
                                            onClick = {
                                                if (selectedPhotoIds.isNotEmpty()) timelineViewModel.toggleSelection(photo.photoId)
                                                else {
                                                    selectedPhotoIndex = albumPhotos.indexOf(photo)
                                                }
                                            },
                                            onLongClick = {
                                                if (isAdmin) timelineViewModel.toggleSelection(photo.photoId)
                                            }
                                        )
                                ) {
                                    TelegramAsyncImage(
                                        photo = photo,
                                        timelineViewModel = timelineViewModel,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                    if (isSelected) {
                                        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha=0.4f))) 
                                        Icon(
                                            Icons.Default.CheckCircle, 
                                            contentDescription = null, 
                                            tint = MaterialTheme.colorScheme.primary, 
                                            modifier = Modifier.padding(4.dp).align(Alignment.TopEnd)
                                        )
                                    }
                                }
                            }
                        }
                    } else {
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
                                    isSelected = photo.photoId in selectedPhotoIds,
                                    isAdmin = isAdmin,
                                    onClick = { 
                                        if (selectedPhotoIds.isNotEmpty()) {
                                            timelineViewModel.toggleSelection(photo.photoId)
                                        } else {
                                            selectedPhotoIndex = index 
                                        }
                                    },
                                    onLongClick = {
                                        if (isAdmin) timelineViewModel.toggleSelection(photo.photoId)
                                    }
                                )
                            }
                            item { Spacer(modifier = Modifier.height(80.dp)) }
                        }
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
                        val isGroupSelected = groupPhotos.any { it.photoId in selectedPhotoIds }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        if (selectedPhotoIds.isNotEmpty()) {
                                            groupPhotos.forEach { timelineViewModel.toggleSelection(it.photoId) }
                                        } else {
                                            selectedGroupKey = groupKey
                                        }
                                    },
                                    onLongClick = {
                                        if (isAdmin) {
                                            groupPhotos.forEach { timelineViewModel.toggleSelection(it.photoId) }
                                        }
                                    }
                                )
                                .padding(8.dp)
                                .background(
                                    if (isGroupSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                    else Color.Transparent,
                                    RoundedCornerShape(12.dp)
                                )
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
                    photoPickerViewModel.requestUpload(
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
private fun UploadProgressBar(
    progress: UploadProgress,
    onCancel: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        color = MaterialTheme.colorScheme.secondaryContainer,
        shadowElevation = 8.dp
    ) {
        Column {
            Row(
                modifier = Modifier
                    .clickable { if (!progress.isPreparing && progress.totalBatches > 1) isExpanded = !isExpanded }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (progress.isPreparing) {
                    // Indeterminate spinner during preparation
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        strokeWidth = 3.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    val fraction = if (progress.total > 0) {
                        val f = progress.uploaded.toFloat() / progress.total
                        if (f.isNaN() || f.isInfinite()) 0f else f.coerceIn(0f, 1f)
                    } else 0f

                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { fraction },
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
                }

                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    if (progress.isPreparing) {
                        Text(
                            text = "Preparing upload...",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${progress.total} photos found so far",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                        )
                    } else {
                        Text(
                            text = if (progress.totalBatches > 1) {
                                "Uploading Batch ${progress.currentBatch} of ${progress.totalBatches}"
                            } else {
                                "Uploading Photos"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${progress.uploaded} of ${progress.total} photos completed",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
                if (!progress.isPreparing && progress.totalBatches > 1) {
                    IconButton(onClick = { isExpanded = !isExpanded }) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                            contentDescription = if (isExpanded) "Collapse" else "Expand"
                        )
                    }
                }
                IconButton(onClick = onCancel) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel Upload")
                }
            }

            AnimatedVisibility(visible = isExpanded && !progress.isPreparing && progress.totalBatches > 1) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    repeat(progress.totalBatches) { i ->
                        val bNum = i + 1
                        val status = when {
                            bNum < progress.currentBatch -> "✓ Done"
                            bNum == progress.currentBatch -> "Uploading (${progress.batchUploaded}/${progress.batchSize})..."
                            else -> "Pending"
                        }
                        val color = when {
                            bNum < progress.currentBatch -> MaterialTheme.colorScheme.primary
                            bNum == progress.currentBatch -> MaterialTheme.colorScheme.secondary
                            else -> MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.4f)
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 48.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Batch $bNum",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (bNum == progress.currentBatch) FontWeight.Bold else FontWeight.Normal
                            )
                            Text(
                                text = status,
                                style = MaterialTheme.typography.labelSmall,
                                color = color,
                                fontWeight = if (bNum == progress.currentBatch) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
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
    isSelected: Boolean = false,
    isAdmin: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                             else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
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

                val sizeText = photo.sizeBytes?.let { " • ${String.format(java.util.Locale.US, "%.1f", it / (1024f * 1024f))} MB" } ?: ""
                Text(
                    text = "${formatTimestamp(photo.timestamp)}$sizeText",
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
    val selectedPhotoIds by timelineViewModel.selectedPhotoIds.collectAsState()
    val pagerState = rememberPagerState(initialPage = initialIndex, pageCount = { photoList.size })
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

