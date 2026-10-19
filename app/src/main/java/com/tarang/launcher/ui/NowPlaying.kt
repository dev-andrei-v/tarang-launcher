package com.tarang.launcher.ui

import android.app.ActivityManager
import android.os.Build
import android.content.Intent
import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import com.tarang.launcher.media.TarangNotificationListenerService
import kotlinx.coroutines.delay

/** A snapshot of whatever's currently playing, for the top-bar chip. */
data class NowPlaying(val title: String, val artist: String?, val art: ImageBitmap?, val packageName: String)

/**
 * Whether two snapshots would RENDER the same chip. Each poll builds a fresh object (with a fresh
 * [ImageBitmap] wrapper), so plain equality is always false — publishing that would recompose the
 * top bar every 2.5s while music plays. Art is compared by presence only: same track + art still
 * there means nothing visible changed.
 */
private fun NowPlaying?.sameDisplay(other: NowPlaying?): Boolean = when {
    this == null || other == null -> (this == null) == (other == null)
    else -> title == other.title && artist == other.artist && packageName == other.packageName &&
        (art == null) == (other.art == null)
}

/**
 * Polls the active media sessions while the launcher is visible and [enabled], returning the currently
 * *playing* session (or null — including when notification access hasn't been granted, since reading
 * sessions then throws and is swallowed). Polling (vs. a live listener) keeps it simple and robust.
 */
@Composable
fun rememberNowPlaying(enabled: Boolean): NowPlaying? {
    val context = LocalContext.current
    var np by remember { mutableStateOf<NowPlaying?>(null) }
    val supported = remember(context) { supportsNotificationAccess(context) }
    RunWhileStarted(enabled, supported) {
        if (!enabled || !supported) return@RunWhileStarted
        val msm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
            ?: return@RunWhileStarted
        val component = ComponentName(context, TarangNotificationListenerService::class.java)
        while (true) {
            val fresh = readNowPlaying(msm, component)
            if (!fresh.sameDisplay(np)) np = fresh
            delay(2500)
        }
    }
    return if (enabled && supported) np else null
}

private fun readNowPlaying(msm: MediaSessionManager, component: ComponentName): NowPlaying? = runCatching {
    val controllers: List<MediaController> = msm.getActiveSessions(component)
    // Only surface something that's actually playing, so the chip stays relevant and disappears on pause.
    val playing = controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: return null
    val md = playing.metadata
    val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() } ?: return null
    val art = (
        md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        )?.asImageBitmap()
    NowPlaying(
        title = title,
        artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() },
        art = art,
        packageName = playing.packageName,
    )
}.getOrNull()

/** Whether Tarang's notification listener is enabled (required to read media sessions). */
fun isNotificationAccessGranted(context: Context): Boolean =
    supportsNotificationAccess(context) &&
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

/** Android 10 and earlier block notification listeners in system low-memory mode. */
fun supportsNotificationAccess(context: Context): Boolean =
    Build.VERSION.SDK_INT > Build.VERSION_CODES.Q ||
        context.getSystemService(ActivityManager::class.java)?.isLowRamDevice != true

/** Prefer the app-specific access screen, then the listener list. Some TV firmware has neither. */
fun notificationAccessIntent(context: Context): Intent? {
    val intents = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            add(Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                ComponentName(context, TarangNotificationListenerService::class.java).flattenToString(),
            ))
        }
        add(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }
    return intents.firstOrNull { it.resolveActivity(context.packageManager) != null }
}
