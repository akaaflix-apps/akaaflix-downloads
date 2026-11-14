package com.akaaflix.launcher.core.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View

/** Thin gradient progress bar used for storage / RAM usage cards. */
class NeonProgressBar @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val d = resources.displayMetrics.density
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(60, 255, 255, 255) }
    private val fg = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    var progress: Float = 0f
        set(value) { field = value.coerceIn(0f, 1f); invalidate() }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(getDefaultSize(suggestedMinimumWidth, widthMeasureSpec), resolveSize((5 * d).toInt(), heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        val pal = LauncherUi.palette
        val h = height.toFloat(); val r = h / 2f
        rect.set(0f, 0f, width.toFloat(), h)
        canvas.drawRoundRect(rect, r, r, bg)
        val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
        val pw = width * progress
        if (rtl) rect.set(width - pw, 0f, width.toFloat(), h) else rect.set(0f, 0f, pw, h)
        fg.shader = LinearGradient(rect.left, 0f, rect.right.coerceAtLeast(rect.left + 1f), 0f, pal.accent, Color.WHITE, Shader.TileMode.CLAMP)
        if (pw > 0f) canvas.drawRoundRect(rect, r, r, fg)
    }
}
