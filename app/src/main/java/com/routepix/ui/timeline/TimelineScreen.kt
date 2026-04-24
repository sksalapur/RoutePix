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
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Brush
import androidx.compose.animation.core.animateFloat
import com.routepix.ui.components.FullScreenLoaderOverlay
import com.routepix.ui.components.GlassBottomSheetContent
import com.routepix.ui.components.GlassTopBar
import com.routepix.ui.components.RoutepixLoader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TimelineScreen(
    tripId: String,
    onBack: () -> Unit,
    onNavigateToOriginal: (String) -> Unit,
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
    val pickerQueueState by photoPickerViewModel.queueState.collectAsState()
    
    var selectedGroupKey by rememberSaveable { mutableStateOf<String?>(null) }
    var showTagEditSheet by rememberSaveable { mutableStateOf(false) }
    var tagToEdit by rememberSaveable { mutableStateOf("") }

    // Screen Entry Animation
    val entryProgress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        entryProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
        )
    }

    if (showTagEditSheet) {
        ModalBottomSheet(
            onDismissRequest = { showTagEditSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Color.Transparent,
            dragHandle = null
        ) {
            GlassBottomSheetContent {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Move to Tag",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Select an existing tag or enter a new one",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                    )

                    OutlinedTextField(
                        value = tagToEdit,
                        onValueChange = { tagToEdit = it },
                        placeholder = { Text("New tag name...") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            if (tagToEdit.isNotBlank()) {
                                IconButton(onClick = {
                                    timelineViewModel.updatePhotoTags(selectedPhotoIds.toList(), tagToEdit)
                                    showTagEditSheet = false
                                    timelineViewModel.clearSelection()
                                    selectedGroupKey = null
                                    tagToEdit = ""
                                }) {
                                    Icon(Icons.Default.Check, contentDescription = "Add Tag", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    )

                    if (availableTags.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Or choose existing:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.Start)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(availableTags) { tag ->
                                Surface(
                                    onClick = {
                                        timelineViewModel.updatePhotoTags(selectedPhotoIds.toList(), tag)
                                        showTagEditSheet = false
                                        timelineViewModel.clearSelection()
                                        selectedGroupKey = null
                                        tagToEdit = ""
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = tag,
                                        modifier = Modifier.padding(16.dp),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }

    if (pendingUpload != null) {
        val cross = pendingUpload!!.crossTagConflicts.size
        val same = pendingUpload!!.sameTagConflicts.size
        
        val message = buildString {
            if (same > 0) append("$same photos already exist in this tag. ")
            if (cross > 0) append("\n$cross photos exist in other tags. Add them to '${pendingUpload!!.tag ?: "Untagged"}' too?")
        }.trim()

        AlertDialog(
            onDismissRequest = { photoPickerViewModel.resolvePendingUpload(com.routepix.ui.picker.PhotoPickerViewModel.DuplicateAction.SKIP_ALL) },
            title = { Text("Duplicates Found") },
            text = { Text(message) },
            confirmButton = {
                androidx.compose.foundation.layout.Row(
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                ) {
                    if (same > 0 && cross > 0) {
                        TextButton(onClick = { photoPickerViewModel.resolvePendingUpload(com.routepix.ui.picker.PhotoPickerViewModel.DuplicateAction.ADD_ALL) }) {
                            Text("Add All")
                        }
                        TextButton(onClick = { photoPickerViewModel.resolvePendingUpload(com.routepix.ui.picker.PhotoPickerViewModel.DuplicateAction.ADD_CROSS_TAG_ONLY) }) {
                            Text("Skip Same Tag")
                        }
                    } else if (cross > 0) {
                        TextButton(onClick = { photoPickerViewModel.resolvePendingUpload(com.routepix.ui.picker.PhotoPickerViewModel.DuplicateAction.ADD_CROSS_TAG_ONLY) }) {
                            Text("Add Anyway")
                        }
                    } else if (same > 0) {
                        TextButton(onClick = { photoPickerViewModel.resolvePendingUpload(com.routepix.ui.picker.PhotoPickerViewModel.DuplicateAction.ADD_ALL) }) {
                            Text("Add Anyway")
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { photoPickerViewModel.resolvePendingUpload(com.routepix.ui.picker.PhotoPickerViewModel.DuplicateAction.SKIP_ALL) }) {
                    Text("Skip All Duplicates")
                }
            }
        )
    }

    var showTagSheet by remember { mutableStateOf(false) }
    var pendingUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var pendingFolderUri by remember { mutableStateOf<Uri?>(null) }
    var tagText by remember { mutableStateOf("") }
    var selectedTag by remember { mutableStateOf<String?>(null) }
    var selectedPhotoIndex by rememberSaveable { mutableStateOf<Int?>(null) }
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
    var showQualityInfo by remember { mutableStateOf(false) }

    val shareProgress by timelineViewModel.shareProgress.collectAsState()
    
    if (shareProgress.inProgress) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { /* Not cancellable */ }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val progress = if (shareProgress.total > 0) shareProgress.current.toFloat() / shareProgress.total else 0f
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.size(64.dp),
                            strokeWidth = 6.dp,
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Text(
                            "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Preparing to share...",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Downloaded ${shareProgress.current} of ${shareProgress.total}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    Scaffold(
        topBar = {
            GlassTopBar(
                title = {
                    if (selectedPhotoIds.isEmpty()) {
                        Text(
                            text = selectedGroupKey ?: activeTrip?.name ?: "Timeline",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Text(
                            text = "${selectedPhotoIds.size} selected",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
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
                        IconButton(onClick = { 
                            val selectedPhotoMetas = photos.filter { it.photoId in selectedPhotoIds }
                            timelineViewModel.sharePhotos(context, selectedPhotoMetas) 
                        }) {
                            Icon(androidx.compose.material.icons.Icons.Default.Share, contentDescription = "Share Selected")
                        }
                        IconButton(onClick = { 
                            val selectedPhotoMetas = photos.filter { it.photoId in selectedPhotoIds }
                            timelineViewModel.downloadAlbum(context, selectedPhotoMetas, "Selection") 
                            timelineViewModel.clearSelection()
                        }) {
                            Icon(androidx.compose.material.icons.Icons.Default.Download, contentDescription = "Download Selected")
                        }
                        if (isAdmin) {
                            IconButton(onClick = { showTagEditSheet = true }) {
                                Icon(androidx.compose.material.icons.Icons.Default.Edit, contentDescription = "Edit Tag")
                            }
                            IconButton(onClick = { timelineViewModel.deletePhotos(selectedPhotoIds.toList()) }) {
                                Icon(androidx.compose.material.icons.Icons.Default.Delete, contentDescription = "Delete Selected")
                            }
                        }
                    } else if (selectedGroupKey != null) {
                        val albumPhotos = grouped[selectedGroupKey] ?: emptyList()
                        var menuExpanded by remember { mutableStateOf(false) }
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
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Options")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Share") },
                                onClick = {
                                    menuExpanded = false
                                    timelineViewModel.sharePhotos(context, albumPhotos)
                                },
                                leadingIcon = { Icon(Icons.Default.Share, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Download") },
                                onClick = {
                                    menuExpanded = false
                                    timelineViewModel.downloadAlbum(context, albumPhotos, selectedGroupKey!!)
                                },
                                leadingIcon = { Icon(Icons.Default.Download, null) }
                            )
                            if (isAdmin) {
                                DropdownMenuItem(
                                    text = { Text("Rename Album") },
                                    onClick = {
                                        menuExpanded = false
                                        tagToEdit = if (selectedGroupKey == "Untagged") "" else selectedGroupKey ?: ""
                                        timelineViewModel.selectAll(albumPhotos.map { it.photoId })
                                        showTagEditSheet = true
                                    },
                                    leadingIcon = { Icon(Icons.Default.Edit, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete") },
                                    onClick = {
                                        menuExpanded = false
                                        timelineViewModel.deleteAlbum(albumPhotos)
                                        selectedGroupKey = null
                                    },
                                    leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
                                )
                            }
                        }
                    } else {
                        IconButton(onClick = { showQualityInfo = true }) {
                            Icon(Icons.Default.Info, contentDescription = "About image quality", tint = MaterialTheme.colorScheme.primary)
                        }
                        SortModeSelector(
                            currentMode = sortMode,
                            onModeSelected = {
                                timelineViewModel.setSortMode(it)
                                selectedGroupKey = null
                            }
                        )
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = entryProgress.value
                    translationY = (1f - entryProgress.value) * 40.dp.toPx()
                }
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
            if (photos.isEmpty()) {
                EmptyTimelineState(
                    modifier = Modifier.fillMaxSize()
                        .padding(padding)
                )
            } else {
                Crossfade(
                    targetState = selectedGroupKey,
                    animationSpec = androidx.compose.animation.core.tween(250),
                    label = "album_transition"
                ) { currentGroupKey ->
                    if (currentGroupKey != null && grouped.containsKey(currentGroupKey)) {
                        val albumPhotos = grouped[currentGroupKey] ?: emptyList()
                        Column(modifier = Modifier.fillMaxSize()) {
                            if (viewMode == ViewMode.GRID) {
                                var gridColumns by remember { mutableStateOf(3) }
                                var scale by remember { mutableStateOf(1f) }
                                val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
                                val totalItems = albumPhotos.size
                                val coroutineScope = rememberCoroutineScope()

                                // Auto-hide thumb after 1.5s of no scrolling
                                var thumbVisible by remember { mutableStateOf(false) }
                                LaunchedEffect(gridState.firstVisibleItemIndex, gridState.firstVisibleItemScrollOffset) {
                                    thumbVisible = true
                                    kotlinx.coroutines.delay(1500)
                                    thumbVisible = false
                                }

                                Box(modifier = Modifier.fillMaxSize()) {
                                    LazyVerticalGrid(
                                        state = gridState,
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
                                        contentPadding = PaddingValues(
                                            top = padding.calculateTopPadding() + 16.dp,
                                            bottom = padding.calculateBottomPadding() + 80.dp,
                                            start = 4.dp,
                                            end = 20.dp // leave room for the thumb
                                        ),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        items(albumPhotos, key = { it.photoId }) { photo ->
                                            val isSelected = photo.photoId in selectedPhotoIds
                                            val interactionSource = remember { MutableInteractionSource() }
                                            val isPressed by interactionSource.collectIsPressedAsState()
                                            val itemScale by animateFloatAsState(
                                                targetValue = if (isPressed) 0.94f else 1f,
                                                label = "grid_scale"
                                            )

                                            Box(
                                                modifier = Modifier
                                                    .aspectRatio(1f)
                                                    .graphicsLayer {
                                                        scaleX = itemScale
                                                        scaleY = itemScale
                                                    }
                                                    .combinedClickable(
                                                        interactionSource = interactionSource,
                                                        indication = androidx.compose.foundation.LocalIndication.current,
                                                        onClick = {
                                                            if (selectedPhotoIds.isNotEmpty()) timelineViewModel.toggleSelection(photo.photoId)
                                                            else {
                                                                selectedPhotoIndex = albumPhotos.indexOf(photo)
                                                            }
                                                        },
                                                        onLongClick = {
                                                            timelineViewModel.toggleSelection(photo.photoId)
                                                        }
                                                    )
                                            ) {
                                                TimelineMediaItem(
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

                                    // ── Fast-scroll thumb scrubber ──────────────────────
                                    androidx.compose.animation.AnimatedVisibility(
                                        visible = thumbVisible,
                                        modifier = Modifier.align(Alignment.CenterEnd),
                                        enter = androidx.compose.animation.fadeIn(tween(150)),
                                        exit = androidx.compose.animation.fadeOut(tween(600))
                                    ) {
                                        val density = androidx.compose.ui.platform.LocalDensity.current
                                        val trackHeightPx = remember { mutableStateOf(0f) }
                                        val thumbFraction = remember(gridState.firstVisibleItemIndex, totalItems) {
                                            if (totalItems <= 1) 0f
                                            else (gridState.firstVisibleItemIndex.toFloat() / (totalItems - 1)).coerceIn(0f, 1f)
                                        }

                                        Box(
                                            modifier = Modifier
                                                .width(28.dp)
                                                .fillMaxHeight()
                                                .onGloballyPositioned { trackHeightPx.value = it.size.height.toFloat() }
                                                .pointerInput(totalItems) {
                                                    detectDragGestures(
                                                        onDragStart = { thumbVisible = true }
                                                    ) { change, _ ->
                                                        change.consume()
                                                        val fraction = (change.position.y / trackHeightPx.value).coerceIn(0f, 1f)
                                                        val targetIndex = (fraction * (totalItems - 1)).toInt()
                                                        coroutineScope.launch {
                                                            gridState.scrollToItem(targetIndex)
                                                        }
                                                        thumbVisible = true
                                                    }
                                                }
                                        ) {
                                            // Thumb pill
                                            Box(
                                                modifier = Modifier
                                                    .width(5.dp)
                                                    .height(40.dp)
                                                    .align(Alignment.TopCenter)
                                                    .offset(y = with(density) { (trackHeightPx.value * thumbFraction - 20.dp.toPx()).toDp() })
                                                    .clip(RoundedCornerShape(50))
                                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f))
                                            )
                                        }
                                    }
                                } // end Box (grid + thumb)
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(
                                        top = padding.calculateTopPadding() + 16.dp,
                                        bottom = padding.calculateBottomPadding() + 80.dp
                                    ),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    items(albumPhotos.size, key = { albumPhotos[it].photoId }) { index ->
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
                                                timelineViewModel.toggleSelection(photo.photoId)
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
                             contentPadding = PaddingValues(
                                 top = padding.calculateTopPadding() + 16.dp,
                                 bottom = padding.calculateBottomPadding() + 80.dp
                             )
                        ) {
                            items(grouped.keys.toList(), key = { it }) { groupKey ->
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
                                                groupPhotos.forEach { timelineViewModel.toggleSelection(it.photoId) }
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
        } // end Crossfade / Column

            // Processing overlay — shown immediately after confirming photo selection,
            // before the bottom progress bar activates
            val showProcessingOverlay = pickerQueueState.isProcessing && !uploadProgress.isActive
            if (showProcessingOverlay) {
                FullScreenLoaderOverlay(label = "Processing...")
            }
        } // end Box
    } // end Scaffold

    AnimatedVisibility(
        visible = selectedPhotoIndex != null && selectedGroupKey != null,
        enter = androidx.compose.animation.fadeIn(
            animationSpec = androidx.compose.animation.core.tween(200)
        ),
        exit = androidx.compose.animation.fadeOut(
            animationSpec = androidx.compose.animation.core.tween(150)
        )
    ) {
        val albumPhotos = grouped[selectedGroupKey] ?: emptyList()
        if (albumPhotos.isNotEmpty() && selectedPhotoIndex != null) {
            PhotoPagerOverlay(
                initialIndex = selectedPhotoIndex!!,
                photoList = albumPhotos,
                timelineViewModel = timelineViewModel,
                isAdmin = isAdmin,
                onClose = { selectedPhotoIndex = null },
                onNavigateToOriginal = { photoId -> onNavigateToOriginal(photoId) }
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

    if (showQualityInfo) {
        AlertDialog(
            onDismissRequest = { showQualityInfo = false },
            confirmButton = {
                TextButton(onClick = { showQualityInfo = false }) { Text("Got it") }
            },
            title = { Text("About image quality") },
            text = { 
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Photos in timeline are optimized for speed. Shared/downloaded photos are full quality.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Icon Guide", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.HighQuality,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier
                                .size(24.dp)
                                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                                .padding(3.dp)
                        )
                        Text(
                            "Original quality available. Tap to view the high-resolution image.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayCircle,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier
                                .size(24.dp)
                                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                                .padding(3.dp)
                        )
                        Text(
                            "Motion photo. Tap to watch the embedded video with audio.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            icon = { Icon(Icons.Default.Info, null) }
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
    if (title == "<SHIMMER>") {
        val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "shimmer")
        val shimmerOffset by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1000f,
            animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                animation = androidx.compose.animation.core.tween(durationMillis = 1200),
                repeatMode = androidx.compose.animation.core.RepeatMode.Restart
            ),
            label = "shimmer_offset"
        )
        val shimmerBrush = Brush.linearGradient(
            colors = listOf(
                MaterialTheme.colorScheme.surfaceVariant,
                MaterialTheme.colorScheme.surface,
                MaterialTheme.colorScheme.surfaceVariant
            ),
            start = Offset(shimmerOffset - 200f, 0f),
            end = Offset(shimmerOffset, 0f)
        )
        Box(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .height(28.dp)
                .width(120.dp)
                .clip(RoundedCornerShape(50))
                .background(shimmerBrush)
        )
        return
    }

    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        )
    }
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
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by animateFloatAsState(
        targetValue = if (interactionSource.collectIsPressedAsState().value) 0.96f else 1f,
        label = "row_scale"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = isPressed
                scaleY = isPressed
            }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = androidx.compose.foundation.LocalIndication.current,
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
            TimelineMediaItem(
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
                TimelineMediaItem(
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
                        TimelineMediaItem(
                            photo = displayPhotos[0],
                            timelineViewModel = timelineViewModel,
                            contentDescription = null,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            contentScale = ContentScale.Crop
                        )
                        if (displayPhotos.size >= 2) {
                            Spacer(modifier = Modifier.width(1.dp))
                            TimelineMediaItem(
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
                            TimelineMediaItem(
                                photo = displayPhotos[2],
                                timelineViewModel = timelineViewModel,
                                contentDescription = null,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                contentScale = ContentScale.Crop
                            )
                            if (displayPhotos.size >= 4) {
                                Spacer(modifier = Modifier.width(1.dp))
                                TimelineMediaItem(
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
private fun TimelineMediaItem(
    photo: PhotoMeta,
    timelineViewModel: TimelineViewModel,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit
) {
    // Observe the pre-resolved URL map. When the ViewModel finishes resolving in the background,
    // this recomposition will fire and the image will snap in — no coroutines in Compose.
    val resolvedUrls by timelineViewModel.resolvedImageUrls.collectAsState()
    val url = resolvedUrls[photo.telegramFileId]
    val context = LocalContext.current
    
    val imageRequest = remember(url) {
        coil.request.ImageRequest.Builder(context)
            .data(url)
            .crossfade(250)
            .memoryCacheKey(photo.telegramFileId)
            .diskCacheKey(photo.telegramFileId)
            .build()
    }

    Box(modifier = modifier) {
        AsyncImage(
            model = imageRequest,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = contentScale,
            placeholder = null,
            error = null
        )
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (photo.isMotionPhoto) {
                Icon(
                    imageVector = Icons.Default.PlayCircle,
                    contentDescription = "Motion Photo",
                    tint = Color.White,
                    modifier = Modifier
                        .size(16.dp)
                        .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                        .padding(2.dp)
                )
            } else if (photo.telegramDocumentId != null) {
                Icon(
                    imageVector = Icons.Default.HighQuality,
                    contentDescription = "HD Content",
                    tint = Color.White,
                    modifier = Modifier
                        .size(16.dp)
                        .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                        .padding(2.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyTimelineState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.PhotoLibrary,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Your trip photos will appear here.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Add photos to start sharing the journey.",
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
        dragHandle = null,
        containerColor = Color.Transparent
    ) {
        GlassBottomSheetContent {
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
}

@Composable
private fun PhotoPagerOverlay(
    initialIndex: Int,
    photoList: List<PhotoMeta>,
    timelineViewModel: TimelineViewModel,
    isAdmin: Boolean,
    onClose: () -> Unit,
    onNavigateToOriginal: (String) -> Unit
) {
    val selectedPhotoIds by timelineViewModel.selectedPhotoIds.collectAsState()
    val pagerState = rememberPagerState(initialPage = initialIndex, pageCount = { photoList.size })
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var isZoomed by remember { mutableStateOf(false) }

    LaunchedEffect(initialIndex) {
        pagerState.scrollToPage(initialIndex)
    }

    var dragOffset by remember { mutableFloatStateOf(0f) }
    val draggableState = rememberDraggableState { delta ->
        if (!isZoomed) {
            dragOffset += delta
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = (1f - (kotlin.math.abs(dragOffset) / 1000f)).coerceIn(0f, 1f)))
            .draggable(
                state = draggableState,
                orientation = androidx.compose.foundation.gestures.Orientation.Vertical,
                onDragStopped = {
                    if (kotlin.math.abs(dragOffset) > 300f) {
                        onClose()
                    } else {
                        dragOffset = 0f
                    }
                }
            )
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            pageSpacing = 16.dp,
            userScrollEnabled = !isZoomed
        ) { page ->
            var scale by remember { mutableStateOf(1f) }
            var offset by remember { mutableStateOf(Offset.Zero) }

            LaunchedEffect(scale) {
                isZoomed = scale > 1f
            }

            LaunchedEffect(pagerState.isScrollInProgress) {
                if (pagerState.isScrollInProgress) {
                    scale = 1f
                    offset = Offset.Zero
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(page) {
                        awaitEachGesture {
                            // Wait for first pointer
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val event = awaitPointerEvent()
                                val fingerCount = event.changes.count { it.pressed }
                                if (fingerCount >= 2) {
                                    // Pinch-to-zoom: always active
                                    val zoom = event.calculateZoom()
                                    val pan = event.calculatePan()
                                    scale = (scale * zoom).coerceIn(1f, 5f)
                                    if (scale > 1f) {
                                        val maxX = (size.width * (scale - 1)) / 2
                                        val maxY = (size.height * (scale - 1)) / 2
                                        offset = Offset(
                                            x = (offset.x + pan.x).coerceIn(-maxX, maxX),
                                            y = (offset.y + pan.y).coerceIn(-maxY, maxY)
                                        )
                                    } else {
                                        offset = Offset.Zero
                                    }
                                    event.changes.forEach { it.consume() }
                                } else if (fingerCount == 1 && isZoomed) {
                                    // Single-finger pan: only when zoomed
                                    val pan = event.calculatePan()
                                    val maxX = (size.width * (scale - 1)) / 2
                                    val maxY = (size.height * (scale - 1)) / 2
                                    offset = Offset(
                                        x = (offset.x + pan.x).coerceIn(-maxX, maxX),
                                        y = (offset.y + pan.y).coerceIn(-maxY, maxY)
                                    )
                                    event.changes.forEach { it.consume() }
                                }
                                // Single finger + NOT zoomed: don't consume → pager handles swipe
                            } while (event.changes.any { it.pressed })
                        }
                    }
                    .pointerInput(page) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (scale > 1f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                } else {
                                    scale = 2.5f
                                }
                            }
                        )
                    }
            ) {
                TelegramAsyncImage(
                    photo = photoList[page],
                    botToken = "",
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y + dragOffset
                        },
                    timelineViewModel = timelineViewModel,
                    contentScale = ContentScale.Fit
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .statusBarsPadding()
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
        }

        // Action Buttons Row (Bottom Center above thumbnail strip)
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 120.dp) // Padded above the thumbnail surface
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // View Original / Motion button
            val currentPhoto = photoList[pagerState.currentPage]
            if (currentPhoto.isMotionPhoto || currentPhoto.telegramDocumentId != null) {
                IconButton(
                    onClick = {
                        onNavigateToOriginal(currentPhoto.photoId)
                    }
                ) {
                    if (currentPhoto.isMotionPhoto) {
                        Icon(Icons.Default.PlayCircle, contentDescription = "View Motion Photo", tint = Color.White)
                    } else {
                        Icon(Icons.Default.HighQuality, contentDescription = "View Original Quality", tint = Color.White)
                    }
                }
            }
            IconButton(
                onClick = { 
                    timelineViewModel.sharePhotos(context, listOf(photoList[pagerState.currentPage])) 
                }
            ) {
                Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White)
            }
            IconButton(
                onClick = { 
                    timelineViewModel.downloadPhoto(context, photoList[pagerState.currentPage]) 
                }
            ) {
                Icon(Icons.Default.Download, contentDescription = "Download", tint = Color.White)
            }
            if (isAdmin) {
                IconButton(
                    onClick = { 
                    timelineViewModel.deletePhoto(photoList[pagerState.currentPage].photoId)
                    if (photoList.size <= 1) onClose()
                    }
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.White)
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
                            botToken = "",
                            modifier = Modifier.fillMaxSize(),
                            timelineViewModel = timelineViewModel
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

