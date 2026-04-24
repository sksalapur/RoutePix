package com.routepix.data.cache

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.firestore.FirebaseFirestore
import com.routepix.data.model.PhotoMeta
import com.routepix.data.remote.RetrofitClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * App-wide singleton cache for resolved Telegram CDN URLs.
 *
 * Persistence strategy:
 *  - Stores `fileId → filePath` in SharedPreferences (NOT the full URL).
 *  - `filePath` is the Telegram `result.file_path` value (e.g. "photos/file_123.jpg").
 *  - On cold start the map is loaded from disk and full URLs are reconstructed instantly
 *    from `botToken + filePath` — zero network calls.
 *  - On update / process death, the on-disk map survives; only truly NEW photos trigger
 *    a `getFile` API call.
 *
 * Key = telegramFileId  |  In-memory value = fully resolved CDN URL
 */
object ThumbnailCache {

    private const val PREFS_NAME = "thumbnail_file_paths"

    // In-memory map: fileId → CDN URL (reconstructed at runtime)
    private val urlCache = ConcurrentHashMap<String, String>()

    // Disk map: fileId → filePath (persisted across restarts)
    private var prefs: SharedPreferences? = null

    // StateFlow observed by every composable
    private val _resolvedUrls = MutableStateFlow<Map<String, String>>(emptyMap())
    val resolvedUrls: StateFlow<Map<String, String>> = _resolvedUrls.asStateFlow()

    // True only during the initial all-trips prefetch triggered after login
    private val _isPrefetching = MutableStateFlow(false)
    val isPrefetching: StateFlow<Boolean> = _isPrefetching.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Must be called once from Application.onCreate() before any other cache usage.
     * Loads persisted filePaths from disk and pre-populates the in-memory URL map
     * using the supplied trip token map (fileId → botToken).
     *
     * This is fire-and-forget; the StateFlow will emit as soon as the map is ready.
     */
    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // The URL map will be populated per-trip as tokens become available
    }

    /**
     * Pre-warm the in-memory URL map from persisted filePaths.
     * Called by TripHomeViewModel as soon as trips (and their tokens) are available.
     */
    fun warmFromDisk(trips: List<com.routepix.data.model.Trip>) {
        val localPrefs = prefs ?: return
        scope.launch {
            val allEntries = localPrefs.all
            trips.forEach { trip ->
                val token = trip.telegramBotToken ?: return@forEach
                allEntries.forEach { (fileId, filePath) ->
                    if (filePath is String && !urlCache.containsKey(fileId)) {
                        urlCache[fileId] = "https://api.telegram.org/file/bot$token/$filePath"
                    }
                }
            }
            if (urlCache.isNotEmpty()) {
                _resolvedUrls.value = HashMap(urlCache)
            }
        }
    }

    fun get(telegramFileId: String): String? = urlCache[telegramFileId]

    fun contains(telegramFileId: String): Boolean = urlCache.containsKey(telegramFileId)

    fun put(telegramFileId: String, cdnUrl: String, filePath: String) {
        urlCache[telegramFileId] = cdnUrl
        prefs?.edit()?.putString(telegramFileId, filePath)?.apply()
        _resolvedUrls.value = HashMap(urlCache)
    }

    /**
     * Called by TripHomeViewModel after login.
     * - Skips anything already in the in-memory cache (warmed from disk).
     * - Only calls getFile for truly new photos.
     * - Sets isPrefetching true while work is outstanding.
     */
    fun prefetchAllTrips(trips: List<com.routepix.data.model.Trip>) {
        scope.launch {
            _isPrefetching.value = true
            try {
                trips.forEach { trip ->
                    val token = trip.telegramBotToken ?: return@forEach
                    try {
                        val snapshot = FirebaseFirestore.getInstance()
                            .collection("trips").document(trip.tripId)
                            .collection("photos")
                            .get().await()

                        val photos = snapshot.toObjects(PhotoMeta::class.java)
                        val unresolved = photos.filter {
                            it.telegramFileId.isNotEmpty() && !urlCache.containsKey(it.telegramFileId)
                        }

                        if (unresolved.isEmpty()) return@forEach

                        unresolved.chunked(5).forEach { batch ->
                            batch.map { photo ->
                                async {
                                    try {
                                        val response = RetrofitClient.telegramApi.getFile(token, photo.telegramFileId)
                                        val filePath = response.result?.filePath
                                        if (!filePath.isNullOrEmpty()) {
                                            val url = "https://api.telegram.org/file/bot$token/$filePath"
                                            put(photo.telegramFileId, url, filePath)
                                        }
                                    } catch (_: Exception) {}
                                }
                            }.awaitAll()
                            delay(150)
                        }
                    } catch (_: Exception) {}
                }
            } finally {
                _isPrefetching.value = false
            }
        }
    }

    /**
     * Called by TimelineViewModel when a trip is opened.
     * Only resolves photos not already in memory (disk-warm or prior resolution).
     */
    fun prefetchTrip(photos: List<PhotoMeta>, token: String) {
        val unresolved = photos.filter {
            it.telegramFileId.isNotEmpty() && !urlCache.containsKey(it.telegramFileId)
        }
        if (unresolved.isEmpty()) return

        scope.launch {
            unresolved.chunked(5).forEach { batch ->
                batch.map { photo ->
                    async {
                        try {
                            val response = RetrofitClient.telegramApi.getFile(token, photo.telegramFileId)
                            val filePath = response.result?.filePath
                            if (!filePath.isNullOrEmpty()) {
                                val url = "https://api.telegram.org/file/bot$token/$filePath"
                                put(photo.telegramFileId, url, filePath)
                            }
                        } catch (_: Exception) {}
                    }
                }.awaitAll()
                delay(150)
            }
        }
    }
}
