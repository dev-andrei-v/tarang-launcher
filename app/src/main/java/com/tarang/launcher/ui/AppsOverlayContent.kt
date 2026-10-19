package com.tarang.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.tarang.launcher.data.AppInfo
import com.tarang.launcher.data.DEFAULT_COLUMNS
import com.tarang.launcher.di.AppContainer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

private val OverlayTileGap = 24.dp

/**
 * The Apps overlay body: a dark scrim over the running app, the favourites row, and the app grid.
 * It reads the same app list, favourites, hidden apps, and column count as the home screen.
 */
@Composable
fun AppsOverlayContent(
    container: AppContainer,
    onLaunch: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = DarkLauncherColors
    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var favorites by remember { mutableStateOf<List<String>>(emptyList()) }
    var hidden by remember { mutableStateOf<Set<String>>(emptySet()) }
    var columns by remember { mutableIntStateOf(DEFAULT_COLUMNS) }
    LaunchedEffect(Unit) {
        val settings = container.settingsStore.settings.first()
        hidden = settings.hiddenApps
        columns = settings.columns
        favorites = container.favoritesStore.favorites.first()
        apps = container.appListCache.read()?.takeIf { it.isNotEmpty() } ?: container.appRepository.loadApps()
    }
    val favoriteSet = favorites.toSet()
    val dock = favorites.mapNotNull { pkg -> apps.firstOrNull { it.packageName == pkg } }
    val grid = apps.filterNot { it.packageName in favoriteSet || it.packageName in hidden }
    val firstFocus = remember { FocusRequester() }
    val ready = dock.isNotEmpty() || grid.isNotEmpty()
    LaunchedEffect(ready) {
        if (!ready) return@LaunchedEffect
        delay(60)
        runCatching { firstFocus.requestFocus() }
    }

    CompositionLocalProvider(LocalLauncherColors provides colors) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.66f))
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && (event.key == Key.Back || event.key == Key.Escape)) {
                        onDismiss()
                        true
                    } else {
                        false
                    }
                },
        ) {
            BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 36.dp)) {
                val gridTileW = (maxWidth - OverlayTileGap * (columns - 1)) / columns
                val gridTileH = gridTileW * 9 / 16
                // Favourites shrink together when there are more of them than grid columns.
                val dockCount = dock.size.coerceAtLeast(1)
                val dockTileW = if (dock.size > columns) (maxWidth - OverlayTileGap * (dockCount - 1)) / dockCount else gridTileW
                val dockTileH = dockTileW * 9 / 16
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(OverlayTileGap),
                ) {
                    Text("Apps", color = colors.text, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                    if (dock.isNotEmpty()) {
                        AppRow(
                            apps = dock,
                            iconLoader = container.iconLoader,
                            onAppFocused = {},
                            onAppClicked = onLaunch,
                            onAppLongPressed = {},
                            tileWidth = dockTileW,
                            tileHeight = dockTileH,
                            firstCardFocusRequester = firstFocus,
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    grid.chunked(columns).forEachIndexed { index, row ->
                        AppRow(
                            apps = row,
                            iconLoader = container.iconLoader,
                            onAppFocused = {},
                            onAppClicked = onLaunch,
                            onAppLongPressed = {},
                            tileWidth = gridTileW,
                            tileHeight = gridTileH,
                            firstCardFocusRequester = if (dock.isEmpty() && index == 0) firstFocus else null,
                        )
                    }
                    Text(
                        "Press Back to return to the app.",
                        color = colors.textDim,
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}
