package com.tarang.launcher.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.tarang.launcher.data.DevicePerformance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** Loads sampled artwork with a byte budget and one decode at a time. */
object ArtworkLoader {
    private const val TAG = "ArtworkLoader"
    private val decodePermit = Semaphore(1)
    private val cache = object : LruCache<String, ImageBitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.asAndroidBitmap().allocationByteCount
    }

    fun clearMemory() = cache.evictAll()

    suspend fun load(context: Context, uriString: String, reqW: Int = 1280, reqH: Int = 720): ImageBitmap? {
        val lowMemory = DevicePerformance.isLowMemory(context)
        val budget = if (lowMemory) 8 * 1024 * 1024 else 24 * 1024 * 1024
        if (cache.maxSize() != budget) cache.resize(budget)
        val width = (if (lowMemory) reqW.coerceAtMost(1280) else reqW).coerceAtLeast(1)
        val height = (if (lowMemory) reqH.coerceAtMost(720) else reqH).coerceAtLeast(1)
        val key = "$uriString@$width:$height"
        cache.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            decodePermit.withPermit {
                cache.get(key)?.let { return@withPermit it }
                val bitmap = runCatching { loadSampled(context, uriString, width, height) }
                    .onFailure { Log.w(TAG, "Load failed: $uriString", it) }
                    .getOrNull()
                if (bitmap != null) cache.put(key, bitmap)
                bitmap
            }
        }
    }

    private fun loadSampled(context: Context, uriString: String, width: Int, height: Int): ImageBitmap? {
        val uri = Uri.parse(uriString)
        if (uri.scheme?.lowercase() !in listOf("http", "https")) {
            return decodeSampled(width, height) { context.contentResolver.openInputStream(uri) }
        }

        // Spool compressed data to disk instead of retaining full image files in the TV heap.
        val file = File.createTempFile("artwork-", ".img", context.cacheDir)
        val connection = (URL(uriString).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            instanceFollowRedirects = true
        }
        try {
            connection.inputStream.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0
                    var count = input.read(buffer)
                    while (count != -1) {
                        total += count
                        check(total <= 16 * 1024 * 1024) { "Artwork exceeds 16 MiB" }
                        output.write(buffer, 0, count)
                        count = input.read(buffer)
                    }
                }
            }
            return decodeSampled(width, height) { file.inputStream() }
        } finally {
            connection.disconnect()
            file.delete()
        }
    }

    private fun decodeSampled(reqW: Int, reqH: Int, open: () -> InputStream?): ImageBitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open()?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > reqW || bounds.outHeight / sample > reqH) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return open()?.use { BitmapFactory.decodeStream(it, null, options) }?.asImageBitmap()
    }
}
