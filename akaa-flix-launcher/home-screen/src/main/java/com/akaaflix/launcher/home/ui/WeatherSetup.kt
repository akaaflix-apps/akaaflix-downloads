package com.akaaflix.launcher.home.ui

import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.akaaflix.launcher.core.R
import com.akaaflix.launcher.core.ui.dialog.Dialogs
import com.akaaflix.launcher.device.weather.WeatherRepository
import com.akaaflix.launcher.prefs.SettingsRepository
import kotlinx.coroutines.launch

/** City search for the optional weather widget (Open-Meteo geocoding, no location permission). */
object WeatherSetup {
    fun chooseCity(fragment: Fragment, onChosen: (() -> Unit)? = null) {
        val ctx = fragment.requireContext()
        val prefs = SettingsRepository.get(ctx)
        val weather = WeatherRepository.get(ctx)
        Dialogs.input(ctx, ctx.getString(R.string.weather_search_title), ctx.getString(R.string.weather_search_hint), prefs.settings.value.weatherCity) { query ->
            if (query.isBlank()) return@input
            fragment.viewLifecycleOwner.lifecycleScope.launch {
                val lang = if (ctx.resources.configuration.locales[0].language == "ar") "ar" else "en"
                val results = try { weather.search(query, lang) } catch (_: Exception) { null }
                when {
                    results == null -> Toast.makeText(ctx, R.string.weather_offline, Toast.LENGTH_LONG).show()
                    results.isEmpty() -> Toast.makeText(ctx, R.string.weather_no_results, Toast.LENGTH_LONG).show()
                    else -> Dialogs.choose(ctx, ctx.getString(R.string.row_weather_city),
                        results.map { r -> listOf(r.name, r.admin1, r.country).filter { it.isNotBlank() }.distinct().joinToString(" - ") }, -1) { i ->
                        val r = results[i]
                        fragment.viewLifecycleOwner.lifecycleScope.launch {
                            prefs.update { it.copy(weatherCity = r.name, weatherCountry = r.country, weatherLat = r.latitude, weatherLon = r.longitude, weatherEnabled = true) }
                            weather.refreshIfStale(force = true)
                            onChosen?.invoke()
                        }
                    }
                }
            }
        }
    }
}
