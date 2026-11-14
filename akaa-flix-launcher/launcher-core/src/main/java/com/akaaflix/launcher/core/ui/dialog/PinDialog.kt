package com.akaaflix.launcher.core.ui.dialog

import android.content.Context
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.widget.GridLayout
import android.widget.TextView
import com.akaaflix.launcher.core.R
import com.akaaflix.launcher.core.databinding.DialogPinBinding
import com.akaaflix.launcher.core.ui.FocusEffects
import com.akaaflix.launcher.core.ui.GlassDrawable
import com.akaaflix.launcher.core.ui.dpi
import com.akaaflix.launcher.core.ui.glass

/**
 * 4-digit PIN entry with an on-screen keypad (D-pad) that also accepts the remote's number keys.
 * [onPin] returns true to accept (dialog closes) or false to show [errorRes] and clear.
 */
object PinDialog {
    private const val LENGTH = 4

    fun show(context: Context, titleRes: Int, errorRes: Int = R.string.pin_wrong, onCancel: (() -> Unit)? = null, onPin: (String) -> Boolean) {
        val d = LauncherDialog(context).title(titleRes).icon(R.drawable.ic_lock)
        val b = DialogPinBinding.inflate(LayoutInflater.from(context))
        val entered = StringBuilder()
        var accepted = false
        val dots = (0 until LENGTH).map {
            View(context).apply {
                glass(GlassDrawable.Variant.CIRCLE, 8f)
                b.pinDots.addView(this, android.widget.LinearLayout.LayoutParams(context.dpi(16f), context.dpi(16f)).apply {
                    marginStart = context.dpi(7f); marginEnd = context.dpi(7f)
                })
            }
        }
        fun refresh() { dots.forEachIndexed { i, v -> v.isActivated = i < entered.length } }
        fun submit() {
            if (entered.length < LENGTH) return
            if (onPin(entered.toString())) { accepted = true; d.dialog.dismiss() } else {
                b.pinError.setText(errorRes); entered.clear(); refresh()
            }
        }
        fun type(c: Char) {
            if (entered.length >= LENGTH) return
            b.pinError.text = ""
            entered.append(c); refresh()
            if (entered.length == LENGTH) b.root.postDelayed({ submit() }, 120)
        }
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "⌫", "0", "✓")
        var firstKey: View? = null
        keys.forEach { k ->
            val tv = androidx.appcompat.widget.AppCompatTextView(context).apply {
                text = k
                gravity = Gravity.CENTER
                setTextAppearance(R.style.TextAppearance_Akaa_Section)
                textSize = 20f
                isFocusable = true; isClickable = true
                contentDescription = if (k == "⌫") context.getString(R.string.pin_clear) else if (k == "✓") context.getString(R.string.ok) else k
                glass(if (k == "✓") GlassDrawable.Variant.PRIMARY else GlassDrawable.Variant.PANEL, 14f, 4f)
                FocusEffects.attach(this, 1.06f)
                setOnClickListener {
                    when (k) {
                        "⌫" -> { if (entered.isNotEmpty()) entered.deleteCharAt(entered.length - 1); refresh() }
                        "✓" -> submit()
                        else -> type(k[0])
                    }
                }
            }
            val lp = GridLayout.LayoutParams().apply { width = context.dpi(72f); height = context.dpi(52f) }
            b.pinPad.addView(tv, lp)
            if (firstKey == null) firstKey = tv
        }
        d.dialog.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) {
                type(('0'.code + keyCode - KeyEvent.KEYCODE_0).toChar()); true
            } else if (event.action == KeyEvent.ACTION_DOWN && keyCode in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9) {
                type(('0'.code + keyCode - KeyEvent.KEYCODE_NUMPAD_0).toChar()); true
            } else false
        }
        d.content(b.root).width(380).button(R.string.cancel)
        d.onDismiss { if (!accepted) onCancel?.invoke() }
        firstKey?.let { d.focusOn(it) }
        d.show()
    }
}
