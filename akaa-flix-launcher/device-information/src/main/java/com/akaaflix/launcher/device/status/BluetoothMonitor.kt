package com.akaaflix.launcher.device.status

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BluetoothState(val supported: Boolean, val enabled: Boolean)

/**
 * Bluetooth on/off status. isEnabled() needs no runtime permission on Android 12+ and only the
 * normal BLUETOOTH permission (maxSdk 30) before. Connected-device details would require the
 * BLUETOOTH_CONNECT runtime permission, which the launcher deliberately does not request.
 */
class BluetoothMonitor(context: Context) {
    private val appContext = context.applicationContext
    private val supported = appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)
    private val _state = MutableStateFlow(BluetoothState(supported, false))
    val state: StateFlow<BluetoothState> = _state.asStateFlow()
    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { refresh() }
    }

    fun start() {
        refresh()
        if (registered || !supported) return
        ContextCompat.registerReceiver(appContext, receiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED), ContextCompat.RECEIVER_EXPORTED)
        registered = true
    }

    fun stop() {
        if (!registered) return
        try { appContext.unregisterReceiver(receiver) } catch (_: IllegalArgumentException) {}
        registered = false
    }

    fun refresh() {
        val enabled = try {
            (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter?.isEnabled == true
        } catch (_: SecurityException) { false }
        _state.value = BluetoothState(supported, enabled)
    }
}
