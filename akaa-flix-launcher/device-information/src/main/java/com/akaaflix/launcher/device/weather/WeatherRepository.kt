package com.akaaflix.launcher.device.weather

import android.content.Context
import com.akaaflix.launcher.core.AppConfig
import com.akaaflix.launcher.prefs.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class WeatherState(
    val temperatureC: Double,
    val weatherCode: Int,
    val isDay: Boolean,
    val city: String,
    val country: String,
    val fetchedAt: Long,
)

data class GeoResult(val name: String, val country: String, val admin1: String, val latitude: Double, val longitude: Double)

enum class WeatherKind { CLEAR, PARTLY_CLOUDY, CLOUDY, FOG, RAIN, SNOW, THUNDER }

/**
 * Optional weather widget data from Open-Meteo (no account, no API key, no location permission:
 * the user types a city). Requests happen only when the widget is enabled, at most every 30 min,
 * and only while the home screen is visible. Last result is cached for offline restarts.
 */
class WeatherRepository private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val settings = SettingsRepository.get(appContext)
    private val prefs = appContext.getSharedPreferences("akaa_weather", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(loadCache())
    val state: StateFlow<WeatherState?> = _state.asStateFlow()

    /** Fresh enough to display (widget is hidden when stale/offline as required). */
    fun displayable(now: Long = System.currentTimeMillis()): WeatherState? =
        _state.value?.takeIf { now - it.fetchedAt < MAX_DISPLAY_AGE_MS }

    suspend fun refreshIfStale(force: Boolean = false): Boolean {
        val s = settings.settings.value
        if (!s.weatherEnabled || !s.hasWeatherLocation) return false
        val cur = _state.value
        val now = System.currentTimeMillis()
        val sameCity = cur != null && cur.city == s.weatherCity
        if (!force && sameCity && now - cur!!.fetchedAt < REFRESH_INTERVAL_MS) return true
        return withContext(Dispatchers.IO) {
            try {
                val key = AppConfig.weatherApiKey
                val host = if (key.isNotBlank()) "https://customer-api.open-meteo.com" else "https://api.open-meteo.com"
                val url = "$host/v1/forecast?latitude=${s.weatherLat}&longitude=${s.weatherLon}" +
                    "&current=temperature_2m,weather_code,is_day&timezone=auto" + (if (key.isNotBlank()) "&apikey=$key" else "")
                val o = JSONObject(get(url)).getJSONObject("current")
                val st = WeatherState(o.getDouble("temperature_2m"), o.optInt("weather_code"), o.optInt("is_day", 1) == 1,
                    s.weatherCity, s.weatherCountry, System.currentTimeMillis())
                _state.value = st
                saveCache(st)
                true
            } catch (_: Exception) { false }
        }
    }

    suspend fun search(query: String, language: String): List<GeoResult> = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val lang = if (language == "ar") "ar" else "en"
        val o = JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=8&language=$lang&format=json"))
        val arr = o.optJSONArray("results") ?: return@withContext emptyList()
        (0 until arr.length()).mapNotNull { i ->
            val r = arr.optJSONObject(i) ?: return@mapNotNull null
            GeoResult(r.optString("name"), r.optString("country"), r.optString("admin1"), r.optDouble("latitude"), r.optDouble("longitude"))
        }
    }

    fun clear() { _state.value = null; prefs.edit().clear().apply() }

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
            prefs.getString("city", "") ?: "", prefs.getString("country", "") ?: "", prefs.getLong("at", 0))
    }

    private fun saveCache(s: WeatherState) {
        prefs.edit().putFloat("t", s.temperatureC.toFloat()).putInt("c", s.weatherCode).putBoolean("d", s.isDay)
            .putString("city", s.city).putString("country", s.country).putLong("at", s.fetchedAt).apply()
    }

    companion object {
        const val REFRESH_INTERVAL_MS = 30 * 60 * 1000L
        const val MAX_DISPLAY_AGE_MS = 3 * 60 * 60 * 1000L

        fun kind(code: Int): WeatherKind = when (code) {
            0 -> WeatherKind.CLEAR
            1, 2 -> WeatherKind.PARTLY_CLOUDY
            3 -> WeatherKind.CLOUDY
            45, 48 -> WeatherKind.FOG
            in 51..67, in 80..82 -> WeatherKind.RAIN
            in 71..77, 85, 86 -> WeatherKind.SNOW
            in 95..99 -> WeatherKind.THUNDER
            else -> WeatherKind.CLOUDY
        }

        fun toFahrenheit(c: Double) = c * 9.0 / 5.0 + 32.0

        @Volatile private var instance: WeatherRepository? = null
        fun get(context: Context): WeatherRepository =
            instance ?: synchronized(this) { instance ?: WeatherRepository(context).also { instance = it } }
    }
}
