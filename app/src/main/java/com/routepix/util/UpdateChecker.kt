package com.routepix.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.routepix.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object UpdateChecker {

    private const val TAG = "UpdateChecker"
    private const val CHANNEL_ID = "app_updates"
    private const val NOTIFICATION_ID = 9001
    private const val GITHUB_API_URL =
        "https://api.github.com/repos/sksalapur/RoutePix/releases/latest"

    /**
     * Check GitHub for a newer release. If found, post a notification
     * that opens the release page when tapped.
     */
    suspend fun checkForUpdate(context: Context) {
        try {
            val (latestTag, releaseUrl, releaseName) = fetchLatestRelease() ?: return

            val currentVersion = getAppVersion(context)
            if (isNewer(latestTag, currentVersion)) {
                showUpdateNotification(context, releaseName, releaseUrl)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Update check failed (non-fatal): ${e.message}")
        }
    }

    // ── GitHub API ──────────────────────────────────────────────

    private data class ReleaseInfo(val tag: String, val url: String, val name: String)

    private suspend fun fetchLatestRelease(): ReleaseInfo? = withContext(Dispatchers.IO) {
        val connection = URL(GITHUB_API_URL).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.connectTimeout = 8_000
        connection.readTimeout = 8_000

        try {
            if (connection.responseCode != 200) return@withContext null

            val body = connection.inputStream.bufferedReader().readText()
            val json = JSONObject(body)

            val tag = json.optString("tag_name", "").removePrefix("v")
            val url = json.optString("html_url", "")
            val name = json.optString("name", "RoutePix Update")

            if (tag.isBlank() || url.isBlank()) null
            else ReleaseInfo(tag, url, name)
        } finally {
            connection.disconnect()
        }
    }

    // ── Version comparison ──────────────────────────────────────

    private fun getAppVersion(context: Context): String {
        return try {
            context.packageManager
                .getPackageInfo(context.packageName, 0)
                .versionName ?: "0.0.0"
        } catch (_: Exception) {
            "0.0.0"
        }
    }

    /**
     * Compare semantic versions. Returns true if [remote] > [local].
     */
    private fun isNewer(remote: String, local: String): Boolean {
        val r = remote.split(".").mapNotNull { it.toIntOrNull() }
        val l = local.split(".").mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(r.size, l.size)) {
            val rv = r.getOrElse(i) { 0 }
            val lv = l.getOrElse(i) { 0 }
            if (rv > lv) return true
            if (rv < lv) return false
        }
        return false
    }

    // ── Notification ────────────────────────────────────────────

    private fun showUpdateNotification(context: Context, title: String, releaseUrl: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create channel (Android O+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "App Updates",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifies when a new version of RoutePix is available"
            }
            manager.createNotificationChannel(channel)
        }

        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(releaseUrl))
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            browserIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_master)
            .setContentTitle("Update Available")
            .setContentText("$title is ready — tap to download")
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "$title is available. Tap to open the release page and download the latest APK."
            ))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }
}
