package com.akaaflix.launcher.core.ui

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.view.animation.DecelerateInterpolator
import androidx.core.graphics.ColorUtils

/**
 * The single background drawable of the Akaa Flix design system: dark translucent "glass" with a
 * neon border, an optional coloured under-glow and the remote-focus neon effect.
 *
 * Focus (D-pad / remote): a bright purple-magenta neon tube (coloured stroke + near-white core),
 * a controlled outer glow drawn inside [insetPx], and a subtle inner illumination. The effect
 * cross-fades through [focusLevel] (animated on the UI thread for ~150 ms, then idle), so the
 * previous item fades out while the new one fades in: no flicker, no lingering glow.
 *
 * Glow is drawn inside [insetPx] so nothing is painted outside the view bounds (avoids stale
 * pixels with partial screen updates on some GPUs). Shaders are cached per bounds/palette.
 */
class GlassDrawable(
    private var radiusPx: Float,
    val variant: Variant = Variant.PANEL,
    private val insetPx: Float = 0f,
    private val density: Float = 2f,
) : Drawable() {

    enum class Variant { PANEL, CARD, CHIP, PRIMARY, CIRCLE, NAV_ITEM, FIELD, TILE }

    /** Per-app colour used for the under-glow of app cards (0 = accent). */
    var glowTint: Int = 0
        set(value) { if (field != value) { field = value; shaderKey = -1; invalidateSelf() } }

    /** When true the drawable renders as selected regardless of view state (e.g. moving item). */
    var forceSelected: Boolean = false
        set(value) { if (field != value) { field = value; invalidateSelf() } }

    /** Draws with this palette instead of the global one (live preview of unsaved theme changes). */
    var paletteOverride: LauncherPalette? = null
        set(value) { if (field != value) { field = value; shaderKey = -1; invalidateSelf() } }

    private val pal: LauncherPalette get() = paletteOverride ?: LauncherUi.palette

    /** Cards can render in alternative styles: "glass" | "solid" | "outline" | "minimal". */
    var cardStyle: String = "glass"
        set(value) { if (field != value) { field = value; shaderKey = -1; invalidateSelf() } }

    private var focused = false
    private var selected = false
    private var activated = false
    private var pressed = false
    private var checked = false

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val sheen = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val under = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val tmp = RectF()
    private var shaderKey = -1
    private var alphaValue = 255
    private val inner = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    /** 0 = not focused, 1 = fully focused; animated between the two. */
    var focusLevel: Float = 0f
        private set
    private var animator: ValueAnimator? = null

    override fun isStateful(): Boolean = true

    override fun onStateChange(state: IntArray): Boolean {
        var f = false; var s = false; var a = false; var p = false; var c = false
        for (st in state) when (st) {
            android.R.attr.state_focused -> f = true
            android.R.attr.state_selected -> s = true
            android.R.attr.state_activated -> a = true
            android.R.attr.state_pressed -> p = true
            android.R.attr.state_checked -> c = true
        }
        val changed = f != focused || s != selected || a != activated || p != pressed || c != checked
        val focusChanged = f != focused
        focused = f; selected = s; activated = a; pressed = p; checked = c
        if (focusChanged) animateFocus(if (f) 1f else 0f)
        if (changed) invalidateSelf()
        return changed
    }

    private fun animateFocus(target: Float) {
        animator?.cancel()
        animator = null
        if (!LauncherUi.motionEnabled || callback == null || focusLevel == target) { focusLevel = target; return }
        animator = ValueAnimator.ofFloat(focusLevel, target).apply {
            duration = (LauncherUi.glowFadeMs * kotlin.math.abs(target - focusLevel)).toLong().coerceAtLeast(60L)
            interpolator = FADE
            addUpdateListener { focusLevel = it.animatedValue as Float; invalidateSelf() }
            start()
        }
    }

    /** Called by the view on attach / visibility changes: no animation, show the final state. */
    override fun jumpToCurrentState() {
        animator?.cancel(); animator = null
        focusLevel = if (focused) 1f else 0f
        invalidateSelf()
    }

    override fun onBoundsChange(bounds: android.graphics.Rect) {
        shaderKey = -1
    }

    fun setRadius(px: Float) { radiusPx = px; invalidateSelf() }

    private val isOn get() = selected || activated || checked || forceSelected

    private fun buildShaders(pal: LauncherPalette) {
        val top = rect.top; val bottom = rect.bottom
        val on = isOn
        val fillShader: Shader = when {
            variant == Variant.PRIMARY || (on && (variant == Variant.CHIP || variant == Variant.CIRCLE)) ->
                LinearGradient(rect.left, 0f, rect.right, 0f, pal.accentDeep, pal.accent, Shader.TileMode.CLAMP)
            variant == Variant.NAV_ITEM && on ->
                LinearGradient(rect.left, 0f, rect.right, 0f,
                    ColorUtils.setAlphaComponent(pal.accent, 0xB0), ColorUtils.setAlphaComponent(pal.accentDeep, 0x40), Shader.TileMode.CLAMP)
            variant == Variant.CARD && cardStyle == "solid" ->
                LinearGradient(0f, top, 0f, bottom, ColorUtils.setAlphaComponent(ColorUtils.blendARGB(pal.panelTop, Color.BLACK, 0.2f), 0xF2),
                    ColorUtils.setAlphaComponent(pal.panelBottom, 0xFA), Shader.TileMode.CLAMP)
            variant == Variant.FIELD || variant == Variant.CHIP || variant == Variant.CIRCLE ->
                LinearGradient(0f, top, 0f, bottom, pal.chipFill, ColorUtils.blendARGB(pal.chipFill, Color.BLACK, 0.25f), Shader.TileMode.CLAMP)
            else -> LinearGradient(0f, top, 0f, bottom, pal.panelTop, pal.panelBottom, Shader.TileMode.CLAMP)
        }
        fill.shader = fillShader
        sheen.shader = LinearGradient(0f, top, 0f, top + rect.height() * 0.45f,
            Color.argb(if (variant == Variant.PRIMARY) 46 else 26, 255, 255, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        val tintColor = if (glowTint != 0) glowTint else pal.accent
        under.shader = LinearGradient(0f, bottom - rect.height() * 0.42f, 0f, bottom,
            Color.TRANSPARENT, ColorUtils.setAlphaComponent(tintColor, 0x55), Shader.TileMode.CLAMP)
        shaderKey = LauncherUi.paletteVersion * 31 + (if (on) 1 else 0) + tintColor * 7 + pal.accentDeep * 3 + (if (cardStyle == "solid") 1 else 0)
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        val pal = this.pal
        rect.set(b.left + insetPx, b.top + insetPx, b.right - insetPx, b.bottom - insetPx)
        val r = if (variant == Variant.CIRCLE || variant == Variant.CHIP) rect.height() / 2f else radiusPx
        val key = LauncherUi.paletteVersion * 31 + (if (isOn) 1 else 0) + (if (glowTint != 0) glowTint else pal.accent) * 7 + pal.accentDeep * 3 + (if (cardStyle == "solid") 1 else 0)
        if (key != shaderKey) buildShaders(pal)
        val a = alphaValue
        val minimal = cardStyle == "minimal" || (variant == Variant.NAV_ITEM && !isOn)
        val outline = variant == Variant.CARD && cardStyle == "outline"

        // Self-heal a stale level (e.g. animator cancelled while the view was recycled).
        if (animator?.isRunning != true) {
            val want = if (focused) 1f else 0f
            if (focusLevel != want) focusLevel = want
        }
        val fl = focusLevel
        val neonStyle = LauncherUi.focusStyle != "scale"

        // Outer neon glow (inside the inset margin, so nothing is drawn outside the view bounds)
        if (fl > 0.01f && neonStyle && LauncherUi.glowEnabled && LauncherUi.focusStyle == "glow" && insetPx > 0f) {
            val rings = 6
            val step = insetPx / (rings + 0.35f)
            for (i in rings downTo 1) {
                val o = step * i
                tmp.set(rect.left - o, rect.top - o, rect.right + o, rect.bottom + o)
                glowPaint.strokeWidth = step * 1.5f
                val falloff = 0.9f / (1f + (i - 1) * 1.0f)
                glowPaint.color = ColorUtils.setAlphaComponent(pal.neon, (falloff * fl * a).toInt().coerceIn(0, 255))
                canvas.drawRoundRect(tmp, r + o, r + o, glowPaint)
            }
        }

        if (!minimal || focused || isOn) {
            if (!outline || focused || isOn) {
                fill.alpha = a
                canvas.drawRoundRect(rect, r, r, fill)
                sheen.alpha = a
                canvas.drawRoundRect(rect, r, r, sheen)
            }
            if (variant == Variant.CARD && !outline) {
                under.alpha = a
                canvas.drawRoundRect(rect, r, r, under)
            }
        }

        // Inner illumination: soft neon light along the inside of the border and a faint lift of the glass
        if (fl > 0.01f && neonStyle) {
            stroke.style = Paint.Style.FILL
            stroke.color = ColorUtils.setAlphaComponent(pal.neon, (0.10f * fl * a).toInt())
            canvas.drawRoundRect(rect, r, r, stroke)
            stroke.style = Paint.Style.STROKE
            val steps = 3
            val w = 2.4f * density
            for (i in 1..steps) {
                val o = w * (i - 0.5f) + 1.2f * density
                tmp.set(rect.left + o, rect.top + o, rect.right - o, rect.bottom - o)
                if (tmp.width() <= 0f || tmp.height() <= 0f) break
                inner.strokeWidth = w
                inner.color = ColorUtils.setAlphaComponent(pal.neon, (0.36f / i * fl * a).toInt().coerceIn(0, 255))
                val ir = (r - o).coerceAtLeast(0f)
                canvas.drawRoundRect(tmp, ir, ir, inner)
            }
        }

        // Resting border
        val bw: Float
        val bc: Int
        when {
            isOn -> { bw = 1.4f * density; bc = pal.accentLight }
            variant == Variant.CARD && glowTint != 0 && !minimal -> { bw = 1f * density; bc = ColorUtils.setAlphaComponent(ColorUtils.blendARGB(glowTint, Color.WHITE, 0.25f), 0x80) }
            minimal -> { bw = 0f; bc = 0 }
            outline -> { bw = 1.4f * density; bc = ColorUtils.setAlphaComponent(pal.accentLight, 0xA0) }
            else -> { bw = 1f * density; bc = pal.panelBorder }
        }
        val rest = 1f - fl
        if (bw > 0f && rest > 0.01f) {
            stroke.strokeWidth = bw
            stroke.color = ColorUtils.setAlphaComponent(bc, (Color.alpha(bc) * a / 255f * rest).toInt())
            val h = bw / 2f
            tmp.set(rect.left + h, rect.top + h, rect.right - h, rect.bottom - h)
            canvas.drawRoundRect(tmp, r - h, r - h, stroke)
        }

        // Focus border: neon tube (coloured stroke + near-white core); thin light line for "scale" style
        if (fl > 0.01f) {
            if (neonStyle) {
                val tube = 3.0f * density
                stroke.strokeWidth = tube
                stroke.color = ColorUtils.setAlphaComponent(pal.neon, (fl * a).toInt())
                var h = tube / 2f
                tmp.set(rect.left + h, rect.top + h, rect.right - h, rect.bottom - h)
                canvas.drawRoundRect(tmp, r - h, r - h, stroke)
                val core = 1.0f * density
                stroke.strokeWidth = core
                stroke.color = ColorUtils.setAlphaComponent(pal.neonCore, (0.9f * fl * a).toInt())
                h = tube / 2f
                tmp.set(rect.left + h, rect.top + h, rect.right - h, rect.bottom - h)
                canvas.drawRoundRect(tmp, r - h, r - h, stroke)
            } else {
                val w = 1.2f * density
                stroke.strokeWidth = w
                stroke.color = ColorUtils.setAlphaComponent(pal.accentLight, (fl * a).toInt())
                val h = w / 2f
                tmp.set(rect.left + h, rect.top + h, rect.right - h, rect.bottom - h)
                canvas.drawRoundRect(tmp, r - h, r - h, stroke)
            }
        }
        if (pressed) {
            stroke.style = Paint.Style.FILL
            stroke.color = Color.argb(28, 255, 255, 255)
            canvas.drawRoundRect(rect, r, r, stroke)
            stroke.style = Paint.Style.STROKE
        }
    }

    override fun setAlpha(alpha: Int) { alphaValue = alpha; invalidateSelf() }

    override fun setVisible(visible: Boolean, restart: Boolean): Boolean {
        if (!visible) { animator?.cancel(); animator = null; focusLevel = if (focused) 1f else 0f }
        return super.setVisible(visible, restart)
    }

    private companion object { val FADE = DecelerateInterpolator(1.4f) }
    override fun getAlpha(): Int = alphaValue
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
