package com.akaaflix.launcher.device.clock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Emits the current time on every minute tick, time change, time-zone change and date change.
 * Uses the system TIME_TICK broadcast – no timers or polling – and only while started (visible).
 */
class ClockTicker(context: Context) {
    private val appContext = context.applicationContext
    private val _now = MutableStateFlow(System.currentTimeMillis())
    val now: StateFlow<Long> = _now.asStateFlow()
    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { _now.value = System.currentTimeMillis() }
    }

    fun start() {
        _now.value = System.currentTimeMillis()
        if (registered) return
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_DATE_CHANGED)
        }
        ContextCompat.registerReceiver(appContext, receiver, f, ContextCompat.RECEIVER_NOT_EXPORTED)
        registered = true
    }

    fun stop() {
        if (!registered) return
        try { appContext.unregisterReceiver(receiver) } catch (_: IllegalArgumentException) {}
        registered = false
    }
}
