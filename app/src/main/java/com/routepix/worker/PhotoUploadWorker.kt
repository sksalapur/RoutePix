package com.routepix.worker

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.routepix.data.local.QueuedPhoto
import com.routepix.data.local.QueuedPhotoDao
import com.routepix.data.local.RoutepixDatabase
import com.routepix.data.model.PhotoMeta
import com.routepix.data.remote.RetrofitClient
import com.routepix.data.remote.TelegramApi
import com.routepix.util.PhotoUtils
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID


class PhotoUploadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_TRIP_ID = "TRIP_ID"
        private const val MAX_RETRIES = 3
        private const val TAG = "PhotoUploadWorker"
    }

    override suspend fun doWork(): Result {
        val tripId = inputData.getString(KEY_TRIP_ID) ?: return Result.failure()

        val dao = RoutepixDatabase.getInstance(applicationContext).queuedPhotoDao()
        val telegramApi = RetrofitClient.telegramApi

        val initialPending = dao.getPendingForTrip(tripId)
        val initialTotal = initialPending.size
        if (initialTotal == 0) return Result.success()

        var hasFailures = false

        while (true) {
            val pendingPhotos = dao.getPendingForTrip(tripId)
            if (pendingPhotos.isEmpty()) break

            val photo = pendingPhotos.first()
            val currentProgress = initialTotal - pendingPhotos.size
            setProgress(workDataOf("progress" to currentProgress, "total" to initialTotal))

            try {
                val success = executeSingle(photo, tripId, telegramApi)
                if (success) {
                    dao.deleteById(photo.id)
                } else {
                    hasFailures = true
                    break
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Upload failed for photo ${photo.id}", e)
                hasFailures = true
                break
            }
        }
        
        val finalPending = dao.getPendingForTrip(tripId).size
        val finalProgress = initialTotal - finalPending
        setProgress(workDataOf("progress" to finalProgress, "total" to initialTotal))

        return if (hasFailures) {
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        } else {
            Result.success()
        }
    }

    private suspend fun executeSingle(
        photo: QueuedPhoto, 
        tripId: String, 
        telegramApi: TelegramApi
    ): Boolean {
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            android.util.Log.e(TAG, "No authenticated user")
            return false
        }

        val tripDoc = FirebaseFirestore.getInstance()
            .collection("trips")
            .document(tripId)
            .get()
            .await()
        
        var botToken = tripDoc.getString("telegramBotToken")
        var chatId = tripDoc.getString("telegramChatId")

        if (botToken.isNullOrBlank() || chatId.isNullOrBlank()) {
            val adminUid = tripDoc.getString("adminUid") ?: run {
                android.util.Log.e(TAG, "Trip has no adminUid")
                return false
            }
            try {
                val adminDoc = FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(adminUid)
                    .get()
                    .await()
                botToken = adminDoc.getString("telegramBotToken")
                chatId = adminDoc.getString("telegramChatId")

                if (!botToken.isNullOrBlank() && !chatId.isNullOrBlank()) {
                    FirebaseFirestore.getInstance()
                        .collection("trips")
                        .document(tripId)
                        .update(mapOf(
                            "telegramBotToken" to botToken,
                            "telegramChatId" to chatId
                        ))
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to read admin user doc", e)
            }
        }

        if (botToken.isNullOrBlank() || chatId.isNullOrBlank()) {
            android.util.Log.e(TAG, "No credentials available, aborting upload")
            return false
        }

        val imageBytes = PhotoUtils.uriToByteArray(applicationContext, Uri.parse(photo.localUri))
        val requestFile = imageBytes.toRequestBody("image/jpeg".toMediaType())
        val photoPart = MultipartBody.Part.createFormData("photo", "photo.jpg", requestFile)
        val chatIdBody = chatId.toRequestBody("text/plain".toMediaType())
        val captionBody = photo.tag?.toRequestBody("text/plain".toMediaType())

        val response = telegramApi.sendPhoto(
            token = botToken,
            chatId = chatIdBody,
            photo = photoPart,
            caption = captionBody
        )

        if (!response.ok || response.result == null) {
            return false
        }

        val photoSizes = response.result.photo ?: return false
        val bestPhoto = photoSizes.maxByOrNull { it.width * it.height } ?: return false
        val fileId = bestPhoto.fileId

        val photoMeta = PhotoMeta(
            photoId = UUID.randomUUID().toString(),
            tripId = tripId,
            uploaderUid = currentUid,
            telegramFileId = fileId,
            timestamp = photo.timestamp,
            tag = photo.tag
        )

        FirebaseFirestore.getInstance()
            .collection("trips")
            .document(tripId)
            .collection("photos")
            .document(photoMeta.photoId)
            .set(photoMeta)
            .await()

        return true
    }
}

