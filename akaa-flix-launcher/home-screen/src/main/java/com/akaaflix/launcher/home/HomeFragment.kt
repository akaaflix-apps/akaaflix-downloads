package com.akaaflix.launcher.home

import com.akaaflix.launcher.core.util.str

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.akaaflix.launcher.apps.ui.AppActions
import com.akaaflix.launcher.apps.ui.AppGridAdapter
import com.akaaflix.launcher.apps.ui.AppGridController
import com.akaaflix.launcher.apps.ui.CardConfig
import com.akaaflix.launcher.apps.ui.GridItem
import com.akaaflix.launcher.apps.ui.GridSizing
import com.akaaflix.launcher.apps.ui.GridSpec
import com.akaaflix.launcher.core.R
import com.akaaflix.launcher.core.nav.LauncherPage
import com.akaaflix.launcher.core.nav.MenuKeyHandler
import com.akaaflix.launcher.core.nav.NavArgs
import com.akaaflix.launcher.core.nav.PageHeader
import com.akaaflix.launcher.core.nav.launcherHost
import com.akaaflix.launcher.core.ui.FocusEffects
import com.akaaflix.launcher.core.ui.GlassDrawable
import com.akaaflix.launcher.core.ui.LauncherUi
import com.akaaflix.launcher.core.ui.dpi
import com.akaaflix.launcher.core.ui.glass
import com.akaaflix.launcher.core.ui.tint
import com.akaaflix.launcher.device.weather.WeatherKind
import com.akaaflix.launcher.device.weather.WeatherRepository
import com.akaaflix.launcher.device.weather.WeatherState
import com.akaaflix.launcher.home.databinding.FragmentHomeBinding
import com.akaaflix.launcher.home.ui.HomeDialogs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** The approved Home screen: logo, favourite apps row(s), weather, quick action bar. */
class HomeFragment : Fragment(), MenuKeyHandler {
    private var _b: FragmentHomeBinding? = null
    private val b get() = _b!!
    private val vm: HomeViewModel by viewModels()
    private lateinit var grid: AppGridController
    private var portrait = false
    private var lastRows = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _b = FragmentHomeBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        portrait = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        b.weatherWidget.glass(GlassDrawable.Variant.CHIP, 28f)
        listOf(b.searchBtn, b.micBtn, b.appsBtn, b.bellBtn, b.infoBtn).forEach {
            it.glass(GlassDrawable.Variant.CIRCLE, 22f, 3f)
            FocusEffects.attach(it, 1.12f)
        }
        b.bellBadge.glass(GlassDrawable.Variant.PRIMARY, 8f)

        grid = AppGridController(this, b.grid, null, { w, h -> sizing(w, h) }, object : AppGridAdapter.Callbacks {
            override fun onItemClick(item: GridItem, view: View) {
                when (item) {
                    is GridItem.App -> AppActions.open(this@HomeFragment, item.entry, view)
                    GridItem.Add -> AppActions.editFavorites(this@HomeFragment)
                }
            }
            override fun onItemLongClick(item: GridItem, view: View): Boolean {
                val app = item as? GridItem.App ?: return false
                AppActions.showMenu(this@HomeFragment, app.entry, view) {
                    launcherHost?.navigate(LauncherPage.FAVORITES, bundleOf(NavArgs.MOVE_MODE to true))
                }
                return true
            }
        })
        grid.allowEdgePaging = false

