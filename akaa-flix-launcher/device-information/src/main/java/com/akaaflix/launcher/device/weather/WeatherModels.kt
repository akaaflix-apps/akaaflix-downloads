package com.akaaflix.launcher.device.weather

import org.json.JSONObject
import kotlin.math.roundToLong

/** Last successful weather result (also persisted for offline restarts). */
data class WeatherState(
    val temperatureC: Double,
    val weatherCode: Int,
    val isDay: Boolean,
    val city: String,
    val country: String,
    val fetchedAt: Long,
    val highC: Double = Double.NaN,
    val lowC: Double = Double.NaN,
) {
    val hasHighLow: Boolean get() = !highC.isNaN() && !lowC.isNaN()
}

data class GeoResult(val name: String, val country: String, val admin1: String, val latitude: Double, val longitude: Double)

/** City/country found for a coordinate (reverse geocoding). */
data class Place(val city: String, val country: String)

enum class WeatherKind { CLEAR, PARTLY_CLOUDY, CLOUDY, FOG, DRIZZLE, RAIN, SNOW, THUNDER }

/** State of the last refresh attempt (for the widget's loading / offline display). */
enum class WeatherStatus { IDLE, LOADING, OK, OFFLINE }

/** Pure JSON parsing and helpers, unit tested on the JVM with documented sample responses. */
object WeatherParsers {

    /** Open-Meteo `/v1/forecast` with `current=temperature_2m,weather_code,is_day&daily=temperature_2m_max,temperature_2m_min`. */
    fun forecast(json: String, city: String, country: String, now: Long): WeatherState {
        val o = JSONObject(json)
        val cur = o.getJSONObject("current")
        val daily = o.optJSONObject("daily")
        fun first(key: String): Double = daily?.optJSONArray(key)?.let { if (it.length() > 0 && !it.isNull(0)) it.optDouble(0, Double.NaN) else Double.NaN } ?: Double.NaN
        return WeatherState(
            temperatureC = cur.getDouble("temperature_2m"),
            weatherCode = cur.optInt("weather_code"),
            isDay = cur.optInt("is_day", 1) == 1,
            city = city, country = country, fetchedAt = now,
            highC = first("temperature_2m_max"), lowC = first("temperature_2m_min"),
        )
    }

    /** Open-Meteo geocoding `/v1/search`. */
    fun search(json: String): List<GeoResult> {
        val arr = JSONObject(json).optJSONArray("results") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val r = arr.optJSONObject(i) ?: return@mapNotNull null
            val lat = r.optDouble("latitude", Double.NaN); val lon = r.optDouble("longitude", Double.NaN)
            if (lat.isNaN() || lon.isNaN() || r.optString("name").isBlank()) null
            else GeoResult(r.optString("name"), r.optString("country"), r.optString("admin1"), lat, lon)
        }
    }

    /** BigDataCloud `reverse-geocode-client` (free client-side endpoint, no key). */
    fun bigDataCloud(json: String): Place? {
        val o = JSONObject(json)
        val city = listOf("city", "locality", "principalSubdivision").map { o.optString(it).trim() }.firstOrNull { it.isNotEmpty() } ?: return null
        return Place(city, o.optString("countryName").trim())
    }

    fun kind(code: Int): WeatherKind = when (code) {
        0 -> WeatherKind.CLEAR
        1, 2 -> WeatherKind.PARTLY_CLOUDY
        3 -> WeatherKind.CLOUDY
        45, 48 -> WeatherKind.FOG
        in 51..57 -> WeatherKind.DRIZZLE
        in 61..67, in 80..82 -> WeatherKind.RAIN
        in 71..77, 85, 86 -> WeatherKind.SNOW
        in 95..99 -> WeatherKind.THUNDER
        else -> WeatherKind.CLOUDY
    }

    fun toFahrenheit(c: Double) = c * 9.0 / 5.0 + 32.0

    /** Coordinates are rounded to 0.01° (≈1 km) before they are stored or sent to any service. */
    fun round2(v: Double): Double = (v * 100.0).roundToLong() / 100.0
}
