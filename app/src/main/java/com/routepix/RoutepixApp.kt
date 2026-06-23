package com.routepix

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.File

class RoutepixApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        // Initialise disk-backed thumbnail cache as early as possible so
        // TripHomeViewModel can warm from disk before triggering any network calls.
        com.routepix.data.cache.ThumbnailCache.init(this)
    }

    override fun newImageLoader(): ImageLoader {
        // IMPORTANT: Use filesDir (NOT cacheDir) for Coil's pixel cache.
        //
        // cacheDir is a TEMPORARY directory — Android is free to wipe it at any
        // time when storage is low, or after the app has been closed for a day or
        // two. This caused thumbnails to disappear after extended inactivity.
        //
        // filesDir is the app's persistent private storage. Android never auto-
        // clears it. Our pixel cache will survive indefinitely across app restarts,
        // so thumbnails load instantly from disk even after a week of inactivity.
        val persistentImageCacheDir = File(filesDir, "image_cache_persistent")

        return ImageLoader.Builder(this)
            // Memory cache: 25% of app memory
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            // Disk cache in persistent filesDir — never auto-cleared by Android
            .diskCache {
                DiskCache.Builder()
                    .directory(persistentImageCacheDir)
                    .maxSizeBytes(512L * 1024 * 1024) // 512 MB — persistent, so be conservative
                    .build()
            }
            .okHttpClient {
                OkHttpClient.Builder()
                    .addNetworkInterceptor(TelegramCacheInterceptor())
                    .build()
            }
            .crossfade(200)
            .build()
    }

    /**
     * Rewrites Cache-Control headers on Telegram CDN responses so Coil stores
     * image bytes on disk. Without this, Telegram CDN headers often say
     * no-cache / must-revalidate, which would bypass the disk cache.
     *
     * Note: the Cache-Control header only governs how Coil's OkHttp layer
     * decides to cache. Our persistent disk cache (filesDir) means even if
     * OkHttp doesn't serve from its HTTP cache, Coil's own disk cache layer
     * will still serve the image bytes without a network round-trip.
     */
    private class TelegramCacheInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val response = chain.proceed(chain.request())
            return if (chain.request().url.host == "cdn4.telegram.org" ||
                chain.request().url.toString().contains("api.telegram.org/file")) {
                response.newBuilder()
                    .header("Cache-Control", "public, max-age=604800, immutable") // 7 days
                    .removeHeader("Pragma")
                    .removeHeader("Expires")
                    .build()
            } else {
                response
            }
        }
    }
}
