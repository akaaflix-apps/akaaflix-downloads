package com.akaaflix.launcher.home.ui

import android.content.Context
import android.widget.Toast
import androidx.core.os.ConfigurationCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.akaaflix.launcher.core.R
import com.akaaflix.launcher.core.nav.launcherHost
import com.akaaflix.launcher.core.ui.dialog.Dialogs
import com.akaaflix.launcher.core.ui.dialog.LauncherDialog
import com.akaaflix.launcher.core.ui.rows.SettingRow
import com.akaaflix.launcher.core.util.str
import com.akaaflix.launcher.device.weather.ApproxLocation
import com.akaaflix.launcher.device.weather.DetectResult
import com.akaaflix.launcher.device.weather.WeatherRefreshJob
import com.akaaflix.launcher.device.weather.WeatherRepository
import com.akaaflix.launcher.prefs.SettingsRepository
import kotlinx.coroutines.launch

/**
 * Weather city setup shared by Home, Customization and Settings:
 *  - "Detect my location": explains why, asks for APPROXIMATE location only, takes one fix and
 *    resolves the city (no tracking). Denied / unavailable -> manual city search.
 *  - "Choose city manually": Open-Meteo city search (no permission).
 */
object WeatherSetup {

    private fun language(c: Context): String = ConfigurationCompat.getLocales(c.resources.configuration)[0]?.language ?: "en"

