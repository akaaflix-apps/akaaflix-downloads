package com.akaaflix.launcher.prefs

import org.json.JSONArray
import org.json.JSONObject

/**
 * Per-app user choices, keyed by the launcher app key ("package/activity#userSerial").
 * Launch statistics are the launcher's own history (no usage-access permission required).
 */
data class AppCustomization(
    val favorites: List<String> = emptyList(),
    val favoritesInitialized: Boolean = false,
    val hidden: Set<String> = emptySet(),
    val customOrder: List<String> = emptyList(),
    val locked: Set<String> = emptySet(),
    val categories: Map<String, String> = emptyMap(),   // key -> entertainment | tools | other
    val launchCounts: Map<String, Int> = emptyMap(),
    val lastLaunched: Map<String, Long> = emptyMap(),
    val sortMode: String = "custom",                     // custom | az | za | installed | most_used | recent
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("favorites", JSONArray(favorites))
        put("favoritesInitialized", favoritesInitialized)
        put("hidden", JSONArray(hidden.toList()))
        put("customOrder", JSONArray(customOrder))
        put("locked", JSONArray(locked.toList()))
        put("categories", JSONObject(categories as Map<*, *>))
        put("launchCounts", JSONObject(launchCounts as Map<*, *>))
        put("lastLaunched", JSONObject(lastLaunched as Map<*, *>))
        put("sortMode", sortMode)
    }

    fun recordLaunch(key: String, now: Long): AppCustomization = copy(
        launchCounts = launchCounts + (key to ((launchCounts[key] ?: 0) + 1)),
        lastLaunched = lastLaunched + (key to now),
    )

    fun toggleFavorite(key: String): AppCustomization =
        if (key in favorites) copy(favorites = favorites - key) else copy(favorites = favorites + key)

    /** Removes references to apps that are no longer installed. */
    fun pruned(installed: Set<String>): AppCustomization = copy(
        favorites = favorites.filter { it in installed },
        hidden = hidden.filterTo(LinkedHashSet()) { it in installed },
        customOrder = customOrder.filter { it in installed },
        locked = locked.filterTo(LinkedHashSet()) { it in installed },
        categories = categories.filterKeys { it in installed },
        launchCounts = launchCounts.filterKeys { it in installed },
        lastLaunched = lastLaunched.filterKeys { it in installed },
    )

    companion object {
        val SORT_MODES = listOf("custom", "az", "za", "installed", "most_used", "recent")

        fun fromJson(o: JSONObject): AppCustomization {
            fun list(k: String): List<String> = o.optJSONArray(k)?.let { a -> (0 until a.length()).mapNotNull { a.optString(it).takeIf { s -> s.isNotEmpty() } } } ?: emptyList()
            fun strMap(k: String): Map<String, String> = o.optJSONObject(k)?.let { m -> m.keys().asSequence().associateWith { m.optString(it) } } ?: emptyMap()
            fun intMap(k: String): Map<String, Int> = o.optJSONObject(k)?.let { m -> m.keys().asSequence().associateWith { m.optInt(it) } } ?: emptyMap()
            fun longMap(k: String): Map<String, Long> = o.optJSONObject(k)?.let { m -> m.keys().asSequence().associateWith { m.optLong(it) } } ?: emptyMap()
            return AppCustomization(
                favorites = list("favorites").distinct(),
                favoritesInitialized = o.optBoolean("favoritesInitialized", false),
                hidden = list("hidden").toSet(),
                customOrder = list("customOrder").distinct(),
                locked = list("locked").toSet(),
                categories = strMap("categories").filterValues { it in setOf("entertainment", "tools", "other") },
                launchCounts = intMap("launchCounts"),
                lastLaunched = longMap("lastLaunched"),
                sortMode = o.optString("sortMode", "custom").takeIf { it in SORT_MODES } ?: "custom",
            )
        }
    }
}

/** User-added wallpapers and wallpaper favourites. Files live in the app's private storage. */
data class UserWallpaper(
    val id: String,
    val name: String,
    val file: String,
    val type: String,        // static | video
    val width: Int,
    val height: Int,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val addedAt: Long,
)

data class WallpaperUserData(
    val favorites: Set<String> = emptySet(),
    val userWallpapers: List<UserWallpaper> = emptyList(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("favorites", JSONArray(favorites.toList()))
        put("user", JSONArray().apply {
            userWallpapers.forEach { w ->
                put(JSONObject().apply {
                    put("id", w.id); put("name", w.name); put("file", w.file); put("type", w.type)
                    put("w", w.width); put("h", w.height); put("sw", w.sourceWidth); put("sh", w.sourceHeight); put("at", w.addedAt)
                })
            }
        })
    }

    companion object {
        fun fromJson(o: JSONObject): WallpaperUserData {
            val fav = o.optJSONArray("favorites")?.let { a -> (0 until a.length()).map { a.optString(it) }.toSet() } ?: emptySet()
            val user = o.optJSONArray("user")?.let { a ->
                (0 until a.length()).mapNotNull { i ->
                    val w = a.optJSONObject(i) ?: return@mapNotNull null
                    UserWallpaper(w.optString("id"), w.optString("name"), w.optString("file"), w.optString("type", "static"),
                        w.optInt("w"), w.optInt("h"), w.optInt("sw"), w.optInt("sh"), w.optLong("at"))
                }.filter { it.id.isNotEmpty() && it.file.isNotEmpty() }
            } ?: emptyList()
            return WallpaperUserData(fav, user)
        }
    }
}
