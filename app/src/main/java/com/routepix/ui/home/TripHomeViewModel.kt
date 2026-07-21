package com.routepix.ui.home


import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.routepix.data.model.Trip
import com.routepix.data.repository.TripRepository
import com.routepix.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class TripHomeUiState(
    val displayName: String = "",
    val trips: List<Trip> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

class TripHomeViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val repository = TripRepository(firestore, auth)
    private val userRepository = UserRepository(firestore, auth)

    private val _uiState = MutableStateFlow(TripHomeUiState())
    val uiState: StateFlow<TripHomeUiState> = _uiState.asStateFlow()

    private val _coverPhotoFileIds = MutableStateFlow<Map<String, List<String>>>(emptyMap()) // tripId → list of fileIds
    /** Maps tripId → list of telegramFileIds for cover photos (up to 4 for collage). */
    val coverPhotoFileIds: StateFlow<Map<String, List<String>>> = _coverPhotoFileIds.asStateFlow()

    init {
        loadUserProfile()
        observeTrips()
    }

    private fun loadUserProfile() {
        viewModelScope.launch {
            repository.syncLegacyInvites()
            val user = userRepository.getCurrentUser()
            _uiState.value = _uiState.value.copy(
                displayName = user?.displayName?.ifBlank { null } 
                    ?: auth.currentUser?.displayName 
                    ?: "Traveler"
            )
        }
    }

    private var hasWarmStartedThisSession = false

    private fun observeTrips() {
        viewModelScope.launch {
            repository.getTripsForCurrentUser()
                .catch { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = e.message
                    )
                }
                .collect { trips ->
                    _uiState.value = _uiState.value.copy(
                        trips = trips,
                        isLoading = false
                    )
                    // Run once per process lifetime (not per Firestore update).
                    // warmFromDisk: instantly rebuilds the URL map from disk so
                    //   Coil can serve cached bytes on the first frame.
                    // prefetchAllTrips: re-resolves anything stale (>23h) or new —
                    //   runs every session so Telegram CDN URLs never silently expire.
                    if (!hasWarmStartedThisSession) {
                        hasWarmStartedThisSession = true
                        com.routepix.data.cache.ThumbnailCache.warmFromDisk(trips)
                        com.routepix.data.cache.ThumbnailCache.prefetchAllTrips(trips)
                        resolveCoverPhotos(trips)
                    }
                }
        }
    }

    // HD-resolved cover URLs: tripId → list of CDN URLs (not file IDs)
    private val _coverPhotoUrls = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val coverPhotoUrls: StateFlow<Map<String, List<String>>> = _coverPhotoUrls.asStateFlow()

    /**
     * For each trip, resolve up to 4 HD cover photo URLs:
     * 1. If Trip.coverPhotoFileId is set, use it as the single cover.
     * 2. Otherwise, pick up to 4 photos spread across the timeline.
     *
     * Uses telegramDocumentId (original quality) when available,
     * falling back to telegramFileId (compressed thumbnail) only if needed.
     * Resolves the CDN URL directly via RetrofitClient — bypasses ThumbnailCache.
     */
    private fun resolveCoverPhotos(trips: List<Trip>) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            for (trip in trips) {
                // Launch a separate coroutine for EACH trip to prevent network blocking
                launch {
                    try {
                        val token = trip.telegramBotToken
                        if (token.isNullOrBlank()) return@launch

                        // Priority 1: explicit cover photo (single)
                        if (!trip.coverPhotoFileId.isNullOrBlank()) {
                            _coverPhotoFileIds.update { current ->
                                current + (trip.tripId to listOf(trip.coverPhotoFileId))
                            }
                            val url = resolveHdUrl(token, trip.coverPhotoFileId)
                            if (url != null) {
                                _coverPhotoUrls.update { current ->
                                    current + (trip.tripId to listOf(url))
                                }
                            }
                            return@launch
                        }

                        // Priority 2: fetch up to 4 photos for collage
                        val snapshot = firestore.collection("trips")
                            .document(trip.tripId)
                            .collection("photos")
                            .orderBy("timestamp")
                            .get()
                            .await()

                        data class PhotoIds(val fileId: String, val docId: String?)
                        val allPhotos = snapshot.documents.mapNotNull { doc ->
                            val fid = doc.getString("telegramFileId") ?: return@mapNotNull null
                            PhotoIds(fid, doc.getString("telegramDocumentId"))
                        }
                        if (allPhotos.isEmpty()) return@launch

                        // Pick up to 4 evenly spaced photos
                        val picked = if (allPhotos.size <= 4) {
                            allPhotos
                        } else {
                            val step = allPhotos.size.toFloat() / 4
                            (0 until 4).map { i -> allPhotos[(i * step).toInt()] }
                        }

                        // IMMEDIATELY update the fileIds map so thumbnails show up instantly on the home screen
                        _coverPhotoFileIds.update { current ->
                            current + (trip.tripId to picked.map { it.fileId })
                        }

                        // Now resolve HD URLs in the background (prefer docId for original quality)
                        val resolvedUrls = picked.mapNotNull { photo ->
                            val idToResolve = photo.docId ?: photo.fileId
                            resolveHdUrl(token, idToResolve)
                        }
                        if (resolvedUrls.isNotEmpty()) {
                            _coverPhotoUrls.update { current ->
                                current + (trip.tripId to resolvedUrls)
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("TripHomeVM", "Failed to resolve cover for ${trip.tripId}", e)
                    }
                }
            }
        }
    }

    /** Resolve a single file ID to a CDN URL via the Telegram API. */
    private suspend fun resolveHdUrl(token: String, fileId: String): String? {
        return try {
            val response = com.routepix.data.remote.RetrofitClient.telegramApi.getFile(token, fileId)
            val filePath = response.result?.filePath
            if (!filePath.isNullOrEmpty()) {
                "https://api.telegram.org/file/bot$token/$filePath"
            } else null
        } catch (_: Exception) { null }
    }

    fun signOut() {
        auth.signOut()
    }

    suspend fun renameTrip(tripId: String, newName: String) {
        if (newName.isBlank()) return
        try {
            FirebaseFirestore.getInstance().collection("trips").document(tripId)
                .update("name", newName)
                .await()
        } catch (_: Exception) { }
    }

    suspend fun resolveMemberNames(uids: List<String>): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for (uid in uids) {
            try {
                val doc = FirebaseFirestore.getInstance().collection("users").document(uid).get().await()
                result[uid] = doc.getString("displayName") ?: uid
            } catch (_: Exception) {
                result[uid] = uid
            }
        }
        return result
    }

    suspend fun removeMember(tripId: String, memberUid: String) {
        try {
            firestore.collection("trips").document(tripId)
                .update("memberUids", com.google.firebase.firestore.FieldValue.arrayRemove(memberUid))
                .await()
        } catch (_: Exception) { }
    }

    suspend fun exitTrip(tripId: String) {
        repository.exitTrip(tripId)
    }

    fun getCurrentUid(): String = auth.currentUser?.uid ?: ""

    // ── Download Album ──

    private val _downloadProgress = MutableStateFlow<Map<String, String>>(emptyMap()) // tripId -> "3/45"
    val downloadProgress: StateFlow<Map<String, String>> = _downloadProgress.asStateFlow()

    fun downloadTripAlbum(trip: Trip, context: android.content.Context) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // Fetch all photos for this trip
                val photosSnapshot = firestore.collection("trips")
                    .document(trip.tripId)
                    .collection("photos")
                    .get()
                    .await()

                val photos = photosSnapshot.documents.mapNotNull {
                    it.toObject(com.routepix.data.model.PhotoMeta::class.java)
                }

                if (photos.isEmpty()) {
                    _downloadProgress.value = _downloadProgress.value - trip.tripId
                    return@launch
                }

                // Get bot token for this trip
                val tripDoc = firestore.collection("trips").document(trip.tripId).get().await()
                val botToken = tripDoc.getString("botToken") ?: return@launch

                val total = photos.size
                var done = 0
                _downloadProgress.value = _downloadProgress.value + (trip.tripId to "0/$total")

                for (photo in photos) {
                    val docId = photo.telegramDocumentId ?: continue
                    val tag = photo.tag ?: "Untagged"
                    val safeTripName = trip.name.replace(Regex("[^a-zA-Z0-9 _-]"), "").trim()
                    val safeTag = tag.replace(Regex("[^a-zA-Z0-9 _-]"), "").trim()

                    try {
                        // Get file path from Telegram
                        val fileResp = com.routepix.data.remote.RetrofitClient.telegramApi.getFile(botToken, docId)
                        val filePath = fileResp.result?.filePath ?: continue
                        val url = "https://api.telegram.org/file/bot$botToken/$filePath"

                        // Download and save to gallery
                        val filename = "${photo.photoId}.jpg"
                        val resolver = context.contentResolver
                        val collection = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                            android.provider.MediaStore.Images.Media.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY)
                        } else {
                            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                        }

                        val details = android.content.ContentValues().apply {
                            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, filename)
                            put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/RoutePix/$safeTripName/$safeTag")
                                put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
                            }
                        }

                        // Check if already exists
                        val existsSelection = "${android.provider.MediaStore.Images.Media.DISPLAY_NAME} = ?"
                        val existsArgs = arrayOf(filename)
                        var exists = false
                        resolver.query(collection, arrayOf(android.provider.MediaStore.Images.Media._ID), existsSelection, existsArgs, null)?.use { cursor ->
                            if (cursor.count > 0) exists = true
                        }

                        if (!exists) {
                            val uri = resolver.insert(collection, details)
                            if (uri != null) {
                                val request = okhttp3.Request.Builder().url(url).build()
                                val response = okhttp3.OkHttpClient().newCall(request).execute()
                                if (response.isSuccessful) {
                                    resolver.openOutputStream(uri)?.use { outStream ->
                                        response.body?.byteStream()?.use { inStream ->
                                            inStream.copyTo(outStream)
                                        }
                                    }
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                        details.clear()
                                        details.put(android.provider.MediaStore.Images.Media.IS_PENDING, 0)
                                        resolver.update(uri, details, null, null)
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("TripHomeVM", "Failed to download ${photo.photoId}", e)
                    }

                    done++
                    _downloadProgress.value = _downloadProgress.value + (trip.tripId to "$done/$total")
                }
            } catch (e: Exception) {
                android.util.Log.e("TripHomeVM", "downloadTripAlbum failed", e)
            } finally {
                // Clear progress after a brief delay so the user sees "done"
                kotlinx.coroutines.delay(1500)
                _downloadProgress.value = _downloadProgress.value - trip.tripId
            }
        }
    }
}
