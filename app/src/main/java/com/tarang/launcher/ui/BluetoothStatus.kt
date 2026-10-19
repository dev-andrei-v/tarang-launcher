package com.tarang.launcher.ui

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.Canvas
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleStartEffect
import com.tarang.launcher.data.DeviceKind

// HID host profile 4 includes TV remotes. Do not count paired devices as connected.
private val connectionProfiles = intArrayOf(BluetoothProfile.HEADSET, BluetoothProfile.A2DP, 4)

enum class BluetoothState(val label: String) {
    UNAVAILABLE("Bluetooth unavailable"),
    PERMISSION_NEEDED("Bluetooth: allow access"),
    OFF("Bluetooth off"),
    DISCONNECTED("Bluetooth disconnected"),
    CONNECTED("Bluetooth connected"),
}

/** A bonded (paired) device and whether it is connected now. */
data class PairedDevice(val name: String, val address: String, val connected: Boolean, val kind: DeviceKind = DeviceKind.OTHER)

/** The default kind from the Bluetooth device class. Settings can override it per device. */
@SuppressLint("MissingPermission")
private fun kindOf(device: BluetoothDevice): DeviceKind {
    val btClass = runCatching { device.bluetoothClass }.getOrNull() ?: return DeviceKind.OTHER
    return when (btClass.deviceClass) {
        android.bluetooth.BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET,
        android.bluetooth.BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES -> DeviceKind.HEADPHONES
        else -> when (btClass.majorDeviceClass) {
            android.bluetooth.BluetoothClass.Device.Major.AUDIO_VIDEO -> DeviceKind.SPEAKER
            android.bluetooth.BluetoothClass.Device.Major.PERIPHERAL -> DeviceKind.REMOTE
            else -> DeviceKind.OTHER
        }
    }
}

/** Applies the user's per-device icon choices from Settings. */
fun BluetoothStatus.withKinds(kinds: Map<String, DeviceKind>): BluetoothStatus {
    if (kinds.isEmpty()) return this
    fun PairedDevice.resolved() = kinds[address]?.let { copy(kind = it) } ?: this
    return copy(connected = connected.map { it.resolved() }, paired = paired.map { it.resolved() })
}

/**
 * Ask the audio and HID profiles to connect a paired device. Android has no public API for this,
 * so the call uses the profiles' hidden `connect` method. Android 11 and later can refuse it.
 * [onResult] runs on the main thread with true when one profile accepted the request.
 */
@SuppressLint("MissingPermission")
fun connectPairedDevice(context: Context, address: String, onResult: (Boolean) -> Unit) {
    val app = context.applicationContext
    if (!canReadBluetooth(app)) return onResult(false)
    val adapter = runCatching { app.getSystemService(BluetoothManager::class.java)?.adapter }.getOrNull()
        ?: return onResult(false)
    val device = runCatching { adapter.getRemoteDevice(address) }.getOrNull() ?: return onResult(false)
    val pending = connectionProfiles.toMutableSet()
    var success = false
    val listener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            val ok = runCatching {
                proxy.javaClass.getMethod("connect", BluetoothDevice::class.java).invoke(proxy, device) as? Boolean
            }.getOrNull() ?: false
            success = success || ok
            runCatching { adapter.closeProfileProxy(profile, proxy) }
            finish(profile)
        }
        override fun onServiceDisconnected(profile: Int) = finish(profile)
        fun finish(profile: Int) {
            if (pending.remove(profile) && pending.isEmpty()) onResult(success)
        }
    }
    connectionProfiles.forEach { profile ->
        val requested = runCatching { adapter.getProfileProxy(app, listener, profile) }.getOrDefault(false)
        if (!requested) listener.finish(profile)
    }
}

data class BluetoothStatus(
    val state: BluetoothState,
    val devices: List<String> = emptyList(),
    /** Connected devices with their kind, for the status icons. */
    val connected: List<PairedDevice> = emptyList(),
    /** All paired devices, connected ones first. Read once per refresh; no scan. */
    val paired: List<PairedDevice> = emptyList(),
) {
    val label: String get() = state.label
    val displayName: String get() = devices.joinToString(", ").ifBlank {
        if (state == BluetoothState.CONNECTED) "Bluetooth" else label
    }
}

fun canReadBluetooth(context: Context): Boolean = Build.VERSION.SDK_INT < 31 ||
    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

@SuppressLint("MissingPermission") // Check before every adapter read, including after permission revocation.
private fun readBluetooth(context: Context): BluetoothState {
    if (!canReadBluetooth(context)) return BluetoothState.PERMISSION_NEEDED
    return runCatching {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
            ?: return BluetoothState.UNAVAILABLE
        when {
            !adapter.isEnabled -> BluetoothState.OFF
            connectionProfiles.any { adapter.getProfileConnectionState(it) == BluetoothAdapter.STATE_CONNECTED } -> BluetoothState.CONNECTED
            else -> BluetoothState.DISCONNECTED
        }
    }.getOrDefault(BluetoothState.UNAVAILABLE)
}

