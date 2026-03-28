package com.routepix.ui.timeline

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.routepix.data.local.RoutepixDatabase
import com.routepix.data.model.PhotoMeta
import com.routepix.data.model.Trip
import com.routepix.data.remote.RetrofitClient
import com.routepix.data.repository.TripRepository
import com.routepix.util.PhotoUtils
import com.routepix.worker.PhotoUploadWorker
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap

data class UploadProgress(
    val uploaded: Int = 0,
    val total: Int = 0,
    val isActive: Boolean = false,
    val isPreparing: Boolean = false,
    val currentBatch: Int = 0,
    val totalBatches: Int = 0,
    val batchUploaded: Int = 0,
    val batchSize: Int = 0
) {
    val percentage: Int get() = if (total > 0) (uploaded * 100) / total else 0
}

enum class ViewMode {
    DETAILED, GRID
}

class TimelineViewModel(application: Application, savedStateHandle: SavedStateHandle) : AndroidViewModel(application) {

    private val tripId: String = savedStateHandle["tripId"] ?: ""
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val repository = TripRepository(firestore, auth)
    private val workManager = WorkManager.getInstance(application)

    private val _activeTrip = MutableStateFlow<Trip?>(null)
    val activeTrip: StateFlow<Trip?> = _activeTrip.asStateFlow()

    private val botTokenCache = ConcurrentHashMap<String, String>()

    private val _photos = MutableStateFlow<List<PhotoMeta>>(emptyList())
    val photos: StateFlow<List<PhotoMeta>> = _photos.asStateFlow()

    private val _userNames = MutableStateFlow<Map<String, String>>(emptyMap())
    val userNames: StateFlow<Map<String, String>> = _userNames.asStateFlow()

    private val _sortMode = MutableStateFlow<SortMode>(SortMode.ByTag)
    val sortMode: StateFlow<SortMode> = _sortMode.asStateFlow()

