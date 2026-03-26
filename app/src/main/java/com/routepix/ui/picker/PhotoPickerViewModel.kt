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
import com.routepix.data.model.PhotoMeta
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
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

    data class UploadRequest(
        val tripId: String,
        val uris: List<Uri>,
        val tag: String?,
        val conflictingUris: List<Uri>,
        val conflictingMetas: List<PhotoMeta>
    )

    private val _pendingUpload = MutableStateFlow<UploadRequest?>(null)
    val pendingUpload: StateFlow<UploadRequest?> = _pendingUpload.asStateFlow()

    /**
     * Application-scoped coroutine scope that survives ViewModel destruction.
     * Uses Dispatchers.IO (not Main) so it's not tied to Activity lifecycle.
     * This ensures the enqueue operation completes even if the user navigates
     * away from the timeline screen (which cancels viewModelScope).
     */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val TAG = "PhotoPickerVM"
        private const val CHUNK_SIZE = 10
    }

    fun enqueueFolder(tripId: String, treeUri: Uri, tag: String?) {
        appScope.launch {
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
                // Ensure trip still exists before starting
                try {
                    val tripDoc = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        .collection("trips").document(tripId).get().await()
                    if (!tripDoc.exists()) {
                        Log.e(TAG, "Trip $tripId does not exist, aborting enqueue")
                        _queueState.value = UploadQueueState(failed = uris.size, isProcessing = false)
                        return@launch
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to verify trip existence", e)
                }
                val (conflictUris, conflictMetas) = checkTagConflicts(tripId, uris, tag)
                if (conflictMetas.isNotEmpty()) {
                    _pendingUpload.value = UploadRequest(tripId, uris, tag, conflictUris, conflictMetas)
                } else {
                    enqueuePhotos(tripId, uris, tag)
                }
            }
        }
    }

    fun requestUpload(tripId: String, uris: List<Uri>, tag: String?) {
        appScope.launch {
            val (conflictUris, conflictMetas) = checkTagConflicts(tripId, uris, tag)
            if (conflictMetas.isNotEmpty()) {
                _pendingUpload.value = UploadRequest(tripId, uris, tag, conflictUris, conflictMetas)
            } else {
                enqueuePhotos(tripId, uris, tag)
            }
        }
    }

    fun resolvePendingUpload(forceAdd: Boolean) {
        val request = _pendingUpload.value ?: return
        _pendingUpload.value = null
        if (forceAdd) {
            forceAddPhotos(request.tripId, request.conflictingMetas, request.tag)
        }
        val remainingUris = request.uris - request.conflictingUris.toSet()
        if (remainingUris.isNotEmpty()) {
            enqueuePhotos(request.tripId, remainingUris, request.tag)
        }
    }

    suspend fun checkTagConflicts(tripId: String, uris: List<Uri>, newTag: String?): Pair<List<Uri>, List<PhotoMeta>> = withContext(Dispatchers.IO) {
        val context = getApplication<Application>()
        val existingHashes = mutableMapOf<String, PhotoMeta>()
        try {
            val snapshot = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("trips").document(tripId)
                .collection("photos")
                .get().await()
            for (doc in snapshot.documents) {
                val meta = doc.toObject(PhotoMeta::class.java)
                if (meta?.md5Hash != null) {
                    existingHashes[meta.md5Hash] = meta
                }
            }
        } catch (_: Exception) {}

        val conflictingUris = mutableListOf<Uri>()
        val conflictingMetas = mutableListOf<PhotoMeta>()

        for (uri in uris) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {}
            
            val md5 = PhotoUtils.computeMd5(context, uri)
            val existing = existingHashes[md5]
            if (existing != null && existing.tag != newTag) {
                conflictingUris.add(uri)
                conflictingMetas.add(existing)
            }
        }
        Pair(conflictingUris, conflictingMetas)
    }

    fun forceAddPhotos(tripId: String, photos: List<PhotoMeta>, newTag: String?) {
        val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
        val uid = auth.currentUser?.uid ?: return
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        val batch = db.batch()
        
        for (photo in photos) {
            val newId = java.util.UUID.randomUUID().toString()
            val newPhoto = PhotoMeta(
                photoId = newId,
                telegramFileId = photo.telegramFileId,
                uploaderUid = uid,
                timestamp = System.currentTimeMillis(),
                placeName = photo.placeName,
                lat = photo.lat,
                lng = photo.lng,
                tag = newTag,
                md5Hash = photo.md5Hash,
                sizeBytes = photo.sizeBytes
            )
            val ref = db.collection("trips").document(tripId).collection("photos").document(newId)
            batch.set(ref, newPhoto)
        }
        
        appScope.launch {
            try {
                batch.commit().await()
                Log.d(TAG, "Successfully force duplicated ${photos.size} photos cross-tag")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to force duplicate", e)
            }
        }
    }

    fun enqueuePhotos(tripId: String, uris: List<Uri>, tag: String?) {
        // Launch on appScope so it survives back navigation
        appScope.launch {
            // NonCancellable ensures this completes even if ViewModel is destroyed
            withContext(NonCancellable) {
                _queueState.value = UploadQueueState(totalSelected = uris.size, isProcessing = true)

                workManager.pruneWork()

                var queuedCount = 0
                var skipped = 0
                var failed = 0

                // Batch the Firestore duplicate check: fetch all existing md5 hashes
                // for this trip ONCE, instead of doing 502 individual queries.
                val existingHashes = mutableSetOf<String>()
                try {
                    val snapshot = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        .collection("trips").document(tripId)
                        .collection("photos")
                        .get().await()
                    for (doc in snapshot.documents) {
                        doc.getString("md5Hash")?.let { existingHashes.add(it) }
                    }
                    Log.d(TAG, "Pre-fetched ${existingHashes.size} existing hashes from Firestore")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to pre-fetch existing hashes, continuing without dedup", e)
                }

                val chunks = uris.chunked(CHUNK_SIZE)

                for (chunk in chunks) {
                    val results = chunk.map { uri -> processPhoto(uri, tripId, tag, existingHashes) }

                    for (result in results) {
                        when (result) {
                            is EnqueueResult.Queued -> queuedCount++
                            is EnqueueResult.Duplicate -> skipped++
                            is EnqueueResult.Failed -> failed++
                        }
                    }

                    _queueState.value = UploadQueueState(
                        totalSelected = uris.size,
                        queued = queuedCount,
                        duplicatesSkipped = skipped,
                        failed = failed,
                        isProcessing = true
                    )
                }

                // Enqueue the worker ONCE after all photos are in the DB
                if (queuedCount > 0) {
                    enqueueWorker(tripId)
                }

                _queueState.value = _queueState.value.copy(isProcessing = false)
                Log.d(TAG, "Enqueue complete: queued=$queuedCount, skipped=$skipped, failed=$failed out of ${uris.size}")
            }
        }
    }

    private suspend fun processPhoto(
        uri: Uri,
        tripId: String,
        tag: String?,
        existingHashes: Set<String>
    ): EnqueueResult {
        val context = getApplication<Application>()
        return try {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) { }

            val md5 = PhotoUtils.computeMd5(context, uri)

            // Check 1: Is it already queued locally for this trip?
            val existingLocal = dao.getByHashForTrip(md5, tripId)
            if (existingLocal != null) {
                return EnqueueResult.Duplicate
            }

            // Check 2: Is it already uploaded in Firestore for this trip?
            // Uses the pre-fetched set (O(1) lookup) instead of a network call per photo.
            if (md5 in existingHashes) {
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

    private fun enqueueWorker(tripId: String) {
        val workRequest = OneTimeWorkRequestBuilder<PhotoUploadWorker>()
            .setInputData(
                workDataOf(
                    PhotoUploadWorker.KEY_TRIP_ID to tripId
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
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }

    private sealed class EnqueueResult {
        data class Queued(val photoId: Int) : EnqueueResult()
        data object Duplicate : EnqueueResult()
        data object Failed : EnqueueResult()
    }
}
