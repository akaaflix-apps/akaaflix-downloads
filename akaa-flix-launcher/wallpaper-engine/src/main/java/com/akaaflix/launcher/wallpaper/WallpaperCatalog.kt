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
 * Bundled wallpapers. The default is the supplied Akaa Flix Egyptian futuristic artwork; the others
 * are original 4K images generated for this launcher (tools/ – no third-party photos).
 */
object WallpaperCatalog {
    const val DEFAULT_ID = LauncherSettings.DEFAULT_WALLPAPER

    val bundled: List<Wallpaper> = listOf(
        Wallpaper(DEFAULT_ID, CoreR.string.wpn_default_pyramids, category = WallpaperCategory.CINEMATIC, type = WallpaperType.STATIC,
            width = 1920, height = 1080, rawRes = R.raw.wp_default_pyramids),
        Wallpaper("builtin:pyramids_sunset", CoreR.string.wpn_pyramids_sunset, category = WallpaperCategory.LANDMARKS, type = WallpaperType.STATIC,
            width = 3840, height = 2160, rawRes = R.raw.wp_pyramids_sunset),
        Wallpaper("builtin:violet_galaxy", CoreR.string.wpn_violet_galaxy, category = WallpaperCategory.SPACE, type = WallpaperType.STATIC,
            width = 3840, height = 2160, rawRes = R.raw.wp_violet_galaxy),
        Wallpaper("builtin:moonrise", CoreR.string.wpn_moonrise, category = WallpaperCategory.SPACE, type = WallpaperType.STATIC,
            width = 3840, height = 2160, rawRes = R.raw.wp_moonrise),
        Wallpaper("builtin:aurora_night", CoreR.string.wpn_aurora_night, category = WallpaperCategory.NATURE, type = WallpaperType.STATIC,
            width = 3840, height = 2160, rawRes = R.raw.wp_aurora_night),
        Wallpaper("builtin:dusk_lake", CoreR.string.wpn_dusk_lake, category = WallpaperCategory.NATURE, type = WallpaperType.STATIC,
            width = 3840, height = 2160, rawRes = R.raw.wp_dusk_lake),
        Wallpaper("builtin:neon_waves", CoreR.string.wpn_neon_waves, category = WallpaperCategory.ABSTRACT, type = WallpaperType.STATIC,
            width = 3840, height = 2160, rawRes = R.raw.wp_neon_waves),
        Wallpaper("builtin:neon_tunnel", CoreR.string.wpn_neon_tunnel, category = WallpaperCategory.ABSTRACT, type = WallpaperType.STATIC,
            width = 3840, height = 2160, rawRes = R.raw.wp_neon_tunnel),
        Wallpaper("builtin:purple_silk", CoreR.string.wpn_purple_silk, category = WallpaperCategory.ABSTRACT, type = WallpaperType.STATIC,
            width = 3840, height = 2160, rawRes = R.raw.wp_purple_silk),
        Wallpaper("builtin:neon_pulse", CoreR.string.wpn_neon_pulse, category = WallpaperCategory.ANIMATED, type = WallpaperType.ANIMATED,
            width = 1920, height = 1080, rawRes = R.raw.wp_default_pyramids, animation = "neon_pulse"),
        Wallpaper("builtin:starfield", CoreR.string.wpn_starfield, category = WallpaperCategory.ANIMATED, type = WallpaperType.ANIMATED,
            width = 3840, height = 2160, rawRes = R.raw.wp_violet_galaxy, animation = "starfield"),
    )

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
