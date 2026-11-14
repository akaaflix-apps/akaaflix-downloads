package com.akaaflix.launcher.device.weather

import android.content.Context
import android.location.Geocoder
import com.akaaflix.launcher.core.AppConfig
import com.akaaflix.launcher.prefs.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/** Outcome of "Detect my location". */
sealed class DetectResult {
    data class Found(val place: Place, val latitude: Double, val longitude: Double) : DetectResult()
    data object NoPermission : DetectResult()
    data object NoProvider : DetectResult()
    data object Timeout : DetectResult()
    /** Coordinates were found but no city name could be resolved (e.g. offline). */
    data object NoCity : DetectResult()
}

/**
 * Weather widget data.
 *  - Provider: Open-Meteo (national weather services' models, no account, no API key needed).
 *  - City: typed by the user, or detected once from the approximate device location when the user
 *    asks for it and grants ACCESS_COARSE_LOCATION. Coordinates are rounded to ~1 km and never
 *    tracked; only the resulting city and the rounded coordinates are stored.
 *  - Requests: never on the startup path, at most every [REFRESH_INTERVAL_MS], while the home
 *    screen is visible or from the periodic [WeatherRefreshJob]. The last result is cached and shown
 *    offline for up to [MAX_DISPLAY_AGE_MS].
 */
