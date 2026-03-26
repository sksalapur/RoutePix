package com.routepix.util

import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import com.routepix.R
import java.io.File

class DownloadCompleteReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "DownloadReceiver"
        private const val CHANNEL_ID = "download_complete"
        private const val CHANNEL_NAME = "Downloads"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return

        val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        if (downloadId == -1L) return

        // Check if this download belongs to us
        if (!DownloadTracker.isTracked(downloadId)) return
        val filename = DownloadTracker.consumeDownload(downloadId) ?: return

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = dm.query(query) ?: return

        cursor.use {
            if (!it.moveToFirst()) return

            val statusIndex = it.getColumnIndex(DownloadManager.COLUMN_STATUS)
            val status = it.getInt(statusIndex)

            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                val localUriIndex = it.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                val localUriStr = it.getString(localUriIndex)
                val localUri = Uri.parse(localUriStr)
                val filePath = localUri.path

                if (filePath != null) {
                    // Trigger media scanner so the image appears in Gallery
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(filePath),
                        arrayOf("image/jpeg")
                    ) { _, scannedUri ->
                        Log.d(TAG, "Media scanned: $scannedUri")
                        // Show notification with the content URI from media scanner
                        showSuccessNotification(context, filename, filePath, scannedUri)
                    }
                } else {
                    showSuccessNotification(context, filename, null, null)
                }
            } else {
                showFailureNotification(context, filename)
            }
        }
    }

    private fun showSuccessNotification(
        context: Context,
        filename: String,
        filePath: String?,
        contentUri: Uri?
    ) {
        ensureChannel(context)

        val pendingIntent = if (contentUri != null) {
            // Use the content:// URI from MediaScanner — works perfectly with Gallery/Photos
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "image/jpeg")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            PendingIntent.getActivity(
                context, filename.hashCode(), viewIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else if (filePath != null) {
            // Fallback: use FileProvider
            val file = File(filePath)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "image/jpeg")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            PendingIntent.getActivity(
                context, filename.hashCode(), viewIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else null

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_master)
            .setContentTitle("Download Complete")
            .setContentText("Tap to view image")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .apply {
                if (pendingIntent != null) setContentIntent(pendingIntent)
            }
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(filename.hashCode(), notification)
    }

    private fun showFailureNotification(context: Context, filename: String) {
        ensureChannel(context)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_master)
            .setContentTitle("Download Failed")
            .setContentText("Could not download $filename")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(filename.hashCode(), notification)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifies when a photo download completes"
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
