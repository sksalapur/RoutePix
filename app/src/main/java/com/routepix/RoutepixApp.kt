package com.routepix

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response

class RoutepixApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        // Initialise disk-backed thumbnail cache as early as possible so
        // TripHomeViewModel can warm from disk before triggering any network calls.
        com.routepix.data.cache.ThumbnailCache.init(this)
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            // Memory cache: 25% of app memory
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            // Disk cache: 1 GB
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(1024L * 1024 * 1024) // 1 GB
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

    // Adds long-lived Cache-Control headers to Telegram CDN responses so Coil
    // stores them on disk and never re-downloads the same image bytes.
    private class TelegramCacheInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val response = chain.proceed(chain.request())
            return if (chain.request().url.host == "cdn4.telegram.org" ||
                chain.request().url.toString().contains("api.telegram.org/file")) {
                response.newBuilder()
                    .header("Cache-Control", "public, max-age=604800") // 7 days
                    .removeHeader("Pragma")
                    .build()
            } else {
                response
            }
        }
    }
}
