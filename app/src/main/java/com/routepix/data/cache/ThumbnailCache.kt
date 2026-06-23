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
 *    serve from its own **persistent** disk cache immediately (zero network
 *    calls on first paint — even after days of inactivity).
 *  - prefetchAllTrips() ALWAYS runs on every app open and re-resolves any
 *    entries older than STALE_THRESHOLD_MS (6 days), because Telegram CDN
 *    URLs expire after ~1 hour. This keeps URLs fresh without hammering the
 *    API, while still allowing Coil to serve images from its pixel cache using
 *    the temporarily stale URL for the brief warmup window.
 *  - Only brand-new photos (never seen before) AND truly stale entries (>6d)
 *    trigger getFile calls.
 *
 * Key = telegramFileId  |  In-memory value = fully resolved CDN URL
 *
 * WHY 6 DAYS?
 *  Coil's disk cache (now in filesDir, never auto-cleared) stores image pixels.
 *  Even with a "stale" URL in SharedPreferences, warmFromDisk() emits the
 *  old URL immediately — Coil finds the pixels on disk by diskCacheKey and
 *  renders them. Meanwhile, prefetchAllTrips() re-resolves the URL in the
 *  background and calls put() to refresh it for subsequent loads.
 *
 *  The old 23h threshold caused EVERY app open after a night's sleep to do a
 *  full re-resolve for all photos before they could display. With 6 days,
 *  only photos older than 6 days need re-resolution, greatly reducing cold-
 *  start network traffic while keeping the "images appear instantly" guarantee.
 */
object ThumbnailCache {

    private const val PREFS_NAME = "thumbnail_file_paths_v2"
    private const val SEPARATOR = "|"

    /**
     * Re-resolve any URL older than this.
     *
     * Set to 6 days. Rationale:
     *  - Telegram CDN URLs expire after ~1 hour, but Coil's pixel cache
     *    (now in persistent filesDir) keeps the image bytes indefinitely.
     *  - warmFromDisk() emits even stale URLs so Coil can serve pixels from
     *    its persistent disk cache while fresh URLs are resolved in background.
     *  - 6 days means most users only need a re-resolve once a week per photo,
     *    instead of on every app open.
     */
    private const val STALE_THRESHOLD_MS = 6L * 24 * 60 * 60 * 1000 // 6 days

    // In-memory map: fileId → CDN URL
    private val urlCache = ConcurrentHashMap<String, String>()

    // Track which fileIds were loaded from disk and have a stale (expired) URL.
    // These are still emitted immediately (so Coil can try the pixel cache),
    // but prefetchAllTrips will re-resolve them in the background.
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
     * Load ALL persisted entries from disk immediately on cold start.
     *
     * This is called before any Firestore fetch so Coil can serve image pixels
     * from its persistent disk cache on the very first frame — without waiting
     * for network.
     *
     * Strategy:
     *  - ALL known fileIds (fresh or stale) are loaded into urlCache immediately
     *    and emitted via _resolvedUrls. This lets Coil attempt a disk cache hit
     *    using the diskCacheKey regardless of URL freshness.
     *  - Entries older than STALE_THRESHOLD_MS are added to staledFromDisk so
     *    prefetchAllTrips knows to re-resolve them in the background.
     *  - We do NOT filter by trip here — we load everything from SharedPrefs to
     *    maximise the chance of a Coil disk cache hit on the first frame.
     */
    fun warmFromDisk(trips: List<com.routepix.data.model.Trip>) {
        val localPrefs = prefs ?: return
        scope.launch {
            val now = System.currentTimeMillis()
            val allEntries = localPrefs.all

            // Build a quick lookup of token per trip for URL reconstruction
            val tokenByTripId = trips.associate { it.tripId to it.telegramBotToken }

            // We don't know which fileId belongs to which trip here, so we
            // reconstruct with the first available token as a placeholder.
            // The URL structure is the same (api.telegram.org/file/bot<TOKEN>/<PATH>)
            // and Coil keying is by diskCacheKey (=fileId), NOT the URL, so
            // even a "wrong-token" URL will still cause a disk cache hit.
            // The correct URL is re-resolved by prefetchAllTrips shortly after.
            val fallbackToken = trips.firstOrNull { !it.telegramBotToken.isNullOrBlank() }
                ?.telegramBotToken

            allEntries.forEach { (fileId, raw) ->
                if (raw !is String) return@forEach
                val parts = raw.split(SEPARATOR)
                val filePath = parts[0]
                if (filePath.isBlank()) return@forEach
                val timestamp = parts.getOrNull(1)?.toLongOrNull() ?: 0L
                val isStale = (now - timestamp) > STALE_THRESHOLD_MS

                if (!urlCache.containsKey(fileId)) {
                    // Emit whatever URL we can reconstruct so Coil can find
                    // pixels in its persistent disk cache immediately
                    val token = fallbackToken ?: return@forEach
                    urlCache[fileId] = "https://api.telegram.org/file/bot$token/$filePath"
                }

                if (isStale) {
                    // Flag for background re-resolution by prefetchAllTrips
                    staledFromDisk.add(fileId)
                }
            }

            if (urlCache.isNotEmpty()) {
                _resolvedUrls.value = HashMap(urlCache)
            }
        }
    }

    fun get(telegramFileId: String): String? = urlCache[telegramFileId]

    /**
     * Returns true only if this fileId has a fresh (non-stale) URL in memory.
     * Stale entries are still in urlCache (for immediate Coil disk hits) but
     * contain() returns false so prefetchAllTrips re-resolves them.
     */
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
     *  2. Photos whose cached URL is stale (> 6 days old)
     *
     * Photos with a fresh URL are skipped entirely — no network calls.
     * Sets isPrefetching=true only if there is actual work to do.
     *
     * After warmFromDisk() has run, staledFromDisk contains the IDs that need
     * re-resolution. This function re-fetches their file paths from Telegram,
     * calls put() to update SharedPrefs + urlCache, and emits via _resolvedUrls
     * so TelegramAsyncImage picks up the refreshed URL automatically.
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
