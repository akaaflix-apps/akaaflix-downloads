package com.akaaflix.launcher.core.ui

import android.graphics.Color
import androidx.annotation.ColorInt
import androidx.annotation.StringRes
import androidx.core.graphics.ColorUtils
import com.akaaflix.launcher.core.R

/** Colours used by every custom drawable. Derived from a theme preset plus an optional accent. */
data class LauncherPalette(
    @ColorInt val accent: Int,
    @ColorInt val accentDeep: Int,
    @ColorInt val accentLight: Int,
    @ColorInt val panelTop: Int,
    @ColorInt val panelBottom: Int,
    @ColorInt val panelBorder: Int,
    @ColorInt val focusBorder: Int,
    @ColorInt val chipFill: Int,
    @ColorInt val sidebarFill: Int,
    /** Bright neon used by the focus border / glow (purple-magenta in the Akaa Flix theme). */
    @ColorInt val neon: Int = focusBorder,
    /** Near-white core of the neon tube. */
    @ColorInt val neonCore: Int = Color.WHITE,
)

data class ThemePreset(
    val id: String,
    @StringRes val nameRes: Int,
    @ColorInt val accent: Int,
    @ColorInt val accentDeep: Int,
    @ColorInt val panelTint: Int,
    @ColorInt val neon: Int,
)

object ThemeCatalog {
    const val DEFAULT_ID = "akaa_purple"

    val presets = listOf(
        ThemePreset("akaa_purple", R.string.theme_akaa_purple, 0xFFA855F7.toInt(), 0xFF6D28D9.toInt(), 0xFF2A1452.toInt(), 0xFFE24BFF.toInt()),
        ThemePreset("royal_blue", R.string.theme_royal_blue, 0xFF4F8DFF.toInt(), 0xFF1D3FAF.toInt(), 0xFF101F4D.toInt(), 0xFF6FB0FF.toInt()),
        ThemePreset("pharaoh_gold", R.string.theme_pharaoh_gold, 0xFFF5B942.toInt(), 0xFFA8670F.toInt(), 0xFF3A2410.toInt(), 0xFFFFCC55.toInt()),
        ThemePreset("silver_night", R.string.theme_silver_night, 0xFFCBD5E1.toInt(), 0xFF5B6476.toInt(), 0xFF1D2130.toInt(), 0xFFE9EEF6.toInt()),
    )

    /** Accent swatches; 0 means "use the theme colour". */
    val accents = intArrayOf(
        0,
        0xFFA855F7.toInt(), 0xFF7C3AED.toInt(), 0xFF3B82F6.toInt(), 0xFF22D3EE.toInt(),
        0xFFF5B942.toInt(), 0xFFCBD5E1.toInt(), 0xFFEC4899.toInt(), 0xFF10B981.toInt(),
    )

    /** Saturated, brighter variant of a colour for the neon tube. */
    private fun brighten(@ColorInt c: Int): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(c, hsl)
        hsl[1] = (hsl[1] * 1.15f).coerceAtMost(1f)
        hsl[2] = hsl[2].coerceIn(0.62f, 0.72f)
        return ColorUtils.HSLToColor(hsl)
    }

    fun preset(id: String): ThemePreset = presets.firstOrNull { it.id == id } ?: presets[0]

    fun palette(themeId: String, @ColorInt accentOverride: Int): LauncherPalette {
        val p = preset(themeId)
        val accent = if (accentOverride != 0) accentOverride else p.accent
        val deep = if (accentOverride != 0) ColorUtils.blendARGB(accent, Color.BLACK, 0.42f) else p.accentDeep
        val light = ColorUtils.blendARGB(accent, Color.WHITE, 0.45f)
        val tint = if (accentOverride != 0) ColorUtils.blendARGB(p.panelTint, accent, 0.22f) else p.panelTint
        return LauncherPalette(
            accent = accent,
            accentDeep = deep,
            accentLight = light,
            panelTop = ColorUtils.setAlphaComponent(ColorUtils.blendARGB(tint, Color.WHITE, 0.07f), 0xB4),
            panelBottom = ColorUtils.setAlphaComponent(ColorUtils.blendARGB(tint, Color.BLACK, 0.52f), 0xCC),
            panelBorder = ColorUtils.setAlphaComponent(light, 0x5C),
            focusBorder = ColorUtils.blendARGB(accent, Color.WHITE, 0.72f),
            chipFill = ColorUtils.setAlphaComponent(ColorUtils.blendARGB(tint, Color.BLACK, 0.30f), 0xB0),
            sidebarFill = ColorUtils.setAlphaComponent(ColorUtils.blendARGB(tint, Color.BLACK, 0.55f), 0xC4),
            neon = if (accentOverride != 0) brighten(accent) else p.neon,
            neonCore = ColorUtils.blendARGB(if (accentOverride != 0) accent else p.neon, Color.WHITE, 0.82f),
        )
    }
}

/**
 * Process-wide UI configuration read by custom views at draw time. Updated on the main thread by
 * the activity whenever settings or the performance profile change, followed by an invalidate.
 */
object LauncherUi {
    var palette: LauncherPalette = ThemeCatalog.palette(ThemeCatalog.DEFAULT_ID, 0)
        private set
    var paletteVersion: Int = 0
        private set

    /** Master switch for animations (motion effects setting and performance mode). */
    var motionEnabled: Boolean = true
    /** Neon glow rings around focused items (disabled in lightweight mode). */
    var glowEnabled: Boolean = true
    /** Scale applied to focused cards. 1f = no zoom. */
    var focusScale: Float = 1.08f
    /** "glow" | "border" | "scale" */
    var focusStyle: String = "glow"
    var animDurationMs: Long = 170L
    /** Duration of the neon glow cross-fade between the previous and the newly focused item. */
    val glowFadeMs: Long get() = (animDurationMs * 0.9f).toLong().coerceIn(90L, 200L)

    fun applyPalette(p: LauncherPalette) {
        if (p != palette) {
            palette = p
            paletteVersion++
        }
    }
}
