package com.akaaflix.launcher.prefs

import org.json.JSONObject

/**
 * Every launcher preference with its default. Stored as JSON (one DataStore entry) so backup,
 * restore and migrations share one code path; unknown/missing keys fall back to defaults.
 */
data class LauncherSettings(
    // General
    val language: String = "ar",                 // system | ar | en | fr
    val interfaceMode: String = "auto",          // auto | tv | touch
    val startPage: String = "home",              // home | all_apps | favorites
    val autoStartOnBoot: Boolean = false,
    // Appearance
    val themeId: String = "akaa_purple",
    val accentColor: Int = 0,
    val fontFamily: String = "tajawal",          // tajawal | cairo | system
    val fontScale: Float = 1.0f,
    val iconShape: String = "original",          // original | rounded | circle | square
    val cardStyle: String = "glass",             // glass | solid | outline | minimal
    val useTvBanners: Boolean = true,
    val showAppLabels: Boolean = true,
    val iconSize: String = "large",              // always large (Medium removed in v1.4)
    val gridColumns: Int = 0,                    // 0 = automatic
    val homeRows: Int = 3,                       // 1..3 (three by default)
    val showHomeLogo: Boolean = true,
    val showFavoritesBar: Boolean = true,
    val showBottomBar: Boolean = true,
    val showSidebarLabels: Boolean = true,
    val showRemoteDecor: Boolean = true,
    // Motion & focus
    val motionEnabled: Boolean = true,
    val focusStyle: String = "glow",             // glow | border | scale
    val focusZoom: String = "medium",            // off | small | medium | large
    val pageTransition: String = "fade",         // fade | slide | none
    val pointerStyle: String = "neon",           // system | neon | large
    // Clock & calendar
    val showClockDate: Boolean = true,
    val showNetworkStatus: Boolean = true,
    val clockFormat: String = "system",          // system | 12 | 24
    val showHijri: Boolean = true,
    val bilingualDate: Boolean = true,
    val hijriMethod: String = "umm_al_qura",     // umm_al_qura | tabular
    val hijriAdjust: Int = 0,                    // -2..+2 days
    val numerals: String = "western",            // western | eastern
    // Weather
    val weatherEnabled: Boolean = true,
    val weatherCity: String = "",
    val weatherCountry: String = "",
    val weatherLat: Double = Double.NaN,
    val weatherLon: Double = Double.NaN,
    val weatherUnit: String = "c",
    /** "auto" = city detected from approximate device location, "manual" = typed by the user. */
    val weatherLocationMode: String = "manual",
    val weatherLocatedAt: Long = 0L,
    // Wallpaper
    val wallpaperId: String = DEFAULT_WALLPAPER,
    val animatedWallpaper: Boolean = true,
    val rotationEnabled: Boolean = false,
    val rotationIntervalMin: Int = 30,
    val rotationSource: String = "favorites",    // favorites | all
    val lastRotationAt: Long = 0L,
    // Performance
    val performanceMode: String = "auto",        // auto | high | balanced | light
    // Remote
    val menuKeyAction: String = "context",       // context | settings | customize
    val longPressAction: String = "context",     // context | move | info
    val backOnHome: String = "focus_first",      // focus_first | none
    // Parental controls & profile
    val pinHash: String = "",
    val pinSalt: String = "",
    val lockSettings: Boolean = false,
    val lockCustomization: Boolean = false,
    val protectHiddenApps: Boolean = false,
    val profileName: String = "",
    val profileAvatar: String = "person",        // person | anubis | pyramids | galaxy | logo
    // Updates
    val autoCheckUpdates: Boolean = false,
    val lastUpdateCheck: Long = 0L,
    // Safety
    val safeMode: Boolean = false,
) {
    val hasPin: Boolean get() = pinHash.isNotEmpty() && pinSalt.isNotEmpty()
    val hasWeatherLocation: Boolean get() = weatherCity.isNotBlank() && !weatherLat.isNaN() && !weatherLon.isNaN()

    /** Copies only the options managed by the Customization page (draft → saved). */
    fun withCustomizationFrom(d: LauncherSettings) = copy(
        themeId = d.themeId, accentColor = d.accentColor, fontFamily = d.fontFamily, fontScale = d.fontScale,
        iconShape = d.iconShape, cardStyle = d.cardStyle, useTvBanners = d.useTvBanners, showAppLabels = d.showAppLabels,
        iconSize = d.iconSize, gridColumns = d.gridColumns, homeRows = d.homeRows, showHomeLogo = d.showHomeLogo,
        showFavoritesBar = d.showFavoritesBar, showBottomBar = d.showBottomBar, showSidebarLabels = d.showSidebarLabels,
        showRemoteDecor = d.showRemoteDecor, motionEnabled = d.motionEnabled, focusStyle = d.focusStyle,
        focusZoom = d.focusZoom, pageTransition = d.pageTransition, pointerStyle = d.pointerStyle,
        showClockDate = d.showClockDate, wallpaperId = d.wallpaperId, startPage = d.startPage,
        interfaceMode = d.interfaceMode, weatherEnabled = d.weatherEnabled,
    )

    /** Customization defaults applied to the current settings ("Restore defaults" on that page). */
    fun withCustomizationDefaults(): LauncherSettings = withCustomizationFrom(LauncherSettings())

    fun toJson(includeSecrets: Boolean = true): JSONObject = JSONObject().apply {
        put("v", SCHEMA)
        put("language", language); put("interfaceMode", interfaceMode); put("startPage", startPage)
        put("autoStartOnBoot", autoStartOnBoot)
        put("themeId", themeId); put("accentColor", accentColor); put("fontFamily", fontFamily)
        put("fontScale", fontScale.toDouble()); put("iconShape", iconShape); put("cardStyle", cardStyle)
        put("useTvBanners", useTvBanners); put("showAppLabels", showAppLabels); put("iconSize", iconSize)
        put("gridColumns", gridColumns); put("homeRows", homeRows); put("showHomeLogo", showHomeLogo)
        put("showFavoritesBar", showFavoritesBar); put("showBottomBar", showBottomBar)
        put("showSidebarLabels", showSidebarLabels); put("showRemoteDecor", showRemoteDecor)
        put("motionEnabled", motionEnabled); put("focusStyle", focusStyle); put("focusZoom", focusZoom)
        put("pageTransition", pageTransition); put("pointerStyle", pointerStyle)
        put("showClockDate", showClockDate); put("showNetworkStatus", showNetworkStatus)
        put("clockFormat", clockFormat); put("showHijri", showHijri); put("bilingualDate", bilingualDate)
        put("hijriMethod", hijriMethod); put("hijriAdjust", hijriAdjust); put("numerals", numerals)
        put("weatherEnabled", weatherEnabled); put("weatherCity", weatherCity); put("weatherCountry", weatherCountry)
        if (!weatherLat.isNaN()) put("weatherLat", weatherLat)
        if (!weatherLon.isNaN()) put("weatherLon", weatherLon)
        put("weatherUnit", weatherUnit); put("weatherLocationMode", weatherLocationMode); put("weatherLocatedAt", weatherLocatedAt)
        put("wallpaperId", wallpaperId); put("animatedWallpaper", animatedWallpaper)
        put("rotationEnabled", rotationEnabled); put("rotationIntervalMin", rotationIntervalMin)
        put("rotationSource", rotationSource); put("lastRotationAt", lastRotationAt)
        put("performanceMode", performanceMode)
        put("menuKeyAction", menuKeyAction); put("longPressAction", longPressAction); put("backOnHome", backOnHome)
        if (includeSecrets) { put("pinHash", pinHash); put("pinSalt", pinSalt) }
        put("lockSettings", lockSettings); put("lockCustomization", lockCustomization)
        put("protectHiddenApps", protectHiddenApps)
        put("profileName", profileName); put("profileAvatar", profileAvatar)
        put("autoCheckUpdates", autoCheckUpdates); put("lastUpdateCheck", lastUpdateCheck)
        put("safeMode", safeMode)
    }

    companion object {
        const val SCHEMA = 2
        val LANGUAGES = listOf("system", "ar", "en", "fr")
        /** Icon size is locked to Large; any stored older value ("medium", "small") loads as Large. */
        val ICON_SIZES = listOf("large")
        const val DEFAULT_WALLPAPER = "builtin:default_pyramids"
        val ROTATION_INTERVALS = intArrayOf(15, 30, 60, 180, 360, 720, 1440)

        fun fromJson(o: JSONObject): LauncherSettings {
            val d = LauncherSettings()
            fun s(k: String, def: String, allowed: Set<String>? = null): String {
                val v = o.optString(k, def)
                return if (allowed != null && v !in allowed) def else v
            }
            fun b(k: String, def: Boolean) = if (o.has(k)) o.optBoolean(k, def) else def
            fun i(k: String, def: Int, range: IntRange? = null): Int {
                val v = if (o.has(k)) o.optInt(k, def) else def
                return if (range != null) v.coerceIn(range) else v
            }
            fun l(k: String, def: Long) = if (o.has(k)) o.optLong(k, def) else def
            fun dbl(k: String) = if (o.has(k)) o.optDouble(k, Double.NaN) else Double.NaN
            // Schema 1 -> 2: Home shows three rows by default, the Icon Style option was removed
            // (shape back to the original icons) and the Small icon size no longer exists.
            val legacy = o.has("v") && o.optInt("v", 1) < 2
            return LauncherSettings(
                language = s("language", d.language, LANGUAGES.toSet()),
                interfaceMode = s("interfaceMode", d.interfaceMode, setOf("auto", "tv", "touch")),
                startPage = s("startPage", d.startPage, setOf("home", "all_apps", "favorites")),
                autoStartOnBoot = b("autoStartOnBoot", d.autoStartOnBoot),
                themeId = s("themeId", d.themeId),
                accentColor = i("accentColor", d.accentColor),
                fontFamily = s("fontFamily", d.fontFamily, setOf("tajawal", "cairo", "system")),
                fontScale = (if (o.has("fontScale")) o.optDouble("fontScale", 1.0) else 1.0).toFloat().coerceIn(0.8f, 1.4f),
                iconShape = if (legacy) "original" else s("iconShape", d.iconShape, setOf("original", "rounded", "circle", "square")),
                cardStyle = s("cardStyle", d.cardStyle, setOf("glass", "solid", "outline", "minimal")),
                useTvBanners = b("useTvBanners", d.useTvBanners),
                showAppLabels = b("showAppLabels", d.showAppLabels),
                iconSize = s("iconSize", d.iconSize, ICON_SIZES.toSet()),
                gridColumns = i("gridColumns", d.gridColumns, 0..10),
                homeRows = if (legacy) 3 else i("homeRows", d.homeRows, 1..3),
                showHomeLogo = b("showHomeLogo", d.showHomeLogo),
                showFavoritesBar = b("showFavoritesBar", d.showFavoritesBar),
                showBottomBar = b("showBottomBar", d.showBottomBar),
                showSidebarLabels = b("showSidebarLabels", d.showSidebarLabels),
                showRemoteDecor = b("showRemoteDecor", d.showRemoteDecor),
                motionEnabled = b("motionEnabled", d.motionEnabled),
                focusStyle = s("focusStyle", d.focusStyle, setOf("glow", "border", "scale")),
                focusZoom = s("focusZoom", d.focusZoom, setOf("off", "small", "medium", "large")),
                pageTransition = s("pageTransition", d.pageTransition, setOf("fade", "slide", "none")),
                pointerStyle = s("pointerStyle", d.pointerStyle, setOf("system", "neon", "large")),
                showClockDate = b("showClockDate", d.showClockDate),
                showNetworkStatus = b("showNetworkStatus", d.showNetworkStatus),
                clockFormat = s("clockFormat", d.clockFormat, setOf("system", "12", "24")),
                showHijri = b("showHijri", d.showHijri),
                bilingualDate = b("bilingualDate", d.bilingualDate),
                hijriMethod = s("hijriMethod", d.hijriMethod, setOf("umm_al_qura", "tabular")),
                hijriAdjust = i("hijriAdjust", d.hijriAdjust, -2..2),
                numerals = s("numerals", d.numerals, setOf("western", "eastern")),
                weatherEnabled = b("weatherEnabled", d.weatherEnabled),
                weatherCity = s("weatherCity", d.weatherCity),
                weatherCountry = s("weatherCountry", d.weatherCountry),
                weatherLat = dbl("weatherLat"),
                weatherLon = dbl("weatherLon"),
                weatherUnit = s("weatherUnit", d.weatherUnit, setOf("c", "f")),
                weatherLocationMode = s("weatherLocationMode", d.weatherLocationMode, setOf("auto", "manual")),
                weatherLocatedAt = l("weatherLocatedAt", d.weatherLocatedAt),
                wallpaperId = s("wallpaperId", d.wallpaperId).ifBlank { DEFAULT_WALLPAPER },
                animatedWallpaper = b("animatedWallpaper", d.animatedWallpaper),
                rotationEnabled = b("rotationEnabled", d.rotationEnabled),
                rotationIntervalMin = i("rotationIntervalMin", d.rotationIntervalMin, 5..1440),
                rotationSource = s("rotationSource", d.rotationSource, setOf("favorites", "all")),
                lastRotationAt = l("lastRotationAt", d.lastRotationAt),
                performanceMode = s("performanceMode", d.performanceMode, setOf("auto", "high", "balanced", "light")),
                menuKeyAction = s("menuKeyAction", d.menuKeyAction, setOf("context", "settings", "customize")),
                longPressAction = s("longPressAction", d.longPressAction, setOf("context", "move", "info")),
                backOnHome = s("backOnHome", d.backOnHome, setOf("focus_first", "none")),
                pinHash = s("pinHash", d.pinHash),
                pinSalt = s("pinSalt", d.pinSalt),
                lockSettings = b("lockSettings", d.lockSettings),
                lockCustomization = b("lockCustomization", d.lockCustomization),
                protectHiddenApps = b("protectHiddenApps", d.protectHiddenApps),
                profileName = s("profileName", d.profileName),
                profileAvatar = s("profileAvatar", d.profileAvatar, setOf("person", "anubis", "pyramids", "galaxy", "logo")),
                autoCheckUpdates = b("autoCheckUpdates", d.autoCheckUpdates),
                lastUpdateCheck = l("lastUpdateCheck", d.lastUpdateCheck),
                safeMode = b("safeMode", d.safeMode),
            )
        }
    }
}