    /**
     * Upload progress derived from WorkManager's live progress data.
     * The worker reports after every single photo, so this is always current.
     * We also check pendingCount to know when the upload is truly done.
     */
    val uploadProgress: StateFlow<UploadProgress> = combine(
        RoutepixDatabase.getInstance(application).queuedPhotoDao().getPendingCountFlow(tripId),
        workManager.getWorkInfosByTagFlow("upload_$tripId")
    ) { pendingCount, workInfos ->
        // Find the running worker
        val runningInfo = workInfos.firstOrNull { it.state == androidx.work.WorkInfo.State.RUNNING }

        if (runningInfo != null) {
            // Worker is running — use its reported progress
            val p = runningInfo.progress
            val uploaded = p.getInt(PhotoUploadWorker.KEY_UPLOADED, 0)
            val total = p.getInt(PhotoUploadWorker.KEY_TOTAL, 0)
            val currentBatch = p.getInt(PhotoUploadWorker.KEY_CURRENT_BATCH, 0)
            val totalBatches = p.getInt(PhotoUploadWorker.KEY_TOTAL_BATCHES, 0)
            val batchUploaded = p.getInt(PhotoUploadWorker.KEY_BATCH_UPLOADED, 0)
            val batchSize = p.getInt(PhotoUploadWorker.KEY_BATCH_SIZE, 0)

            UploadProgress(
                uploaded = uploaded,
                total = if (total > 0) total else pendingCount,
                isActive = true,
                currentBatch = currentBatch,
                totalBatches = totalBatches,
                batchUploaded = batchUploaded,
                batchSize = batchSize
            )
        } else if (pendingCount > 0) {
            // Worker not running yet but there are pending photos (still enqueuing)
            UploadProgress(uploaded = 0, total = pendingCount, isActive = true, isPreparing = true)
        } else {
            UploadProgress()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UploadProgress())

    fun cancelUpload() {
        viewModelScope.launch {
            RoutepixDatabase.getInstance(getApplication()).queuedPhotoDao().deleteForTrip(tripId)
            workManager.cancelAllWorkByTag("upload_$tripId")
        }
    }

    val availableTags: StateFlow<List<String>> = _photos.map { photos ->
        photos.mapNotNull { it.tag }.distinct().sorted()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val groupedPhotos: StateFlow<Map<String, List<PhotoMeta>>> =
        combine(_photos, _sortMode, _userNames) { photoList, mode, _ ->
            groupPhotos(photoList, mode)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val urlCache = ConcurrentHashMap<String, String>()
    private val fetchedUserIds = mutableSetOf<String>()

    init {
        loadTrip()
        observePhotos()
    }

    fun setSortMode(mode: SortMode) {
        _sortMode.value = mode
    }

    private val _viewMode = MutableStateFlow(ViewMode.GRID)
    val viewMode: StateFlow<ViewMode> = _viewMode.asStateFlow()

    data class ShareProgress(
        val inProgress: Boolean = false,
        val current: Int = 0,
        val total: Int = 0
    )

    private val _shareProgress = MutableStateFlow(ShareProgress())
    val shareProgress: StateFlow<ShareProgress> = _shareProgress.asStateFlow()

    fun setViewMode(mode: ViewMode) {
        _viewMode.value = mode
    }

    fun resolveImageUrl(photo: PhotoMeta): Flow<String?> = activeTrip
        .filterNotNull()
        .flatMapLatest { trip ->
            flow {
                val fileId = photo.telegramFileId
                urlCache[fileId]?.let {
                    emit(it)
                    return@flow
                }
                val adminUid = trip.adminUid
                val token = getBotToken(adminUid)
                if (token == null) {
                    emit(null)
                    return@flow
                }
                try {
                    val response = RetrofitClient.telegramApi.getFile(token, fileId)
                    val filePath = response.result?.filePath
                    if (filePath != null) {
                        val url = "https://api.telegram.org/file/bot$token/$filePath"
                        urlCache[fileId] = url
                        emit(url)
                    } else {
                        emit(null)
                    }
                } catch (_: Exception) {
                    emit(null)
                }
            }
        }

    /**
     * Resolve the high-quality download URL for a photo.
     * Uses the document version (original quality) only.
     */
    fun resolveDocumentUrl(photo: PhotoMeta): Flow<String?> = activeTrip
        .filterNotNull()
        .flatMapLatest { trip ->
            flow {
                val fileId = photo.telegramDocumentId ?: photo.telegramFileId
                if (fileId.isEmpty()) {
                    emit(null)
                    return@flow
                }
                urlCache[fileId]?.let {
                    emit(it)
                    return@flow
                }
                val adminUid = trip.adminUid
                val token = getBotToken(adminUid)
                if (token == null) {
                    emit(null)
                    return@flow
                }
                try {
                    val response = RetrofitClient.telegramApi.getFile(token, fileId)
                    val filePath = response.result?.filePath
                    if (filePath != null) {
                        val url = "https://api.telegram.org/file/bot$token/$filePath"
                        urlCache[fileId] = url
                        emit(url)
                    } else {
                        emit(null)
                    }
                } catch (_: Exception) {
                    emit(null)
                }
            }
        }

    fun prefetchThumbnails(context: android.content.Context, photos: List<PhotoMeta>) {
        viewModelScope.launch {
            val imageLoader = coil.Coil.imageLoader(context)
            photos.take(10).forEach { photo ->
                val url = resolveImageUrl(photo).firstOrNull()
                if (url != null) {
                    val request = coil.request.ImageRequest.Builder(context)
                        .data(url)
                        .size(coil.size.Size(256, 256))
                        .memoryCacheKey(photo.telegramFileId)
                        .diskCacheKey(photo.telegramFileId)
                        .build()
                    imageLoader.enqueue(request)
                }
            }
        }
    }

    fun downloadPhoto(context: android.content.Context, photo: PhotoMeta, albumName: String? = null) {
        viewModelScope.launch {
            val user = com.routepix.data.repository.UserRepository(
                FirebaseFirestore.getInstance(), 
                com.google.firebase.auth.FirebaseAuth.getInstance()
            ).getCurrentUser()
            val useGallery = user?.showDownloadedPhotosInGallery ?: false
            
            val url = resolveDocumentUrl(photo).firstOrNull()
            if (url != null) {
                com.routepix.util.ImageDownloadManager.saveToAppStorage(
                    context, url, "RoutePix_${photo.photoId}.jpg",
                    subfolder = photo.tag ?: "Uncategorized"
                )
                
                if (useGallery) {
                    com.routepix.util.DownloadUtils.enqueueDownload(
                        context, 
                        url, 
                        "RoutePix_${photo.photoId}.jpg",
                        albumName
                    )
                } else {
                    launch(kotlinx.coroutines.Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Photo saved", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    fun downloadAlbum(context: android.content.Context, photos: List<PhotoMeta>, albumName: String) {
        viewModelScope.launch {
            launch(kotlinx.coroutines.Dispatchers.Main) {
                android.widget.Toast.makeText(context, "Download in progress...", android.widget.Toast.LENGTH_SHORT).show()
            }
            
            val user = com.routepix.data.repository.UserRepository(
                FirebaseFirestore.getInstance(), 
                com.google.firebase.auth.FirebaseAuth.getInstance()
            ).getCurrentUser()
            val useGallery = user?.showDownloadedPhotosInGallery ?: false
            
            val deferredDownloads = photos.map { photo ->
                async(Dispatchers.IO) {
                    val url = resolveDocumentUrl(photo).firstOrNull()
                    if (url != null) {
                        val file = com.routepix.util.ImageDownloadManager.saveToAppStorage(
                            context, url, "RoutePix_${photo.photoId}.jpg",
                            subfolder = photo.tag ?: "Uncategorized"
                        )
                        if (file != null) {
                            if (useGallery) {
                                com.routepix.util.DownloadUtils.enqueueDownload(
                                    context, 
                                    url, 
                                    "RoutePix_${photo.photoId}.jpg",
                                    albumName
                                )
                            }
                            return@async true
                        }
                    }
                    false
                }
            }
            
            val results = deferredDownloads.awaitAll()
            val savedCount = results.count { it }

            if (!useGallery && savedCount > 0) {
                launch(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "$savedCount photos saved", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun sharePhotos(context: android.content.Context, photos: List<PhotoMeta>) {
        if (photos.isEmpty()) return
        
        viewModelScope.launch {
            val photosToShare = photos.take(20)
            _shareProgress.value = ShareProgress(inProgress = true, current = 0, total = photosToShare.size)
            
            val uris = java.util.concurrent.ConcurrentLinkedQueue<android.net.Uri>()
            val currentProgress = java.util.concurrent.atomic.AtomicInteger(0)
            
            val deferredShares = photosToShare.map { photo ->
                async(Dispatchers.IO) {
                    val url = resolveDocumentUrl(photo).firstOrNull()
                    if (url != null) {
                        val file = com.routepix.util.ImageDownloadManager.downloadToCache(
                            context, url, "Share_${photo.photoId}.jpg"
                        )
                        if (file != null) {
                            uris.add(com.routepix.util.ImageDownloadManager.uriForFile(context, file))
                        }
                    }
                    val count = currentProgress.incrementAndGet()
                    _shareProgress.value = ShareProgress(inProgress = true, current = count, total = photosToShare.size)
                }
            }
            deferredShares.awaitAll()
            
            _shareProgress.value = ShareProgress(inProgress = false)
            
            if (uris.isEmpty()) return@launch
            
            val intent = android.content.Intent().apply {
                action = if (uris.size == 1) android.content.Intent.ACTION_SEND else android.content.Intent.ACTION_SEND_MULTIPLE
                type = "image/jpeg"
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (uris.size == 1) {
                    putExtra(android.content.Intent.EXTRA_STREAM, uris.first())
                } else {
                    putParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM, ArrayList(uris))
                }
            }
            
            launch(Dispatchers.Main) {
                context.startActivity(android.content.Intent.createChooser(intent, "Share via..."))
            }
            
            // Clean up cache roughly after 5 minutes
            launch(Dispatchers.IO) {
                delay(300_000)
                com.routepix.util.ImageDownloadManager.cleanShareCache(context)
            }
        }
    }

    fun deletePhoto(photoId: String) {
        viewModelScope.launch {
            repository.deletePhoto(tripId, photoId)
        }
    }

    fun deleteAlbum(photos: List<PhotoMeta>) {
        viewModelScope.launch {
            photos.forEach { photo ->
                repository.deletePhoto(tripId, photo.photoId)
            }
        }
    }

    fun deletePhotos(photoIds: List<String>) {
        viewModelScope.launch {
            photoIds.forEach { photoId ->
                repository.deletePhoto(tripId, photoId)
            }
            clearSelection()
        }
    }

    private fun loadTrip() {
        viewModelScope.launch {
            try {
                val doc = firestore.collection("trips").document(tripId).get().await()
                _activeTrip.value = doc.toObject(Trip::class.java)
            } catch (_: Exception) {  }
        }
    }

    private fun observePhotos() {
        viewModelScope.launch {
            photosFlow().collect { photoList ->
                _photos.value = photoList
                resolveUserNames(photoList)
                scanForMotionPhotos(photoList)
            }
        }
    }

    private fun photosFlow(): Flow<List<PhotoMeta>> = callbackFlow {
        val reg = firestore.collection("trips").document(tripId)
            .collection("photos")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) { trySend(emptyList()); return@addSnapshotListener }
                val list = snap?.toObjects(PhotoMeta::class.java) ?: emptyList()
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    private fun resolveUserNames(photoList: List<PhotoMeta>) {
        val newUids = photoList.map { it.uploaderUid }
            .distinct()
            .filter { it.isNotEmpty() && it !in fetchedUserIds }

        if (newUids.isEmpty()) return

        viewModelScope.launch {
            for (uid in newUids) {
                try {
                    val doc = firestore.collection("users").document(uid).get().await()
                    val name = doc.getString("displayName") 
                        ?: doc.getString("email")?.substringBefore("@")
                        ?: "User ${uid.take(4)}"
                    _userNames.value = _userNames.value + (uid to name)
                    fetchedUserIds.add(uid)
                } catch (_: Exception) {
                    fetchedUserIds.add(uid)
                }
            }
        }
    }

    private suspend fun getBotToken(adminUid: String): String? {
        botTokenCache[adminUid]?.let { return it }
        val trip = _activeTrip.value
        if (trip != null && !trip.telegramBotToken.isNullOrBlank()) {
            botTokenCache[adminUid] = trip.telegramBotToken
            return trip.telegramBotToken
        }
        return try {
            val doc = firestore.collection("users").document(adminUid).get().await()
            val token = doc.getString("telegramBotToken")
            if (token != null) {
                botTokenCache[adminUid] = token
            }
            token
        } catch (_: Exception) {
            null
        }
    }

    private fun groupPhotos(
        photoList: List<PhotoMeta>,
        mode: SortMode
    ): Map<String, List<PhotoMeta>> {
        if (photoList.isEmpty()) return emptyMap()

        // Filter out photos that are duplicated across multiple tags 
        // to prevent them from showing up twice in the Date or Uploader grids!
        val displayList = if (mode !is SortMode.ByTag) {
            photoList.distinctBy { it.md5Hash ?: it.photoId }
        } else {
            photoList
        }

        return when (mode) {
            is SortMode.ByDate -> {
                displayList.groupBy { photo ->
                    SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                        .format(Date(photo.timestamp))
                }
            }
            is SortMode.ByUploader -> {
                displayList.groupBy { photo ->
                    photo.uploaderUid
                }.mapKeys { (uid, _) ->
                    val name = _userNames.value[uid]
                    if (!name.isNullOrBlank()) name else uid
                }
            }
            is SortMode.ByTag -> {
                displayList.groupBy { it.tag ?: "Untagged" }
            }
        }
    }

    private val _selectedPhotoIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedPhotoIds: StateFlow<Set<String>> = _selectedPhotoIds.asStateFlow()

    fun toggleSelection(photoId: String) {
        _selectedPhotoIds.update { current ->
            if (photoId in current) current - photoId else current + photoId
        }
    }

    fun selectAll(photoIds: List<String>) {
        _selectedPhotoIds.update { current ->
            current + photoIds
        }
    }

    fun clearSelection() {
        _selectedPhotoIds.value = emptySet()
    }

    fun updatePhotoTags(photoIds: List<String>, newTag: String?) {
        val tId = _activeTrip.value?.tripId ?: tripId
        viewModelScope.launch {
            try {
                val db = FirebaseFirestore.getInstance()
                val batch = db.batch()
                
                photoIds.forEach { id ->
                    val ref = db.collection("trips").document(tId)
                        .collection("photos").document(id)
                    batch.update(ref, "tag", newTag)
                }
                
                batch.commit().await()
                clearSelection()
            } catch (e: Exception) {
                Log.e("TimelineVM", "Failed to update tags", e)
            }
        }
    }

    fun markAsMotionPhoto(photoId: String) {
        val tId = _activeTrip.value?.tripId ?: tripId
        viewModelScope.launch {
            try {
                FirebaseFirestore.getInstance()
                    .collection("trips").document(tId)
                    .collection("photos").document(photoId)
                    .update("isMotionPhoto", true)
                    .await()
            } catch (e: Exception) {
                Log.e("TimelineVM", "Failed to mark photo $photoId as motion photo", e)
            }
        }
    }

    // Track which photos we've already scanned to avoid duplicate work
    private val scannedPhotoIds = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    
    companion object {
        // Application-scoped scope that survives ViewModel destruction
        private val scanScope = kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + Dispatchers.IO
        )
        private val scanMutex = kotlinx.coroutines.sync.Mutex()
        private var isScanRunning = false
    }

    /**
     * Background scan: for photos that have an original (telegramDocumentId) but
     * isMotionPhoto==false, download the original file and check for an embedded
     * MP4 ftyp box. If found, permanently update Firestore.
     * 
     * Uses a companion-object scope so the scan survives ViewModel destruction.
     * Uses a Mutex to ensure only one scan runs at a time.
     */
    fun scanForMotionPhotos(photos: List<PhotoMeta>) {
        val candidates = photos.filter {
            it.telegramDocumentId != null && !it.isMotionPhoto && it.photoId !in scannedPhotoIds
        }
        if (candidates.isEmpty()) return
        if (isScanRunning) return
        
        // Mark all candidates upfront
        candidates.forEach { scannedPhotoIds.add(it.photoId) }
        
        // Capture needed values before launching (ViewModel may be destroyed)
        val tripFlow = _activeTrip
        val app = getApplication<Application>()
        val firestore = this.firestore
        
        scanScope.launch {
            if (!scanMutex.tryLock()) return@launch
            isScanRunning = true
            try {
                val trip = tripFlow.filterNotNull().first()
                val token = getBotToken(trip.adminUid)
                if (token == null) {
                    Log.e("TimelineVM", "scanForMotionPhotos: no bot token")
                    return@launch
                }
                Log.d("TimelineVM", "scanForMotionPhotos: scanning ${candidates.size} photos")
                
                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                
                for (photo in candidates) {
                    try {
                        val docId = photo.telegramDocumentId ?: continue
                        val response = RetrofitClient.telegramApi.getFile(token, docId)
                        val filePath = response.result?.filePath ?: continue
                        val url = "https://api.telegram.org/file/bot$token/$filePath"
                        
                        val tempFile = java.io.File(
                            app.cacheDir, 
                            "motion_scan_${photo.photoId}.tmp"
                        )
                        try {
                            val request = okhttp3.Request.Builder().url(url).build()
                            client.newCall(request).execute().use { resp ->
                                resp.body?.byteStream()?.use { input ->
                                    tempFile.outputStream().use { output -> input.copyTo(output) }
                                }
                            }
                            
                            val mp4 = PhotoUtils.extractMotionPhotoVideo(app, tempFile)
                            val hasMotion = mp4 != null
                            mp4?.delete()
                            
                            Log.d("TimelineVM", "scan: ${photo.photoId} hasMotion=$hasMotion (${tempFile.length()} bytes)")
                            if (hasMotion) {
                                // Update Firestore directly (ViewModel may be dead)
                                try {
                                    firestore.collection("trips").document(photo.tripId)
                                        .collection("photos").document(photo.photoId)
                                        .update("isMotionPhoto", true).await()
                                    Log.d("TimelineVM", "Marked ${photo.photoId} as motion photo")
                                } catch (e: Exception) {
                                    Log.e("TimelineVM", "Failed to update Firestore for ${photo.photoId}", e)
                                }
                            }
                        } finally {
                            tempFile.delete()
                        }
                    } catch (e: Exception) {
                        Log.w("TimelineVM", "Motion scan failed for ${photo.photoId}", e)
                    }
                }
                Log.d("TimelineVM", "scanForMotionPhotos: complete")
            } finally {
                isScanRunning = false
                scanMutex.unlock()
            }
        }
    }
}
