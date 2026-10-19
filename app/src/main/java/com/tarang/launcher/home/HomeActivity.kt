package com.tarang.launcher.home

import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.tarang.launcher.TarangApp
import com.tarang.launcher.ui.LauncherScreen
import com.tarang.launcher.ui.theme.TarangTheme

/**
 * The launcher home screen. Hosts the launcher UI (see [LauncherScreen]) under [TarangTheme].
 */
class HomeActivity : ComponentActivity() {
    private var navigation by mutableStateOf(HomeNavigation())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        when (intent?.action) {
            Intent.ACTION_ALL_APPS -> navigation = HomeNavigation(1, showAllApps = true)
            ACTION_FRAME -> navigation = HomeNavigation(1, startFrame = true)
        }
        val container = (application as TarangApp).container
        setContent {
            TarangTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                ) {
                    LauncherScreen(container = container, navigation = navigation)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == Intent.ACTION_ALL_APPS || intent.action == ACTION_FRAME ||
            (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME))) {
            navigation = HomeNavigation(
                navigation.sequence + 1,
                showAllApps = intent.action == Intent.ACTION_ALL_APPS,
                startFrame = intent.action == ACTION_FRAME,
            )
        }
    }

    companion object {
        /** Open the launcher straight into Frame Art (sent by [FrameDreamService]). */
        const val ACTION_FRAME = "com.tarang.launcher.action.FRAME"
    }

}
