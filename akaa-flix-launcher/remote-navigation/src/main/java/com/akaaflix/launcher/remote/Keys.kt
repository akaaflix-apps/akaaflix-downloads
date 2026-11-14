package com.akaaflix.launcher.remote

import android.view.KeyEvent

/** Remote-control key helpers shared by every screen. */
object Keys {
    private val confirm = setOf(
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER,
        KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_SELECT,
    )
    private val menu = setOf(KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_TV_CONTENTS_MENU)

    fun isConfirm(keyCode: Int) = keyCode in confirm
    fun isMenu(keyCode: Int) = keyCode in menu
    fun isBack(keyCode: Int) = keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_BUTTON_B || keyCode == KeyEvent.KEYCODE_ESCAPE
    fun isDpad(keyCode: Int) = keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
        keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT

    /** Human readable key name for the remote test screen, e.g. "DPAD_UP". */
    fun name(keyCode: Int): String = KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_")
}
