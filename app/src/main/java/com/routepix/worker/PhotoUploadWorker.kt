package com.routepix.worker

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.routepix.data.local.QueuedPhoto
import com.routepix.data.local.RoutepixDatabase
import com.routepix.data.model.PhotoMeta
import com.routepix.data.remote.RetrofitClient
import com.routepix.data.remote.TelegramApi
import com.routepix.util.NotificationHelper
import com.routepix.util.PhotoUtils
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID

/**
 * Uploads ALL pending photos for a trip, one-by-one (sequential).
 *
 * Design decisions:
 * - Fully sequential: upload one photo, wait for success, move to next.
 *   This is the "copying method" approach that was stable before.
 * - Photos are chunked into batches of BATCH_SIZE for progress display only.
 *   The batching is purely cosmetic — it does NOT affect upload order.
 * - Progress is reported after EVERY single photo via setProgress().
 * - The worker re-queries the DB each time it starts, so it always picks up
 *   everything that's pending — no photos get lost.
 */
class PhotoUploadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_TRIP_ID = "TRIP_ID"
        const val BATCH_SIZE = 25
        private const val MAX_RETRIES = 3
        private const val TAG = "PhotoUploadWorker"

        // Progress keys — read by TimelineViewModel
        const val KEY_UPLOADED = "uploaded"
        const val KEY_TOTAL = "total"
        const val KEY_CURRENT_BATCH = "currentBatch"
        const val KEY_TOTAL_BATCHES = "totalBatches"
        const val KEY_BATCH_UPLOADED = "batchUploaded"
        const val KEY_BATCH_SIZE = "batchSize"
    }

    private val dao = RoutepixDatabase.getInstance(applicationContext).queuedPhotoDao()

    override suspend fun doWork(): Result {
        val tripId = inputData.getString(KEY_TRIP_ID) ?: return Result.failure()
        val telegramApi = RetrofitClient.telegramApi

        // Always read the full pending list fresh from DB
        val allPending = dao.getPendingForTrip(tripId)
        if (allPending.isEmpty()) return Result.success()

        val totalPhotos = allPending.size
        val totalBatches = (totalPhotos + BATCH_SIZE - 1) / BATCH_SIZE // ceil division

        // Fetch Telegram credentials once
        val creds = fetchCredentials(tripId) ?: return Result.failure()

        NotificationHelper.createNotificationChannel(applicationContext)
        setForeground(NotificationHelper.getForegroundInfo(applicationContext, 0, totalPhotos))

        var uploadedCount = 0
        var failureCount = 0

        // Report initial state
        reportProgress(uploadedCount, totalPhotos, 1, totalBatches, 0, minOf(BATCH_SIZE, totalPhotos))

        for ((index, photo) in allPending.withIndex()) {
            val currentBatch = (index / BATCH_SIZE) + 1
            val batchStart = (currentBatch - 1) * BATCH_SIZE
            val batchSize = minOf(BATCH_SIZE, totalPhotos - batchStart)

            try {
                val success = uploadSinglePhoto(photo, tripId, telegramApi, creds.first, creds.second)
                if (success) {
                    dao.deleteById(photo.id)
                    uploadedCount++
                    Log.d(TAG, "Uploaded ${photo.id} ($uploadedCount/$totalPhotos)")
                } else {
                    failureCount++
                    Log.w(TAG, "Upload returned false for photo ${photo.id} (failure $failureCount)")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception uploading photo ${photo.id} (failure ${failureCount + 1})", e)
                failureCount++
            }

            // Report progress with ACTUAL uploaded count for both overall and batch
            val uploadedInBatch = minOf(uploadedCount - (batchStart), batchSize).coerceAtLeast(0)
            reportProgress(uploadedCount, totalPhotos, currentBatch, totalBatches, uploadedInBatch, batchSize)
            
            // Update notification every 3 photos or on last photo
            if (uploadedCount % 3 == 0 || index == allPending.size - 1) {
                setForeground(NotificationHelper.getForegroundInfo(applicationContext, uploadedCount, totalPhotos))
            }
        }

        Log.d(TAG, "Upload complete: $uploadedCount/$totalPhotos uploaded, $failureCount failures")

        return if (failureCount > 0 && uploadedCount == 0) {
            // All failed — retry or give up
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        } else {
            // Some or all succeeded. If there are remaining failures in DB,
            // the next worker invocation will pick them up.
            Result.success()
        }
    }

    private suspend fun reportProgress(
        uploaded: Int, total: Int,
        currentBatch: Int, totalBatches: Int,
        batchUploaded: Int, batchSize: Int
    ) {
        setProgress(workDataOf(
            KEY_UPLOADED to uploaded,
            KEY_TOTAL to total,
            KEY_CURRENT_BATCH to currentBatch,
            KEY_TOTAL_BATCHES to totalBatches,
            KEY_BATCH_UPLOADED to batchUploaded,
            KEY_BATCH_SIZE to batchSize
        ))
    }

    private suspend fun fetchCredentials(tripId: String): Pair<String, String>? {
        return try {
            val tripDoc = FirebaseFirestore.getInstance()
                .collection("trips").document(tripId).get().await()

            var botToken = tripDoc.getString("telegramBotToken")
            var chatId = tripDoc.getString("telegramChatId")

            if (botToken.isNullOrBlank() || chatId.isNullOrBlank()) {
                val adminUid = tripDoc.getString("adminUid") ?: return null
                val adminDoc = FirebaseFirestore.getInstance()
                    .collection("users").document(adminUid).get().await()
                botToken = adminDoc.getString("telegramBotToken")
                chatId = adminDoc.getString("telegramChatId")
            }

            if (!botToken.isNullOrBlank() && !chatId.isNullOrBlank()) {
                Pair(botToken, chatId)
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch credentials", e)
            null
        }
    }

    private suspend fun uploadSinglePhoto(
        photo: QueuedPhoto,
        tripId: String,
        telegramApi: TelegramApi,
        botToken: String,
        chatId: String
    ): Boolean {
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            Log.e(TAG, "No authenticated user")
            return false
        }

        val uri = Uri.parse(photo.localUri)
        
        // ── Step 1: Upload compressed thumbnail via sendPhoto ──────────
        var thumbnailCompressedFile: java.io.File? = null
        var photoFileId: String? = null
        var photoFileSize: Long? = null

        try {
            val afd = applicationContext.contentResolver.openAssetFileDescriptor(uri, "r")
            val sizeBytes = afd?.use { it.length } ?: 0L
            // Compress for thumbnail if > 9.5 MB
            if (sizeBytes > 9.5 * 1024 * 1024) {
                thumbnailCompressedFile = PhotoUtils.compressImage(applicationContext, uri)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check file size for thumbnail", e)
        }

        try {
            val thumbRequestFile = if (thumbnailCompressedFile != null) {
                thumbnailCompressedFile.asRequestBody("image/jpeg".toMediaType())
            } else {
                PhotoUtils.uriToRequestBody(applicationContext, uri)
            }
            val photoPart = MultipartBody.Part.createFormData("photo", "photo.jpg", thumbRequestFile)
            val chatIdBody = chatId.toRequestBody("text/plain".toMediaType())
            val captionBody = photo.tag?.toRequestBody("text/plain".toMediaType())

            val photoResponse = try {
                telegramApi.sendPhoto(
                    token = botToken,
                    chatId = chatIdBody,
                    photo = photoPart,
                    caption = captionBody
                )
            } catch (e: retrofit2.HttpException) {
                if (e.code() == 400) {
                    Log.e(TAG, "HTTP 400 for sendPhoto ${photo.localUri}, removing from queue")
                    dao.deleteById(photo.id)
                    return true
                }
                if (e.code() == 429) {
                    Log.w(TAG, "Rate limited (429) on sendPhoto, waiting 5s...")
                    kotlinx.coroutines.delay(5000)
                    return false
                }
                Log.e(TAG, "HTTP ${e.code()} for sendPhoto ${photo.localUri}", e)
                return false
            } catch (e: java.net.SocketTimeoutException) {
                Log.e(TAG, "Timeout on sendPhoto ${photo.localUri}", e)
                return false
            } catch (e: java.io.IOException) {
                Log.e(TAG, "IO error on sendPhoto ${photo.localUri}", e)
                return false
            } catch (e: Exception) {
                Log.e(TAG, "Network error on sendPhoto ${photo.localUri}", e)
                return false
            }

            if (!photoResponse.ok || photoResponse.result == null) {
                Log.e(TAG, "sendPhoto response not ok")
                return false
            }

            val photoSizes = photoResponse.result.photo
            if (photoSizes.isNullOrEmpty()) {
                Log.e(TAG, "No photo sizes in sendPhoto response")
                return false
            }
            val bestPhoto = photoSizes.maxByOrNull { it.width * it.height }!!
            photoFileId = bestPhoto.fileId
            photoFileSize = bestPhoto.fileSize
        } finally {
            thumbnailCompressedFile?.let { if (it.exists()) it.delete() }
        }

        // ── Step 2: Upload original quality via sendDocument ──────────
        var documentFileId: String? = null
        var docCompressedFile: java.io.File? = null

        try {
            // Only compress for document if file > 50 MB (Telegram bot limit)
            docCompressedFile = PhotoUtils.compressForDocument(applicationContext, uri)

            val docRequestFile = if (docCompressedFile != null) {
                docCompressedFile.asRequestBody("image/jpeg".toMediaType())
            } else {
                PhotoUtils.uriToRequestBody(applicationContext, uri)
            }
            val docPart = MultipartBody.Part.createFormData("document", "photo.jpg", docRequestFile)
            val chatIdBody2 = chatId.toRequestBody("text/plain".toMediaType())

            val docResponse = try {
                telegramApi.sendDocument(
                    token = botToken,
                    chatId = chatIdBody2,
                    document = docPart
                )
            } catch (e: retrofit2.HttpException) {
                if (e.code() == 429) {
                    Log.w(TAG, "Rate limited (429) on sendDocument, waiting 5s...")
                    kotlinx.coroutines.delay(5000)
                }
                Log.w(TAG, "sendDocument failed (HTTP ${e.code()}), proceeding with photo-only", e)
                null
            } catch (e: Exception) {
                Log.w(TAG, "sendDocument failed, proceeding with photo-only", e)
                null
            }

            if (docResponse?.ok == true && docResponse.result?.document != null) {
                documentFileId = docResponse.result.document.fileId
                Log.d(TAG, "sendDocument success, docFileId=$documentFileId")
            } else {
                Log.w(TAG, "sendDocument response not ok or no document in result, photo-only upload")
            }
        } finally {
            docCompressedFile?.let { if (it.exists()) it.delete() }
        }

        // ── Step 3: Save metadata to Firestore ──────────
        val photoMeta = PhotoMeta(
            photoId = UUID.randomUUID().toString(),
            tripId = tripId,
            uploaderUid = currentUid,
            telegramFileId = photoFileId ?: return false,
            telegramDocumentId = documentFileId,
            timestamp = photo.timestamp,
            tag = photo.tag,
            md5Hash = photo.md5Hash,
            sizeBytes = photoFileSize
        )

        try {
            FirebaseFirestore.getInstance()
                .collection("trips").document(tripId)
                .collection("photos").document(photoMeta.photoId)
                .set(photoMeta).await()
        } catch (e: Exception) {
            Log.e(TAG, "Firestore write failed for ${photo.localUri}", e)
            return false
        }

        return true
    }
}
