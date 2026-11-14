package com.akaaflix.launcher.device.status

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.Inet4Address

data class NetworkState(
    val connected: Boolean = false,
    val type: Type = Type.NONE,
    /** Wi-Fi signal 0..3 (3 = best). */
    val wifiLevel: Int = 0,
    val ipAddress: String? = null,
    val validated: Boolean = false,
) {
    enum class Type { WIFI, ETHERNET, CELLULAR, VPN, OTHER, NONE }
}

/** Observes the default network with callbacks (no polling). Requires ACCESS_NETWORK_STATE. */
class NetworkMonitor(context: Context) {
    private val appContext = context.applicationContext
    private val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val _state = MutableStateFlow(NetworkState())
    val state: StateFlow<NetworkState> = _state.asStateFlow()
    private var registered = false

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = refresh()
        override fun onLost(network: Network) = refresh()
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = refresh()
        override fun onLinkPropertiesChanged(network: Network, lp: LinkProperties) = refresh()
    }

    fun start() {
        refresh()
        if (registered) return
        try {
            if (Build.VERSION.SDK_INT >= 24) cm.registerDefaultNetworkCallback(callback)
            else cm.registerNetworkCallback(NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(), callback)
            registered = true
        } catch (_: Exception) { /* some TV boxes throw when too many callbacks exist */ }
    }

    fun stop() {
        if (!registered) return
        try { cm.unregisterNetworkCallback(callback) } catch (_: Exception) {}
        registered = false
    }

    fun refresh() { _state.value = read() }

    @SuppressLint("MissingPermission")
    private fun read(): NetworkState {
        return try {
            val net = cm.activeNetwork ?: return NetworkState()
            val caps = cm.getNetworkCapabilities(net) ?: return NetworkState()
            val type = when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkState.Type.ETHERNET
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkState.Type.WIFI
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkState.Type.CELLULAR
                caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> NetworkState.Type.VPN
                else -> NetworkState.Type.OTHER
            }
            val ip = cm.getLinkProperties(net)?.linkAddresses?.map { it.address }?.firstOrNull { it is Inet4Address }?.hostAddress
            val level = if (type == NetworkState.Type.WIFI) wifiLevel(caps) else 0
            NetworkState(true, type, level, ip, caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
        } catch (_: SecurityException) {
            NetworkState()
        }
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    private fun wifiLevel(caps: NetworkCapabilities): Int {
        val rssi = if (Build.VERSION.SDK_INT >= 29) caps.signalStrength.takeIf { it != NetworkCapabilities.SIGNAL_STRENGTH_UNSPECIFIED } else null
        val r = rssi ?: try {
            (appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager)?.connectionInfo?.rssi
        } catch (_: Exception) { null } ?: return 3
        return levelFromRssi(r)
    }

    companion object {
        /** -55 dBm or better = 3 bars; -66 = 2; -77 = 1; else 0. */
        fun levelFromRssi(rssi: Int): Int = when {
            rssi >= -55 -> 3
            rssi >= -66 -> 2
            rssi >= -77 -> 1
            else -> 0
        }
    }
}
