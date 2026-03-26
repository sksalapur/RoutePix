package com.routepix.util

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment

object DownloadUtils {

    
    fun enqueueDownload(context: Context, url: String, filename: String, albumName: String? = null) {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val uri = Uri.parse(url)
        
        val subPath = if (albumName != null) {
            "RoutePix/$albumName/$filename"
        } else {
            "RoutePix/$filename"
        }

        val request = DownloadManager.Request(uri)
            .setTitle(filename)
            .setDescription("Saving to your RoutePix collection...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(
                Environment.DIRECTORY_PICTURES,
                subPath
            )

        downloadManager.enqueue(request)
    }
}

