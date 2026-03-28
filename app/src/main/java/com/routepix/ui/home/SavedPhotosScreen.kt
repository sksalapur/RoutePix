@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.routepix.ui.home

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.routepix.util.ImageDownloadManager
import com.routepix.util.PhotoUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun SavedPhotosScreen(
    onBack: () -> Unit,
    viewModel: SavedPhotosViewModel = viewModel()
) {
    val context = LocalContext.current
    val savedPhotos by viewModel.savedPhotos.collectAsState()
    val activeDownloads by ImageDownloadManager.activeDownloads.collectAsState()
    var selectedFiles by remember { mutableStateOf<Set<File>>(emptySet()) }
    var selectedPhotoIndex by rememberSaveable { mutableStateOf<Int?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadSavedPhotos(context)
    }

    // Group by tag
    val grouped = remember(savedPhotos) {
        savedPhotos.groupBy { it.tag }
            .toSortedMap()
    }
    
    // Flat list for pager
    val flatPhotos = remember(savedPhotos) { savedPhotos }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (selectedFiles.isEmpty()) {
                        Text("Saved Photos", fontWeight = FontWeight.Bold)
                    } else {
                        Text("${selectedFiles.size} selected")
                    }
                },
                navigationIcon = {
                    if (selectedFiles.isNotEmpty()) {
                        IconButton(onClick = { selectedFiles = emptySet() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Clear Selection")
                        }
                    } else {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (selectedFiles.isNotEmpty()) {
                        IconButton(onClick = {
                            val uris = selectedFiles.map { ImageDownloadManager.uriForFile(context, it) }
                            val intent = Intent().apply {
                                action = if (uris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE
                                type = "image/jpeg"
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                if (uris.size == 1) {
                                    putExtra(Intent.EXTRA_STREAM, uris.first())
                                } else {
                                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                                }
                            }
                            context.startActivity(Intent.createChooser(intent, "Share via..."))
                            selectedFiles = emptySet()
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "Share")
                        }
                        IconButton(onClick = {
                            viewModel.deletePhotos(selectedFiles)
                            selectedFiles = emptySet()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Selected")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (savedPhotos.isEmpty() && activeDownloads.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.ImageSearch,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No saved photos found",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Download photos from your trips to see them here offline.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                grouped.forEach { (tag, photos) ->
                    // Tag header
                    item(span = { GridItemSpan(3) }) {
                        Text(
                            text = tag,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .padding(top = if (tag == grouped.keys.first()) 0.dp else 8.dp)
                        )
                    }

                    items(photos, key = { it.file.absolutePath }) { savedPhoto ->
                        val file = savedPhoto.file
                        val isSelected = file in selectedFiles
                        val globalIndex = flatPhotos.indexOf(savedPhoto)

                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .combinedClickable(
                                    onClick = {
                                        if (selectedFiles.isNotEmpty()) {
                                            if (isSelected) selectedFiles -= file else selectedFiles += file
                                        } else {
                                            selectedPhotoIndex = globalIndex
                                        }
                                    },
                                    onLongClick = {
                                        if (isSelected) selectedFiles -= file else selectedFiles += file
                                    }
                                )
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(file)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            if (isSelected) {
                                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)))
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(4.dp).align(Alignment.TopEnd)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Full-screen photo viewer overlay
    if (selectedPhotoIndex != null && flatPhotos.isNotEmpty()) {
        SavedPhotoViewer(
            photos = flatPhotos,
            initialIndex = selectedPhotoIndex!!,
            onClose = { selectedPhotoIndex = null },
            onDelete = { savedPhoto ->
                viewModel.deletePhoto(savedPhoto.file)
                if (flatPhotos.size <= 1) {
                    selectedPhotoIndex = null
                }
            },
            onShare = { savedPhoto ->
                val uri = ImageDownloadManager.uriForFile(context, savedPhoto.file)
                val intent = Intent().apply {
                    action = Intent.ACTION_SEND
                    type = "image/jpeg"
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    putExtra(Intent.EXTRA_STREAM, uri)
                }
                context.startActivity(Intent.createChooser(intent, "Share via..."))
            }
        )
    }
}

@Composable
private fun SavedPhotoViewer(
    photos: List<SavedPhoto>,
    initialIndex: Int,
    onClose: () -> Unit,
    onDelete: (SavedPhoto) -> Unit,
    onShare: (SavedPhoto) -> Unit
) {
    val context = LocalContext.current
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, (photos.size - 1).coerceAtLeast(0))
    ) { photos.size }

    var showConfirmDelete by remember { mutableStateOf(false) }

    // Motion photo state
    var motionVideoFile by remember { mutableStateOf<File?>(null) }
    var isCheckingMotion by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Check for motion photo when page changes
    val currentPhoto = if (photos.isNotEmpty() && pagerState.currentPage < photos.size) {
        photos[pagerState.currentPage]
    } else null

    LaunchedEffect(pagerState.currentPage) {
        // Reset motion state on page change
        motionVideoFile?.delete()
        motionVideoFile = null
        isCheckingMotion = false
    }

    // Delete confirmation dialog
    if (showConfirmDelete && currentPhoto != null) {
        AlertDialog(
            onDismissRequest = { showConfirmDelete = false },
            title = { Text("Delete Photo") },
            text = { Text("Are you sure you want to permanently delete this photo from saved?") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(currentPhoto)
                    showConfirmDelete = false
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDelete = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (motionVideoFile != null) {
            // Motion photo player
            SavedMotionPhotoPlayer(videoFile = motionVideoFile!!)
        } else {
            // Photo pager
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                if (page < photos.size) {
                    val photo = photos[page]
                    var scale by remember { mutableStateOf(1f) }
                    var offset by remember { mutableStateOf(Offset.Zero) }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    scale = (scale * zoom).coerceIn(1f, 5f)
                                    if (scale > 1f) {
                                        offset = Offset(offset.x + pan.x, offset.y + pan.y)
                                    } else {
                                        offset = Offset.Zero
                                    }
                                }
                            }
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onDoubleTap = {
                                        scale = if (scale > 1f) 1f else 2.5f
                                        offset = Offset.Zero
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(photo.file)
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                    translationX = offset.x
                                    translationY = offset.y
                                }
                        )
                    }
                }
            }

            // Loading spinner for motion check
            if (isCheckingMotion) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(32.dp).align(Alignment.Center),
                    strokeWidth = 3.dp
                )
            }
        }

        // Top bar with close, share, delete, motion photo
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    motionVideoFile?.delete()
                    motionVideoFile = null
                    onClose()
                },
                modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Motion photo / view original button
                if (currentPhoto != null && motionVideoFile == null) {
                    IconButton(
                        onClick = {
                            scope.launch {
                                isCheckingMotion = true
                                val mp4 = withContext(Dispatchers.IO) {
                                    PhotoUtils.extractMotionPhotoVideo(context, currentPhoto.file)
                                }
                                if (mp4 != null) {
                                    motionVideoFile = mp4
                                }
                                isCheckingMotion = false
                            }
                        },
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
                    ) {
                        Icon(Icons.Default.PlayCircle, contentDescription = "Play Motion Photo", tint = Color.White)
                    }
                } else if (motionVideoFile != null) {
                    // Back to still photo
                    IconButton(
                        onClick = {
                            motionVideoFile?.delete()
                            motionVideoFile = null
                        },
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
                    ) {
                        Icon(Icons.Default.Photo, contentDescription = "View Photo", tint = Color.White)
                    }
                }

                // Share
                if (currentPhoto != null) {
                    IconButton(
                        onClick = { onShare(currentPhoto) },
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White)
                    }
                }

                // Delete
                IconButton(
                    onClick = { showConfirmDelete = true },
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.White)
                }
            }
        }

        // Page indicator
        if (photos.size > 1) {
            Text(
                text = "${pagerState.currentPage + 1} / ${photos.size}",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun SavedMotionPhotoPlayer(videoFile: File) {
    val context = LocalContext.current
    var isMuted by remember { mutableStateOf(true) }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(videoFile)))
            repeatMode = Player.REPEAT_MODE_ALL
            volume = 0f
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        IconButton(
            onClick = {
                isMuted = !isMuted
                exoPlayer.volume = if (isMuted) 0f else 1f
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
        ) {
            Icon(
                imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                contentDescription = if (isMuted) "Unmute" else "Mute",
                tint = Color.White
            )
        }
    }
}
