package com.akaaflix.launcher.wallpaper.model

import android.content.Context
import androidx.annotation.RawRes
import androidx.annotation.StringRes
import java.io.File

enum class WallpaperType { STATIC, VIDEO, ANIMATED }
enum class WallpaperCategory { CINEMATIC, SPACE, NATURE, LANDMARKS, MOSQUES, ABSTRACT, ANIMATED, MINE }

data class Wallpaper(
    val id: String,
    @StringRes val nameRes: Int = 0,
    val customName: String? = null,
    val category: WallpaperCategory,
    val type: WallpaperType,
    /** Resolution of the stored image (what is decoded). */
    val width: Int,
    val height: Int,
    /** Resolution of the original source (imports above 4K are stored optimised; badge shows the source). */
    val sourceWidth: Int = width,
    val sourceHeight: Int = height,
    @RawRes val rawRes: Int = 0,
    val file: File? = null,
    val animation: String? = null,
) {
    val isUser: Boolean get() = file != null

    fun name(context: Context): String = customName ?: if (nameRes != 0) context.getString(nameRes) else id

    /** "8K", "4K", "2K", "FHD" or "HD" from the source resolution. */
    fun badge(): String = badgeFor(sourceWidth, sourceHeight)

    companion object {
        fun badgeFor(w: Int, h: Int): String {
            val edge = maxOf(w, h)
            return when {
                edge >= 7680 -> "8K"
                edge >= 3840 -> "4K"
                edge >= 2560 -> "2K"
                edge >= 1920 -> "FHD"
                else -> "HD"
            }
        }
    }
}
