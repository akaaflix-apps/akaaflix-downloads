package com.akaaflix.launcher.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

private val Context.launcherStore: DataStore<Preferences> by preferencesDataStore(name = "akaa_launcher")

/**
 * Single persistence entry point (Jetpack DataStore). Exposes hot StateFlows that the UI observes;
 * writes are atomic read-modify-write transactions.
 */
class SettingsRepository private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val store = appContext.launcherStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _settings = MutableStateFlow(LauncherSettings())
    private val _apps = MutableStateFlow(AppCustomization())
    private val _wallpapers = MutableStateFlow(WallpaperUserData())
    private val _loaded = MutableStateFlow(false)

    val settings: StateFlow<LauncherSettings> = _settings.asStateFlow()
    val apps: StateFlow<AppCustomization> = _apps.asStateFlow()
    val wallpapers: StateFlow<WallpaperUserData> = _wallpapers.asStateFlow()
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    init {
        scope.launch {
            store.data.collect { p ->
                _settings.value = parse(p[KEY_SETTINGS]) { LauncherSettings.fromJson(it) } ?: LauncherSettings()
                _apps.value = parse(p[KEY_APPS]) { AppCustomization.fromJson(it) } ?: AppCustomization()
                _wallpapers.value = parse(p[KEY_WALLPAPERS]) { WallpaperUserData.fromJson(it) } ?: WallpaperUserData()
                _loaded.value = true
                BootPrefs.write(appContext, _settings.value.autoStartOnBoot)
            }
        }
    }

    /** Blocks briefly (first launch only) so the activity can theme itself before inflating. */
    fun awaitLoaded(timeoutMs: Long = 1500): LauncherSettings {
        if (!_loaded.value) runBlocking { withTimeoutOrNull(timeoutMs) { _loaded.first { it } } }
        return _settings.value
    }

    suspend fun update(transform: (LauncherSettings) -> LauncherSettings) {
        store.edit { p ->
            val cur = parse(p[KEY_SETTINGS]) { LauncherSettings.fromJson(it) } ?: LauncherSettings()
            val next = transform(cur)
            if (next != cur) p[KEY_SETTINGS] = next.toJson().toString()
        }
    }

    suspend fun updateApps(transform: (AppCustomization) -> AppCustomization) {
        store.edit { p ->
            val cur = parse(p[KEY_APPS]) { AppCustomization.fromJson(it) } ?: AppCustomization()
            val next = transform(cur)
            if (next != cur) p[KEY_APPS] = next.toJson().toString()
        }
    }

    suspend fun updateWallpapers(transform: (WallpaperUserData) -> WallpaperUserData) {
        store.edit { p ->
            val cur = parse(p[KEY_WALLPAPERS]) { WallpaperUserData.fromJson(it) } ?: WallpaperUserData()
            val next = transform(cur)
            if (next != cur) p[KEY_WALLPAPERS] = next.toJson().toString()
        }
    }

    fun edit(transform: (LauncherSettings) -> LauncherSettings) { scope.launch { update(transform) } }
    fun editApps(transform: (AppCustomization) -> AppCustomization) { scope.launch { updateApps(transform) } }
    fun editWallpapers(transform: (WallpaperUserData) -> WallpaperUserData) { scope.launch { updateWallpapers(transform) } }

    /** Restores defaults but keeps parental PIN, profile, safe-mode and weather location. */
    suspend fun resetSettings() = update { cur ->
        LauncherSettings().copy(
            pinHash = cur.pinHash, pinSalt = cur.pinSalt, profileName = cur.profileName,
            profileAvatar = cur.profileAvatar, weatherCity = cur.weatherCity, weatherCountry = cur.weatherCountry,
            weatherLat = cur.weatherLat, weatherLon = cur.weatherLon, language = cur.language,
            weatherLocationMode = cur.weatherLocationMode, weatherLocatedAt = cur.weatherLocatedAt,
        )
    }

    fun exportBackup(): String = BackupCodec.encode(_settings.value, _apps.value, _wallpapers.value)

    /** Returns false when the text is not a launcher backup. PIN is never restored from a file. */
    suspend fun importBackup(json: String): Boolean {
        val decoded = BackupCodec.decode(json) ?: return false
        update { cur -> decoded.settings.copy(pinHash = cur.pinHash, pinSalt = cur.pinSalt, safeMode = false) }
        updateApps { decoded.apps }
        updateWallpapers { cur -> cur.copy(favorites = decoded.wallpaperFavorites) }
        return true
    }

    private inline fun <T> parse(text: String?, block: (JSONObject) -> T): T? =
        if (text.isNullOrEmpty()) null else try { block(JSONObject(text)) } catch (_: Exception) { null }

    companion object {
        private val KEY_SETTINGS = stringPreferencesKey("settings_json")
        private val KEY_APPS = stringPreferencesKey("apps_json")
        private val KEY_WALLPAPERS = stringPreferencesKey("wallpapers_json")

        @Volatile private var instance: SettingsRepository? = null
        fun get(context: Context): SettingsRepository =
            instance ?: synchronized(this) { instance ?: SettingsRepository(context).also { instance = it } }
    }
}

/** Backup file format (versioned JSON). */
object BackupCodec {
    const val FORMAT = "akaa-flix-launcher-backup"
    const val VERSION = 1

    data class Decoded(val settings: LauncherSettings, val apps: AppCustomization, val wallpaperFavorites: Set<String>)

    fun encode(s: LauncherSettings, a: AppCustomization, w: WallpaperUserData): String = JSONObject().apply {
        put("format", FORMAT)
        put("version", VERSION)
        put("createdAt", System.currentTimeMillis())
        put("settings", s.toJson(includeSecrets = false))
        put("apps", a.toJson())
        put("wallpaperFavorites", org.json.JSONArray(w.favorites.toList()))
    }.toString(2)

    fun decode(text: String): Decoded? = try {
        val o = JSONObject(text)
        if (o.optString("format") != FORMAT) null else {
            val fav = o.optJSONArray("wallpaperFavorites")?.let { a -> (0 until a.length()).map { a.optString(it) }.toSet() } ?: emptySet()
            Decoded(
                LauncherSettings.fromJson(o.optJSONObject("settings") ?: JSONObject()),
                AppCustomization.fromJson(o.optJSONObject("apps") ?: JSONObject()),
                fav,
            )
        }
    } catch (_: Exception) { null }
}

/** Tiny synchronous mirror read by the boot receiver (DataStore is asynchronous). */
object BootPrefs {
    private const val FILE = "akaa_boot"
    fun write(context: Context, autoStart: Boolean) {
        val p = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        if (p.getBoolean("autostart", false) != autoStart) p.edit().putBoolean("autostart", autoStart).apply()
    }
    fun autoStart(context: Context): Boolean = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean("autostart", false)
}
