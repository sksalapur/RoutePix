package com.routepix.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * On-device face counting powered by Google ML Kit Face Detection.
 *
 * Design decisions:
 * - Bundled model: offline-first, works without Play Services — same as ImageLabeler.
 * - PERFORMANCE mode: fastest detection, no landmarks or contours needed.
 * - We only count faces, never store bounding boxes or face data for privacy.
 * - Input bitmap is sampled down to 512×512 to prevent OOM.
 * - The detector client is closed after every call to free resources.
 * - Non-fatal: returns 0 on failure so the photo is still queued.
 */
object FaceCounter {

    private const val TAG = "FaceCounter"
    private const val SAMPLE_SIZE = 512

    /**
     * Counts the number of faces in the image at [uri].
     *
     * Returns 0 if detection fails or no faces are found.
     *
     * Must be called from a background thread (Dispatchers.IO).
     */
    suspend fun countFaces(context: Context, uri: Uri): Int {
        return try {
            val bitmap = decodeSampledBitmap(context, uri, SAMPLE_SIZE, SAMPLE_SIZE)
                ?: return 0

            val image = InputImage.fromBitmap(bitmap, 0)
            val options = FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .build()
            val detector = FaceDetection.getClient(options)

            val count = suspendCancellableCoroutine<Int> { cont ->
                detector.process(image)
                    .addOnSuccessListener { faces ->
                        cont.resume(faces.size)
                    }
                    .addOnFailureListener { cont.resumeWithException(it) }
                    .addOnCanceledListener { cont.cancel() }
            }

            detector.close()
            bitmap.recycle()

            Log.d(TAG, "Detected $count face(s) in $uri")
            count
        } catch (e: Exception) {
            Log.w(TAG, "Face detection failed for $uri", e)
            0 // non-fatal: photo is still queued, just without face count
        }
    }

    private fun decodeSampledBitmap(
        context: Context,
        uri: Uri,
        reqWidth: Int,
        reqHeight: Int
    ): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            }

            val opts = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(bounds, reqWidth, reqHeight)
                inJustDecodeBounds = false
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, opts)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to decode bitmap for $uri", e)
            null
        }
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqHeight &&
                halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
