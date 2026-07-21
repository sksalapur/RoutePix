@file:OptIn(ExperimentalFoundationApi::class)

package com.routepix.ui.timeline

import android.media.MediaPlayer
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.routepix.R
import com.routepix.data.model.PhotoMeta
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import java.text.SimpleDateFormat
import java.util.*

/**
 * Selects up to [maxPhotos] highlight-worthy photos from the list.
 * Priority: photos with faces > photos with scenic AI labels > recent photos.
 * Results are **shuffled** with a time-based seed so each visit produces a different order.
 */
fun selectHighlights(photos: List<PhotoMeta>, maxPhotos: Int = 18): List<PhotoMeta> {
    if (photos.isEmpty()) return emptyList()
    if (photos.size <= maxPhotos) {
        return photos.shuffled(Random(System.currentTimeMillis()))
    }

    val scored = photos.map { photo ->
        var score = 0.0
        if (photo.faceCount >= 3) score += 5.0
        else if (photo.faceCount == 2) score += 4.0
        else if (photo.faceCount == 1) score += 3.0

        val scenicKeywords = setOf(
            "mountain", "beach", "sunset", "sunrise", "sky", "ocean", "sea",
            "lake", "waterfall", "forest", "landscape", "snow", "architecture",
            "bridge", "temple", "flower", "food", "night", "city"
        )
        val labels = photo.aiLabels?.lowercase()?.split(",")?.map { it.trim() } ?: emptyList()
        score += labels.count { it in scenicKeywords } * 1.5
        score += 0.5
        score += Random(System.currentTimeMillis() xor photo.photoId.hashCode().toLong()).nextDouble() * 2.0
        photo to score
    }

    return scored
        .sortedByDescending { it.second }
        .take(maxPhotos)
        .map { it.first }
        .shuffled(Random(System.currentTimeMillis()))
}

