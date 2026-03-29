package com.routepix.ui.timeline

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.routepix.data.model.PhotoMeta
import com.routepix.ui.components.RoutepixLoader
import com.routepix.util.PhotoUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OriginalViewerScreen(
    tripId: String,
    photoId: String,
    onNavigateBack: () -> Unit,
    timelineViewModel: TimelineViewModel
) {
    val photos by timelineViewModel.photos.collectAsState()
    val photo = photos.find { it.photoId == photoId }

    if (photo == null) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            RoutepixLoader(modifier = Modifier.size(48.dp), speed = 1800)
        }
        return
    }

    val context = LocalContext.current

    // Resolve document URL (original quality)
    val documentUrl by remember(photo.photoId) {
        timelineViewModel.resolveDocumentUrl(photo)
    }.collectAsState(initial = null)

    // State
    var isOriginalLoaded by remember { mutableStateOf(false) }
    var motionVideoFile by remember { mutableStateOf<java.io.File?>(null) }
    var localOriginalFile by remember { mutableStateOf<java.io.File?>(null) }
    var isProcessing by remember { mutableStateOf(false) }

    // For all photos: check local cache first, then download if needed
    LaunchedEffect(documentUrl) {
        if (motionVideoFile == null && localOriginalFile == null && !isProcessing) {
            isProcessing = true
            withContext(Dispatchers.IO) {
                // Check if already saved locally (original quality)
                val savedFile = java.io.File(context.filesDir, "saved/RoutePix_${photo.photoId}.jpg")
                // Also check tag subdirectories
                val savedDir = java.io.File(context.filesDir, "saved")
                val tagSavedFile = savedDir.listFiles()
                    ?.filter { it.isDirectory }
                    ?.flatMap { it.listFiles()?.toList() ?: emptyList() }
                    ?.find { it.name == "RoutePix_${photo.photoId}.jpg" }
                
                val sourceFile = when {
                    savedFile.exists() && savedFile.length() > 0 -> savedFile
                    tagSavedFile != null && tagSavedFile.exists() && tagSavedFile.length() > 0 -> tagSavedFile
                    documentUrl != null -> PhotoUtils.downloadToCache(context, documentUrl!!)
                    else -> null
                }
                
                if (sourceFile != null) {
                    val mp4 = PhotoUtils.extractMotionPhotoVideo(context, sourceFile)
                    if (mp4 != null) {
                        motionVideoFile = mp4
                        if (!photo.isMotionPhoto) {
                            timelineViewModel.markAsMotionPhoto(photoId)
                        }
                        // Only delete if it was a temp download, not a saved file
                        if (sourceFile != savedFile && sourceFile != tagSavedFile) {
                            sourceFile.delete()
                        }
                    } else {
                        localOriginalFile = sourceFile
                    }
                }
            }
            isProcessing = false
        }
    }

    // Cleanup temp files on dispose
    DisposableEffect(Unit) {
        onDispose {
            motionVideoFile?.delete()
            localOriginalFile?.delete()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = Color.Black
    ) { paddingVals ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(paddingVals),
            contentAlignment = Alignment.Center
        ) {
            // Check the effective state (either known motion photo, or just discovered to be one)
            val isMotionNow = motionVideoFile != null || (photo.isMotionPhoto && !isOriginalLoaded)

            if (isMotionNow) {
                // ── Motion Photo Flow ──
                if (motionVideoFile != null) {
                    MotionPhotoPlayer(videoFile = motionVideoFile!!)
                } else {
                    // Show thumbnail while downloading + extracting
                    TelegramAsyncImage(
                        photo = photo,
                        botToken = "",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                    // Central loader
                    RoutepixLoader(
                        modifier = Modifier.align(Alignment.Center),
                        size = 48.dp
                    )
                }
            } else {
                // ── Standard Photo Flow ──
                var scale by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(1f) }
                var offset by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }

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
                    Box(modifier = Modifier.fillMaxSize().graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    }) {
                        // Thumbnail as placeholder
                        TelegramAsyncImage(
                            photo = photo,
                            botToken = "",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )

                        // Original quality overlay with crossfade
                        if (localOriginalFile != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(localOriginalFile) // use the already downloaded local file
                                    .crossfade(400)
                                    .listener(
                                        onSuccess = { _, _ -> isOriginalLoaded = true }
                                    )
                                    .build(),
                                contentDescription = "Original Photo",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }

                // Central spinner while loading
                if (!isOriginalLoaded) {
                    RoutepixLoader(
                        modifier = Modifier.align(Alignment.Center),
                        size = 48.dp
                    )
                }
            }
        }
    }
}

@Composable
private fun MotionPhotoPlayer(videoFile: java.io.File) {
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

        // Mute/Unmute toggle
        Surface(
            onClick = {
                isMuted = !isMuted
                exoPlayer.volume = if (isMuted) 0f else 1f
            },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
            color = Color.Black.copy(alpha = 0.5f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
        ) {
            Box(modifier = Modifier.padding(12.dp)) {
                Icon(
                    imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                    contentDescription = if (isMuted) "Unmute" else "Mute",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
