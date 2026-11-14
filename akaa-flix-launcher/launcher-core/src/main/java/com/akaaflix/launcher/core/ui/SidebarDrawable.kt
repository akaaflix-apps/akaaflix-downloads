package com.akaaflix.launcher.core.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Shader
import android.graphics.drawable.Drawable
import androidx.core.graphics.ColorUtils

/**
 * The curved glass navigation panel from the reference: straight edge on the screen side and a
 * convex neon edge facing the content. [horizontal] renders the bottom-bar variant on phones.
 */
class SidebarDrawable(private val density: Float, private val horizontal: Boolean = false) : Drawable() {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val path = Path()
    private val edgePath = Path()
    private var key = -1
    var paletteOverride: LauncherPalette? = null
        set(value) { field = value; key = -1; invalidateSelf() }

    override fun onBoundsChange(bounds: android.graphics.Rect) { key = -1 }

    private fun build() {
        val b = bounds
        val pal = paletteOverride ?: LauncherUi.palette
        path.reset(); edgePath.reset()
        val w = b.width().toFloat(); val h = b.height().toFloat()
        if (!horizontal) {
            val curve = w * 0.9f
            val l = b.left.toFloat(); val t = b.top.toFloat()
            path.moveTo(l, t)
            path.cubicTo(l + w * 0.65f, t, l + w, t + curve * 0.35f, l + w, t + curve)
            path.lineTo(l + w, t + h - curve)
            path.cubicTo(l + w, t + h - curve * 0.35f, l + w * 0.65f, t + h, l, t + h)
            path.close()
            edgePath.moveTo(l + w * 0.2f, t + 1f)
            edgePath.cubicTo(l + w * 0.68f, t + 1f, l + w - 1f, t + curve * 0.35f, l + w - 1f, t + curve)
            edgePath.lineTo(l + w - 1f, t + h - curve)
            edgePath.cubicTo(l + w - 1f, t + h - curve * 0.35f, l + w * 0.68f, t + h - 1f, l + w * 0.2f, t + h - 1f)
            fill.shader = LinearGradient(l, 0f, l + w, 0f, ColorUtils.setAlphaComponent(pal.sidebarFill, 0xD8),
                ColorUtils.setAlphaComponent(pal.sidebarFill, 0x96), Shader.TileMode.CLAMP)
            edge.shader = LinearGradient(0f, t, 0f, t + h, ColorUtils.setAlphaComponent(pal.accent, 0x20), pal.accent, Shader.TileMode.MIRROR)
        } else {
            val l = b.left.toFloat(); val t = b.top.toFloat(); val r = 22f * density
            path.addRoundRect(l, t, l + w, t + h, floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f), Path.Direction.CW)
            edgePath.moveTo(l + r, t + 1f); edgePath.lineTo(l + w - r, t + 1f)
            fill.shader = LinearGradient(0f, t, 0f, t + h, ColorUtils.setAlphaComponent(pal.sidebarFill, 0xE0),
                ColorUtils.setAlphaComponent(pal.sidebarFill, 0xF2), Shader.TileMode.CLAMP)
            edge.shader = LinearGradient(l, 0f, l + w, 0f, ColorUtils.setAlphaComponent(pal.accent, 0x20), pal.accent, Shader.TileMode.MIRROR)
        }
        edge.strokeWidth = 1.6f * density
        key = LauncherUi.paletteVersion * 7 + (paletteOverride?.accent ?: 0)
    }

    override fun draw(canvas: Canvas) {
        if (bounds.isEmpty) return
        if (key != LauncherUi.paletteVersion * 7 + (paletteOverride?.accent ?: 0)) build()
        canvas.drawPath(path, fill)
        canvas.drawPath(edgePath, edge)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
