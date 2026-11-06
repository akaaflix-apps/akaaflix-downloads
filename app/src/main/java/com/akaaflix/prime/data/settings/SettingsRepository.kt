package com.akaaflix.prime.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.akaaflix.prime.security.PinHasher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * DataStore-backed settings. The selected UI language is mirrored to SharedPreferences
 * because it must be read synchronously in `Activity.attachBaseContext`.
 */
class SettingsRepository(
    private val dataStore: DataStore<Preferences>,
    private val localeMirror: LocaleMirror? = null,
) {

    val settings: Flow<AppSettings> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it.toSettings() }
        .distinctUntilChanged()

    suspend fun current(): AppSettings = settings.first()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        var language: AppLanguage? = null
        dataStore.edit { prefs ->
            val updated = transform(prefs.toSettings())
            prefs.write(updated)
            language = updated.language
        }
        language?.let { localeMirror?.save(it.tag) }
    }

    /** Sets (or replaces) the parental PIN. */
    suspend fun setPin(pin: String): Boolean {
        if (!PinHasher.isValidPin(pin)) return false
        val hashed = PinHasher.hash(pin)
        update { it.copy(pinHash = hashed.hash, pinSalt = hashed.salt) }
        return true
    }

    suspend fun verifyPin(pin: String): Boolean {
        val s = current()
        return PinHasher.verify(pin, s.pinHash, s.pinSalt)
    }

    /** Restores defaults; the parental PIN is preserved unless [includeParental] is true. */
    suspend fun restoreDefaults(includeParental: Boolean) {
        val old = current()
        val defaults = AppSettings()
        val next = if (includeParental) defaults else defaults.copy(
            parentalEnabled = old.parentalEnabled,
            pinHash = old.pinHash,
            pinSalt = old.pinSalt,
            hideAdultContent = old.hideAdultContent,
            maxAgeRating = old.maxAgeRating,
            language = old.language,
        )
        dataStore.edit { prefs ->
            prefs.clear()
            prefs.write(next)
        }
        localeMirror?.save(next.language.tag)
    }

    /** Exported (non-secret) settings for backups. */
    suspend fun exportMap(): Map<String, String> {
        val prefs = dataStore.data.first()
        return prefs.asMap()
            .filterKeys { it.name !in setOf(K.pinHash.name, K.pinSalt.name) }
            .mapKeys { it.key.name }
            .mapValues { it.value.toString() }
    }

    suspend fun importMap(values: Map<String, String>) {
        dataStore.edit { prefs ->
            for ((name, raw) in values) {
                when (name) {
                    K.pinHash.name, K.pinSalt.name -> Unit
                    in K.booleanKeys -> raw.toBooleanStrictOrNull()?.let { prefs[booleanPreferencesKey(name)] = it }
                    in K.intKeys -> raw.toIntOrNull()?.let { prefs[intPreferencesKey(name)] = it }
                    in K.longKeys -> raw.toLongOrNull()?.let { prefs[longPreferencesKey(name)] = it }
                    in K.stringKeys -> prefs[stringPreferencesKey(name)] = raw
                }
            }
        }
        localeMirror?.save(current().language.tag)
    }

    private object K {
        val language = stringPreferencesKey("language")
        val theme = stringPreferencesKey("theme")
        val accent = stringPreferencesKey("accent")
        val timeZone = stringPreferencesKey("time_zone")
        val notifications = booleanPreferencesKey("notifications")
        val autoRefreshHours = intPreferencesKey("auto_refresh_hours")
        val interfaceMode = stringPreferencesKey("interface_mode")
        val hideLive = booleanPreferencesKey("hide_live_section")
        val hideMovies = booleanPreferencesKey("hide_movies_section")
        val hideSeries = booleanPreferencesKey("hide_series_section")
        val defaultPlayer = stringPreferencesKey("default_player")
        val decoder = stringPreferencesKey("decoder")
        val audioOutput = stringPreferencesKey("audio_output")
        val defaultAspect = stringPreferencesKey("default_aspect")
        val autoPlay = booleanPreferencesKey("auto_play")
        val autoNext = booleanPreferencesKey("auto_next_episode")
        val resume = booleanPreferencesKey("resume_playback")
        val buffer = stringPreferencesKey("buffer_profile")
        val dataSaver = booleanPreferencesKey("data_saver")
        val audioLang = stringPreferencesKey("preferred_audio_language")
        val rememberChannel = booleanPreferencesKey("remember_last_channel")
        val livePreview = booleanPreferencesKey("live_preview")
        val liveFormat = stringPreferencesKey("live_format")
        val subEnabled = booleanPreferencesKey("subtitles_enabled")
        val subLang = stringPreferencesKey("subtitle_language")
        val subSource = stringPreferencesKey("subtitle_source")
        val subEncoding = stringPreferencesKey("subtitle_encoding")
        val subScale = intPreferencesKey("subtitle_scale")
        val subTextColor = longPreferencesKey("subtitle_text_color")
        val subBgColor = longPreferencesKey("subtitle_bg_color")
        val subBgOpacity = intPreferencesKey("subtitle_bg_opacity")
        val subPosition = stringPreferencesKey("subtitle_position")
        val subEdge = stringPreferencesKey("subtitle_edge")
        val subDelay = longPreferencesKey("subtitle_delay_ms")
        val epgEnabled = booleanPreferencesKey("epg_enabled")
        val epgShift = intPreferencesKey("epg_time_shift")
        val epgCacheHours = intPreferencesKey("epg_cache_hours")
        val parental = booleanPreferencesKey("parental_enabled")
        val pinHash = stringPreferencesKey("pin_hash")
        val pinSalt = stringPreferencesKey("pin_salt")
        val hideAdult = booleanPreferencesKey("hide_adult")
        val maxAge = intPreferencesKey("max_age_rating")
        val saveSearch = booleanPreferencesKey("save_search_history")

        val booleanKeys = setOf(notifications, hideLive, hideMovies, hideSeries, autoPlay, autoNext, resume, dataSaver, rememberChannel, livePreview, subEnabled, epgEnabled, parental, hideAdult, saveSearch).map { it.name }.toSet()
        val intKeys = setOf(autoRefreshHours, subScale, subBgOpacity, epgShift, epgCacheHours, maxAge).map { it.name }.toSet()
        val longKeys = setOf(subTextColor, subBgColor, subDelay).map { it.name }.toSet()
        val stringKeys = setOf(language, theme, accent, timeZone, interfaceMode, defaultPlayer, decoder, audioOutput, defaultAspect, buffer, audioLang, liveFormat, subLang, subSource, subEncoding, subPosition, subEdge).map { it.name }.toSet()
    }

    private inline fun <reified E : Enum<E>> String?.toEnum(default: E): E =
        this?.let { v -> enumValues<E>().firstOrNull { it.name == v } } ?: default

    private fun Preferences.toSettings(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            language = this[K.language].toEnum(d.language),
            theme = this[K.theme].toEnum(d.theme),
            accent = this[K.accent].toEnum(d.accent),
            timeZone = this[K.timeZone] ?: d.timeZone,
            notificationsEnabled = this[K.notifications] ?: d.notificationsEnabled,
            autoRefreshHours = this[K.autoRefreshHours] ?: d.autoRefreshHours,
            interfaceMode = this[K.interfaceMode].toEnum(d.interfaceMode),
            hideLiveSection = this[K.hideLive] ?: d.hideLiveSection,
            hideMoviesSection = this[K.hideMovies] ?: d.hideMoviesSection,
            hideSeriesSection = this[K.hideSeries] ?: d.hideSeriesSection,
            defaultPlayer = this[K.defaultPlayer].toEnum(d.defaultPlayer),
            decoder = this[K.decoder].toEnum(d.decoder),
            audioOutput = this[K.audioOutput].toEnum(d.audioOutput),
            defaultAspect = this[K.defaultAspect].toEnum(d.defaultAspect),
            autoPlay = this[K.autoPlay] ?: d.autoPlay,
            autoNextEpisode = this[K.autoNext] ?: d.autoNextEpisode,
            resumePlayback = this[K.resume] ?: d.resumePlayback,
            bufferProfile = this[K.buffer].toEnum(d.bufferProfile),
            dataSaver = this[K.dataSaver] ?: d.dataSaver,
            preferredAudioLanguage = this[K.audioLang] ?: d.preferredAudioLanguage,
            rememberLastChannel = this[K.rememberChannel] ?: d.rememberLastChannel,
            livePreview = this[K.livePreview] ?: d.livePreview,
            liveFormat = this[K.liveFormat].toEnum(d.liveFormat),
            subtitlesEnabled = this[K.subEnabled] ?: d.subtitlesEnabled,
            subtitleLanguage = this[K.subLang] ?: d.subtitleLanguage,
            subtitleSource = this[K.subSource].toEnum(d.subtitleSource),
            subtitleEncoding = this[K.subEncoding] ?: d.subtitleEncoding,
            subtitleScale = this[K.subScale] ?: d.subtitleScale,
            subtitleTextColor = this[K.subTextColor] ?: d.subtitleTextColor,
            subtitleBackgroundColor = this[K.subBgColor] ?: d.subtitleBackgroundColor,
            subtitleBackgroundOpacity = this[K.subBgOpacity] ?: d.subtitleBackgroundOpacity,
            subtitlePosition = this[K.subPosition].toEnum(d.subtitlePosition),
            subtitleEdge = this[K.subEdge].toEnum(d.subtitleEdge),
            subtitleDelayMs = this[K.subDelay] ?: d.subtitleDelayMs,
            epgEnabled = this[K.epgEnabled] ?: d.epgEnabled,
            epgTimeShiftHours = this[K.epgShift] ?: d.epgTimeShiftHours,
            epgCacheHours = this[K.epgCacheHours] ?: d.epgCacheHours,
            parentalEnabled = this[K.parental] ?: d.parentalEnabled,
            pinHash = this[K.pinHash] ?: d.pinHash,
            pinSalt = this[K.pinSalt] ?: d.pinSalt,
            hideAdultContent = this[K.hideAdult] ?: d.hideAdultContent,
            maxAgeRating = this[K.maxAge] ?: d.maxAgeRating,
            saveSearchHistory = this[K.saveSearch] ?: d.saveSearchHistory,
        )
    }

    private fun MutablePreferences.write(s: AppSettings) {
        this[K.language] = s.language.name
        this[K.theme] = s.theme.name
        this[K.accent] = s.accent.name
        this[K.timeZone] = s.timeZone
        this[K.notifications] = s.notificationsEnabled
        this[K.autoRefreshHours] = s.autoRefreshHours
        this[K.interfaceMode] = s.interfaceMode.name
        this[K.hideLive] = s.hideLiveSection
        this[K.hideMovies] = s.hideMoviesSection
        this[K.hideSeries] = s.hideSeriesSection
        this[K.defaultPlayer] = s.defaultPlayer.name
        this[K.decoder] = s.decoder.name
        this[K.audioOutput] = s.audioOutput.name
        this[K.defaultAspect] = s.defaultAspect.name
        this[K.autoPlay] = s.autoPlay
        this[K.autoNext] = s.autoNextEpisode
        this[K.resume] = s.resumePlayback
        this[K.buffer] = s.bufferProfile.name
        this[K.dataSaver] = s.dataSaver
        this[K.audioLang] = s.preferredAudioLanguage
        this[K.rememberChannel] = s.rememberLastChannel
        this[K.livePreview] = s.livePreview
        this[K.liveFormat] = s.liveFormat.name
        this[K.subEnabled] = s.subtitlesEnabled
        this[K.subLang] = s.subtitleLanguage
        this[K.subSource] = s.subtitleSource.name
        this[K.subEncoding] = s.subtitleEncoding
        this[K.subScale] = s.subtitleScale.coerceIn(50, 250)
        this[K.subTextColor] = s.subtitleTextColor
        this[K.subBgColor] = s.subtitleBackgroundColor
        this[K.subBgOpacity] = s.subtitleBackgroundOpacity.coerceIn(0, 100)
        this[K.subPosition] = s.subtitlePosition.name
        this[K.subEdge] = s.subtitleEdge.name
        this[K.subDelay] = s.subtitleDelayMs.coerceIn(-30_000, 30_000)
        this[K.epgEnabled] = s.epgEnabled
        this[K.epgShift] = s.epgTimeShiftHours.coerceIn(-12, 12)
        this[K.epgCacheHours] = s.epgCacheHours.coerceIn(1, 48)
        this[K.parental] = s.parentalEnabled
        this[K.pinHash] = s.pinHash
        this[K.pinSalt] = s.pinSalt
        this[K.hideAdult] = s.hideAdultContent
        this[K.maxAge] = s.maxAgeRating
        this[K.saveSearch] = s.saveSearchHistory
    }
}

/** Synchronous mirror of the selected language for `attachBaseContext`. */
class LocaleMirror(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("akaa_locale", Context.MODE_PRIVATE)
    fun save(tag: String) = prefs.edit().putString(KEY, tag).apply()
    fun read(): String = prefs.getString(KEY, null) ?: AppLanguage.ARABIC.tag

    companion object {
        private const val KEY = "language_tag"
        fun read(context: Context): String =
            context.getSharedPreferences("akaa_locale", Context.MODE_PRIVATE).getString(KEY, null) ?: AppLanguage.ARABIC.tag
    }
}
