package com.routepix.util

import android.content.Context
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.InputStream
import java.security.MessageDigest
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okio.BufferedSink


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
 
    
    fun uriToRequestBody(context: Context, uri: Uri, contentType: String? = "image/jpeg"): RequestBody {
        return object : RequestBody() {
            override fun contentType() = contentType?.toMediaTypeOrNull()
            
            override fun contentLength(): Long {
                return try {
                    context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
                } catch (e: Exception) {
                    -1L
                }
            }
 
            override fun writeTo(sink: BufferedSink) {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        sink.write(buffer, 0, read)
                    }
                }
            }
        }
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

    
    fun compressImage(context: Context, uri: Uri, targetMaxDim: Int = 4000, quality: Int = 95): java.io.File? {
        try {
            // 1. Get bounds
            val options = android.graphics.BitmapFactory.Options()
            options.inJustDecodeBounds = true
            context.contentResolver.openInputStream(uri)?.use { stream ->
                android.graphics.BitmapFactory.decodeStream(stream, null, options)
            }
            if (options.outWidth <= 0 || options.outHeight <= 0) return null

            // 2. Calculate scale to prevent OOM
            var scale = 1
            while (options.outWidth / scale > targetMaxDim || options.outHeight / scale > targetMaxDim) {
                scale *= 2
            }

            // 3. Decode scaled
            val decodeOptions = android.graphics.BitmapFactory.Options()
            decodeOptions.inSampleSize = scale
            val originalBitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                // Decoding here automatically extracts the static frame of motion photos
                android.graphics.BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return null

            // 4. Exif Rotation
            var rotatedBitmap = originalBitmap
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                val orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
                val matrix = android.graphics.Matrix()
                when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                    ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                }
                if (!matrix.isIdentity) {
                    rotatedBitmap = android.graphics.Bitmap.createBitmap(
                        originalBitmap, 0, 0, originalBitmap.width, originalBitmap.height, matrix, true
                    )
                    if (rotatedBitmap != originalBitmap) {
                        originalBitmap.recycle()
                    }
                }
            }

            // 5. Compress to file
            val tempFile = java.io.File(context.cacheDir, "upload_${java.util.UUID.randomUUID()}.jpg")
            java.io.FileOutputStream(tempFile).use { out ->
                rotatedBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, out)
            }
            if (rotatedBitmap != originalBitmap) {
                rotatedBitmap.recycle()
            }
            originalBitmap.recycle()

            return tempFile
        } catch (e: Exception) {
            android.util.Log.e("PhotoUtils", "Compression failed", e)
            return null
        }
    }
    /**
     * Compress an image for sendDocument upload.
     * Only compresses if the file exceeds [maxSizeBytes] (default 50 MB).
     * Uses iterative quality reduction to target ~48 MB.
     * Returns null if the original file is already small enough.
     */
    fun compressForDocument(
        context: Context,
        uri: Uri,
        maxSizeBytes: Long = 50L * 1024 * 1024,
        targetSizeBytes: Long = 48L * 1024 * 1024
    ): java.io.File? {
        try {
            // Check original size
            val originalSize = context.contentResolver.openAssetFileDescriptor(uri, "r")
                ?.use { it.length } ?: return null
            if (originalSize <= maxSizeBytes) return null // No compression needed

            // Decode full resolution
            val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                android.graphics.BitmapFactory.decodeStream(stream)
            } ?: return null

            // Apply EXIF rotation
            val rotatedBitmap = applyExifRotation(context, uri, bitmap)

            // Iterative quality reduction: start at 97 and step down
            var quality = 97
            var tempFile: java.io.File
            do {
                tempFile = java.io.File(context.cacheDir, "doc_${java.util.UUID.randomUUID()}.jpg")
                java.io.FileOutputStream(tempFile).use { out ->
                    rotatedBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, out)
                }
                if (tempFile.length() <= targetSizeBytes) break
                quality -= 3
                if (quality < 50) break // Safety floor
            } while (true)

            if (rotatedBitmap != bitmap) rotatedBitmap.recycle()
            bitmap.recycle()

            return tempFile
        } catch (e: Exception) {
            android.util.Log.e("PhotoUtils", "compressForDocument failed", e)
            return null
        }
    }

    /**
     * Apply EXIF rotation to a bitmap. Shared between compressImage and compressForDocument.
     */
    private fun applyExifRotation(
        context: Context,
        uri: Uri,
        sourceBitmap: android.graphics.Bitmap
    ): android.graphics.Bitmap {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                val orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
                val matrix = android.graphics.Matrix()
                when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                    ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                }
                if (!matrix.isIdentity) {
                    val rotated = android.graphics.Bitmap.createBitmap(
                        sourceBitmap, 0, 0,
                        sourceBitmap.width, sourceBitmap.height,
                        matrix, true
                    )
                    rotated
                } else {
                    sourceBitmap
                }
            } ?: sourceBitmap
        } catch (_: Exception) {
            sourceBitmap
        }
    }
}
