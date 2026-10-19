package com.tarang.launcher.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Last-known app list, persisted as a small JSON file so a cold start can draw the dock/grid on the
 * first frame instead of waiting for the PackageManager scan (which shows "Loading apps…" for
 * seconds on weak TV hardware). The real scan still runs right after and replaces the list — the
 * cache only ever fronts it, so a stale entry (app uninstalled overnight) lives for a moment at most.
 */
class AppListCache(context: Context) {

    private val file = File(context.applicationContext.filesDir, "app_list.json")
    private var lastWritten: List<AppInfo>? = null

    /** The cached list, or null when missing/unreadable (first run, corrupt file). */
    suspend fun read(): List<AppInfo>? = withContext(Dispatchers.IO) {
        runCatching {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                AppInfo(
                    label = AppPresentation.label(o.getString("pkg"), o.getString("label")),
                    packageName = o.getString("pkg"),
                    activityName = o.getString("activity"),
                    isTvApp = o.getBoolean("tv"),
                )
            }
        }.getOrNull()?.also { lastWritten = it }
    }

    /** Persists [apps] for the next cold start; skipped when unchanged since the last read/write. */
    suspend fun write(apps: List<AppInfo>) = withContext(Dispatchers.IO) {
        if (apps == lastWritten) return@withContext
        runCatching {
            val arr = JSONArray()
            for (app in apps) {
                arr.put(
                    JSONObject()
                        .put("label", app.label)
                        .put("pkg", app.packageName)
                        .put("activity", app.activityName)
                        .put("tv", app.isTvApp),
                )
            }
            file.writeText(arr.toString())
            lastWritten = apps
        }
    }
}
