package com.tarang.launcher.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock

/**
 * Apps that open on a chosen screen on their first start after boot.
 *
 * Each entry is a deep link that the app declares in its manifest. The launcher sends the link only
 * when it has not opened the app since the TV booted. Later launches use the normal launch intent,
 * so an app that is still open resumes where the user left it. When the system kills the app in the
 * background, Android restarts it with the first intent, which still holds the link.
 */
class AppLaunchTargets(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("launch_targets", Context.MODE_PRIVATE)

    /** The deep link intent for [packageName], or null when the app opens normally this time. */
    fun intentFor(packageName: String): Intent? {
        val link = TARGETS[packageName] ?: return null
        if (openedSinceBoot(packageName)) return null
        return Intent(Intent.ACTION_VIEW, Uri.parse(link)).setPackage(packageName)
    }

    /** Records that the launcher opened [packageName]. Call it after a successful start. */
    fun markOpened(packageName: String) {
        if (packageName !in TARGETS) return
        prefs.edit().putLong(packageName, System.currentTimeMillis()).apply()
    }

    private fun openedSinceBoot(packageName: String): Boolean {
        val bootTime = System.currentTimeMillis() - SystemClock.elapsedRealtime()
        return prefs.getLong(packageName, 0L) >= bootTime
    }

    private companion object {
        val TARGETS = mapOf(
            // Digi Online: open the live TV channel list, not the home feed.
            "ro.digionline.tv" to "digitv://channel",
        )
    }
}
