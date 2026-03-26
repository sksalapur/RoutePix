package com.routepix.util

/**
 * Simple in-memory tracker that maps DownloadManager IDs to filenames.
 * This lets the BroadcastReceiver know which downloads belong to RoutePix
 * and what filename to use in the notification.
 */
object DownloadTracker {
    private val activeDownloads = mutableMapOf<Long, String>()

    @Synchronized
    fun track(downloadId: Long, filename: String) {
        activeDownloads[downloadId] = filename
    }

    @Synchronized
    fun isTracked(downloadId: Long): Boolean = activeDownloads.containsKey(downloadId)

    @Synchronized
    fun consumeDownload(downloadId: Long): String? = activeDownloads.remove(downloadId)
}
