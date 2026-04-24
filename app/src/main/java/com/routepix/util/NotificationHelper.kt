package com.routepix.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import com.routepix.R

object NotificationHelper {
    private const val CHANNEL_ID = "upload_progress_channel"
    private const val CHANNEL_NAME = "Upload Progress"
    private const val NOTIFICATION_ID = 1001

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress of photo uploads"
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun getForegroundInfo(context: Context, progress: Int, total: Int, customTitle: String? = null, customContent: String? = null): ForegroundInfo {
        val notification = createNotification(context, progress, total, customTitle, customContent)
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    fun createNotification(context: Context, progress: Int, total: Int, customTitle: String? = null, customContent: String? = null): Notification {
        val title = customTitle ?: "Uploading Photos"
        val content = customContent ?: if (total > 0) "$progress of $total completed" else "Preparing upload..."
        
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .setProgress(total, progress, total == 0)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()
    }
}
