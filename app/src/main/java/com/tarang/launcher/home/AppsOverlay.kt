package com.tarang.launcher.home

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.tarang.launcher.TarangApp
import com.tarang.launcher.ui.AppsOverlayContent
import com.tarang.launcher.ui.theme.TarangTheme

/**
 * The Apps overlay: the app grid over the running app, like the tvOS app switcher. It is an
 * accessibility overlay window, so the app below keeps running and keeps playing. The window takes
 * key focus, so the D-pad moves through the grid. [HomeRedirectService] closes it on Back or Apps.
 */
internal class AppsOverlay(private val service: AccessibilityService) {

    private val windowManager = service.getSystemService(WindowManager::class.java)
    private var view: ComposeView? = null
    private var host: OverlayHost? = null

    val isShowing: Boolean get() = view != null

    fun show() {
        if (view != null) return
        val container = (service.application as TarangApp).container
        val owner = OverlayHost()
        val composeView = ComposeView(service).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                TarangTheme {
                    AppsOverlayContent(
                        container = container,
                        onLaunch = { pkg ->
                            container.appRepository.launch(pkg)
                            hide()
                        },
                        onDismiss = ::hide,
                    )
                }
            }
        }
        val params = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.FILL }
        runCatching {
            windowManager.addView(composeView, params)
            owner.start()
        }.onSuccess {
            view = composeView
            host = owner
            Log.i(TAG, "Apps overlay shown")
        }.onFailure { Log.w(TAG, "Apps overlay failed", it) }
    }

    fun hide() {
        val current = view ?: return
        view = null
        host?.stop()
        host = null
        runCatching { windowManager.removeView(current) }
    }

    private companion object {
        const val TAG = "AppsOverlay"
    }
}
