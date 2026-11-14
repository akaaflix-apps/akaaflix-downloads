package com.akaaflix.launcher.home

import com.akaaflix.launcher.core.util.str

import android.content.res.Configuration
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
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
import com.akaaflix.launcher.apps.ui.GridSpec
import com.akaaflix.launcher.apps.ui.HomeCapacity
import com.akaaflix.launcher.apps.ui.MoveModeController
import com.akaaflix.launcher.core.R
import com.akaaflix.launcher.core.nav.ArgsReceiver
import com.akaaflix.launcher.core.nav.BackHandler
import com.akaaflix.launcher.core.nav.KeyInterceptor
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
import com.akaaflix.launcher.home.ui.WeatherSetup
import com.akaaflix.launcher.prefs.LauncherSettings
import com.akaaflix.launcher.prefs.SettingsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The approved Home screen: logo, the user's Home apps (exactly [LauncherSettings.homeRows] rows –
 * three by default – sized to fit between the header and the bottom controls), weather widget and
 * the quick bar (search, all apps, device info). Home apps are independent of Favorites.
 */
class HomeFragment : Fragment(), MenuKeyHandler, KeyInterceptor, BackHandler, ArgsReceiver {
    private var _b: FragmentHomeBinding? = null
    private val b get() = _b!!
    private val vm: HomeViewModel by viewModels()
    private val prefs by lazy { SettingsRepository.get(requireContext()) }
    private lateinit var grid: AppGridController
    private var portrait = false
    private var lastGeometryKey = ""

    private val move = MoveModeController(object : MoveModeController.Listener {
        override fun currentOrder() = visibleHomeApps().map { it.key }
        override fun columns() = grid.spec?.columns ?: 1
        override fun applyOrder(order: List<String>, movingKey: String?) {
            val byKey = vm.state.value.homeApps.associateBy { it.key }
            val locked = vm.state.value.locked
            grid.setItems(order.mapNotNull { byKey[it] }.map { GridItem.App(it, it.key in locked, it.key == movingKey) },
                focusId = movingKey ?: grid.focusedItem()?.id)
        }
        override fun commit(order: List<String>) {
            prefs.editApps { c -> c.copy(homeApps = order + c.homeApps.filter { it !in order }, homeInitialized = true) }
            Toast.makeText(requireContext(), R.string.move_mode_done, Toast.LENGTH_SHORT).show()
        }
        override fun onModeChanged(active: Boolean) {
            b.moveHint.isVisible = active
            b.bottomBar.isVisible = !active && vm.state.value.settings.showBottomBar
            if (!active) render(vm.state.value)
        }
        override fun focusedKey() = (grid.focusedItem() as? GridItem.App)?.entry?.key
    })

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _b = FragmentHomeBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        portrait = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        b.weatherWidget.glass(GlassDrawable.Variant.CHIP, 28f, 3f)
        FocusEffects.attach(b.weatherWidget, 1.05f)
        listOf(b.searchBtn, b.appsBtn, b.infoBtn).forEach {
            it.glass(GlassDrawable.Variant.CIRCLE, 22f, 3f)
            FocusEffects.attach(it, 1.12f)
        }
        b.moveHint.glass(GlassDrawable.Variant.PANEL, 16f)

        grid = AppGridController(this, b.grid, null, { w, _ -> sizing(w) }, object : AppGridAdapter.Callbacks {
            override fun onItemClick(item: GridItem, view: View) {
                if (move.active) return
                when (item) {
                    is GridItem.App -> AppActions.open(this@HomeFragment, item.entry, view)
                    GridItem.Add -> AppActions.editHomeApps(this@HomeFragment)
                }
            }
            override fun onItemLongClick(item: GridItem, view: View): Boolean {
                val app = item as? GridItem.App ?: return false
                if (move.active) return true
                when (vm.state.value.settings.longPressAction) {
                    "move" -> move.begin(app.entry.key)
                    "info" -> AppActions.openAppInfo(requireContext(), app.entry)
                    else -> AppActions.showMenu(this@HomeFragment, app.entry, view) { move.begin(app.entry.key) }
                }
                return true
            }
            override fun onItemMenuKey(item: GridItem, view: View): Boolean {
                val app = item as? GridItem.App ?: return false
                if (!move.active) AppActions.showMenu(this@HomeFragment, app.entry, view) { move.begin(app.entry.key) }
                return true
            }
        })
        grid.allowEdgePaging = false

        b.searchBtn.setOnClickListener { launcherHost?.navigate(LauncherPage.ALL_APPS, bundleOf(NavArgs.FOCUS_SEARCH to true)) }
        b.appsBtn.setOnClickListener { launcherHost?.navigate(LauncherPage.ALL_APPS) }
        b.infoBtn.setOnClickListener { HomeDialogs.showDeviceInfo(requireContext()) }
        b.remoteDecor.setOnClickListener { launcherHost?.navigate(LauncherPage.SETTINGS, bundleOf(NavArgs.OPEN_SECTION to "remote")) }
        b.weatherWidget.setOnClickListener { WeatherSetup.open(this) }

