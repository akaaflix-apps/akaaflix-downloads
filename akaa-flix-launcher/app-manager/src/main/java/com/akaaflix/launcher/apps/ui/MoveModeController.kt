package com.akaaflix.launcher.apps.ui

import android.view.KeyEvent
import com.akaaflix.launcher.apps.AppLists
import com.akaaflix.launcher.remote.Keys

/**
 * Remote-friendly reordering: OK picks the focused app, arrows move it, OK drops it, Back ends.
 * Works across pages because the controller moves the item inside the full ordered list.
 */
class MoveModeController(private val listener: Listener) {
    interface Listener {
        fun currentOrder(): List<String>
        fun columns(): Int
        fun applyOrder(order: List<String>, movingKey: String?)
        fun commit(order: List<String>)
        fun onModeChanged(active: Boolean)
        fun focusedKey(): String?
    }

    var active = false
        private set
    var movingKey: String? = null
        private set
    private var order: List<String> = emptyList()
    private val consumedDown = HashSet<Int>()

    fun begin(pickKey: String?) {
        active = true
        order = listener.currentOrder()
        movingKey = pickKey?.takeIf { it in order }
        listener.onModeChanged(true)
        listener.applyOrder(order, movingKey)
    }

    fun end() {
        if (!active) return
        active = false
        movingKey = null
        listener.commit(order)
        listener.onModeChanged(false)
    }

    fun onKey(event: KeyEvent): Boolean {
        if (!active) return false
        val code = event.keyCode
        if (event.action == KeyEvent.ACTION_UP) return consumedDown.remove(code)
        if (event.action != KeyEvent.ACTION_DOWN) return false
        val handled = when {
            Keys.isBack(code) -> { end(); true }
            Keys.isConfirm(code) -> {
                if (movingKey != null) { movingKey = null; listener.applyOrder(order, null) }
                else listener.focusedKey()?.takeIf { it in order }?.let { movingKey = it; listener.applyOrder(order, it) }
                true
            }
            movingKey != null && Keys.isDpad(code) -> {
                val cols = listener.columns().coerceAtLeast(1)
                val delta = when (code) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> -1
                    KeyEvent.KEYCODE_DPAD_RIGHT -> 1
                    KeyEvent.KEYCODE_DPAD_UP -> -cols
                    else -> cols
                }
                order = AppLists.move(order, movingKey!!, delta)
                listener.applyOrder(order, movingKey)
                true
            }
            else -> false
        }
        if (handled) consumedDown.add(code)
        return handled
    }
}