class WeatherRepository private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val settings = SettingsRepository.get(appContext)
    private val prefs = appContext.getSharedPreferences("akaa_weather", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(loadCache())
    private val _status = MutableStateFlow(WeatherStatus.IDLE)
    private val mutex = Mutex()
    val state: StateFlow<WeatherState?> = _state.asStateFlow()
    val status: StateFlow<WeatherStatus> = _status.asStateFlow()

    /** Cached result for the configured city, if recent enough to show (also while offline). */
    fun displayable(now: Long = System.currentTimeMillis()): WeatherState? {
        val s = settings.settings.value
        return _state.value?.takeIf { now - it.fetchedAt in 0 until MAX_DISPLAY_AGE_MS && it.city == s.weatherCity }
    }

    /** Fetches new data when the cache is older than the refresh interval. Returns true on success / fresh cache. */
    suspend fun refreshIfStale(force: Boolean = false): Boolean = mutex.withLock {
        settings.loaded.first { it }
        val s = settings.settings.value
        if (!s.weatherEnabled || !s.hasWeatherLocation) return false
        val cur = _state.value
        val now = System.currentTimeMillis()
        val sameCity = cur != null && cur.city == s.weatherCity
        if (!force && sameCity && now - cur!!.fetchedAt in 0 until REFRESH_INTERVAL_MS) return true
        _status.value = WeatherStatus.LOADING
        val ok = withContext(Dispatchers.IO) {
            try {
                val key = AppConfig.weatherApiKey
                val host = if (key.isNotBlank()) "https://customer-api.open-meteo.com" else "https://api.open-meteo.com"
                val lat = WeatherParsers.round2(s.weatherLat); val lon = WeatherParsers.round2(s.weatherLon)
                val url = "$host/v1/forecast?latitude=$lat&longitude=$lon" +
                    "&current=temperature_2m,weather_code,is_day&daily=temperature_2m_max,temperature_2m_min" +
                    "&timezone=auto&forecast_days=1" + (if (key.isNotBlank()) "&apikey=" + URLEncoder.encode(key, "UTF-8") else "")
                val st = WeatherParsers.forecast(get(url), s.weatherCity, s.weatherCountry, System.currentTimeMillis())
                _state.value = st
                saveCache(st)
                true
            } catch (_: Exception) { false }
        }
        _status.value = if (ok) WeatherStatus.OK else WeatherStatus.OFFLINE
        ok
    }

    suspend fun search(query: String, language: String): List<GeoResult> = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        WeatherParsers.search(get("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=8&language=${lang(language)}&format=json"))
    }

    /** One-shot approximate location → city. Does not change settings (the caller saves the result). */
    suspend fun detect(language: String): DetectResult {
        return when (val r = ApproxLocation.get(appContext)) {
            ApproxLocation.Result.NoPermission -> DetectResult.NoPermission
            ApproxLocation.Result.NoProvider -> DetectResult.NoProvider
            ApproxLocation.Result.Timeout -> DetectResult.Timeout
            is ApproxLocation.Result.Ok -> {
                val lat = WeatherParsers.round2(r.latitude); val lon = WeatherParsers.round2(r.longitude)
                val place = reverseGeocode(lat, lon, language) ?: return DetectResult.NoCity
                DetectResult.Found(place, lat, lon)
            }
        }
    }

    /** Android's Geocoder when the device has a backend, otherwise BigDataCloud's free client-side endpoint. */
    private suspend fun reverseGeocode(lat: Double, lon: Double, language: String): Place? = withContext(Dispatchers.IO) {
        try {
            if (Geocoder.isPresent()) {
                @Suppress("DEPRECATION")
                val a = Geocoder(appContext, Locale(lang(language))).getFromLocation(lat, lon, 1)?.firstOrNull()
                val city = listOf(a?.locality, a?.subAdminArea, a?.adminArea).firstOrNull { !it.isNullOrBlank() }
                if (city != null) return@withContext Place(city, a?.countryName.orEmpty())
            }
        } catch (_: Exception) { }
        try {
            WeatherParsers.bigDataCloud(get("https://api.bigdatacloud.net/data/reverse-geocode-client?latitude=$lat&longitude=$lon&localityLanguage=${lang(language)}"))
        } catch (_: Exception) { null }
    }

    fun clear() { _state.value = null; _status.value = WeatherStatus.IDLE; prefs.edit().clear().apply() }

    private fun lang(l: String) = when (l) { "ar", "fr" -> l; else -> "en" }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 8000
        c.setRequestProperty("User-Agent", "AkaaFlixLauncher/${AppConfig.versionName} (Android)")
        try {
            if (c.responseCode !in 200..299) throw java.io.IOException("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally { c.disconnect() }
    }

    private fun loadCache(): WeatherState? {
        if (!prefs.contains("t")) return null
        return WeatherState(prefs.getFloat("t", 0f).toDouble(), prefs.getInt("c", 0), prefs.getBoolean("d", true),
            prefs.getString("city", "") ?: "", prefs.getString("country", "") ?: "", prefs.getLong("at", 0),
            if (prefs.contains("hi")) prefs.getFloat("hi", 0f).toDouble() else Double.NaN,
            if (prefs.contains("lo")) prefs.getFloat("lo", 0f).toDouble() else Double.NaN)
    }

    private fun saveCache(s: WeatherState) {
        val e = prefs.edit().putFloat("t", s.temperatureC.toFloat()).putInt("c", s.weatherCode).putBoolean("d", s.isDay)
            .putString("city", s.city).putString("country", s.country).putLong("at", s.fetchedAt)
        if (s.hasHighLow) e.putFloat("hi", s.highC.toFloat()).putFloat("lo", s.lowC.toFloat()) else e.remove("hi").remove("lo")
        e.apply()
    }

    companion object {
        const val REFRESH_INTERVAL_MS = 30 * 60 * 1000L
        const val MAX_DISPLAY_AGE_MS = 12 * 60 * 60 * 1000L
        /** Auto-detected cities are re-checked (single fix, while visible) at most this often. */
        const val RELOCATE_INTERVAL_MS = 12 * 60 * 60 * 1000L

        fun kind(code: Int): WeatherKind = WeatherParsers.kind(code)
        fun toFahrenheit(c: Double) = WeatherParsers.toFahrenheit(c)

        @Volatile private var instance: WeatherRepository? = null
        fun get(context: Context): WeatherRepository =
            instance ?: synchronized(this) { instance ?: WeatherRepository(context).also { instance = it } }
    }
}
