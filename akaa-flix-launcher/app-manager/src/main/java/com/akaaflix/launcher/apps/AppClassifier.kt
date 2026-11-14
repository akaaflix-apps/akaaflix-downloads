package com.akaaflix.launcher.apps

import com.akaaflix.launcher.apps.model.AppCategory

/**
 * Assigns apps to the two optional filters of the All Apps page ("Entertainment" / "Tools").
 * There are deliberately no "Games" or "System apps" groups. Uses ApplicationInfo.category on
 * Android 8+ and keyword heuristics otherwise; users can override per app.
 */
object AppClassifier {
    // ApplicationInfo.CATEGORY_* constants (API 26) – duplicated so this stays testable on the JVM.
    private const val CAT_GAME = 0
    private const val CAT_AUDIO = 1
    private const val CAT_VIDEO = 2
    private const val CAT_IMAGE = 3
    private const val CAT_SOCIAL = 4
    private const val CAT_NEWS = 5
    private const val CAT_MAPS = 6
    private const val CAT_PRODUCTIVITY = 7
    private const val CAT_ACCESSIBILITY = 8

    private val entertainmentWords = listOf(
        "player", "video", "movie", "film", "cinema", "stream", "music", "radio", "netflix", "youtube", "disney",
        "primevideo", "amazonvideo", "avod", "hulu", "kodi", "xbmc", "iptv", "vlc", "mxtech", "spotify", "anghami",
        "shahid", "osn", "watch", "plex", "emby", "jellyfin", "twitch", "tiktok", "game", "media", "stremio", "live",
        "sport", "tv.", ".tv", "appletv", "atve", "smarters", "tivimate", "perfectplayer", "podcast", "deezer",
        "soundcloud", "akaaflix", "starz", "hbo", "max", "paramount", "peacock", "pluto", "crunchyroll", "mubi",
    )
    private val toolWords = listOf(
        "file", "explorer", "manager", "browser", "chrome", "opera", "firefox", "download", "settings", "cleaner",
        "vpn", "keyboard", "zip", "archiv", "rar", "cast", "mirror", "miracast", "airscreen", "screen", "store",
        "aptoide", "vending", "market", "backup", "speedtest", "analyz", "tool", "util", "calculator", "clock",
        "calendar", "camera", "gallery", "photos", "drive", "dropbox", "remote", "terminal", "launcher", "update",
        "installer", "packageinstaller", "send", "share", "transfer", "wifi", "network", "bluetooth", "maps", "translate",
    )

    fun classify(systemCategory: Int, packageName: String, label: String): AppCategory {
        when (systemCategory) {
            CAT_GAME, CAT_AUDIO, CAT_VIDEO, CAT_IMAGE, CAT_SOCIAL, CAT_NEWS -> return AppCategory.ENTERTAINMENT
            CAT_MAPS, CAT_PRODUCTIVITY, CAT_ACCESSIBILITY -> return AppCategory.TOOLS
        }
        val hay = (packageName + " " + label).lowercase()
        val ent = entertainmentWords.count { hay.contains(it) }
        val tool = toolWords.count { hay.contains(it) }
        return when {
            ent == 0 && tool == 0 -> AppCategory.OTHER
            ent >= tool -> AppCategory.ENTERTAINMENT
            else -> AppCategory.TOOLS
        }
    }
}
