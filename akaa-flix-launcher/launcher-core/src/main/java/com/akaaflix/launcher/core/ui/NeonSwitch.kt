package com.akaaflix.launcher.core.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.widget.Checkable
import androidx.core.graphics.ColorUtils

/**
 * Pill toggle drawn like the reference (purple track, white knob). Not focusable by default: rows
 * own the focus and toggle it, so the remote never needs an extra stop.
 */
class NeonSwitch @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs), Checkable {
    private val d = resources.displayMetrics.density
    private val track = Paint(Paint.ANTI_ALIAS_FLAG)
    private val knob = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val rect = RectF()
    private var position = 0f
    private var animator: ValueAnimator? = null
    private var checkedState = false
    var onCheckedChange: ((Boolean) -> Unit)? = null

    init {
        isFocusable = false
        isClickable = false
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(resolveSize((46 * d).toInt(), widthMeasureSpec), resolveSize((26 * d).toInt(), heightMeasureSpec))
    }

    override fun isChecked() = checkedState
    override fun toggle() = setChecked(!checkedState)

    override fun setChecked(checked: Boolean) = setChecked(checked, isAttachedToWindow && LauncherUi.motionEnabled)

    fun setChecked(checked: Boolean, animate: Boolean) {
        if (checked == checkedState && animator == null) { position = if (checked) 1f else 0f; invalidate(); return }
        checkedState = checked
        animator?.cancel(); animator = null
        val target = if (checked) 1f else 0f
        if (animate) {
            animator = ValueAnimator.ofFloat(position, target).apply {
                duration = 160
                addUpdateListener { position = it.animatedValue as Float; invalidate() }
                start()
            }
        } else { position = target; invalidate() }
        onCheckedChange?.invoke(checked)
    }

    override fun onDraw(canvas: Canvas) {
        val pal = LauncherUi.palette
        val h = height.toFloat(); val w = width.toFloat()
        val th = h * 0.86f
        rect.set(0f, (h - th) / 2f, w, (h + th) / 2f)
        val r = th / 2f
        val offColor = Color.argb(70, 255, 255, 255)
        if (position > 0.01f) {
            track.shader = LinearGradient(0f, 0f, w, 0f, pal.accentDeep, pal.accent, Shader.TileMode.CLAMP)
            track.alpha = (255 * position).toInt().coerceAtLeast(90)
        } else {
            track.shader = null; track.color = offColor
        }
        canvas.drawRoundRect(rect, r, r, track)
        border.strokeWidth = 1f * d
        border.color = ColorUtils.setAlphaComponent(pal.accentLight, if (position > 0.5f) 0xA0 else 0x50)
        canvas.drawRoundRect(rect, r, r, border)
        val kr = th / 2f - 3f * d
        val cx = rect.left + r + (w - 2 * r) * position
        canvas.drawCircle(cx, h / 2f, kr, knob)
    }
}
