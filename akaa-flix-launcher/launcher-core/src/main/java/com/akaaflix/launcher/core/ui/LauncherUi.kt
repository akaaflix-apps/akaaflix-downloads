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
)

data class ThemePreset(
    val id: String,
    @StringRes val nameRes: Int,
    @ColorInt val accent: Int,
    @ColorInt val accentDeep: Int,
    @ColorInt val panelTint: Int,
)

object ThemeCatalog {
    const val DEFAULT_ID = "akaa_purple"

    val presets = listOf(
        ThemePreset("akaa_purple", R.string.theme_akaa_purple, 0xFFA855F7.toInt(), 0xFF6D28D9.toInt(), 0xFF2A1452.toInt()),
        ThemePreset("royal_blue", R.string.theme_royal_blue, 0xFF4F8DFF.toInt(), 0xFF1D3FAF.toInt(), 0xFF101F4D.toInt()),
        ThemePreset("pharaoh_gold", R.string.theme_pharaoh_gold, 0xFFF5B942.toInt(), 0xFFA8670F.toInt(), 0xFF3A2410.toInt()),
        ThemePreset("silver_night", R.string.theme_silver_night, 0xFFCBD5E1.toInt(), 0xFF5B6476.toInt(), 0xFF1D2130.toInt()),
    )

    /** Accent swatches; 0 means "use the theme colour". */
    val accents = intArrayOf(
        0,
        0xFFA855F7.toInt(), 0xFF7C3AED.toInt(), 0xFF3B82F6.toInt(), 0xFF22D3EE.toInt(),
        0xFFF5B942.toInt(), 0xFFCBD5E1.toInt(), 0xFFEC4899.toInt(), 0xFF10B981.toInt(),
    )

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

    fun applyPalette(p: LauncherPalette) {
        if (p != palette) {
            palette = p
            paletteVersion++
        }
    }
}
