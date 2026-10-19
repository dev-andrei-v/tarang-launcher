package com.tarang.launcher.home

import android.content.Intent
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.service.dreams.DreamService
import android.util.Log
import android.view.View

/**
 * The system screensaver entry for Tarang. When the TV goes idle, Android starts this dream. It opens
 * the launcher in Frame Art and then ends itself, so Frame Art runs as the normal launcher screen.
 * Any key leaves Frame Art, as usual.
 *
 * Select it with: `settings put secure screensaver_components com.tarang.launcher/.home.FrameDreamService`.
 */
class FrameDreamService : DreamService() {

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        setContentView(View(this).apply { setBackgroundColor(Color.BLACK) })
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        val intent = Intent(this, HomeActivity::class.java)
            .setAction(HomeActivity.ACTION_FRAME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        runCatching { startActivity(intent) }
            .onSuccess { Log.i(TAG, "Screensaver opened Frame Art") }
            .onFailure { Log.w(TAG, "Screensaver could not open Frame Art", it) }
        // Give the activity a moment to come up before the dream window goes away.
        Handler(Looper.getMainLooper()).postDelayed({ runCatching { finish() } }, 600)
    }

    private companion object {
        const val TAG = "FrameDreamService"
    }
}
