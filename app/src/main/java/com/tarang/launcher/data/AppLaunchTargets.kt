package com.tarang.launcher.data

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.net.Uri
import android.util.Log

/**
 * Apps that open on a chosen screen when the launcher starts them.
 *
 * Each entry is a deep link that the app declares in its manifest. The launcher sends it on every
 * start, with one exception: when the app is playing audio at that moment (a TV channel is on),
 * the launcher resumes the app as it is, so the channel keeps playing. The launcher falls back to
 * the normal launch intent when the app does not resolve the link.
 */
class AppLaunchTargets(context: Context) {

    private val appContext = context.applicationContext
    private val audio = appContext.getSystemService(AudioManager::class.java)

    /** The deep link intent for [packageName], or null when the app opens normally this time. */
    fun intentFor(packageName: String): Intent? {
        val link = TARGETS[packageName] ?: return null
        if (isPlayingAudio(packageName)) {
            Log.i(TAG, "$packageName is playing; resume it as it is")
            return null
        }
        return Intent(Intent.ACTION_VIEW, Uri.parse(link)).setPackage(packageName)
    }

    /** Kept for the repository call site; the link needs no stored state. */
    fun markOpened(@Suppress("UNUSED_PARAMETER") packageName: String) = Unit

    /**
     * True when [packageName] has an active audio player. The player's owner uid is not in the
     * public API, so this reads it by reflection and answers false when the system refuses.
     */
    private fun isPlayingAudio(packageName: String): Boolean {
        val uid = runCatching { appContext.packageManager.getApplicationInfo(packageName, 0).uid }
            .getOrNull() ?: return false
        val players = runCatching { audio?.activePlaybackConfigurations }.getOrNull() ?: return false
        if (players.isEmpty()) return false
        val clientUid = runCatching {
            AudioPlaybackConfiguration::class.java.getMethod("getClientUid")
        }.getOrNull() ?: return false
        return players.any { player -> runCatching { clientUid.invoke(player) as? Int }.getOrNull() == uid }
    }

    private companion object {
        const val TAG = "AppLaunchTargets"
        val TARGETS = mapOf(
            // Digi Online: open the live TV channel list, not the home feed.
            "ro.digionline.tv" to "digitv://channel",
        )
    }
}