    /** Weather panel (opened from the Home widget). */
    fun open(fragment: Fragment) {
        val ctx = fragment.requireContext()
        val prefs = SettingsRepository.get(ctx)
        val repo = WeatherRepository.get(ctx)
        var dialog: LauncherDialog? = null
        fun close() = dialog?.dialog?.dismiss()
        val s = prefs.settings.value
        val units = listOf("c", "f")
        val rows = mutableListOf<SettingRow>()
        if (s.hasWeatherLocation) {
            rows += SettingRow.Info("city", ctx.getString(R.string.row_weather_city),
                listOf(s.weatherCity, s.weatherCountry).filter { it.isNotBlank() }.joinToString(" - ") + "\n" +
                    ctx.getString(if (s.weatherLocationMode == "auto") R.string.weather_mode_auto else R.string.weather_mode_manual), R.drawable.ic_location)
            rows += SettingRow.Action("refresh", ctx.getString(R.string.weather_refresh), icon = R.drawable.ic_refresh, showChevron = false) {
                close()
                fragment.viewLifecycleOwner.lifecycleScope.launch {
                    val ok = repo.refreshIfStale(force = true)
                    Toast.makeText(ctx, if (ok) R.string.weather_refreshed else R.string.weather_refresh_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
        rows += SettingRow.Action("detect", ctx.getString(R.string.weather_detect), ctx.getString(R.string.weather_detect_sub), R.drawable.ic_location) { close(); detect(fragment) }
        rows += SettingRow.Action("manual", ctx.getString(R.string.weather_manual), icon = R.drawable.ic_search) { close(); chooseCity(fragment) }
        rows += SettingRow.Choice("unit", ctx.getString(R.string.row_weather_unit), icon = R.drawable.ic_sun_outline,
            options = listOf(ctx.getString(R.string.unit_c), ctx.getString(R.string.unit_f)), selected = units.indexOf(s.weatherUnit).coerceAtLeast(0)) { i ->
            prefs.edit { it.copy(weatherUnit = units[i]) }
        }
        rows += SettingRow.Toggle("show", ctx.getString(R.string.row_weather), ctx.getString(R.string.row_weather_sub), R.drawable.ic_weather_partly_day, s.weatherEnabled) { v ->
            prefs.edit { it.copy(weatherEnabled = v) }
        }
        dialog = Dialogs.rows(ctx, ctx.getString(R.string.weather_setup_title), rows, icon = R.drawable.ic_weather_partly_day, widthDp = 520) { d, _ ->
            d.button(R.string.close, primary = true)
        }
    }

    /** Explains the permission, requests it and detects the city once. */
    fun detect(fragment: Fragment, onDone: (() -> Unit)? = null) {
        val ctx = fragment.requireContext()
        val go = { runDetect(fragment, onDone) }
        if (ApproxLocation.hasPermission(ctx)) { go(); return }
        LauncherDialog(ctx).title(R.string.weather_detect).icon(R.drawable.ic_location)
            .message(R.string.weather_location_rationale)
            .button(R.string.weather_manual) { chooseCity(fragment, onDone) }
            .button(R.string.weather_allow, primary = true) {
                val host = fragment.launcherHost
                if (host == null) { chooseCity(fragment, onDone); return@button }
                host.requestPermission(ApproxLocation.PERMISSION) { granted ->
                    if (granted && fragment.isAdded) go()
                    else if (fragment.isAdded) { Toast.makeText(ctx, R.string.weather_permission_denied, Toast.LENGTH_LONG).show(); chooseCity(fragment, onDone) }
                }
            }
            .show()
    }

    private fun runDetect(fragment: Fragment, onDone: (() -> Unit)?) {
        val ctx = fragment.requireContext()
        val prefs = SettingsRepository.get(ctx)
        val repo = WeatherRepository.get(ctx)
        Toast.makeText(ctx, R.string.weather_detecting, Toast.LENGTH_SHORT).show()
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            when (val r = repo.detect(language(ctx))) {
                is DetectResult.Found -> {
                    prefs.update {
                        it.copy(weatherCity = r.place.city, weatherCountry = r.place.country, weatherLat = r.latitude, weatherLon = r.longitude,
                            weatherEnabled = true, weatherLocationMode = "auto", weatherLocatedAt = System.currentTimeMillis())
                    }
                    WeatherRefreshJob.sync(ctx, true)
                    Toast.makeText(ctx, ctx.str(R.string.weather_detected_fmt, r.place.city), Toast.LENGTH_LONG).show()
                    repo.refreshIfStale(force = true)
                    onDone?.invoke()
                }
                else -> {
                    if (!fragment.isAdded) return@launch
                    val msg = when (r) {
                        DetectResult.NoPermission -> R.string.weather_permission_denied
                        DetectResult.NoProvider -> R.string.weather_no_provider
                        DetectResult.Timeout -> R.string.weather_timeout
                        else -> R.string.weather_no_city
                    }
                    LauncherDialog(ctx).title(R.string.weather_detect).icon(R.drawable.ic_location).message(msg)
                        .button(R.string.cancel)
                        .button(R.string.weather_manual, primary = true) { chooseCity(fragment, onDone) }
                        .show()
                }
            }
        }
    }

    /** City search (no location permission). */
    fun chooseCity(fragment: Fragment, onChosen: (() -> Unit)? = null) {
        val ctx = fragment.requireContext()
        val prefs = SettingsRepository.get(ctx)
        val weather = WeatherRepository.get(ctx)
        val initial = if (prefs.settings.value.weatherLocationMode == "manual") prefs.settings.value.weatherCity else ""
        Dialogs.input(ctx, ctx.getString(R.string.weather_search_title), ctx.getString(R.string.weather_search_hint), initial) { query ->
            if (query.isBlank()) return@input
            fragment.viewLifecycleOwner.lifecycleScope.launch {
                val results = try { weather.search(query, language(ctx)) } catch (_: Exception) { null }
                when {
                    results == null -> Toast.makeText(ctx, R.string.weather_offline, Toast.LENGTH_LONG).show()
                    results.isEmpty() -> Toast.makeText(ctx, R.string.weather_no_results, Toast.LENGTH_LONG).show()
                    else -> Dialogs.choose(ctx, ctx.getString(R.string.row_weather_city),
                        results.map { r -> listOf(r.name, r.admin1, r.country).filter { it.isNotBlank() }.distinct().joinToString(" - ") }, -1) { i ->
                        val r = results[i]
                        fragment.viewLifecycleOwner.lifecycleScope.launch {
                            prefs.update {
                                it.copy(weatherCity = r.name, weatherCountry = r.country, weatherLat = r.latitude, weatherLon = r.longitude,
                                    weatherEnabled = true, weatherLocationMode = "manual", weatherLocatedAt = 0L)
                            }
                            WeatherRefreshJob.sync(ctx, true)
                            weather.refreshIfStale(force = true)
                            onChosen?.invoke()
                        }
                    }
                }
            }
        }
    }

    /**
     * Auto-detected cities are re-checked at most every 12 h while the home screen is visible
     * (one fix, silently). If the permission was revoked the city simply stays as it is.
     */
    suspend fun maybeRelocate(context: Context) {
        val prefs = SettingsRepository.get(context)
        val s = prefs.settings.value
        if (!s.weatherEnabled || s.weatherLocationMode != "auto" || !ApproxLocation.hasPermission(context)) return
        if (System.currentTimeMillis() - s.weatherLocatedAt < WeatherRepository.RELOCATE_INTERVAL_MS) return
        val r = WeatherRepository.get(context).detect(language(context))
        if (r is DetectResult.Found) {
            prefs.update {
                if (it.weatherLocationMode != "auto") it
                else it.copy(weatherCity = r.place.city, weatherCountry = r.place.country, weatherLat = r.latitude, weatherLon = r.longitude,
                    weatherLocatedAt = System.currentTimeMillis())
            }
        } else prefs.update { it.copy(weatherLocatedAt = System.currentTimeMillis()) }
    }
}
