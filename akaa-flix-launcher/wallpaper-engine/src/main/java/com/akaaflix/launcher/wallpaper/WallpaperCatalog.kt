package com.akaaflix.launcher.wallpaper

import android.content.Context
import com.akaaflix.launcher.core.R as CoreR
import com.akaaflix.launcher.prefs.LauncherSettings
import com.akaaflix.launcher.prefs.WallpaperUserData
import com.akaaflix.launcher.wallpaper.model.Wallpaper
import com.akaaflix.launcher.wallpaper.model.WallpaperCategory
import com.akaaflix.launcher.wallpaper.model.WallpaperType
import java.io.File

/**
 * The approved, selectable wallpaper library (v1.1):
 *  - Egyptian Pyramids Cinematic: the supplied Akaa Flix artwork, the primary/default background;
 *  - Animated Neon Pulse: purple/blue neon waves, procedural and lightweight (static frame when
 *    animation is off or on low-memory devices);
 *  - the wallpapers extracted from the approved PDF (tools/import_pdf_wallpapers.py), stored at their
 *    native 1670–1672 × 941 resolution (badge "HD"; the decoder scales them to the screen, never stretched).
 * User-imported images and videos are listed after these.
 */
object WallpaperCatalog {
    const val DEFAULT_ID = LauncherSettings.DEFAULT_WALLPAPER

    private fun pdf(id: String, name: Int, cat: WallpaperCategory, raw: Int, w: Int) =
        Wallpaper("builtin:$id", name, category = cat, type = WallpaperType.STATIC, width = w, height = 941, rawRes = raw)

    val bundled: List<Wallpaper> = listOf(
        Wallpaper(DEFAULT_ID, CoreR.string.wpn_default_pyramids, category = WallpaperCategory.CINEMATIC, type = WallpaperType.STATIC,
            width = 1920, height = 1080, rawRes = R.raw.wp_default_pyramids),
        Wallpaper("builtin:neon_pulse", CoreR.string.wpn_neon_pulse, category = WallpaperCategory.ANIMATED, type = WallpaperType.ANIMATED,
            width = 3840, height = 2160, rawRes = R.raw.wp_neon_waves, animation = "neon_pulse"),
        pdf("dubai_skyline", CoreR.string.wpn_dubai_skyline, WallpaperCategory.LANDMARKS, R.raw.wp_dubai_skyline, 1672),
        pdf("aqsa_dome", CoreR.string.wpn_aqsa_dome, WallpaperCategory.MOSQUES, R.raw.wp_aqsa_dome, 1672),
        pdf("neon_supercar", CoreR.string.wpn_neon_supercar, WallpaperCategory.CINEMATIC, R.raw.wp_neon_supercar, 1671),
        pdf("sphinx_pyramids", CoreR.string.wpn_sphinx_pyramids, WallpaperCategory.LANDMARKS, R.raw.wp_sphinx_pyramids, 1671),
        pdf("cherry_lake", CoreR.string.wpn_cherry_lake, WallpaperCategory.NATURE, R.raw.wp_cherry_lake, 1670),
        pdf("moon_mountains", CoreR.string.wpn_moon_mountains, WallpaperCategory.NATURE, R.raw.wp_moon_mountains, 1672),
        pdf("dubai_waterfront", CoreR.string.wpn_dubai_waterfront, WallpaperCategory.LANDMARKS, R.raw.wp_dubai_waterfront, 1672),
        pdf("al_azhar", CoreR.string.wpn_al_azhar, WallpaperCategory.MOSQUES, R.raw.wp_al_azhar, 1672),
        pdf("prophets_mosque", CoreR.string.wpn_prophets_mosque, WallpaperCategory.MOSQUES, R.raw.wp_prophets_mosque, 1670),
        pdf("lakeside_mosque", CoreR.string.wpn_lakeside_mosque, WallpaperCategory.MOSQUES, R.raw.wp_lakeside_mosque, 1671),
    )

    fun isBundled(id: String) = bundled.any { it.id == id }

    fun userWallpaperDir(context: Context) = File(context.filesDir, "wallpapers").apply { mkdirs() }

    fun all(context: Context, user: WallpaperUserData): List<Wallpaper> {
        val dir = userWallpaperDir(context)
        val mine = user.userWallpapers.sortedByDescending { it.addedAt }.map { u ->
            Wallpaper(u.id, customName = u.name, category = WallpaperCategory.MINE,
                type = if (u.type == "video") WallpaperType.VIDEO else WallpaperType.STATIC,
                width = u.width, height = u.height, sourceWidth = u.sourceWidth, sourceHeight = u.sourceHeight,
                file = File(dir, u.file))
        }.filter { it.file?.exists() == true }
        return bundled + mine
    }
}
