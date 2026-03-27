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

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            // Memory cache: 25% of app memory
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            // Disk cache: 250 MB at context.cacheDir/image_cache
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(250L * 1024 * 1024) // 250 MB
                    .build()
            }
            .okHttpClient {
                OkHttpClient.Builder()
                    .addInterceptor(TelegramCacheInterceptor())
                    // Optionally add other okhttp configs if needed here
                    .build()
            }
            .crossfade(200)
            .build()
    }

    private class TelegramCacheInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val urlString = request.url.toString()
            
            val originalResponse = chain.proceed(request)
            
            // Override Telegram CDN headers
            if (urlString.contains("api.telegram.org/file")) {
                return originalResponse.newBuilder()
                    .header("Cache-Control", "public, max-age=86400")
                    .removeHeader("Pragma") // Remove Pragma to ensure Cache-Control works
                    .build()
            }
            
            return originalResponse
        }
    }
}
