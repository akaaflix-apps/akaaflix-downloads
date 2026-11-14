package com.akaaflix.launcher.core.ui

import android.view.View
import android.view.animation.DecelerateInterpolator

/** Focus zoom used by every focusable card. Honors motion and lightweight settings. */
object FocusEffects {
    private val interpolator = DecelerateInterpolator(1.6f)

    fun apply(view: View, focused: Boolean, scaleOverride: Float = 0f) {
        val zoom = if (LauncherUi.focusStyle == "border") 1f else (if (scaleOverride > 0f) scaleOverride else LauncherUi.focusScale)
        val target = if (focused) zoom else 1f
        view.animate().cancel()
        if (LauncherUi.motionEnabled && view.isAttachedToWindow) {
            view.animate().scaleX(target).scaleY(target).setDuration(LauncherUi.animDurationMs).setInterpolator(interpolator).start()
        } else {
            view.scaleX = target; view.scaleY = target
        }
    }

    /** Attaches the zoom to a view's focus changes, chaining any listener it already has. */
    fun attach(view: View, scaleOverride: Float = 0f) {
        val previous = view.onFocusChangeListener
        view.setOnFocusChangeListener { v, has ->
            apply(v, has, scaleOverride)
            previous?.onFocusChange(v, has)
        }
    }
}
