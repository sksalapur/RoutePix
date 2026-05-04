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
import java.util.concurrent.ConcurrentHashMap

/**
 * App-wide singleton cache for resolved Telegram CDN URLs.
 *
 * Persistence strategy:
 *  - Stores `fileId → "$filePath|$timestamp"` in SharedPreferences.
 *  - On cold start, warmFromDisk() reconstructs URLs instantly so Coil can
 *    serve from its own disk cache immediately (zero network calls on first paint).
 *  - prefetchAllTrips() ALWAYS runs on every app open and re-resolves any
 *    entries older than STALE_THRESHOLD_MS (23 hours), because Telegram CDN
 *    URLs expire after ~1 hour. This keeps URLs fresh without hammering the API.
 *  - Only brand-new photos (never seen before) AND stale entries trigger getFile calls.
 *
 * Key = telegramFileId  |  In-memory value = fully resolved CDN URL
 */
object ThumbnailCache {

    private const val PREFS_NAME = "thumbnail_file_paths_v2"
    private const val SEPARATOR = "|"

    /**
     * Re-resolve any URL older than this. Set to 23 hours — well within Telegram's
     * ~1 hour CDN URL lifetime, ensuring URLs are always fresh on next app open.
     */
    private const val STALE_THRESHOLD_MS = 23L * 60 * 60 * 1000

    // In-memory map: fileId → CDN URL
    private val urlCache = ConcurrentHashMap<String, String>()

    // Track which fileIds were loaded from disk (may have stale URLs)
    // These will be re-resolved by prefetchAllTrips even if already in urlCache
    private val staledFromDisk = ConcurrentHashMap.newKeySet<String>()

    private var prefs: SharedPreferences? = null

    private val _resolvedUrls = MutableStateFlow<Map<String, String>>(emptyMap())
    val resolvedUrls: StateFlow<Map<String, String>> = _resolvedUrls.asStateFlow()

    private val _isPrefetching = MutableStateFlow(false)
    val isPrefetching: StateFlow<Boolean> = _isPrefetching.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Call once from Application.onCreate(). */
    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Load persisted entries from disk so Coil can serve from its own disk cache
     * immediately on the first frame. Marks stale entries so prefetchAllTrips
     * knows to re-resolve them in the background.
     */
    fun warmFromDisk(trips: List<com.routepix.data.model.Trip>) {
        val localPrefs = prefs ?: return
        scope.launch {
            val now = System.currentTimeMillis()
            val allEntries = localPrefs.all

            trips.forEach { trip ->
                val token = trip.telegramBotToken ?: return@forEach
                allEntries.forEach { (fileId, raw) ->
                    if (raw !is String) return@forEach
                    val parts = raw.split(SEPARATOR)
                    val filePath = parts[0]
                    val timestamp = parts.getOrNull(1)?.toLongOrNull() ?: 0L
                    val isStale = (now - timestamp) > STALE_THRESHOLD_MS

                    if (!urlCache.containsKey(fileId)) {
                        // Always populate so UI has something to show Coil immediately
                        urlCache[fileId] = "https://api.telegram.org/file/bot$token/$filePath"
                    }

                    if (isStale) {
                        // Flag for re-resolution by prefetchAllTrips
                        staledFromDisk.add(fileId)
                    }
                }
            }

            if (urlCache.isNotEmpty()) {
                _resolvedUrls.value = HashMap(urlCache)
            }
        }
    }

    fun get(telegramFileId: String): String? = urlCache[telegramFileId]

    fun contains(telegramFileId: String): Boolean =
        urlCache.containsKey(telegramFileId) && !staledFromDisk.contains(telegramFileId)

    fun put(telegramFileId: String, cdnUrl: String, filePath: String) {
        urlCache[telegramFileId] = cdnUrl
        staledFromDisk.remove(telegramFileId)
        val value = "$filePath$SEPARATOR${System.currentTimeMillis()}"
        prefs?.edit()?.putString(telegramFileId, value)?.apply()
        _resolvedUrls.value = HashMap(urlCache)
    }

    /**
     * Called by TripHomeViewModel on every app open.
     *
     * Resolves:
     *  1. Photos never seen before (not in urlCache at all)
     *  2. Photos whose cached URL is stale (> 23 hours old)
     *
     * Photos with a fresh URL are skipped entirely — no network calls.
     * Sets isPrefetching=true only if there is actual work to do.
     */
    fun prefetchAllTrips(trips: List<com.routepix.data.model.Trip>) {
        scope.launch {
            var anyWork = false
            try {
                trips.forEach tripLoop@{ trip ->
                    val token = trip.telegramBotToken ?: return@tripLoop
                    try {
                        val snapshot = FirebaseFirestore.getInstance()
                            .collection("trips").document(trip.tripId)
                            .collection("photos")
                            .get().await()

                        val photos = snapshot.toObjects(PhotoMeta::class.java)

                        // Resolve: brand new (never seen) OR stale (URL expired)
                        val needsResolution = photos.filter {
                            it.telegramFileId.isNotEmpty() &&
                                (!urlCache.containsKey(it.telegramFileId) ||
                                    staledFromDisk.contains(it.telegramFileId))
                        }

                        if (needsResolution.isEmpty()) return@tripLoop

                        if (!anyWork) {
                            anyWork = true
                            _isPrefetching.value = true
                        }

                        needsResolution.chunked(5).forEach { batch ->
                            batch.map { photo ->
                                async {
                                    try {
                                        val response = RetrofitClient.telegramApi
                                            .getFile(token, photo.telegramFileId)
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
     * Called by TimelineViewModel when a specific album is opened.
     * Resolves photos that are either new or stale, same logic as prefetchAllTrips.
     */
    fun prefetchTrip(photos: List<PhotoMeta>, token: String) {
        val needsResolution = photos.filter {
            it.telegramFileId.isNotEmpty() &&
                (!urlCache.containsKey(it.telegramFileId) ||
                    staledFromDisk.contains(it.telegramFileId))
        }
        if (needsResolution.isEmpty()) return

        scope.launch {
            needsResolution.chunked(5).forEach { batch ->
                batch.map { photo ->
                    async {
                        try {
                            val response = RetrofitClient.telegramApi
                                .getFile(token, photo.telegramFileId)
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
