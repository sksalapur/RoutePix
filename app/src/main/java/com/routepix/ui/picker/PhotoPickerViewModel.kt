package com.routepix.ui.picker

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.routepix.data.local.QueuedPhoto
import com.routepix.data.local.RoutepixDatabase
import com.routepix.util.PhotoUtils
import com.routepix.worker.PhotoUploadWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UploadQueueState(
    val totalQueued: Int = 0,
    val duplicatesSkipped: Int = 0,
    val isProcessing: Boolean = false
)


class PhotoPickerViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = RoutepixDatabase.getInstance(application).queuedPhotoDao()
    private val workManager = WorkManager.getInstance(application)

    private val _queueState = MutableStateFlow(UploadQueueState())
    val queueState: StateFlow<UploadQueueState> = _queueState.asStateFlow()

    
    fun enqueuePhotos(tripId: String, uris: List<Uri>, tag: String?) {
        viewModelScope.launch {
            _queueState.value = UploadQueueState(isProcessing = true)
            val context = getApplication<Application>()
            var queued = 0
            var skipped = 0

            for (uri in uris) {
                try {

                    val md5 = PhotoUtils.computeMd5(context, uri)

                    val existing = dao.getByHash(md5)
                    if (existing != null) {
                        skipped++
                        continue
                    }

                    val exif = PhotoUtils.extractExif(context, uri)

                    val cachedUriString = PhotoUtils.copyToCache(context, uri, md5)

                    val queuedPhoto = QueuedPhoto(
                        localUri = cachedUriString,
                        tripId = tripId,
                        timestamp = exif.timestamp ?: System.currentTimeMillis(),
                        lat = exif.lat,
                        lng = exif.lng,
                        tag = tag,
                        md5Hash = md5
                    )
                    val insertedId = dao.insert(queuedPhoto).toInt()

                    val workRequest = OneTimeWorkRequestBuilder<PhotoUploadWorker>()
                        .setInputData(
                            workDataOf(
                                PhotoUploadWorker.KEY_TRIP_ID to tripId,
                                PhotoUploadWorker.KEY_QUEUED_PHOTO_ID to insertedId
                            )
                        )
                        .setConstraints(
                            Constraints.Builder()
                                .setRequiredNetworkType(NetworkType.CONNECTED)
                                .build()
                        )
                        .addTag("upload_$tripId")
                        .build()

                    workManager.enqueue(workRequest)
                    queued++
                } catch (_: Exception) {

                }
            }

            _queueState.value = UploadQueueState(
                totalQueued = queued,
                duplicatesSkipped = skipped,
                isProcessing = false
            )
        }
    }
}

