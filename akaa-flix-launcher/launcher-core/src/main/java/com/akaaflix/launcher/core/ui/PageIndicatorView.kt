package com.akaaflix.launcher.core.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/** Pagination dots from the reference (active dot = glowing accent). */
class PageIndicatorView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val d = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    var count = 1
        set(value) { field = value.coerceAtLeast(1); requestLayout(); invalidate() }
    var current = 0
        set(value) { field = value; invalidate() }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = (count * 18 * d).toInt()
        setMeasuredDimension(resolveSize(w, widthMeasureSpec), resolveSize((18 * d).toInt(), heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        val step = 18 * d
        val start = (width - count * step) / 2f + step / 2f
        val cy = height / 2f
        val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
        for (i in 0 until count) {
            val idx = if (rtl) count - 1 - i else i
            val cx = start + i * step
            if (idx == current) {
                paint.color = LauncherUi.palette.accent
                paint.alpha = 90
                canvas.drawCircle(cx, cy, 7.5f * d, paint)
                paint.alpha = 255
                paint.color = Color.argb(255, 230, 120, 255)
                canvas.drawCircle(cx, cy, 5f * d, paint)
            } else {
                paint.color = Color.argb(110, 255, 255, 255)
                canvas.drawCircle(cx, cy, 4.5f * d, paint)
            }
        }
    }
}
