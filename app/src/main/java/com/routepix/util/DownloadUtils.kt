package com.routepix.util

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment

object DownloadUtils {

    fun enqueueDownload(context: Context, url: String, filename: String, albumName: String? = null) {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val uri = Uri.parse(url)

        // Ensure .jpg extension
        val safeFilename = if (!filename.endsWith(".jpg", ignoreCase = true)) {
            "$filename.jpg"
        } else {
            filename
        }

        val subPath = if (albumName != null) {
            "RoutePix/$albumName/$safeFilename"
        } else {
            "RoutePix/$safeFilename"
        }

        val request = DownloadManager.Request(uri)
            .setTitle(safeFilename)
            .setDescription("Downloading from RoutePix")
            .setMimeType("image/jpeg")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_HIDDEN)
            .setDestinationInExternalPublicDir(
                Environment.DIRECTORY_PICTURES,
                subPath
            )

        val downloadId = downloadManager.enqueue(request)
        DownloadTracker.track(downloadId, safeFilename)
    }
}
