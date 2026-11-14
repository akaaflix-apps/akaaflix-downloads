package com.akaaflix.launcher.wallpaper.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.view.View
import androidx.core.graphics.ColorUtils
import com.akaaflix.launcher.core.ui.LauncherUi
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/** Base for procedural animated wallpapers: ~30 fps, stops completely when not running. */
abstract class AnimatedOverlay(context: Context) : View(context) {
    protected val density = resources.displayMetrics.density
    var running = false
        set(value) { field = value; if (value) postInvalidateOnAnimation() }
    private var lastFrame = 0L
    private val start = SystemClock.uptimeMillis()

    override fun onDraw(canvas: Canvas) {
        val t = (SystemClock.uptimeMillis() - start) / 1000f
        drawFrame(canvas, t)
        if (running && isAttachedToWindow) {
            val now = SystemClock.uptimeMillis()
            val delay = (33 - (now - lastFrame)).coerceIn(0, 33)
            lastFrame = now + delay
            postInvalidateDelayed(delay)
        }
    }

    override fun onDetachedFromWindow() { running = false; super.onDetachedFromWindow() }
    abstract fun drawFrame(canvas: Canvas, t: Float)
}

/** "Neon Pulse": the floor rings of the Akaa Flix artwork pulse outward; stars twinkle. */
class NeonPulseOverlay(context: Context) : AnimatedOverlay(context) {
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val star = Paint(Paint.ANTI_ALIAS_FLAG)
    private val oval = RectF()
    private val rnd = Random(42)
    private val stars = Array(42) { floatArrayOf(rnd.nextFloat(), rnd.nextFloat() * 0.42f, rnd.nextFloat() * 6.28f, 0.6f + rnd.nextFloat() * 1.6f) }

    override fun drawFrame(canvas: Canvas, t: Float) {
        val w = width.toFloat(); val h = height.toFloat()
        val accent = LauncherUi.palette.accent
        val cx = w * 0.5f; val cy = h * 0.88f
        for (i in 0 until 3) {
            val phase = ((t * 0.22f) + i / 3f) % 1f
            val rx = w * (0.18f + phase * 0.55f)
            val ry = rx * 0.20f
            oval.set(cx - rx, cy - ry, cx + rx, cy + ry)
            val a = ((1f - phase) * 200).toInt().coerceIn(0, 255)
            ring.color = ColorUtils.setAlphaComponent(accent, a / 3)
            ring.strokeWidth = 9f * density
            canvas.drawOval(oval, ring)
            ring.color = ColorUtils.setAlphaComponent(Color.WHITE, (a * 0.8f).toInt())
            ring.strokeWidth = 1.6f * density
            canvas.drawOval(oval, ring)
        }
        for (s in stars) {
            val a = (abs(sin(t * s[3] + s[2])) * 230).toInt()
            star.color = Color.argb(a, 235, 225, 255)
            canvas.drawCircle(s[0] * w, s[1] * h, 1.4f * density, star)
        }
    }
}

/** "Starfield Drift": slow parallax star flight over the galaxy artwork. */
class StarfieldOverlay(context: Context) : AnimatedOverlay(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rnd = Random(7)
    private val stars = Array(150) { floatArrayOf(rnd.nextFloat() * 2 - 1, rnd.nextFloat() * 2 - 1, rnd.nextFloat()) }
    private var lastT = 0f

    override fun drawFrame(canvas: Canvas, t: Float) {
        val dt = (t - lastT).coerceIn(0f, 0.1f); lastT = t
        val w = width.toFloat(); val h = height.toFloat()
        val cx = w / 2f; val cy = h / 2f
        for (s in stars) {
            s[2] -= dt * 0.07f
            if (s[2] <= 0.02f) { s[0] = rnd.nextFloat() * 2 - 1; s[1] = rnd.nextFloat() * 2 - 1; s[2] = 1f }
            val k = 0.5f / s[2]
            val x = cx + s[0] * k * cx; val y = cy + s[1] * k * cy
            if (x < 0 || x > w || y < 0 || y > h) { s[2] = 1f; continue }
            val bright = ((1f - s[2]) * 255).toInt().coerceIn(30, 255)
            paint.color = Color.argb(bright, 225, 215, 255)
            canvas.drawCircle(x, y, (1f + (1f - s[2]) * 2.2f) * density, paint)
        }
    }
}