@Composable
fun StoryHighlightScreen(
    photos: List<PhotoMeta>,
    tripName: String,
    timelineViewModel: TimelineViewModel,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val highlights = remember(photos) { selectHighlights(photos) }

    // ── HD image URL resolution ─────────────────────────────────────────────
    val hdUrls = remember { mutableStateMapOf<String, String>() }

    LaunchedEffect(highlights) {
        highlights.forEach { photo ->
            if (!hdUrls.containsKey(photo.photoId)) {
                try {
                    val url = timelineViewModel.resolveDocumentUrl(photo).firstOrNull()
                    if (url != null) {
                        hdUrls[photo.photoId] = url
                    }
                } catch (_: Exception) {}
            }
        }
    }

    val pagerState = rememberPagerState(pageCount = { highlights.size })
    val scope = rememberCoroutineScope()

    var isPlaying by remember { mutableStateOf(true) }
    var isMuted by remember { mutableStateOf(false) }

    // ── Wake lock ───────────────────────────────────────────────────────────
    val activity = context as? android.app.Activity
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // ── MediaPlayer ─────────────────────────────────────────────────────────
    val mediaPlayer = remember {
        try {
            MediaPlayer.create(context, R.raw.soothing_music)?.apply {
                isLooping = true
                setVolume(0.35f, 0.35f)
                start()
            }
        } catch (e: Exception) { null }
    }

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.apply {
                try { if (isPlaying) stop() } catch (_: Exception) {}
                release()
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> mediaPlayer?.pause()
                Lifecycle.Event.ON_RESUME -> {
                    if (isPlaying && !isMuted) {
                        try { mediaPlayer?.start() } catch (_: Exception) {}
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // ── Auto-advance timer ──────────────────────────────────────────────────
    // Use settledPage instead of currentPage to prevent interrupting manual drags
    LaunchedEffect(isPlaying, pagerState.settledPage) {
        if (isPlaying && highlights.isNotEmpty()) {
            delay(3000L)
            if (pagerState.settledPage < highlights.size - 1) {
                pagerState.animateScrollToPage(
                    pagerState.settledPage + 1,
                    animationSpec = tween(400, easing = FastOutSlowInEasing)
                )
            } else {
                onClose()
            }
        }
    }

    // ── Progress bar animation ──────────────────────────────────────────────
    val segmentProgress = remember { Animatable(0f) }
    LaunchedEffect(pagerState.settledPage, isPlaying) {
        segmentProgress.snapTo(0f)
        if (isPlaying) {
            segmentProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(3000, easing = LinearEasing)
            )
        }
    }

    // ── UI ───────────────────────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Photo pager — each page fills the ENTIRE screen.
        // beyondViewportPageCount = 1 pre-loads adjacent pages so transitions are seamless.
        if (highlights.isNotEmpty()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
                userScrollEnabled = true,
                key = { highlights[it].photoId }
            ) { page ->
                val photo = highlights[page]
                val displayUrl = hdUrls[photo.photoId]
                val thumbnailUrls by timelineViewModel.resolvedImageUrls.collectAsState()
                val finalUrl = displayUrl ?: thumbnailUrls[photo.telegramFileId]

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                ) {
                    if (finalUrl != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(finalUrl)
                                .crossfade(300)
                                .build(),
                            contentDescription = "Story photo",
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        // Fallback shimmer if even thumbnail isn't ready
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF1A1A2E)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(32.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    }

                    // ── Left tap zone (previous photo) ──
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(0.3f)
                            .align(Alignment.CenterStart)
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null
                            ) {
                                if (page > 0) {
                                    scope.launch {
                                        pagerState.animateScrollToPage(page - 1)
                                    }
                                }
                            }
                    )

                    // ── Right tap zone (next photo) ──
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(0.3f)
                            .align(Alignment.CenterEnd)
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null
                            ) {
                                if (page < highlights.size - 1) {
                                    scope.launch {
                                        pagerState.animateScrollToPage(page + 1)
                                    }
                                } else {
                                    onClose()
                                }
                            }
                    )
                }
            }

            // ── Bottom gradient overlay (OUTSIDE pager so it doesn't scroll) ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.85f)
                            )
                        )
                    )
            )

            // ── Top gradient for progress bar readability ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.6f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }

        // ── Top controls: progress bars + buttons ───────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                // Use safeDrawing to avoid camera cutouts and status bar
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(top = 16.dp, start = 12.dp, end = 12.dp)
        ) {
            // Progress bars
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                highlights.forEachIndexed { index, _ ->
                    val progress = when {
                        index < pagerState.currentPage -> 1f
                        index == pagerState.currentPage -> segmentProgress.value
                        else -> 0f
                    }
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .weight(1f)
                            .height(2.5.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.25f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Control buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.4f))
                        .clickable {
                            isMuted = !isMuted
                            if (isMuted) mediaPlayer?.setVolume(0f, 0f)
                            else mediaPlayer?.setVolume(0.35f, 0.35f)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isMuted) Icons.Default.MusicOff else Icons.Default.MusicNote,
                        contentDescription = if (isMuted) "Unmute" else "Mute",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.4f))
                        .clickable {
                            isPlaying = !isPlaying
                            if (isPlaying && !isMuted) {
                                try { mediaPlayer?.start() } catch (_: Exception) {}
                            } else mediaPlayer?.pause()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.4f))
                        .clickable { onClose() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // ── Bottom: Photo info & HD Buffering Overlay ───────────────────────
        if (highlights.isNotEmpty()) {
            val currentPhoto = highlights[pagerState.currentPage]
            
            // Show popup if the current page is playing a low-res thumbnail
            val isHdReady = hdUrls.containsKey(currentPhoto.photoId)
            
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.Start
            ) {
                // HD Buffering message
                AnimatedVisibility(
                    visible = !isHdReady,
                    enter = androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.fadeOut()
                ) {
                    Surface(
                        modifier = Modifier.padding(bottom = 16.dp),
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 1.5.dp,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Buffering HD...",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White
                            )
                        }
                    }
                }

                Text(
                    text = tripName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val dateStr = remember(currentPhoto.timestamp) {
                    SimpleDateFormat("MMMM dd, yyyy • h:mm a", Locale.getDefault())
                        .format(Date(currentPhoto.timestamp))
                }
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f)
                )

                val labels = currentPhoto.aiLabels?.split(",")?.map { it.trim() }?.take(3)
                if (!labels.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        labels.forEach { label ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color.White.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = label,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${pagerState.currentPage + 1} / ${highlights.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }
        }
    }
}
