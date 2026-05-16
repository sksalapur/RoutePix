package com.routepix.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * On-device image labeling powered by Google ML Kit.
 *
 * Design decisions:
 * - Bundled TFLite model: offline-first, no Play Services, works in remote areas.
 * - Confidence threshold: 0.70 — wide enough to catch obvious scene context without
 *   too many false positives.
 * - Top 5 labels only: keeps the stored string compact (comma-separated) while
 *   still providing rich search coverage.
 * - Input bitmap is sampled down to 512×512 before labeling to prevent OOM on
 *   high-resolution camera photos (e.g., 108 MP sensors).
 * - The labeler client is closed after every call to free the TFLite interpreter.
 */
object ImageLabeler {

    private const val TAG            = "ImageLabeler"
    private const val MIN_CONFIDENCE = 0.60f   // wider net → catches Water, River, Lake etc.
    private const val MAX_LABELS     = 8        // top-8 labels per photo
    private const val SAMPLE_SIZE    = 512

    /**
     * Labels the image at [uri] and returns the top concepts as a comma-separated
     * string, e.g. "Mountain,Sky,Snow,Cloud,Nature".
     *
     * Returns null if labeling fails or no label exceeds the confidence threshold.
     *
     * Must be called from a background thread (Dispatchers.IO).
     */
    suspend fun label(context: Context, uri: Uri): String? {
        return try {
            val bitmap = decodeSampledBitmap(context, uri, SAMPLE_SIZE, SAMPLE_SIZE)
                ?: return null

            val image   = InputImage.fromBitmap(bitmap, 0)
            val options = ImageLabelerOptions.Builder()
                .setConfidenceThreshold(MIN_CONFIDENCE)
                .build()
            val labeler = ImageLabeling.getClient(options)

            val labels = suspendCancellableCoroutine<List<String>> { cont ->
                labeler.process(image)
                    .addOnSuccessListener { results ->
                        val top = results
                            .sortedByDescending { it.confidence }
                            .take(MAX_LABELS)
                            .map { it.text }
                        cont.resume(top)
                    }
                    .addOnFailureListener { cont.resumeWithException(it) }
                    .addOnCanceledListener  { cont.cancel() }
            }

            labeler.close()
            bitmap.recycle()

            if (labels.isEmpty()) {
                Log.d(TAG, "No labels above threshold for $uri")
                null
            } else {
                val result = labels.joinToString(",")
                Log.d(TAG, "Labeled $uri → $result")
                result
            }
        } catch (e: Exception) {
            Log.w(TAG, "Labeling failed for $uri", e)
            null  // non-fatal: photo is still queued, just without AI labels
        }
    }

    /**
     * Decodes the image at [uri] with a calculated inSampleSize so the decoded
     * bitmap is no larger than [reqWidth] × [reqHeight] pixels.
     * Opens the stream twice — once to read dimensions, once to decode.
     */
    private fun decodeSampledBitmap(
        context: Context,
        uri: Uri,
        reqWidth: Int,
        reqHeight: Int
    ): Bitmap? {
        return try {
            // First pass — read bounds only
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            }

            // Second pass — decode at calculated sample size
            val opts = BitmapFactory.Options().apply {
                inSampleSize      = calculateInSampleSize(bounds, reqWidth, reqHeight)
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
        val width  = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth  = width  / 2
            while (halfHeight / inSampleSize >= reqHeight &&
                   halfWidth  / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
