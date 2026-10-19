package com.tarang.launcher.di

import android.content.Context
import com.tarang.launcher.data.ApkDownloader
import com.tarang.launcher.data.AppListCache
import com.tarang.launcher.data.AppRepository
import com.tarang.launcher.data.DailyWallpaper
import com.tarang.launcher.data.FavoritesStore
import com.tarang.launcher.data.IconLoader
import com.tarang.launcher.data.SettingsStore
import com.tarang.launcher.data.UpdateChecker
import com.tarang.launcher.data.UpdateInstaller
import com.tarang.launcher.data.UpdateStore
import com.tarang.launcher.media.UiSounds

/**
 * Minimal manual dependency graph (plan §4). Held by [com.tarang.launcher.TarangApp].
 * Hilt is the upgrade path if/when this grows.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val appRepository: AppRepository by lazy { AppRepository(appContext) }
    val iconLoader: IconLoader by lazy { IconLoader(appContext) }
    val favoritesStore: FavoritesStore by lazy { FavoritesStore(appContext) }
    val settingsStore: SettingsStore by lazy { SettingsStore(appContext) }
    val dailyWallpaper: DailyWallpaper by lazy { DailyWallpaper(appContext, settingsStore) }
    val appListCache: AppListCache by lazy { AppListCache(appContext) }
    val uiSounds: UiSounds by lazy { UiSounds(appContext) }
    val updateStore: UpdateStore by lazy { UpdateStore(appContext) }
    val updateChecker: UpdateChecker by lazy { UpdateChecker(appContext, updateStore) }
    val apkDownloader: ApkDownloader by lazy { ApkDownloader(appContext) }
    val updateInstaller: UpdateInstaller by lazy { UpdateInstaller(appContext) }
}
