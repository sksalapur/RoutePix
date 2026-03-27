@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.routepix.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.launch
import java.io.File
import com.routepix.util.ImageDownloadManager
import android.content.Intent

@Composable
fun SavedPhotosScreen(
    onBack: () -> Unit,
    viewModel: SavedPhotosViewModel = viewModel()
) {
    val context = LocalContext.current
    val savedFiles by viewModel.savedFiles.collectAsState()
    val activeDownloads by ImageDownloadManager.activeDownloads.collectAsState()
    var selectedFiles by remember { mutableStateOf<Set<File>>(emptySet()) }
    var fileToDelete by remember { mutableStateOf<File?>(null) }
    var selectedPhotoIndex by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadSavedPhotos(context)
    }

    if (fileToDelete != null) {
        AlertDialog(
            onDismissRequest = { fileToDelete = null },
            title = { Text("Delete Photo") },
            text = { Text("Are you sure you want to permanently delete this photo from your offline saved items?") },
            confirmButton = {
                TextButton(onClick = {
                    fileToDelete?.let { viewModel.deletePhoto(it) }
                    fileToDelete = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { fileToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

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
                            selectedFiles.forEach { viewModel.deletePhoto(it) }
                            selectedFiles = emptySet()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Selected")
                        }
                    }
                }
            )
        }
    ) { padding ->
        val downloadingSaved = activeDownloads.filter { it.key.startsWith("RoutePix") }.toList()
        val savedNotDownloading = savedFiles.filter { file -> !activeDownloads.containsKey(file.name) }

        if (downloadingSaved.isEmpty() && savedNotDownloading.isEmpty()) {
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
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
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
                items(downloadingSaved.size) { index ->
                    val (_, url) = downloadingSaved[index]
                    Box(modifier = Modifier.aspectRatio(1f)) {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(url).crossfade(true).build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp),
                                strokeWidth = 3.dp
                            )
                        }
                    }
                }
                
                items(savedNotDownloading, key = { it.absolutePath }) { file ->
                    val isSelected = file in selectedFiles
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .combinedClickable(
                                onClick = {
                                    if (selectedFiles.isNotEmpty()) {
                                        if (isSelected) selectedFiles -= file else selectedFiles += file
                                    } else {
                                        selectedPhotoIndex = savedNotDownloading.indexOf(file) + downloadingSaved.size
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
