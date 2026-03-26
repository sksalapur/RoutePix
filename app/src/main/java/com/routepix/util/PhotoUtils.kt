package com.routepix.util

import android.content.Context
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.InputStream
import java.security.MessageDigest


object PhotoUtils {

    
    fun extractExif(context: Context, uri: Uri): ExifData {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                val dateTimeMillis = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                    ?.let { parseExifDateTime(it) }

                ExifData(
                    lat = null,
                    lng = null,
                    timestamp = dateTimeMillis
                )
            } ?: ExifData()
        } catch (_: Exception) {
            ExifData()
        }
    }

    
    fun computeMd5(context: Context, uri: Uri): String {
        val digest = MessageDigest.getInstance("MD5")
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (stream.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    
    fun copyToCache(context: Context, uri: Uri, md5: String): String {
        val cacheFile = java.io.File(context.cacheDir, "$md5.jpg")
        if (!cacheFile.exists()) {
            context.contentResolver.openInputStream(uri)?.use { input ->
                cacheFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: throw IllegalArgumentException("Cannot open URI: $uri")
        }
        return Uri.fromFile(cacheFile).toString()
    }

    
    fun uriToByteArray(context: Context, uri: Uri): ByteArray {
        return context.contentResolver.openInputStream(uri)?.use(InputStream::readBytes)
            ?: throw IllegalArgumentException("Cannot open URI: $uri")
    }


    
    private fun parseExifDateTime(dateTime: String): Long? {
        return try {
            val sdf = java.text.SimpleDateFormat("yyyy:MM:dd HH:mm:ss", java.util.Locale.US)
            sdf.parse(dateTime)?.time
        } catch (_: Exception) {
            null
        }
    }
}

