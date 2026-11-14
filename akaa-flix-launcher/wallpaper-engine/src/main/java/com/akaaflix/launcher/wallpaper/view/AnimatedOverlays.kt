package com.akaaflix.launcher.wallpaper.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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

/**
 * "Animated Neon Pulse": purple and blue neon waves flowing slowly across a deep night gradient,
 * with a gentle breathing glow. Lightweight: 7 polylines (57 points) stroked three times per frame
 * at ~30 fps with preallocated paints/paths, no bitmaps, no blur filters, no allocation per frame.
 * When animation is off (setting or low-RAM device) the static frame (wp_neon_waves) is shown instead.
 */
class NeonPulseOverlay(context: Context) : AnimatedOverlay(context) {
    private class Wave(val color: Int, val amp: Float, val freq: Float, val speed: Float, val phase: Float, val offset: Float)

    private val waves = arrayOf(
        Wave(0xFFE040FB.toInt(), 0.070f, 1.10f, 0.050f, 0.0f, 0.020f),
        Wave(0xFFB15CFF.toInt(), 0.095f, 0.85f, -0.040f, 1.3f, -0.030f),
        Wave(0xFF7C6CFF.toInt(), 0.080f, 1.35f, 0.065f, 2.1f, 0.050f),
        Wave(0xFF4F8DFF.toInt(), 0.115f, 0.70f, -0.032f, 0.6f, 0.000f),
        Wave(0xFF5B9BFF.toInt(), 0.065f, 1.65f, 0.075f, 3.0f, -0.060f),
        Wave(0xFFD946EF.toInt(), 0.090f, 1.00f, -0.048f, 4.2f, 0.080f),
        Wave(0xFF9F7AEA.toInt(), 0.055f, 1.90f, 0.058f, 5.0f, -0.012f),
    )
    private val bg = Paint()
    private var bgH = -1f
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val path = android.graphics.Path()
    private val star = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rnd = Random(11)
    private val stars = Array(36) { floatArrayOf(rnd.nextFloat(), rnd.nextFloat() * 0.38f, rnd.nextFloat() * 6.28f, 0.5f + rnd.nextFloat() * 1.4f) }

    override fun drawFrame(canvas: Canvas, t: Float) {
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        if (bgH != h) {
            bgH = h
            bg.shader = android.graphics.LinearGradient(0f, 0f, 0f, h,
                intArrayOf(0xFF05010D.toInt(), 0xFF0D0322.toInt(), 0xFF140632.toInt(), 0xFF05010C.toInt()),
                floatArrayOf(0f, 0.45f, 0.62f, 1f), android.graphics.Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, w, h, bg)
        for (s in stars) {
            star.color = Color.argb((abs(sin(t * s[3] + s[2])) * 150).toInt(), 225, 215, 255)
            canvas.drawCircle(s[0] * w, s[1] * h, 1.1f * density, star)
        }
        val cy = h * 0.56f
        val breathe = 0.78f + 0.22f * sin(t * 1.2f)
        val twoPi = 6.2831855f
        val n = 56
        for (wv in waves) {
            path.rewind()
            for (i in 0..n) {
                val u = i / n.toFloat()
                val env = 0.38f + 0.62f * u          // waves gather on the left and open to the right
                val y = cy + h * (wv.offset * env + wv.amp * env * sin(u * wv.freq * twoPi + wv.phase + t * wv.speed * twoPi))
                if (i == 0) path.moveTo(0f, y) else path.lineTo(u * w, y)
            }
            stroke.color = ColorUtils.setAlphaComponent(wv.color, (30 * breathe).toInt()); stroke.strokeWidth = 14f * density
            canvas.drawPath(path, stroke)
            stroke.color = ColorUtils.setAlphaComponent(wv.color, (72 * breathe).toInt()); stroke.strokeWidth = 5.5f * density
            canvas.drawPath(path, stroke)
            stroke.color = ColorUtils.setAlphaComponent(ColorUtils.blendARGB(wv.color, Color.WHITE, 0.38f), 235); stroke.strokeWidth = 1.7f * density
            canvas.drawPath(path, stroke)
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
