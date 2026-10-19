package com.tarang.launcher.home

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * Fallback "be the home screen" mechanism (plan §2.1 / §5.2).
 *
 * The clean path — `cmd package set-home-activity` — does not reliably stick on Google TV, so
 * this service watches for the stock launcher coming to the foreground and bounces straight back
 * to Tarang.
 *
 * The Apps key opens Tarang's app grid. Home uses the system's normal launcher path.
 */
class HomeRedirectService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var lastForegroundPkg: String? = null
    private var pendingRedirect: Runnable? = null

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_ALL_APPS) return false
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            val intent = Intent(this, HomeActivity::class.java)
                .setAction(Intent.ACTION_ALL_APPS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            runCatching { startActivity(intent) }
                .onSuccess { Log.i(TAG, "Apps button opened Tarang") }
                .onFailure { Log.w(TAG, "Apps button launch failed", it) }
        }
        return true
    }

    override fun onDestroy() {
        pendingRedirect?.let { handler.removeCallbacks(it) }
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        lastForegroundPkg = pkg
        if (pkg !in STOCK_LAUNCHERS) {
            // A non-launcher is in front (e.g. an app returned right after a transient system flash
            // during a VPN connect) — drop any bounce we had queued.
            pendingRedirect?.let { handler.removeCallbacks(it); pendingRedirect = null }
            return
        }
        // The stock launcher is in front. Queue ONE bounce and do NOT reschedule on later events: the
        // launcher fires many window events while it draws, and resetting the timer on each would defer
        // the bounce forever (that was the bug that left the user stranded on the stock launcher). Fire
        // only if the launcher is STILL in front after the guard, so a brief flash — a VPN connecting —
        // doesn't yank the user out of the app they're in.
        if (pendingRedirect == null) {
            val redirect = Runnable {
                pendingRedirect = null
                if (lastForegroundPkg in STOCK_LAUNCHERS) {
                    val intent = Intent(this, HomeActivity::class.java).addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT,
                    )
                    runCatching { startActivity(intent) }
                        .onFailure { Log.w(TAG, "Home redirect failed", it) }
                }
            }
            pendingRedirect = redirect
            handler.postDelayed(redirect, GUARD_MS)
        }
    }

    override fun onInterrupt() = Unit

    companion object {
        private const val TAG = "HomeRedirect"
        // How long a stock-launcher foreground must persist before we bounce back to Tarang. Long
        // enough to let a transient flash (a VPN connect) pass; short enough that returning from an app
        // snaps back quickly. Tunable.
        private const val GUARD_MS = 200L

        /** Stock launcher packages we bounce away from. */
        private val STOCK_LAUNCHERS = setOf(
            "com.google.android.apps.tv.launcherx", // Google TV (Chromecast w/ Google TV)
            "com.google.android.tvlauncher", // AOSP / older Android TV Leanback launcher
        )
    }
}
