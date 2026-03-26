package com.routepix.ui.picker

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.routepix.data.local.QueuedPhoto
import com.routepix.data.local.RoutepixDatabase
import com.routepix.util.PhotoUtils
import com.routepix.worker.PhotoUploadWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UploadQueueState(
    val totalSelected: Int = 0,
    val queued: Int = 0,
    val duplicatesSkipped: Int = 0,
    val failed: Int = 0,
    val isProcessing: Boolean = false
)


class PhotoPickerViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = RoutepixDatabase.getInstance(application).queuedPhotoDao()
    private val workManager = WorkManager.getInstance(application)

    private val _queueState = MutableStateFlow(UploadQueueState())
    val queueState: StateFlow<UploadQueueState> = _queueState.asStateFlow()

    companion object {
        private const val TAG = "PhotoPickerVM"
        private const val CHUNK_SIZE = 5
    }

    fun enqueueFolder(tripId: String, treeUri: Uri, tag: String?) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            try {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to take persistable permission on tree URI", e)
            }

            val uris = mutableListOf<Uri>()
            val childrenUri = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(
                treeUri,
                android.provider.DocumentsContract.getTreeDocumentId(treeUri)
            )

            try {
                context.contentResolver.query(
                    childrenUri,
                    arrayOf(
                        android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE
                    ),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    val idColumn = cursor.getColumnIndexOrThrow(android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val mimeColumn = cursor.getColumnIndexOrThrow(android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE)

                    while (cursor.moveToNext()) {
                        val mimeType = cursor.getString(mimeColumn)
                        if (mimeType != null && mimeType.startsWith("image/")) {
                            val docId = cursor.getString(idColumn)
                            val documentUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(
                                treeUri,
                                docId
                            )
                            uris.add(documentUri)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to query document tree", e)
            }

            if (uris.isNotEmpty()) {
                enqueuePhotos(tripId, uris, tag)
            }
        }
    }

    fun enqueuePhotos(tripId: String, uris: List<Uri>, tag: String?) {
        viewModelScope.launch {
            _queueState.value = UploadQueueState(totalSelected = uris.size, isProcessing = true)

            workManager.pruneWork()

            var queued = 0
            var skipped = 0
            var failed = 0

            val chunks = uris.chunked(CHUNK_SIZE)

            for (chunk in chunks) {
                val results = withContext(Dispatchers.IO) {
                    chunk.map { uri -> processPhoto(uri, tripId, tag) }
                }

                for (result in results) {
                    when (result) {
                        is EnqueueResult.Queued -> {
                            enqueueWorker(tripId, result.photoId)
                            queued++
                        }
                        is EnqueueResult.Duplicate -> skipped++
                        is EnqueueResult.Failed -> failed++
                    }
                }

                _queueState.value = UploadQueueState(
                    totalSelected = uris.size,
                    queued = queued,
                    duplicatesSkipped = skipped,
                    failed = failed,
                    isProcessing = true
                )
            }

            _queueState.value = _queueState.value.copy(isProcessing = false)
            Log.d(TAG, "Enqueue complete: queued=$queued, skipped=$skipped, failed=$failed out of ${uris.size}")
        }
    }

    private suspend fun processPhoto(uri: Uri, tripId: String, tag: String?): EnqueueResult {
        val context = getApplication<Application>()
        return try {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) { }

            val md5 = PhotoUtils.computeMd5(context, uri)

            val existing = dao.getByHash(md5)
            if (existing != null) {
                return EnqueueResult.Duplicate
            }

            val exif = PhotoUtils.extractExif(context, uri)

            val queuedPhoto = QueuedPhoto(
                localUri = uri.toString(),
                tripId = tripId,
                timestamp = exif.timestamp ?: System.currentTimeMillis(),
                lat = exif.lat,
                lng = exif.lng,
                tag = tag,
                md5Hash = md5
            )
            val insertedId = dao.insert(queuedPhoto).toInt()
            EnqueueResult.Queued(insertedId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process photo: $uri", e)
            EnqueueResult.Failed
        }
    }

    private fun enqueueWorker(tripId: String, photoId: Int) {
        val workRequest = OneTimeWorkRequestBuilder<PhotoUploadWorker>()
            .setInputData(
                workDataOf(
                    PhotoUploadWorker.KEY_TRIP_ID to tripId,
                    PhotoUploadWorker.KEY_QUEUED_PHOTO_ID to photoId
                )
            )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .addTag("upload_$tripId")
            .build()

        workManager.enqueueUniqueWork(
            "upload_chain_$tripId",
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            workRequest
        )
    }

    private sealed class EnqueueResult {
        data class Queued(val photoId: Int) : EnqueueResult()
        data object Duplicate : EnqueueResult()
        data object Failed : EnqueueResult()
    }
}
