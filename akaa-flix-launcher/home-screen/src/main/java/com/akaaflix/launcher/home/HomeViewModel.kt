package com.akaaflix.launcher.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.akaaflix.launcher.apps.AppLists
import com.akaaflix.launcher.apps.AppRepository
import com.akaaflix.launcher.apps.model.AppEntry
import com.akaaflix.launcher.device.weather.WeatherRepository
import com.akaaflix.launcher.device.weather.WeatherState
import com.akaaflix.launcher.device.weather.WeatherStatus
import com.akaaflix.launcher.prefs.LauncherSettings
import com.akaaflix.launcher.prefs.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeState(
    val homeApps: List<AppEntry> = emptyList(),
    val locked: Set<String> = emptySet(),
    val settings: LauncherSettings = LauncherSettings(),
    val loaded: Boolean = false,
    val hasApps: Boolean = false,
)

/** What the weather widget shows. */
sealed class WeatherUi {
    data object Hidden : WeatherUi()
    /** Enabled but no city yet: "Set up weather" (opens detect / manual choice). */
    data object Setup : WeatherUi()
    data class Loading(val city: String) : WeatherUi()
    data class Data(val state: WeatherState, val stale: Boolean) : WeatherUi()
    /** City set, nothing cached and the last request failed (offline). */
    data class Unavailable(val city: String) : WeatherUi()
}

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = AppRepository.get(app)
    private val prefs = SettingsRepository.get(app)
    val weatherRepo = WeatherRepository.get(app)

    val state: StateFlow<HomeState> = combine(repo.apps, prefs.apps, prefs.settings, repo.loaded) { apps, custom, s, loaded ->
        HomeState(AppLists.homeApps(apps, custom), custom.locked, s, loaded, apps.isNotEmpty())
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, HomeState())

    val weather: StateFlow<WeatherUi> = combine(weatherRepo.state, weatherRepo.status, prefs.settings) { _, status, s ->
        when {
            !s.weatherEnabled -> WeatherUi.Hidden
            !s.hasWeatherLocation -> WeatherUi.Setup
            else -> {
                val data = weatherRepo.displayable()
                when {
                    data != null -> WeatherUi.Data(data, status == WeatherStatus.OFFLINE &&
                        System.currentTimeMillis() - data.fetchedAt > WeatherRepository.REFRESH_INTERVAL_MS)
                    status == WeatherStatus.OFFLINE -> WeatherUi.Unavailable(s.weatherCity)
                    else -> WeatherUi.Loading(s.weatherCity)
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, WeatherUi.Hidden)

    init {
        // First run only: place some of the apps that are really installed on the Home screen.
        // Favorites are never filled automatically.
        viewModelScope.launch {
            prefs.loaded.first { it }
            repo.loaded.first { it }
            val apps = repo.apps.first { it.isNotEmpty() }
            if (!prefs.apps.value.homeInitialized) {
                prefs.updateApps { c ->
                    if (c.homeInitialized) c else c.copy(homeApps = AppLists.defaultHomeApps(apps, DEFAULT_HOME_APPS), homeInitialized = true)
                }
            }
        }
    }

    suspend fun refreshWeather(force: Boolean = false) = weatherRepo.refreshIfStale(force)

    companion object { const val DEFAULT_HOME_APPS = 17 }
}
