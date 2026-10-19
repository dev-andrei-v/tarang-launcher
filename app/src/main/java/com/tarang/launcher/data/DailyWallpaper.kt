package com.tarang.launcher.data

import android.util.Log
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** The rotation intervals (hours) for the daily picture wallpaper. */
val DAILY_WALLPAPER_HOURS = listOf(24, 12, 6, 1)

/**
 * A picture from the Bing daily image archive as the home wallpaper.
 *
 * The archive publishes one new picture each day and keeps the last eight. Tarang downloads one
 * picture at a time, stores it in `filesDir/daily/`, and keeps at most [MAX_CACHED] pictures.
 * When the TV is offline, the cached pictures rotate instead. A caption sidecar (`.txt`) holds the
 * picture credit for the settings page.
 */
class DailyWallpaper(context: android.content.Context, private val settingsStore: SettingsStore) {

    private val appContext = context.applicationContext
    private val dir = File(appContext.filesDir, "daily")
    private val lowMemory = DevicePerformance.isLowMemory(appContext)
    private val lock = Mutex()
    private var lastAttemptAt = 0L

    /** A picture entry from the archive. */
    private data class Entry(val date: String, val urlBase: String, val caption: String)

    /**
     * Downloads the next picture when the rotation slot changed, or when [force] is true.
     * Does nothing when the daily wallpaper is off. Keeps the current picture on a network error.
     */
    suspend fun refreshIfDue(now: Long, force: Boolean = false) = lock.withLock {
        withContext(Dispatchers.IO) {
            val s = settingsStore.settings.first()
            if (!s.dailyWallpaper) return@withContext
            val intervalMs = s.dailyWallpaperHours.coerceAtLeast(1) * HOUR_MS
            val current = s.dailyWallpaperPath?.let(::File)?.takeIf { it.exists() }
            val slot = slotOf(now, intervalMs)
            val due = force || current == null || slot != slotOf(s.dailyWallpaperUpdatedAt, intervalMs)
            if (!due) return@withContext
            if (!force && now - lastAttemptAt < RETRY_MS) return@withContext
            lastAttemptAt = now

            dir.mkdirs()
            val fetched = runCatching { fetchArchive() }
                .onFailure { Log.w(TAG, "Archive fetch failed", it) }
                .getOrNull()
            val picked: File? = if (!fetched.isNullOrEmpty()) {
                // Daily: today's picture. Shorter intervals: step through the archive.
                val index = if (s.dailyWallpaperHours >= 24) 0 else (slot % fetched.size).toInt()
                val entry = fetched[index]
                val file = File(dir, "${entry.date}.jpg")
                val ok = file.exists() || runCatching { download(entry, file) }
                    .onFailure { Log.w(TAG, "Picture download failed", it) }
                    .getOrDefault(false)
                if (ok) {
                    File(dir, "${entry.date}.txt").writeText(entry.caption)
                    file
                } else {
                    null
                }
            } else {
                null
            }
            val next = picked ?: rotateCached(slot) ?: return@withContext
            prune(keep = next)
            val caption = File(dir, next.nameWithoutExtension + ".txt")
                .takeIf { it.exists() }?.readText()?.trim().orEmpty()
            settingsStore.setDailyWallpaperPicture(next.absolutePath, caption, now)
            Log.i(TAG, "Daily wallpaper is ${next.name}")
        }
    }

    /** Offline: the next cached picture for this slot, or null when the cache is empty. */
    private fun rotateCached(slot: Long): File? {
        val files = cachedPictures()
        if (files.isEmpty()) return null
        return files[(slot % files.size).toInt()]
    }

    private fun cachedPictures(): List<File> =
        dir.listFiles { f -> f.isFile && f.extension == "jpg" }?.sortedBy { it.name } ?: emptyList()

    /** Keeps the newest [MAX_CACHED] pictures and the one in use. Removes the rest. */
    private fun prune(keep: File) {
        val files = cachedPictures().sortedByDescending { it.name }
        files.drop(MAX_CACHED).filter { it != keep }.forEach { f ->
            f.delete()
            File(dir, f.nameWithoutExtension + ".txt").delete()
        }
    }

    /** Local-time rotation slots, so a daily picture changes at local midnight. */
    private fun slotOf(timeMs: Long, intervalMs: Long): Long =
        (timeMs + TimeZone.getDefault().getOffset(timeMs)) / intervalMs

    private fun fetchArchive(): List<Entry> {
        val connection = URL(ARCHIVE_URL).openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        try {
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val images = JSONObject(body).getJSONArray("images")
            return (0 until images.length()).map { i ->
                val o = images.getJSONObject(i)
                val copyright = o.optString("copyright", "")
                Entry(
                    date = o.getString("startdate").filter { it.isDigit() },
                    urlBase = o.getString("urlbase"),
                    caption = copyright.ifBlank { o.optString("title", "") },
                )
            }.filter { it.date.isNotEmpty() && it.urlBase.startsWith("/") }
        } finally {
            connection.disconnect()
        }
    }

    /** Downloads the picture to a temporary file, then moves it into place. */
    private fun download(entry: Entry, target: File): Boolean {
        val size = if (lowMemory) "1280x720" else "1920x1080"
        val url = URL("https://www.bing.com${entry.urlBase}_$size.jpg")
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        val tmp = File(dir, target.name + ".part")
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return false
            var total = 0L
            connection.inputStream.use { input ->
                tmp.outputStream().use { out ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        total += n
                        if (total > MAX_BYTES) return false
                        out.write(buffer, 0, n)
                    }
                }
            }
            return total > 0 && tmp.renameTo(target)
        } finally {
            tmp.delete()
            connection.disconnect()
        }
    }

    private companion object {
        const val TAG = "DailyWallpaper"
        const val ARCHIVE_URL = "https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=8&mkt=en-US"
        const val HOUR_MS = 3_600_000L
        const val RETRY_MS = 15 * 60_000L
        const val MAX_CACHED = 8
        const val MAX_BYTES = 6L * 1024 * 1024
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 20_000
    }
}
