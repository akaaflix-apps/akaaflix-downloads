package com.akaaflix.launcher.core.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import androidx.core.graphics.ColorUtils

/**
 * The single background drawable of the Akaa Flix design system: dark translucent "glass" with a
 * neon border, an optional coloured under-glow and focus glow rings.
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
        focused = f; selected = s; activated = a; pressed = p; checked = c
        if (changed) invalidateSelf()
        return changed
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

        // Focus glow rings (inside the inset margin)
        val showGlow = focused && LauncherUi.glowEnabled && LauncherUi.focusStyle == "glow" && insetPx > 0f
        if (showGlow) {
            val rings = 4
            val step = insetPx / (rings + 0.5f)
            for (i in rings downTo 1) {
                val o = step * i
                tmp.set(rect.left - o, rect.top - o, rect.right + o, rect.bottom + o)
                glowPaint.strokeWidth = step * 1.25f
                glowPaint.color = ColorUtils.setAlphaComponent(pal.accent, ((0.55f / (i * 1.35f)) * a).toInt().coerceIn(0, 255))
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

        // Border
        val bw: Float
        val bc: Int
        when {
            focused && LauncherUi.focusStyle != "scale" -> { bw = 2.2f * density; bc = pal.focusBorder }
            focused -> { bw = 1.2f * density; bc = pal.accentLight }
            isOn -> { bw = 1.4f * density; bc = pal.accentLight }
            variant == Variant.CARD && glowTint != 0 && !minimal -> { bw = 1f * density; bc = ColorUtils.setAlphaComponent(ColorUtils.blendARGB(glowTint, Color.WHITE, 0.25f), 0x80) }
            minimal -> { bw = 0f; bc = 0 }
            outline -> { bw = 1.4f * density; bc = ColorUtils.setAlphaComponent(pal.accentLight, 0xA0) }
            else -> { bw = 1f * density; bc = pal.panelBorder }
        }
        if (bw > 0f) {
            stroke.strokeWidth = bw
            stroke.color = ColorUtils.setAlphaComponent(bc, (Color.alpha(bc) * a / 255f).toInt())
            val h = bw / 2f
            tmp.set(rect.left + h, rect.top + h, rect.right - h, rect.bottom - h)
            canvas.drawRoundRect(tmp, r - h, r - h, stroke)
        }
        if (pressed) {
            stroke.style = Paint.Style.FILL
            stroke.color = Color.argb(28, 255, 255, 255)
            canvas.drawRoundRect(rect, r, r, stroke)
            stroke.style = Paint.Style.STROKE
        }
    }

    override fun setAlpha(alpha: Int) { alphaValue = alpha; invalidateSelf() }
    override fun getAlpha(): Int = alphaValue
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