/** Bind to connected profiles only while visible. Do not scan or poll for devices. */
@SuppressLint("MissingPermission")
@Composable
fun rememberBluetoothStatus(refreshKey: Int = 0): BluetoothStatus {
    val context = LocalContext.current.applicationContext
    var status by remember { mutableStateOf(BluetoothStatus(readBluetooth(context))) }
    LifecycleStartEffect(context, refreshKey) {
        val adapter = runCatching { context.getSystemService(BluetoothManager::class.java)?.adapter }.getOrNull()
        val proxies = mutableMapOf<Int, BluetoothProfile>()
        val namesByAddress = mutableMapOf<String, String>()
        val connectedByAddress = mutableMapOf<String, PairedDevice>()
        var active = true
        fun refresh() {
            if (!active) return
            val state = readBluetooth(context)
            namesByAddress.clear()
            connectedByAddress.clear()
            if (canReadBluetooth(context) && state == BluetoothState.CONNECTED) {
                for (proxy in proxies.values) {
                    runCatching {
                        proxy.connectedDevices.forEach { device ->
                            val name = device.name?.takeIf { it.isNotBlank() } ?: "Bluetooth device"
                            namesByAddress[device.address] = name
                            connectedByAddress[device.address] = PairedDevice(name, device.address, true, kindOf(device))
                        }
                    }
                }
            }
            val paired = if (canReadBluetooth(context) && state != BluetoothState.OFF && state != BluetoothState.UNAVAILABLE) {
                runCatching {
                    adapter?.bondedDevices.orEmpty().map { device ->
                        val name = device.name?.takeIf { it.isNotBlank() } ?: "Bluetooth device"
                        PairedDevice(
                            name = name,
                            address = device.address,
                            connected = device.address in namesByAddress,
                            kind = kindOf(device),
                        )
                    }
                }.getOrDefault(emptyList())
            } else {
                emptyList()
            }
            status = BluetoothStatus(
                state,
                namesByAddress.values.distinct().sorted(),
                connectedByAddress.values.sortedBy { it.name },
                paired.sortedWith(compareByDescending<PairedDevice> { it.connected }.thenBy { it.name }),
            )
        }
        val listener = object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                if (active) { proxies[profile] = proxy; refresh() }
                else runCatching { adapter?.closeProfileProxy(profile, proxy) }
            }
            override fun onServiceDisconnected(profile: Int) { proxies.remove(profile); refresh() }
        }
        if (canReadBluetooth(context)) {
            connectionProfiles.forEach { profile ->
                runCatching { adapter?.getProfileProxy(context, listener, profile) }
            }
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) { refresh() }
        }
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothDevice.ACTION_NAME_CHANGED)
            addAction("android.bluetooth.input.profile.action.CONNECTION_STATE_CHANGED")
            addAction("android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED")
            addAction("android.bluetooth.headset.profile.action.CONNECTION_STATE_CHANGED")
        }
        // Bluetooth broadcasts can come from a privileged UID outside the system process.
        val registered = runCatching {
            ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        }.isSuccess
        refresh()
        onStopOrDispose {
            active = false
            if (registered) runCatching { context.unregisterReceiver(receiver) }
            proxies.toMap().forEach { (profile, proxy) -> runCatching { adapter?.closeProfileProxy(profile, proxy) } }
            proxies.clear()
        }
    }
    return status
}

/**
 * Connects the chosen paired device on its own while the launcher is on screen. It tries when the
 * device is paired, Bluetooth is on, and the device is not connected. A new try waits
 * [AUTO_CONNECT_RETRY_MS], so a speaker that is switched off does not keep the radio busy.
 */
@Composable
fun BluetoothAutoConnect(address: String?) {
    if (address == null) return
    val context = LocalContext.current.applicationContext
    val status = rememberBluetoothStatus()
    val target = status.paired.firstOrNull { it.address == address }
    val wanted = target != null && !target.connected &&
        (status.state == BluetoothState.DISCONNECTED || status.state == BluetoothState.CONNECTED)
    RunWhileStarted(wanted, address) {
        if (!wanted) return@RunWhileStarted
        while (true) {
            connectPairedDevice(context, address) { ok ->
                android.util.Log.i("BluetoothAutoConnect", "Auto-connect $address: ${if (ok) "requested" else "refused"}")
            }
            kotlinx.coroutines.delay(AUTO_CONNECT_RETRY_MS)
        }
    }
}

private const val AUTO_CONNECT_RETRY_MS = 90_000L

@Composable
fun BluetoothIndicator(modifier: Modifier = Modifier, tint: Color = Color.White) {
    Canvas(modifier) {
        val points = listOf(.28f to .28f, .72f to .68f, .5f to .9f, .5f to .1f, .72f to .32f, .28f to .72f)
        points.zipWithNext().forEach { (a, b) ->
            drawLine(tint, Offset(size.width * a.first, size.height * a.second),
                Offset(size.width * b.first, size.height * b.second), size.minDimension * .07f, StrokeCap.Round)
        }
    }
}

fun openLocationSettings(context: Context) = openConnectionSettings(context, Settings.ACTION_LOCATION_SOURCE_SETTINGS)

fun openWifiSettings(context: Context) = openConnectionSettings(context,
    Settings.ACTION_WIFI_SETTINGS, Settings.ACTION_WIRELESS_SETTINGS)

fun openBluetoothSettings(context: Context) = openConnectionSettings(context,
    Settings.ACTION_BLUETOOTH_SETTINGS, "com.google.android.intent.action.CONNECT_INPUT")

private fun openConnectionSettings(context: Context, vararg actions: String) {
    for (action in actions.asList() + Settings.ACTION_SETTINGS) {
        if (runCatching {
            context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess) return
    }
    Toast.makeText(context, "System settings are unavailable", Toast.LENGTH_SHORT).show()
}