        b.homeRoot.addOnLayoutChangeListener { _, _, t, _, bt, _, ot, _, ob -> if (bt - t != ob - ot) applyGeometry() }
        b.homeRoot.doOnLayout { applyGeometry() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { vm.state.collect { render(it) } }
                launch { vm.weather.collect { renderWeather(it) } }
                launch {
                    while (true) {
                        try { WeatherSetup.maybeRelocate(requireContext().applicationContext) } catch (_: Exception) { }
                        vm.refreshWeather()
                        delay(WeatherRepository.REFRESH_INTERVAL_MS)
                    }
                }
            }
        }
        arguments?.let { onNavigationArgs(it) }
    }

    override fun onNavigationArgs(args: Bundle) {
        if (args.getBoolean(NavArgs.MOVE_MODE)) b.root.post { if (_b != null && !move.active) { move.begin(null); grid.focusFirst() } }
    }

    private fun iconScale(s: LauncherSettings) = if (s.iconSize == "large") 1.15f else 1f

    /** Grid geometry: columns from the width, card height limited so all rows fit the free band. */
    private fun sizing(w: Int): GridSpec {
        val s = vm.state.value.settings
        val rows = s.homeRows.coerceIn(1, 3)
        val scale = if (s.iconSize == "large") 1.12f else 1f
        val ctx = requireContext()
        val cols = if (portrait) (w / ctx.dpi(84f * scale)).coerceIn(3, 6) else (w / ctx.dpi(132f * scale)).coerceIn(4, 9)
        val cellW = w / cols
        val rootH = _b?.homeRoot?.height ?: 0
        val top = resources.getDimensionPixelSize(R.dimen.home_grid_top)
        val reserve = resources.getDimensionPixelSize(R.dimen.home_bottom_reserve)
        val band = if (rootH > 0) rootH - top - reserve else Int.MAX_VALUE
        val natural = (cellW * (if (portrait) 1.08f else 0.74f)).toInt()
        val cellH = minOf(natural, band / rows).coerceAtLeast(ctx.dpi(56f))
        return GridSpec(cols, rows, cellW, cellH, true)
    }

    private fun applyGeometry(fromRender: Boolean = false) {
        val bb = _b ?: return
        val w = bb.gridContainer.width
        if (w <= 0 || bb.homeRoot.height <= 0) return
        val s = vm.state.value.settings
        val key = "$w|${bb.homeRoot.height}|${s.homeRows}|${s.iconSize}"
        if (key == lastGeometryKey) return
        lastGeometryKey = key
        val spec = sizing(w)
        val target = spec.cellH * spec.rows + 2
        val lp = bb.gridContainer.layoutParams
        if (lp.height != target) { lp.height = target; bb.gridContainer.layoutParams = lp }
        grid.relayout()
        if (!fromRender) render(vm.state.value)
    }

    private fun visibleHomeApps() = vm.state.value.homeApps.take(capacity())

    private fun capacity(): Int {
        val spec = grid.spec ?: return Int.MAX_VALUE
        return (spec.columns * spec.rows - 1).coerceAtLeast(0)
    }

    private fun render(s: HomeState) {
        val bb = _b ?: return
        val st = s.settings
        launcherHost?.takeIf { it.currentPage() == LauncherPage.HOME }?.setHeader(PageHeader(showLogo = st.showHomeLogo))
        bb.gridContainer.isVisible = st.showFavoritesBar
        bb.bottomBar.isVisible = st.showBottomBar && !move.active
        bb.remoteDecor.isVisible = st.showRemoteDecor && !portrait
        listOf(bb.searchBtn, bb.appsBtn, bb.infoBtn).forEach { it.tint(0xFFFFFFFF.toInt()) }
        applyGeometry(fromRender = true)
        grid.setConfig(CardConfig(iconShape = "original", showLabels = st.showAppLabels, useBanners = st.useTvBanners,
            cardStyle = st.cardStyle, iconScale = iconScale(st)))
        val cap = capacity()
        HomeCapacity.value = cap
        if (move.active) return
        val items = s.homeApps.take(cap).map { GridItem.App(it, it.key in s.locked) } + GridItem.Add
        grid.setItems(items)
        bb.homeMessage.isVisible = !s.loaded
        if (!s.loaded) bb.homeMessage.setText(R.string.home_loading_apps)
    }

    private fun renderWeather(w: WeatherUi) {
        val bb = _b ?: return
        bb.weatherWidget.isVisible = w !is WeatherUi.Hidden
        bb.weatherTemp.textSize = if (w is WeatherUi.Setup) 15f else 21f
        bb.weatherHiLo.isVisible = false
        val pin = bb.weatherPlace.compoundDrawablesRelative[2]
        pin?.setTint(LauncherUi.palette.accentLight)
        when (w) {
            WeatherUi.Hidden -> return
            WeatherUi.Setup -> {
                bb.weatherIcon.setImageResource(R.drawable.ic_weather_partly_day)
                bb.weatherTemp.setText(R.string.weather_setup_title)
                bb.weatherCond.text = ""
                bb.weatherPlace.setText(R.string.weather_setup_cta)
                bb.weatherWidget.contentDescription = getString(R.string.weather_setup_cta)
            }
            is WeatherUi.Loading -> {
                bb.weatherIcon.setImageResource(R.drawable.ic_weather_cloudy)
                bb.weatherTemp.text = getString(R.string.weather_temp_unknown)
                bb.weatherCond.setText(R.string.weather_updating)
                bb.weatherPlace.text = w.city
                bb.weatherWidget.contentDescription = w.city
            }
            is WeatherUi.Unavailable -> {
                bb.weatherIcon.setImageResource(R.drawable.ic_weather_cloudy)
                bb.weatherTemp.text = getString(R.string.weather_temp_unknown)
                bb.weatherCond.setText(R.string.weather_offline_short)
                bb.weatherPlace.text = w.city
                bb.weatherWidget.contentDescription = w.city
            }
            is WeatherUi.Data -> bindData(w.state, w.stale)
        }
    }

    private fun bindData(d: WeatherState, stale: Boolean) {
        val bb = _b ?: return
        val f = vm.state.value.settings.weatherUnit == "f"
        fun deg(c: Double) = (if (f) WeatherRepository.toFahrenheit(c) else c).roundToInt()
        bb.weatherTemp.text = getString(if (f) R.string.weather_temp_f else R.string.weather_temp_c, deg(d.temperatureC))
        val kind = WeatherRepository.kind(d.weatherCode)
        val cond = getString(conditionLabel(kind))
        bb.weatherCond.text = if (stale) str(R.string.weather_cond_offline_fmt, cond) else cond
        bb.weatherPlace.text = if (d.country.isNotBlank()) str(R.string.weather_location_fmt, d.city, d.country) else d.city
        if (d.hasHighLow) {
            bb.weatherHiLo.isVisible = true
            bb.weatherHiLo.text = str(R.string.weather_hi_lo_fmt, deg(d.highC), deg(d.lowC))
        }
        bb.weatherIcon.setImageResource(when (kind) {
            WeatherKind.CLEAR -> if (d.isDay) R.drawable.ic_weather_clear_day else R.drawable.ic_weather_clear_night
            WeatherKind.PARTLY_CLOUDY -> if (d.isDay) R.drawable.ic_weather_partly_day else R.drawable.ic_weather_partly_night
            WeatherKind.CLOUDY -> R.drawable.ic_weather_cloudy
            WeatherKind.FOG -> R.drawable.ic_weather_fog
            WeatherKind.DRIZZLE, WeatherKind.RAIN -> R.drawable.ic_weather_rain
            WeatherKind.SNOW -> R.drawable.ic_weather_snow
            WeatherKind.THUNDER -> R.drawable.ic_weather_thunder
        })
        bb.weatherWidget.contentDescription = "${bb.weatherTemp.text}, $cond, ${bb.weatherPlace.text}"
    }

    private fun conditionLabel(k: WeatherKind) = when (k) {
        WeatherKind.CLEAR -> R.string.weather_cond_clear
        WeatherKind.PARTLY_CLOUDY -> R.string.weather_cond_partly
        WeatherKind.CLOUDY -> R.string.weather_cond_cloudy
        WeatherKind.FOG -> R.string.weather_cond_fog
        WeatherKind.DRIZZLE -> R.string.weather_cond_drizzle
        WeatherKind.RAIN -> R.string.weather_cond_rain
        WeatherKind.SNOW -> R.string.weather_cond_snow
        WeatherKind.THUNDER -> R.string.weather_cond_thunder
    }

    /** Called by the activity when HOME is pressed again or Back is pressed on home. */
    fun focusDefault(): Boolean = if (_b != null && b.gridContainer.isVisible) grid.focusFirst() else false

    override fun onInterceptKey(event: KeyEvent): Boolean = isVisible && move.onKey(event)

    override fun onBackPressed(): Boolean {
        if (move.active) { move.end(); return true }
        return false
    }

    override fun onMenuKey(): Boolean {
        val item = grid.focusedItem() as? GridItem.App ?: return false
        AppActions.showMenu(this, item.entry, b.grid.focusedChild) { move.begin(item.entry.key) }; return true
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (hidden && move.active) move.end()
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
