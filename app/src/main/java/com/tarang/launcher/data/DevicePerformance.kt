package com.tarang.launcher.data

import android.app.ActivityManager
import android.content.Context
import android.os.Build

/** Use small caches and flat glass on TVs with at most 1.5 GiB RAM. */
object DevicePerformance {
    fun isLowMemory(context: Context): Boolean {
        val manager = context.getSystemService(ActivityManager::class.java) ?: return true
        val info = ActivityManager.MemoryInfo()
        manager.getMemoryInfo(info)
        return manager.isLowRamDevice || info.totalMem <= 1536L * 1024 * 1024
    }

    fun supportsGlass(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !isLowMemory(context)
}
