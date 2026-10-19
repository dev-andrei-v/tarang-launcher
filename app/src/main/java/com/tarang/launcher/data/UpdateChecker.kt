package com.tarang.launcher.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

sealed class UpdateResult {
    data object UpToDate : UpdateResult()
    data class UpdateAvailable(
        val versionTag: String,
        val changelog: String,
        val apkUrl: String,
    ) : UpdateResult()
    data class Error(val reason: String) : UpdateResult()
}

/** Checks GitHub Releases for a newer Tarang build than the one currently installed. */
class UpdateChecker(private val context: Context, private val updateStore: UpdateStore) {

    /**
     * @param force when true (Retry after an error), skip the cooldown so a failed flow can
     *   continue without waiting out [MIN_CHECK_INTERVAL_MILLIS].
     */
    suspend fun checkForUpdate(nowMillis: Long, force: Boolean = false): UpdateResult =
        withContext(Dispatchers.IO) {
            val lastChecked = updateStore.lastCheckedAtMillis.first()
            if (!force && nowMillis - lastChecked < MIN_CHECK_INTERVAL_MILLIS) {
                // Prefer a stored pending release over a dead-end "checked recently" error so a
                // silent launch probe does not block Settings > Check for five minutes.
                val pending = updateStore.pendingUpdate.first()
                return@withContext if (pending != null) {
                    UpdateResult.UpdateAvailable(pending.versionTag, pending.changelog, pending.apkUrl)
                } else {
                    UpdateResult.UpToDate
                }
            }
            try {
                val json = fetchLatestRelease()
                updateStore.markCheckedNow(nowMillis)

                val tag = json.getString("tag_name")
                val changelog = json.optString("body", "")

                val assets = json.getJSONArray("assets")
                val apkAsset = (0 until assets.length())
                    .map { assets.getJSONObject(it) }
                    .firstOrNull { it.getString("name").endsWith(".apk") }
                    ?: return@withContext UpdateResult.Error("The latest release has no APK file.")
                val apkUrl = apkAsset.getString("browser_download_url")
                if (!isTrustedApkHost(apkUrl)) {
                    return@withContext UpdateResult.Error("The release asset came from an unexpected host.")
                }

                val currentVersion = installedVersionName()

                if (isNewer(tag, currentVersion)) {
                    UpdateResult.UpdateAvailable(tag, changelog, apkUrl)
                } else {
                    UpdateResult.UpToDate
                }
            } catch (e: Exception) {
                UpdateResult.Error(e.message ?: "The update check failed.")
            }
        }

    /** True when [versionTag] is newer than the installed app version. */
    fun isNewerThanInstalled(versionTag: String): Boolean =
        isNewer(versionTag, installedVersionName())

    private fun installedVersionName(): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"

    private fun fetchLatestRelease(): JSONObject {
        val connection = URL(RELEASES_URL).openConnection() as HttpURLConnection
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
        connection.readTimeout = READ_TIMEOUT_MILLIS
        try {
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            return JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Rejects anything but an https download from GitHub. [ApkDownloader] hands this URL to the
     * system DownloadManager, which is a separate process with its own network policy — this app's
     * cleartext-traffic restriction doesn't apply to it, so a plain-http or unexpected host in the
     * API response must be caught here instead.
     */
    private fun isTrustedApkHost(apkUrl: String): Boolean {
        val uri = Uri.parse(apkUrl)
        val host = uri.host ?: return false
        return uri.scheme == "https" &&
            (host == "github.com" || host == "objects.githubusercontent.com" || host.endsWith(".github.com"))
    }

    private fun isNewer(latestTag: String, currentVersion: String): Boolean {
        val latest = latestTag.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
        val current = currentVersion.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(latest.size, current.size)) {
            val l = latest.getOrElse(i) { 0 }
            val c = current.getOrElse(i) { 0 }
            if (l != c) return l > c
        }
        return false
    }

    companion object {
        /** Set false while this fork has no release feed of its own. Hides Updates and skips the launch probe. */
        const val ENABLED = false

        const val RELEASES_URL = "https://api.github.com/repos/mohitkyadav/tarang-launcher/releases/latest"

        // GitHub's unauthenticated API allows 60 requests/hour; this keeps repeated button taps
        // well under that even if the launcher is left running for a long time.
        const val MIN_CHECK_INTERVAL_MILLIS = 5 * 60 * 1000L

        const val CONNECT_TIMEOUT_MILLIS = 10_000
        const val READ_TIMEOUT_MILLIS = 10_000
    }
}
