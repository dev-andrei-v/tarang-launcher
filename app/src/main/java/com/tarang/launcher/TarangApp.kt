package com.tarang.launcher

import android.app.Application
import android.content.ComponentCallbacks2
import com.tarang.launcher.di.AppContainer
import com.tarang.launcher.ui.ArtworkLoader

/**
 * Application entry point. Owns the manual-DI [AppContainer] (plan §4).
 */
class TarangApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            ArtworkLoader.clearMemory()
            container.iconLoader.clearMemory()
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        ArtworkLoader.clearMemory()
        container.iconLoader.clearMemory()
    }

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
