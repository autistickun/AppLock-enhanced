package dev.pranav.applock.core.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import dev.pranav.applock.MainActivity
import dev.pranav.applock.R
import dev.pranav.applock.core.utils.LogUtils
import dev.pranav.applock.core.utils.appLockRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object UpdateManager {
    private const val TAG = "UpdateManager"
    const val EXTRA_SHOW_UPDATE = "extra_show_update"
    private const val ENHANCED_RELEASE_API = "https://api.github.com/repos/autistickun/AppLock-enhanced/releases/latest"
    private const val UPSTREAM_RELEASE_API = "https://api.github.com/repos/aload0/AppLock/releases/latest"
    private const val CHANNEL_ID = "applock_updates_channel"
    private const val NOTIFICATION_ID = 2001

    /**
     * Checks for updates against GitHub Releases or a custom endpoint.
     * Pure offline design: strictly runs on-demand without persistent connections.
     */
    suspend fun checkForUpdate(context: Context): UpdateResult = withContext(Dispatchers.IO) {
        try {
            val appLockRepository = context.appLockRepository()
            val customUrl = appLockRepository.getCustomUpdateUrl()
            val endpointsToTry = if (!customUrl.isNullOrBlank()) {
                listOf(customUrl)
            } else {
                listOf(ENHANCED_RELEASE_API, UPSTREAM_RELEASE_API)
            }

            var jsonResult: JSONObject? = null
            var lastError = "Could not fetch release info"

            for (endpoint in endpointsToTry) {
                try {
                    val url = URL(endpoint)
                    val connection = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 6000
                        readTimeout = 6000
                        setRequestProperty("User-Agent", "AppLock-Android-Updater")
                        setRequestProperty("Accept", "application/vnd.github.v3+json")
                    }

                    val responseCode = connection.responseCode
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        val reader = BufferedReader(InputStreamReader(connection.inputStream))
                        val response = StringBuilder()
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            response.append(line)
                        }
                        reader.close()
                        connection.disconnect()
                        jsonResult = JSONObject(response.toString())
                        break
                    } else {
                        connection.disconnect()
                        lastError = "Server returned code: $responseCode from $endpoint"
                    }
                } catch (e: Exception) {
                    lastError = e.localizedMessage ?: "Failed to connect to $endpoint"
                }
            }

            if (jsonResult == null) {
                return@withContext UpdateResult.Error(lastError)
            }

            val json = jsonResult
            val tagName = json.optString("tag_name", "").removePrefix("v")
            val releaseTitle = json.optString("name", "Version $tagName")
            val patchNotes = json.optString("body", "No patch notes provided.")
            val htmlUrl = json.optString("html_url", "")
            val publishedAt = json.optString("published_at", "")

            var downloadUrl = htmlUrl
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                var releaseApkUrl: String? = null
                var fallbackApkUrl: String? = null
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val assetName = asset.optString("name", "")
                    // Always strictly prioritize the production release APK over debug
                    if (assetName.contains("release", ignoreCase = true) && assetName.endsWith(".apk", ignoreCase = true)) {
                        releaseApkUrl = asset.optString("browser_download_url", "")
                        break
                    } else if (assetName.endsWith(".apk", ignoreCase = true) && !assetName.contains("debug", ignoreCase = true)) {
                        fallbackApkUrl = asset.optString("browser_download_url", "")
                    }
                }
                downloadUrl = releaseApkUrl ?: fallbackApkUrl ?: htmlUrl
            }

            appLockRepository.setLastUpdateCheckTime(System.currentTimeMillis())

            val currentVersion = getCurrentVersionName(context)
            if (isNewerVersion(tagName, currentVersion)) {
                val updateInfo = UpdateInfo(
                    versionName = tagName,
                    releaseTitle = releaseTitle,
                    patchNotes = patchNotes,
                    downloadUrl = downloadUrl,
                    htmlUrl = htmlUrl,
                    publishedAt = publishedAt
                )
                UpdateResult.Available(updateInfo)
            } else {
                UpdateResult.UpToDate
            }
        } catch (e: Exception) {
            LogUtils.e(TAG, "Error checking for updates", e)
            UpdateResult.Error(e.localizedMessage ?: "Failed to check for updates")
        }
    }

    suspend fun checkAndNotifyUpdate(context: Context): UpdateResult = withContext(Dispatchers.IO) {
        val result = checkForUpdate(context)
        if (result is UpdateResult.Available) {
            showUpdateNotification(context, result.updateInfo)
        }
        result
    }

    fun getCurrentVersionName(context: Context): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName?.removePrefix("v") ?: "0.0.0"
        } catch (_: Exception) {
            "0.0.0"
        }
    }

    /**
     * Compares semantic versions: returns true if targetVersion > currentVersion.
     */
    fun isNewerVersion(targetVersion: String, currentVersion: String): Boolean {
        if (targetVersion.isBlank() || currentVersion.isBlank()) return false
        val targetParts = targetVersion.split(".").mapNotNull { it.takeWhile { char -> char.isDigit() }.toIntOrNull() }
        val currentParts = currentVersion.split(".").mapNotNull { it.takeWhile { char -> char.isDigit() }.toIntOrNull() }

        val length = maxOf(targetParts.size, currentParts.size)
        for (i in 0 until length) {
            val targetNum = targetParts.getOrElse(i) { 0 }
            val currentNum = currentParts.getOrElse(i) { 0 }
            if (targetNum > currentNum) return true
            if (targetNum < currentNum) return false
        }
        return false
    }

    /**
     * Shows a system notification alerting the user of an available update.
     */
    fun showUpdateNotification(context: Context, updateInfo: UpdateInfo) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "App Updates",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for AppLock updates"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_SHOW_UPDATE, true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.update_available_title, updateInfo.versionName))
            .setContentText(context.getString(R.string.update_available_text))
            .setStyle(NotificationCompat.BigTextStyle().bigText(updateInfo.patchNotes))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    /**
     * Directly downloads the release APK to cache and launches the system Package Installer.
     * Completely bypasses opening the GitHub website in the browser.
     */
    suspend fun downloadAndInstallApk(
        context: Context,
        downloadUrl: String,
        onProgress: ((Float) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val updatesDir = File(context.cacheDir, "updates")
            if (!updatesDir.exists()) {
                updatesDir.mkdirs()
            }
            val apkFile = File(updatesDir, "app-release.apk")
            if (apkFile.exists()) {
                apkFile.delete()
            }

            var currentUrl = downloadUrl
            var connection = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 15000
                readTimeout = 25000
                setRequestProperty("User-Agent", "AppLock-Android-Updater")
            }

            var redirectCount = 0
            while (connection.responseCode in listOf(301, 302, 303, 307, 308) && redirectCount < 5) {
                val newUrl = connection.getHeaderField("Location") ?: break
                connection.disconnect()
                currentUrl = newUrl
                connection = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 25000
                    setRequestProperty("User-Agent", "AppLock-Android-Updater")
                }
                redirectCount++
            }

            val totalBytes = connection.contentLengthLong
            val inputStream = connection.inputStream
            val outputStream = FileOutputStream(apkFile)

            val buffer = ByteArray(8192)
            var bytesRead: Int
            var totalRead: Long = 0

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalRead += bytesRead
                if (totalBytes > 0) {
                    val progress = totalRead.toFloat() / totalBytes.toFloat()
                    onProgress?.invoke(progress.coerceIn(0f, 1f))
                }
            }

            outputStream.flush()
            outputStream.close()
            inputStream.close()
            connection.disconnect()

            withContext(Dispatchers.Main) {
                installApk(context, apkFile)
            }
            true
        } catch (e: Exception) {
            LogUtils.e(TAG, "Failed to download and install APK: $downloadUrl", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Direct download failed, opening browser", Toast.LENGTH_SHORT).show()
                launchUpdateDownloadFallback(context, downloadUrl)
            }
            false
        }
    }

    fun installApk(context: Context, apkFile: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            LogUtils.e(TAG, "Failed to launch package installer", e)
            launchUpdateDownloadFallback(context, "https://github.com/autistickun/AppLock-enhanced/releases/latest")
        }
    }

    fun launchUpdateDownloadFallback(context: Context, downloadUrl: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            LogUtils.e(TAG, "Failed to launch update URL: $downloadUrl", e)
        }
    }
}