        b.searchBtn.setOnClickListener { launcherHost?.navigate(LauncherPage.ALL_APPS, bundleOf(NavArgs.FOCUS_SEARCH to true)) }
        b.micBtn.setOnClickListener { launcherHost?.startVoiceSearch { q -> launcherHost?.navigate(LauncherPage.ALL_APPS, bundleOf(NavArgs.SEARCH_QUERY to q)) } }
        b.appsBtn.setOnClickListener { launcherHost?.navigate(LauncherPage.ALL_APPS) }
        b.bellBtn.setOnClickListener { com.akaaflix.launcher.device.system.SystemShortcuts.notifications(requireContext()) }
        b.infoBtn.setOnClickListener { HomeDialogs.showDeviceInfo(requireContext()) }
        b.remoteDecor.setOnClickListener { launcherHost?.navigate(LauncherPage.SETTINGS, bundleOf(NavArgs.OPEN_SECTION to "remote")) }
        b.weatherWidget.setOnClickListener { launcherHost?.navigate(LauncherPage.SETTINGS, bundleOf(NavArgs.OPEN_SECTION to "interface")) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { vm.state.collect { render(it) } }
                launch { vm.weather.collect { renderWeather(it) } }
                launch { while (true) { vm.refreshWeather(); delay(WeatherRepository.REFRESH_INTERVAL_MS) } }
            }
        }
    }

    private fun sizing(w: Int, h: Int): GridSpec {
        val s = vm.state.value.settings
        val scale = when (s.iconSize) { "small" -> 0.9f; "large" -> 1.12f; else -> 1f }
        if (portrait) return GridSizing.scrolling(w, requireContext().dpi(84f * scale), 1.08f, 0, 3, 6)
        val cols = (w / requireContext().dpi(132f * scale)).coerceIn(4, 9)
        val cellW = w / cols
        val cellH = (cellW * 0.74f).toInt()
        return GridSpec(cols, s.homeRows.coerceIn(1, 3), cellW, cellH, true)
    }

    private fun applyGridHeight(rows: Int) {
        if (portrait) return
        b.homeRoot.doOnLayout {
            val w = b.gridContainer.width
            if (w <= 0) return@doOnLayout
            val scale = when (vm.state.value.settings.iconSize) { "small" -> 0.9f; "large" -> 1.12f; else -> 1f }
            val cols = (w / requireContext().dpi(132f * scale)).coerceIn(4, 9)
            val cellH = ((w / cols) * 0.74f).roundToInt()
            val target = cellH * rows + 2
            val lp = b.gridContainer.layoutParams
            if (lp.height != target) { lp.height = target; b.gridContainer.layoutParams = lp }
        }
    }

    private fun render(s: HomeState) {
        val st = s.settings
        launcherHost?.setHeader(PageHeader(showLogo = st.showHomeLogo))
        b.gridContainer.isVisible = st.showFavoritesBar
        b.bottomBar.isVisible = st.showBottomBar
        b.remoteDecor.isVisible = st.showRemoteDecor && !portrait
        b.micBtn.isVisible = true
        listOf(b.searchBtn, b.micBtn, b.appsBtn, b.bellBtn, b.infoBtn).forEach { it.tint(0xFFFFFFFF.toInt()) }
        if (st.homeRows != lastRows) { lastRows = st.homeRows; applyGridHeight(st.homeRows); grid.relayout() }
        grid.setConfig(CardConfig(iconShape = st.iconShape, showLabels = st.showAppLabels, useBanners = st.useTvBanners,
            cardStyle = st.cardStyle, iconScale = when (st.iconSize) { "small" -> 0.88f; "large" -> 1.15f; else -> 1f }))
        val spec = grid.spec
        val capacity = if (spec == null || portrait) Int.MAX_VALUE else spec.columns * spec.rows - 1
        val items = s.favorites.take(capacity.coerceAtLeast(0)).map { GridItem.App(it, it.key in s.locked) } + GridItem.Add
        grid.setItems(items)
        b.homeMessage.isVisible = !s.loaded
        if (!s.loaded) b.homeMessage.setText(R.string.home_loading_apps)
    }

    private fun renderWeather(w: WeatherState?) {
        b.weatherWidget.isVisible = w != null
        if (w == null) return
        val f = vm.state.value.settings.weatherUnit == "f"
        val t = (if (f) WeatherRepository.toFahrenheit(w.temperatureC) else w.temperatureC).roundToInt()
        b.weatherTemp.text = getString(if (f) R.string.weather_temp_f else R.string.weather_temp_c, t)
        b.weatherPlace.text = if (w.country.isNotBlank()) str(R.string.weather_location_fmt, w.city, w.country) else w.city
        b.weatherIcon.setImageResource(when (WeatherRepository.kind(w.weatherCode)) {
            WeatherKind.CLEAR -> if (w.isDay) R.drawable.ic_weather_clear_day else R.drawable.ic_weather_clear_night
            WeatherKind.PARTLY_CLOUDY -> if (w.isDay) R.drawable.ic_weather_partly_day else R.drawable.ic_weather_partly_night
            WeatherKind.CLOUDY -> R.drawable.ic_weather_cloudy
            WeatherKind.FOG -> R.drawable.ic_weather_fog
            WeatherKind.RAIN -> R.drawable.ic_weather_rain
            WeatherKind.SNOW -> R.drawable.ic_weather_snow
            WeatherKind.THUNDER -> R.drawable.ic_weather_thunder
        })
        b.weatherPlace.compoundDrawablesRelative[2]?.setTint(LauncherUi.palette.accentLight)
    }

    /** Called by the activity when HOME is pressed again or Back is pressed on home. */
    fun focusDefault(): Boolean = if (_b != null && b.gridContainer.isVisible) grid.focusFirst() else false

    override fun onMenuKey(): Boolean {
        val item = grid.focusedItem() as? GridItem.App ?: return false
        AppActions.showMenu(this, item.entry, b.grid.focusedChild); return true
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
