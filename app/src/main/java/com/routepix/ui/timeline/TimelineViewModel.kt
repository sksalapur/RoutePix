package com.routepix.ui.timeline

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.routepix.data.model.PhotoMeta
import com.routepix.data.model.Trip
import com.routepix.data.remote.RetrofitClient
import com.routepix.data.repository.TripRepository
import com.routepix.security.SecurityManager
import androidx.work.WorkManager
import androidx.work.WorkInfo
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class UploadProgress(
    val total: Int = 0,
    val finished: Int = 0,
    val isActive: Boolean = false
) {
    val percentage: Int get() = if (total > 0) (finished * 100) / total else 0
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


    val uploadProgress: StateFlow<UploadProgress> = workManager.getWorkInfosByTagFlow("upload_$tripId")
        .map { infoList ->
            if (infoList.isEmpty()) return@map UploadProgress()
            
            val running = infoList.filter { !it.state.isFinished }
            if (running.isEmpty()) return@map UploadProgress()
            
            var progress = 0
            var total = 0
            for (work in running) {
                progress += work.progress.getInt("progress", 0)
                total += work.progress.getInt("total", 0)
            }
            
            if (total == 0) return@map UploadProgress(isActive = true)
            UploadProgress(total = total, finished = progress, isActive = true)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UploadProgress())

    
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

    
    fun downloadPhoto(context: android.content.Context, photo: PhotoMeta, albumName: String? = null) {
        viewModelScope.launch {
            val url = resolveImageUrl(photo).firstOrNull()
            if (url != null) {
                com.routepix.util.DownloadUtils.enqueueDownload(
                    context, 
                    url, 
                    "RoutePix_${photo.telegramFileId.take(8)}.jpg",
                    albumName
                )
            }
        }
    }

    fun downloadAlbum(context: android.content.Context, photos: List<PhotoMeta>, albumName: String) {
        viewModelScope.launch {
            photos.forEach { photo ->
                val url = resolveImageUrl(photo).firstOrNull()
                if (url != null) {
                    com.routepix.util.DownloadUtils.enqueueDownload(
                        context, 
                        url, 
                        "RoutePix_${photo.telegramFileId.take(8)}.jpg",
                        albumName
                    )
                }
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

        return when (mode) {
            is SortMode.ByDate -> {
                photoList.groupBy { photo ->
                    SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                        .format(Date(photo.timestamp))
                }
            }
            is SortMode.ByUploader -> {
                photoList.groupBy { photo ->
                    photo.uploaderUid // Group by UID for stability
                }.mapKeys { (uid, _) ->
                    val name = _userNames.value[uid]
                    if (!name.isNullOrBlank()) name else uid
                }
            }
            is SortMode.ByTag -> {
                photoList.groupBy { it.tag ?: "Untagged" }
            }
        }
    }
}

