package com.akaaflix.launcher.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.akaaflix.launcher.apps.AppLists
import com.akaaflix.launcher.apps.AppRepository
import com.akaaflix.launcher.apps.model.AppEntry
import com.akaaflix.launcher.device.weather.WeatherRepository
import com.akaaflix.launcher.device.weather.WeatherState
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
    val favorites: List<AppEntry> = emptyList(),
    val locked: Set<String> = emptySet(),
    val settings: LauncherSettings = LauncherSettings(),
    val loaded: Boolean = false,
    val hasApps: Boolean = false,
)

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = AppRepository.get(app)
    private val prefs = SettingsRepository.get(app)
    val weatherRepo = WeatherRepository.get(app)

    val state: StateFlow<HomeState> = combine(repo.apps, prefs.apps, prefs.settings, repo.loaded) { apps, custom, s, loaded ->
        HomeState(AppLists.favorites(apps, custom), custom.locked, s, loaded, apps.isNotEmpty())
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, HomeState())

    /** Weather shown only when enabled, configured and fetched recently (online). */
    val weather: StateFlow<WeatherState?> = combine(weatherRepo.state, prefs.settings) { _, s ->
        if (s.weatherEnabled && s.hasWeatherLocation) weatherRepo.displayable() else null
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        // First run: pick initial favourites among the apps that are really installed.
        viewModelScope.launch {
            prefs.loaded.first { it }
            repo.loaded.first { it }
            val apps = repo.apps.first { it.isNotEmpty() }
            if (!prefs.apps.value.favoritesInitialized) {
                prefs.updateApps { c ->
                    if (c.favoritesInitialized) c else c.copy(favorites = AppLists.defaultFavorites(apps, DEFAULT_FAVORITES), favoritesInitialized = true)
                }
            }
        }
    }

    suspend fun refreshWeather(force: Boolean = false) = weatherRepo.refreshIfStale(force)

    companion object { const val DEFAULT_FAVORITES = 11 }
}
