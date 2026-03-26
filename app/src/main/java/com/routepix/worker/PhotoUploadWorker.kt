package com.routepix.worker

import android.content.Context
import android.location.Geocoder
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.routepix.data.local.RoutepixDatabase
import com.routepix.data.model.PhotoMeta
import com.routepix.data.model.Trip
import com.routepix.data.remote.RetrofitClient
import com.routepix.security.SecurityManager
import com.routepix.util.PhotoUtils
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Locale
import java.util.UUID


class PhotoUploadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_TRIP_ID = "TRIP_ID"
        const val KEY_QUEUED_PHOTO_ID = "QUEUED_PHOTO_ID"
        private const val MAX_RETRIES = 3
    }

    override suspend fun doWork(): Result {
        val tripId = inputData.getString(KEY_TRIP_ID) ?: return Result.failure()
        val photoId = inputData.getInt(KEY_QUEUED_PHOTO_ID, -1)
        if (photoId == -1) return Result.failure()

        return try {
            execute(tripId, photoId)
        } catch (e: Exception) {
            android.util.Log.e("PhotoUploadWorker", "Upload failed", e)
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        }
    }

    private suspend fun execute(tripId: String, queuedPhotoId: Int): Result {
        val TAG = "PhotoUploadWorker"
        val dao = RoutepixDatabase.getInstance(applicationContext).queuedPhotoDao()
        val telegramApi = RetrofitClient.telegramApi

        val photo = dao.getById(queuedPhotoId) ?: run {
            android.util.Log.e(TAG, "Queued photo $queuedPhotoId not found in DB")
            return Result.failure()
        }
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            android.util.Log.e(TAG, "No authenticated user")
            return Result.failure()
        }

        android.util.Log.d(TAG, "Starting upload for trip=$tripId, photo=$queuedPhotoId, user=$currentUid")

        val tripDoc = FirebaseFirestore.getInstance()
            .collection("trips")
            .document(tripId)
            .get()
            .await()
        
        var botToken = tripDoc.getString("telegramBotToken")
        var chatId = tripDoc.getString("telegramChatId")

        android.util.Log.d(TAG, "Trip credentials: botToken=${if (botToken.isNullOrBlank()) "MISSING" else "present"}, chatId=${if (chatId.isNullOrBlank()) "MISSING" else "present"}")

        if (botToken.isNullOrBlank() || chatId.isNullOrBlank()) {
            val adminUid = tripDoc.getString("adminUid") ?: run {
                android.util.Log.e(TAG, "Trip has no adminUid")
                return Result.failure()
            }
            android.util.Log.d(TAG, "Falling back to admin user doc: $adminUid")
            try {
                val adminDoc = FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(adminUid)
                    .get()
                    .await()
                botToken = adminDoc.getString("telegramBotToken")
                chatId = adminDoc.getString("telegramChatId")
                android.util.Log.d(TAG, "Admin credentials: botToken=${if (botToken.isNullOrBlank()) "MISSING" else "present"}, chatId=${if (chatId.isNullOrBlank()) "MISSING" else "present"}")

                if (!botToken.isNullOrBlank() && !chatId.isNullOrBlank()) {
                    FirebaseFirestore.getInstance()
                        .collection("trips")
                        .document(tripId)
                        .update(mapOf(
                            "telegramBotToken" to botToken,
                            "telegramChatId" to chatId
                        ))
                    android.util.Log.d(TAG, "Cached credentials into trip document")
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to read admin user doc", e)
            }
        }

        if (botToken.isNullOrBlank() || chatId.isNullOrBlank()) {
            android.util.Log.e(TAG, "No credentials available, aborting upload")
            return Result.failure()
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
            return Result.retry()
        }

        val photoSizes = response.result.photo ?: return Result.failure()
        val bestPhoto = photoSizes.maxByOrNull { it.width * it.height }
            ?: return Result.failure()
        val fileId = bestPhoto.fileId

        telegramApi.getFile(token = botToken, fileId = fileId)

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

        dao.deleteById(photo.id)

        return Result.success()
    }


}

