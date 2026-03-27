package com.routepix.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object ImageDownloadManager {

    private val _activeDownloads = MutableStateFlow<Map<String, String>>(emptyMap())
    val activeDownloads = _activeDownloads.asStateFlow()

    private val client = OkHttpClient()

    /**
     * Downloads an image to the private share_cache directory.
     * Returns the File if successful, null otherwise.
     */
    suspend fun downloadToCache(context: Context, url: String, filename: String): File? = withContext(Dispatchers.IO) {
        try {
            val cacheDir = File(context.cacheDir, "share_cache")
            if (!cacheDir.exists()) cacheDir.mkdirs()

            val savedFile = File(context.filesDir, "saved/$filename")
            if (savedFile.exists() && savedFile.length() > 0) {
                return@withContext savedFile
            }

            val file = File(cacheDir, filename)
            // If already exists (and has size), reuse it
            if (file.exists() && file.length() > 0) {
                return@withContext file
            }

            _activeDownloads.value = _activeDownloads.value + (filename to url)
            
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val inputStream: InputStream? = response.body?.byteStream()
                if (inputStream != null) {
                    val fos = FileOutputStream(file)
                    inputStream.copyTo(fos)
                    fos.close()
                    inputStream.close()
                    return@withContext file
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            _activeDownloads.value = _activeDownloads.value - filename
        }
        null
    }

    /**
     * Downloads an image to the private saved directory.
     * Returns the File if successful, null otherwise.
     */
    suspend fun saveToAppStorage(context: Context, url: String, filename: String): File? = withContext(Dispatchers.IO) {
        try {
            val savedDir = File(context.filesDir, "saved")
            if (!savedDir.exists()) savedDir.mkdirs()

            val file = File(savedDir, filename)
            // If already exists, we might just return it
            if (file.exists() && file.length() > 0) {
                return@withContext file
            }

            _activeDownloads.value = _activeDownloads.value + (filename to url)

            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val inputStream: InputStream? = response.body?.byteStream()
                if (inputStream != null) {
                    val fos = FileOutputStream(file)
                    inputStream.copyTo(fos)
                    fos.close()
                    inputStream.close()
                    return@withContext file
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            _activeDownloads.value = _activeDownloads.value - filename
        }
        null
    }

    /**
     * Generates a content:// URI using FileProvider for an internal File.
     */
    fun uriForFile(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    /**
     * Cleans up files in the share cache directory that are older than 1 hour.
     */
    fun cleanShareCache(context: Context) {
        try {
            val cacheDir = File(context.cacheDir, "share_cache")
            if (!cacheDir.exists()) return

            val thresholdTime = System.currentTimeMillis() - (60 * 60 * 1000) // 1 hour ago
            cacheDir.listFiles()?.forEach { file ->
                if (file.lastModified() < thresholdTime) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Syncs all private saved photos to the public gallery using MediaStore.
     */
    suspend fun syncSavedPhotosToGallery(context: Context) = withContext(Dispatchers.IO) {
        val savedDir = File(context.filesDir, "saved")
        if (!savedDir.exists()) return@withContext
        val files = savedDir.listFiles()?.filter { it.isFile && it.name.endsWith(".jpg") } ?: return@withContext

        val resolver = context.contentResolver
        val collection = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            android.provider.MediaStore.Images.Media.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        files.forEach { file ->
            val projection = arrayOf(android.provider.MediaStore.Images.Media._ID)
            val selection = "${android.provider.MediaStore.Images.Media.DISPLAY_NAME} = ?"
            val selectionArgs = arrayOf(file.name)
            
            var exists = false
            try {
                resolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
                    if (cursor.count > 0) exists = true
                }
            } catch (e: Exception) { e.printStackTrace() }

            if (!exists) {
                val details = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, file.name)
                    put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/RoutePix")
                        put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
                    } else {
                        val externalDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES)
                        val routePixDir = File(externalDir, "RoutePix")
                        if (!routePixDir.exists()) routePixDir.mkdirs()
                        put(android.provider.MediaStore.Images.Media.DATA, File(routePixDir, file.name).absolutePath)
                    }
                }

                val uri = resolver.insert(collection, details)
                if (uri != null) {
                    try {
                        resolver.openOutputStream(uri)?.use { outStream ->
                            file.inputStream().use { inStream ->
                                inStream.copyTo(outStream)
                            }
                        }
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                            details.clear()
                            details.put(android.provider.MediaStore.Images.Media.IS_PENDING, 0)
                            resolver.update(uri, details, null, null)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        resolver.delete(uri, null, null)
                    }
                }
            }
        }
    }
}
