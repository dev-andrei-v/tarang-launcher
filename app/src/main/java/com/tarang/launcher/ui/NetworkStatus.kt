package com.tarang.launcher.ui

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext

enum class NetKind { WIFI, ETHERNET, CELLULAR, NONE }

/** Active-connection summary for the status indicator. [wifiLevel] is 0..3 (Wi-Fi only). */
data class NetStatus(val kind: NetKind, val wifiLevel: Int, val online: Boolean, val wifiName: String? = null)

fun canReadWifiName(context: Context): Boolean = ContextCompat.checkSelfPermission(
    context, Manifest.permission.ACCESS_FINE_LOCATION,
) == PackageManager.PERMISSION_GRANTED

@Suppress("DEPRECATION", "MissingPermission")
private fun wifiName(wm: WifiManager?): String? = runCatching {
    wm?.connectionInfo?.ssid?.takeUnless { it == "<unknown ssid>" }
        ?.removeSurrounding("\"")?.takeIf { it.isNotBlank() }
}.getOrNull()

val NetStatus.label: String get() = when (kind) {
    NetKind.WIFI -> if (online) "Wi-Fi connected" else "Wi-Fi: no internet"
    NetKind.ETHERNET -> if (online) "Ethernet connected" else "Ethernet: no internet"
    NetKind.CELLULAR -> if (online) "Mobile connected" else "Mobile: no internet"
    NetKind.NONE -> "Wi-Fi disconnected"
}

val NetStatus.displayName: String get() = when (kind) {
    NetKind.WIFI -> wifiName ?: "Wi-Fi"
    else -> label
}

/** Observe the default network only while visible. */
@Composable
fun rememberNetStatus(refreshKey: Int = 0): NetStatus {
    val appContext = LocalContext.current.applicationContext
    val cm = remember { appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager }
    val wm = remember { appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager }
    var status by remember { mutableStateOf(readStatus(cm, wm)) }

    LifecycleStartEffect(cm, refreshKey) {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { status = readStatus(cm, wm) }
            override fun onLost(network: Network) { status = readStatus(cm, wm) }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                status = readStatus(cm, wm)
            }
        }
        val registered = runCatching {
            cm.registerDefaultNetworkCallback(callback, Handler(Looper.getMainLooper()))
        }.isSuccess
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) { status = readStatus(cm, wm) }
        }
        val filter = IntentFilter().apply {
            addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(LocationManager.MODE_CHANGED_ACTION)
        }
        val receiverRegistered = runCatching {
            ContextCompat.registerReceiver(appContext, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        }.isSuccess
        status = readStatus(cm, wm)
        onStopOrDispose {
            if (registered) runCatching { cm.unregisterNetworkCallback(callback) }
            if (receiverRegistered) runCatching { appContext.unregisterReceiver(receiver) }
        }
    }
    return status
}

private fun readStatus(cm: ConnectivityManager, wm: WifiManager?): NetStatus {
    val network = cm.activeNetwork ?: return NetStatus(NetKind.NONE, 0, false)
    val caps = cm.getNetworkCapabilities(network) ?: return NetStatus(NetKind.NONE, 0, false)
    val online = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    return when {
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
            val rssi = wifiRssi(caps, wm)
            val level = if (rssi != null) signalLevel(rssi) else 3
            NetStatus(NetKind.WIFI, level, online, wifiName(wm))
        }
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetStatus(NetKind.ETHERNET, 3, online)
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetStatus(NetKind.CELLULAR, 3, online)
        else -> NetStatus(NetKind.NONE, 0, online)
    }
}

@Suppress("DEPRECATION") // connectionInfo only needed as the API 28 fallback (no location data read)
private fun wifiRssi(caps: NetworkCapabilities, wm: WifiManager?): Int? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val strength = caps.signalStrength
        if (strength != Int.MIN_VALUE) return strength
    }
    return runCatching { wm?.connectionInfo?.rssi }.getOrNull()
}

@Suppress("DEPRECATION") // static calculateSignalLevel is the only minSdk-28-safe variant
private fun signalLevel(rssi: Int): Int = WifiManager.calculateSignalLevel(rssi, 4).coerceIn(0, 3)

/**
 * A small Wi-Fi glyph (dot + three arcs) that lights up arcs to match the signal level. Ethernet/
 * cellular show full; no connection dims everything and adds a slash.
 */
@Composable
fun WifiIndicator(status: NetStatus, modifier: Modifier = Modifier, tint: Color = Color.White) {
    val bright = tint
    val dim = tint.copy(alpha = 0.25f)
    val online = status.online && status.kind != NetKind.NONE
    val arcsOn = if (online) status.wifiLevel.coerceIn(0, 3) else 0

    Canvas(modifier = modifier) {
        val s = size.minDimension
        val cx = size.width / 2f
        val cy = size.height * 0.80f
        val stroke = s * 0.075f
        val radii = floatArrayOf(s * 0.22f, s * 0.37f, s * 0.52f)
        radii.forEachIndexed { i, r ->
            drawArc(
                color = if (arcsOn >= i + 1) bright else dim,
                startAngle = 215f,
                sweepAngle = 110f,
                useCenter = false,
                topLeft = Offset(cx - r, cy - r),
                size = Size(2 * r, 2 * r),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        drawCircle(color = if (online) bright else dim, radius = s * 0.055f, center = Offset(cx, cy))
        if (!online) {
            drawLine(
                color = bright,
                start = Offset(s * 0.20f, s * 0.20f),
                end = Offset(s * 0.80f, s * 0.80f),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}
