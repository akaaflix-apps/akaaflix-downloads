package com.akaaflix.launcher.settings

import com.akaaflix.launcher.core.util.str

import android.content.Context
import android.os.Build
import android.view.InputDevice
import com.akaaflix.launcher.apps.model.AppEntry
import com.akaaflix.launcher.core.AppConfig
import com.akaaflix.launcher.core.R
import com.akaaflix.launcher.core.perf.DeviceProfile
import com.akaaflix.launcher.core.perf.DevicePerformance
import com.akaaflix.launcher.core.ui.ThemeCatalog
import com.akaaflix.launcher.core.ui.rows.SettingRow
import com.akaaflix.launcher.device.clock.DateConfig
import com.akaaflix.launcher.device.clock.DateTextFormatter
import com.akaaflix.launcher.device.clock.HijriCalendar
import com.akaaflix.launcher.device.status.BluetoothState
import com.akaaflix.launcher.device.status.NetworkState
import com.akaaflix.launcher.home.ui.HomeDialogs
import com.akaaflix.launcher.prefs.AppCustomization
import com.akaaflix.launcher.prefs.LauncherSettings
import java.util.TimeZone

enum class SettingsSection(val key: String, val icon: Int, val title: Int, val subtitle: Int) {
    INTERFACE("interface", R.drawable.ic_monitor, R.string.set_interface, R.string.set_interface_sub),
    APPEARANCE("appearance", R.drawable.ic_image, R.string.set_appearance, R.string.set_appearance_sub),
    APPS("apps", R.drawable.ic_apps, R.string.set_apps, R.string.set_apps_sub),
    PROFILES("profiles", R.drawable.ic_users, R.string.set_profiles, R.string.set_profiles_sub),
    NETWORK("network", R.drawable.ic_wifi, R.string.set_network, R.string.set_network_sub),
    DEVICES("devices", R.drawable.ic_bluetooth, R.string.set_devices, R.string.set_devices_sub),
    REMOTE("remote", R.drawable.ic_remote, R.string.set_remote, R.string.set_remote_sub),
    NOTIFICATIONS("notifications", R.drawable.ic_bell, R.string.set_notifications, R.string.set_notifications_sub),
    DATETIME("datetime", R.drawable.ic_clock, R.string.set_datetime, R.string.set_datetime_sub),
    LANGUAGE("language", R.drawable.ic_translate, R.string.set_language, R.string.set_language_sub),
    POWER("power", R.drawable.ic_power, R.string.set_power, R.string.set_power_sub),
    ABOUT("about", R.drawable.ic_info, R.string.set_about, R.string.set_about_sub);

    companion object { fun of(key: String?) = entries.firstOrNull { it.key == key } }
}

data class SettingsState(
    val settings: LauncherSettings,
    val custom: AppCustomization,
    val apps: List<AppEntry>,
    val network: NetworkState,
    val bluetooth: BluetoothState,
    val isDefaultLauncher: Boolean,
    val profile: DeviceProfile,
    val screenTimeoutMs: Long?,
)

/** Actions the rows trigger (implemented by the fragment). */
interface SettingsActions {
    fun edit(t: (LauncherSettings) -> LauncherSettings)
    fun editRecreate(t: (LauncherSettings) -> LauncherSettings)
    fun requestDefaultLauncher()
    fun setAutoStart(enabled: Boolean)
    fun chooseWeatherCity()
    fun openWallpapers()
    fun editHomeApps()
    fun reorderHomeApps()
    fun editFavorites()
    fun reorderFavorites()
    fun editHidden()
    fun editLocked()
    fun resetOrder()
    fun clearHistory()
    fun refreshApps()
    fun editProfileName()
    fun setPin()
    fun changePin()
    fun removePin()
    fun guarded(action: () -> Unit)
    fun setSort(mode: String)
    fun remoteTest()
    fun clearCache()
    fun checkUpdates()
    fun backup()
    fun restore()
    fun showText(title: Int, text: String)
    fun openWebsite()
    fun showDefaultLauncherGuide()
    fun exitSafeMode()
    fun system(which: String)
}

object SettingsSections {
    private fun Context.s(id: Int) = getString(id)

    fun build(c: Context, sec: SettingsSection, st: SettingsState, a: SettingsActions): List<SettingRow> {
        val s = st.settings
        return when (sec) {
            SettingsSection.INTERFACE -> interfaceRows(c, s, st, a)
            SettingsSection.APPEARANCE -> appearanceRows(c, s, a)
            SettingsSection.APPS -> appsRows(c, s, st, a)
            SettingsSection.PROFILES -> profileRows(c, s, a)
            SettingsSection.NETWORK -> networkRows(c, s, st, a)
            SettingsSection.DEVICES -> deviceRows(c, st, a)
            SettingsSection.REMOTE -> remoteRows(c, s, a)
            SettingsSection.NOTIFICATIONS -> notificationRows(c, s, st, a)
            SettingsSection.DATETIME -> dateRows(c, s, a)
            SettingsSection.LANGUAGE -> languageRows(c, s, a)
            SettingsSection.POWER -> powerRows(c, s, st, a)
            SettingsSection.ABOUT -> aboutRows(c, s, a)
        }
    }

    private fun interfaceRows(c: Context, s: LauncherSettings, st: SettingsState, a: SettingsActions): List<SettingRow> {
        val modes = listOf("auto", "tv", "touch")
        val start = listOf("home", "all_apps", "favorites")
        val cols = listOf(0, 5, 6, 7, 8)
        val sizes = LauncherSettings.ICON_SIZES
        val units = listOf("c", "f")
        return listOf(
            SettingRow.Header("h_general", c.s(R.string.row_general_header)),
            SettingRow.Choice("mode", c.s(R.string.row_interface_mode), icon = R.drawable.ic_tv,
                options = listOf(c.s(R.string.mode_auto), c.s(R.string.mode_tv), c.s(R.string.mode_touch)), selected = modes.indexOf(s.interfaceMode).coerceAtLeast(0)) { i -> a.edit { it.copy(interfaceMode = modes[i]) } },
            SettingRow.Choice("start", c.s(R.string.row_start_page), icon = R.drawable.ic_home,
                options = listOf(c.s(R.string.nav_home), c.s(R.string.nav_all_apps), c.s(R.string.nav_favorites)), selected = start.indexOf(s.startPage).coerceAtLeast(0)) { i -> a.edit { it.copy(startPage = start[i]) } },
            SettingRow.Action("default", c.s(R.string.row_default_launcher), c.s(if (st.isDefaultLauncher) R.string.row_default_launcher_yes else R.string.row_default_launcher_no), R.drawable.ic_home) { a.requestDefaultLauncher() },
            SettingRow.Action("defaultGuide", c.s(R.string.row_default_guide), icon = R.drawable.ic_info) { a.showDefaultLauncherGuide() },
            SettingRow.Toggle("autostart", c.s(R.string.set_quick_autostart), c.s(R.string.row_autostart_sub), R.drawable.ic_power, s.autoStartOnBoot) { v -> a.setAutoStart(v) },
            SettingRow.Header("h_layout", c.s(R.string.row_layout_header)),
            SettingRow.Choice("rows", c.s(R.string.row_home_rows), icon = R.drawable.ic_apps, options = listOf("1", "2", "3"), selected = s.homeRows - 1) { i -> a.edit { it.copy(homeRows = i + 1) } },
            SettingRow.Choice("cols", c.s(R.string.row_grid_columns), icon = R.drawable.ic_apps, options = cols.map { if (it == 0) c.s(R.string.auto) else it.toString() },
                selected = cols.indexOf(s.gridColumns).coerceAtLeast(0)) { i -> a.edit { it.copy(gridColumns = cols[i]) } },
            SettingRow.Choice("size", c.s(R.string.row_icon_size), icon = R.drawable.ic_image, options = listOf(c.s(R.string.size_medium), c.s(R.string.size_large)),
                selected = sizes.indexOf(s.iconSize).coerceAtLeast(0)) { i -> a.edit { it.copy(iconSize = sizes[i]) } },
            SettingRow.Toggle("labels", c.s(R.string.row_show_labels), icon = R.drawable.ic_text_size, checked = s.showAppLabels) { v -> a.edit { it.copy(showAppLabels = v) } },
            SettingRow.Toggle("sidebarLabels", c.s(R.string.row_sidebar_labels), icon = R.drawable.ic_sliders, checked = s.showSidebarLabels) { v -> a.edit { it.copy(showSidebarLabels = v) } },
            SettingRow.Toggle("logo", c.s(R.string.row_home_logo), icon = R.drawable.ic_logo_mark, checked = s.showHomeLogo) { v -> a.edit { it.copy(showHomeLogo = v) } },
            SettingRow.Toggle("favbar", c.s(R.string.cust_favbar), c.s(R.string.cust_favbar_sub), R.drawable.ic_star, s.showFavoritesBar) { v -> a.edit { it.copy(showFavoritesBar = v) } },
            SettingRow.Toggle("bar", c.s(R.string.row_bottom_bar), icon = R.drawable.ic_search, checked = s.showBottomBar) { v -> a.edit { it.copy(showBottomBar = v) } },
            SettingRow.Toggle("remoteDecor", c.s(R.string.row_remote_decor), icon = R.drawable.ic_remote, checked = s.showRemoteDecor) { v -> a.edit { it.copy(showRemoteDecor = v) } },
            SettingRow.Header("h_weather", c.s(R.string.row_weather)),
            SettingRow.Toggle("weather", c.s(R.string.row_weather), c.s(R.string.row_weather_sub), R.drawable.ic_weather_partly_day, s.weatherEnabled) { v -> a.edit { it.copy(weatherEnabled = v) } },
            SettingRow.Action("city", c.s(R.string.row_weather_city), c.s(if (!s.hasWeatherLocation) R.string.weather_setup_cta else if (s.weatherLocationMode == "auto") R.string.weather_mode_auto else R.string.weather_mode_manual),
                R.drawable.ic_location, value = s.weatherCity.ifBlank { c.s(R.string.row_weather_city_none) }) { a.chooseWeatherCity() },
            SettingRow.Choice("unit", c.s(R.string.row_weather_unit), icon = R.drawable.ic_sun_outline, options = listOf(c.s(R.string.unit_c), c.s(R.string.unit_f)),
                selected = units.indexOf(s.weatherUnit).coerceAtLeast(0)) { i -> a.edit { it.copy(weatherUnit = units[i]) } },
        )
    }

    private fun appearanceRows(c: Context, s: LauncherSettings, a: SettingsActions): List<SettingRow> {
        val presets = ThemeCatalog.presets
        val accents = ThemeCatalog.accents
        val intervals = LauncherSettings.ROTATION_INTERVALS
        val cards = listOf("glass", "solid", "outline", "minimal")
        val fonts = listOf("tajawal", "cairo", "system")
        val scales = listOf(0.9f, 1.0f, 1.12f, 1.25f)
        val styles = listOf("glow", "border", "scale")
        val zooms = listOf("off", "small", "medium", "large")
        val sources = listOf("favorites", "all")
        fun interval(m: Int) = when { m < 60 -> c.str(R.string.wp_every_min, m); m == 60 -> c.s(R.string.wp_every_hour); m >= 1440 -> c.s(R.string.wp_every_day); else -> c.str(R.string.wp_every_hours, m / 60) }
        return listOf(
            SettingRow.Header("h_wp", c.s(R.string.row_wallpaper_header)),
            SettingRow.Action("wallpapers", c.s(R.string.row_open_wallpapers), icon = R.drawable.ic_image) { a.openWallpapers() },
            SettingRow.Toggle("animated", c.s(R.string.row_animated_wallpaper), icon = R.drawable.ic_video, checked = s.animatedWallpaper) { v -> a.edit { it.copy(animatedWallpaper = v) } },
            SettingRow.Toggle("rotation", c.s(R.string.row_rotation), c.s(R.string.wp_rotation_sub), R.drawable.ic_refresh, s.rotationEnabled) { v -> a.edit { it.copy(rotationEnabled = v, lastRotationAt = System.currentTimeMillis()) } },
            SettingRow.Choice("interval", c.s(R.string.row_rotation_interval), icon = R.drawable.ic_clock, options = intervals.map { interval(it) },
                selected = intervals.indexOf(s.rotationIntervalMin).coerceAtLeast(0)) { i -> a.edit { it.copy(rotationIntervalMin = intervals[i]) } },
            SettingRow.Choice("source", c.s(R.string.wp_rotation_source), icon = R.drawable.ic_heart, options = listOf(c.s(R.string.wp_rotation_favorites), c.s(R.string.wp_rotation_all)),
                selected = sources.indexOf(s.rotationSource).coerceAtLeast(0)) { i -> a.edit { it.copy(rotationSource = sources[i]) } },
            SettingRow.Header("h_colors", c.s(R.string.row_colors_header)),
            SettingRow.Choice("theme", c.s(R.string.row_theme), icon = R.drawable.ic_palette, options = presets.map { c.s(it.nameRes) },
                selected = presets.indexOfFirst { it.id == s.themeId }.coerceAtLeast(0)) { i -> a.edit { it.copy(themeId = presets[i].id) } },
            SettingRow.Colors("accent", c.s(R.string.row_accent), R.drawable.ic_palette, accents, accents.indexOf(s.accentColor).coerceAtLeast(0)) { i -> a.edit { it.copy(accentColor = accents[i]) } },
            SettingRow.Header("h_cards", c.s(R.string.row_cards_header)),
            SettingRow.Choice("card", c.s(R.string.row_card_style), icon = R.drawable.ic_layers, options = listOf(R.string.card_glass, R.string.card_solid, R.string.card_outline, R.string.card_minimal).map { c.s(it) },
                selected = cards.indexOf(s.cardStyle).coerceAtLeast(0)) { i -> a.edit { it.copy(cardStyle = cards[i]) } },
            SettingRow.Toggle("banners", c.s(R.string.row_tv_banners), c.s(R.string.row_tv_banners_sub), R.drawable.ic_tv, s.useTvBanners) { v -> a.edit { it.copy(useTvBanners = v) } },
            SettingRow.Choice("font", c.s(R.string.row_font), c.s(R.string.font_preview_text), R.drawable.ic_text_size, listOf(R.string.font_tajawal, R.string.font_cairo, R.string.font_system).map { c.s(it) },
                fonts.indexOf(s.fontFamily).coerceAtLeast(0)) { i -> a.editRecreate { it.copy(fontFamily = fonts[i]) } },
            SettingRow.Choice("fontScale", c.s(R.string.row_font_size), icon = R.drawable.ic_text_size, options = listOf(R.string.size_small, R.string.size_medium, R.string.size_large, R.string.size_xlarge).map { c.s(it) },
                selected = scales.indexOfFirst { kotlin.math.abs(it - s.fontScale) < 0.01f }.coerceAtLeast(1)) { i -> a.editRecreate { it.copy(fontScale = scales[i]) } },
            SettingRow.Choice("focus", c.s(R.string.row_focus_style), icon = R.drawable.ic_cursor, options = listOf(R.string.focus_glow, R.string.focus_border, R.string.focus_scale).map { c.s(it) },
                selected = styles.indexOf(s.focusStyle).coerceAtLeast(0)) { i -> a.edit { it.copy(focusStyle = styles[i]) } },
            SettingRow.Choice("zoom", c.s(R.string.row_focus_zoom), icon = R.drawable.ic_fullscreen, options = listOf(R.string.zoom_off, R.string.size_small, R.string.size_medium, R.string.size_large).map { c.s(it) },
                selected = zooms.indexOf(s.focusZoom).coerceAtLeast(0)) { i -> a.edit { it.copy(focusZoom = zooms[i]) } },
            SettingRow.Toggle("motion", c.s(R.string.cust_motion), c.s(R.string.cust_motion_sub), R.drawable.ic_sparkles, s.motionEnabled) { v -> a.edit { it.copy(motionEnabled = v) } },
        )
    }

    private fun appsRows(c: Context, @Suppress("UNUSED_PARAMETER") s: LauncherSettings, st: SettingsState, a: SettingsActions): List<SettingRow> {
        val modes = AppCustomization.SORT_MODES
        val labels = listOf(R.string.sort_custom, R.string.sort_az, R.string.sort_za, R.string.sort_installed, R.string.sort_most_used, R.string.sort_recent).map { c.s(it) }
        val hidden = st.custom.hidden.count { k -> st.apps.any { it.key == k } }
        return listOf(
            SettingRow.Header("h_home_apps", c.s(R.string.row_home_header)),
            SettingRow.Action("home", c.s(R.string.row_edit_home_apps), icon = R.drawable.ic_home) { a.editHomeApps() },
            SettingRow.Action("reorderHome", c.s(R.string.row_reorder_home_apps), icon = R.drawable.ic_move) { a.reorderHomeApps() },
            SettingRow.Header("h_fav_apps", c.s(R.string.nav_favorites)),
            SettingRow.Action("fav", c.s(R.string.row_edit_favorites), icon = R.drawable.ic_star) { a.editFavorites() },
            SettingRow.Action("reorder", c.s(R.string.row_reorder_favorites), icon = R.drawable.ic_move) { a.reorderFavorites() },
            SettingRow.Header("h_manage", c.s(R.string.app_manage_header)),
            SettingRow.Action("hidden", c.s(R.string.row_hidden_apps), icon = R.drawable.ic_eye_off, value = c.str(R.string.row_hidden_apps_count, hidden)) { a.editHidden() },
            SettingRow.Action("locked", c.s(R.string.row_locked_apps), icon = R.drawable.ic_lock, value = st.custom.locked.size.toString()) { a.editLocked() },
            SettingRow.Choice("sort", c.s(R.string.row_default_sort), icon = R.drawable.ic_sort, options = labels, selected = modes.indexOf(st.custom.sortMode).coerceAtLeast(0)) { i -> a.setSort(modes[i]) },
            SettingRow.Action("resetOrder", c.s(R.string.row_reset_order), icon = R.drawable.ic_restore, showChevron = false) { a.resetOrder() },
            SettingRow.Action("clearHistory", c.s(R.string.row_clear_history), icon = R.drawable.ic_clock, showChevron = false) { a.clearHistory() },
            SettingRow.Action("refresh", c.s(R.string.row_refresh_apps), icon = R.drawable.ic_refresh, showChevron = false) { a.refreshApps() },
            SettingRow.Action("system", c.s(R.string.row_system_apps_settings), icon = R.drawable.ic_open) { a.system("apps") },
        )
    }

    private fun profileRows(c: Context, s: LauncherSettings, a: SettingsActions): List<SettingRow> {
        val avatars = listOf("person", "anubis", "pyramids", "galaxy", "logo")
        val avatarLabels = listOf(R.string.avatar_person, R.string.avatar_anubis, R.string.avatar_pyramids, R.string.avatar_galaxy, R.string.avatar_logo).map { c.s(it) }
        val rows = mutableListOf<SettingRow>(
            SettingRow.Header("h_profile", c.s(R.string.row_profile_header)),
            SettingRow.Action("name", c.s(R.string.row_profile_name), icon = R.drawable.ic_edit, value = s.profileName.ifBlank { c.s(R.string.brand_line1) }) { a.editProfileName() },
            SettingRow.Choice("avatar", c.s(R.string.row_profile_avatar), icon = R.drawable.ic_avatar, options = avatarLabels, selected = avatars.indexOf(s.profileAvatar).coerceAtLeast(0)) { i -> a.edit { it.copy(profileAvatar = avatars[i]) } },
            SettingRow.Header("h_parental", c.s(R.string.row_parental_header)),
        )
        if (!s.hasPin) rows += SettingRow.Action("pin", c.s(R.string.row_pin_set), icon = R.drawable.ic_lock) { a.setPin() }
        else {
            rows += SettingRow.Action("pinChange", c.s(R.string.row_pin_change), icon = R.drawable.ic_lock) { a.changePin() }
            rows += SettingRow.Action("pinRemove", c.s(R.string.row_pin_remove), icon = R.drawable.ic_lock_open, danger = true) { a.removePin() }
        }
        rows += SettingRow.Toggle("lockSettings", c.s(R.string.row_lock_settings), icon = R.drawable.ic_settings, checked = s.lockSettings, enabled = s.hasPin) { v -> a.guarded { a.edit { it.copy(lockSettings = v) } } }
        rows += SettingRow.Toggle("lockCustom", c.s(R.string.row_lock_customization), icon = R.drawable.ic_brush, checked = s.lockCustomization, enabled = s.hasPin) { v -> a.guarded { a.edit { it.copy(lockCustomization = v) } } }
        rows += SettingRow.Toggle("protectHidden", c.s(R.string.row_protect_hidden), icon = R.drawable.ic_eye_off, checked = s.protectHiddenApps, enabled = s.hasPin) { v -> a.guarded { a.edit { it.copy(protectHiddenApps = v) } } }
        rows += SettingRow.Action("locked", c.s(R.string.row_locked_apps), icon = R.drawable.ic_lock, enabled = s.hasPin) { a.editLocked() }
        rows += SettingRow.Info("note", c.s(R.string.row_parental_note), null, R.drawable.ic_info)
        return rows
    }

    private fun networkRows(c: Context, s: LauncherSettings, st: SettingsState, a: SettingsActions) = listOf(
        SettingRow.Header("h_status", c.s(R.string.row_status_header)),
        SettingRow.Info("conn", c.s(R.string.row_connection), HomeDialogs.networkLabel(c, st.network), when (st.network.type) {
            NetworkState.Type.ETHERNET -> R.drawable.ic_ethernet; else -> if (st.network.connected) R.drawable.ic_wifi else R.drawable.ic_wifi_off }),
        SettingRow.Info("ip", c.s(R.string.row_ip), st.network.ipAddress ?: c.s(R.string.not_available), R.drawable.ic_globe),
        SettingRow.Toggle("showNet", c.s(R.string.set_quick_network), icon = R.drawable.ic_wifi, checked = s.showNetworkStatus) { v -> a.edit { it.copy(showNetworkStatus = v) } },
        SettingRow.Header("h_sys", c.s(R.string.row_shortcuts_header)),
        SettingRow.Action("wifi", c.s(R.string.row_wifi_settings), icon = R.drawable.ic_wifi) { a.system("wifi") },
        SettingRow.Action("network", c.s(R.string.row_network_settings), icon = R.drawable.ic_ethernet) { a.system("network") },
        SettingRow.Action("vpn", c.s(R.string.row_vpn_settings), icon = R.drawable.ic_shield) { a.system("vpn") },
        SettingRow.Info("note", c.s(R.string.row_data_note), null, R.drawable.ic_info),
    )

    private fun deviceRows(c: Context, st: SettingsState, a: SettingsActions): List<SettingRow> {
        val bt = when { !st.bluetooth.supported -> c.s(R.string.bt_unsupported); st.bluetooth.enabled -> c.s(R.string.bt_on); else -> c.s(R.string.bt_off) }
        val inputs = ArrayList<String>()
        val wanted = InputDevice.SOURCE_KEYBOARD or InputDevice.SOURCE_DPAD or InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_MOUSE
        for (id in InputDevice.getDeviceIds()) {
            val dev: InputDevice = InputDevice.getDevice(id) ?: continue
            if (!dev.isVirtual && (dev.sources and wanted) != 0 && dev.name !in inputs) inputs.add(dev.name)
        }
        return listOf(
            SettingRow.Info("bt", c.s(R.string.row_bt_status), bt, R.drawable.ic_bluetooth),
            SettingRow.Action("btSettings", c.s(R.string.row_bt_settings), icon = R.drawable.ic_bluetooth) { a.system("bluetooth") },
            SettingRow.Info("inputs", c.s(R.string.row_input_devices), (if (inputs.isEmpty()) c.s(R.string.not_available) else inputs.joinToString("\n")), R.drawable.ic_remote),
            SettingRow.Action("input", c.s(R.string.row_input_settings), icon = R.drawable.ic_text_size) { a.system("input") },
        )
    }

    private fun remoteRows(c: Context, s: LauncherSettings, a: SettingsActions): List<SettingRow> {
        val menu = listOf("context", "settings", "customize")
        val lp = listOf("context", "move", "info")
        val back = listOf("focus_first", "none")
        return listOf(
            SettingRow.Header("h_remote", c.s(R.string.row_remote_header)),
            SettingRow.Choice("menu", c.s(R.string.row_menu_key), icon = R.drawable.ic_remote, options = listOf(R.string.action_context_menu, R.string.action_open_settings, R.string.action_open_customization).map { c.s(it) },
                selected = menu.indexOf(s.menuKeyAction).coerceAtLeast(0)) { i -> a.edit { it.copy(menuKeyAction = menu[i]) } },
            SettingRow.Choice("long", c.s(R.string.row_long_press), icon = R.drawable.ic_cursor, options = listOf(R.string.action_context_menu, R.string.action_move, R.string.action_app_info).map { c.s(it) },
                selected = lp.indexOf(s.longPressAction).coerceAtLeast(0)) { i -> a.edit { it.copy(longPressAction = lp[i]) } },
            SettingRow.Choice("back", c.s(R.string.row_back_home), icon = R.drawable.ic_back, options = listOf(R.string.back_focus_first, R.string.back_nothing).map { c.s(it) },
                selected = back.indexOf(s.backOnHome).coerceAtLeast(0)) { i -> a.edit { it.copy(backOnHome = back[i]) } },
            SettingRow.Action("test", c.s(R.string.row_remote_test), c.s(R.string.row_remote_test_sub), R.drawable.ic_remote) { a.remoteTest() },
            SettingRow.Action("cec", c.s(R.string.row_cec_settings), icon = R.drawable.ic_tv) { a.system("cec") },
        )
    }

    private fun notificationRows(c: Context, s: LauncherSettings, st: SettingsState, a: SettingsActions) = listOf(
        SettingRow.Action("system", c.s(R.string.row_notif_system), icon = R.drawable.ic_bell) { a.system("notifications") },
        SettingRow.Toggle("updates", c.s(R.string.row_auto_update_check), icon = R.drawable.ic_download, checked = s.autoCheckUpdates) { v -> a.edit { it.copy(autoCheckUpdates = v) } },
        SettingRow.Info("note", c.s(R.string.row_notif_note), null, R.drawable.ic_shield),
    ).also { @Suppress("UNUSED_VARIABLE") val unused = st }

    private fun dateRows(c: Context, s: LauncherSettings, a: SettingsActions): List<SettingRow> {
        val formats = listOf("system", "12", "24")
        val methods = listOf("umm_al_qura", "tabular")
        val nums = listOf("western", "eastern")
        val use24 = when (s.clockFormat) { "24" -> true; "12" -> false; else -> android.text.format.DateFormat.is24HourFormat(c) }
        val t = DateTextFormatter.format(System.currentTimeMillis(), TimeZone.getDefault(),
            DateConfig(use24, if (s.hijriMethod == "tabular") HijriCalendar.Method.TABULAR else HijriCalendar.Method.UMM_AL_QURA, s.hijriAdjust, s.numerals == "eastern"))
        return listOf(
            SettingRow.Info("today", c.s(R.string.row_date_preview), run {
                val lang = androidx.core.os.ConfigurationCompat.getLocales(c.resources.configuration)[0]?.language ?: "en"
                val second = com.akaaflix.launcher.device.clock.DateTexts.secondaryLanguage(lang)
                "${t.gregorian(lang)}\n${t.gregorian(second)}\n${t.hijriText(lang)}\n${t.hijriText(second)}"
            }, R.drawable.ic_calendar),
            SettingRow.Header("h_clock", c.s(R.string.row_clock_header)),
            SettingRow.Toggle("show", c.s(R.string.set_quick_clock), icon = R.drawable.ic_clock, checked = s.showClockDate) { v -> a.edit { it.copy(showClockDate = v) } },
            SettingRow.Choice("format", c.s(R.string.row_clock_format), icon = R.drawable.ic_clock, options = listOf(c.s(R.string.system_default), c.s(R.string.clock_12), c.s(R.string.clock_24)),
                selected = formats.indexOf(s.clockFormat).coerceAtLeast(0)) { i -> a.edit { it.copy(clockFormat = formats[i]) } },
            SettingRow.Header("h_cal", c.s(R.string.row_calendar_header)),
            SettingRow.Toggle("hijri", c.s(R.string.row_show_hijri), icon = R.drawable.ic_moon, checked = s.showHijri) { v -> a.edit { it.copy(showHijri = v) } },
            SettingRow.Toggle("bilingual", c.s(R.string.row_bilingual_date), icon = R.drawable.ic_translate, checked = s.bilingualDate) { v -> a.edit { it.copy(bilingualDate = v) } },
            SettingRow.Choice("method", c.s(R.string.row_hijri_method), icon = R.drawable.ic_moon, options = listOf(c.s(R.string.hijri_umm_al_qura), c.s(R.string.hijri_tabular)),
                selected = methods.indexOf(s.hijriMethod).coerceAtLeast(0)) { i -> a.edit { it.copy(hijriMethod = methods[i]) } },
            SettingRow.Stepper("adjust", c.s(R.string.row_hijri_adjust), icon = R.drawable.ic_calendar, value = s.hijriAdjust, min = -2, max = 2,
                format = { v -> c.str(R.string.hijri_adjust_fmt, if (v > 0) "+$v" else v.toString()) }) { v -> a.edit { it.copy(hijriAdjust = v) } },
            SettingRow.Choice("numerals", c.s(R.string.row_numerals), icon = R.drawable.ic_text_size, options = listOf(c.s(R.string.numerals_western), c.s(R.string.numerals_eastern)),
                selected = nums.indexOf(s.numerals).coerceAtLeast(0)) { i -> a.edit { it.copy(numerals = nums[i]) } },
            SettingRow.Info("tz", c.s(R.string.row_timezone), TimeZone.getDefault().id, R.drawable.ic_globe),
            SettingRow.Action("system", c.s(R.string.row_system_date), icon = R.drawable.ic_open) { a.system("date") },
        )
    }

    private fun languageRows(c: Context, s: LauncherSettings, a: SettingsActions): List<SettingRow> {
        val langs = LauncherSettings.LANGUAGES
        return listOf(
            SettingRow.Choice("lang", c.s(R.string.row_language), icon = R.drawable.ic_translate, options = listOf(c.s(R.string.lang_system), c.s(R.string.lang_ar), c.s(R.string.lang_en), c.s(R.string.lang_fr)),
                selected = langs.indexOf(s.language).coerceAtLeast(0)) { i -> a.edit { it.copy(language = langs[i]) } },
            SettingRow.Action("system", c.s(R.string.row_system_language), icon = R.drawable.ic_open) { a.system("language") },
        )
    }

    private fun powerRows(c: Context, s: LauncherSettings, st: SettingsState, a: SettingsActions): List<SettingRow> {
        val modes = listOf("auto", "high", "balanced", "light")
        val autoLabel = c.str(R.string.perf_auto_fmt, c.s(when (DevicePerformance.quality("auto", st.profile).mode) {
            "light" -> R.string.perf_light; "balanced" -> R.string.perf_balanced; else -> R.string.perf_high }))
        val timeout = st.screenTimeoutMs?.let { ms -> if (ms >= Int.MAX_VALUE / 2) "∞" else "${ms / 60000} min" } ?: c.s(R.string.not_available)
        val rows = mutableListOf<SettingRow>(
            SettingRow.Header("h_perf", c.s(R.string.row_performance_header)),
            SettingRow.Choice("perf", c.s(R.string.row_performance_mode), icon = R.drawable.ic_gauge, options = listOf(autoLabel, c.s(R.string.perf_high), c.s(R.string.perf_balanced), c.s(R.string.perf_light)),
                selected = modes.indexOf(s.performanceMode).coerceAtLeast(0)) { i -> a.edit { it.copy(performanceMode = modes[i]) } },
            SettingRow.Info("tier", c.s(R.string.row_detected_device), c.s(HomeDialogs.tierLabel(st.profile.tier)), R.drawable.ic_cpu),
            SettingRow.Action("cache", c.s(R.string.row_clear_cache), icon = R.drawable.ic_trash, showChevron = false) { a.clearCache() },
            SettingRow.Header("h_power", c.s(R.string.row_power_header)),
            SettingRow.Info("timeout", c.s(R.string.row_screen_timeout), timeout, R.drawable.ic_clock),
            SettingRow.Action("dream", c.s(R.string.row_screensaver), icon = R.drawable.ic_moon) { a.system("screensaver") },
            SettingRow.Action("display", c.s(R.string.row_display_settings), icon = R.drawable.ic_monitor) { a.system("display") },
        )
        if (!st.profile.isTv) rows += SettingRow.Action("battery", c.s(R.string.row_battery_settings), icon = R.drawable.ic_power) { a.system("battery") }
        rows += SettingRow.Info("note", c.s(R.string.row_power_note), null, R.drawable.ic_info)
        return rows
    }

    private fun aboutRows(c: Context, s: LauncherSettings, a: SettingsActions): List<SettingRow> {
        val rows = mutableListOf<SettingRow>()
        if (s.safeMode) rows += SettingRow.Action("safe", c.s(R.string.row_safe_mode), c.s(R.string.row_safe_mode_sub), R.drawable.ic_shield) { a.exitSafeMode() }
        rows += listOf(
            SettingRow.Hero("hero", R.drawable.akaa_logo, c.s(R.string.app_name), listOf(c.str(R.string.about_version_fmt, AppConfig.versionName), c.s(R.string.about_copyright))),
            SettingRow.Action("website", c.s(R.string.about_website_label), c.s(R.string.about_website_url), R.drawable.ic_globe, value = c.s(R.string.about_website_text)) { a.openWebsite() },
            SettingRow.Info("copyright", c.s(R.string.about_copyright), null, R.drawable.ic_shield),
            SettingRow.Header("h_about", c.s(R.string.row_about_header)),
            SettingRow.Info("version", c.s(R.string.row_version), "v" + AppConfig.versionName, R.drawable.ic_logo_mark),
            SettingRow.Info("build", c.s(R.string.row_build), "${AppConfig.versionCode} • ${if (AppConfig.isDebug) "debug" else "release"} • Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})", R.drawable.ic_cpu),
            SettingRow.Action("updates", c.s(R.string.row_check_updates), icon = R.drawable.ic_refresh) { a.checkUpdates() },
            SettingRow.Toggle("autoUpdates", c.s(R.string.row_auto_update_check), icon = R.drawable.ic_download, checked = s.autoCheckUpdates) { v -> a.edit { it.copy(autoCheckUpdates = v) } },
            SettingRow.Header("h_backup", c.s(R.string.row_backup_header)),
            SettingRow.Action("backup", c.s(R.string.row_backup), icon = R.drawable.ic_upload) { a.backup() },
            SettingRow.Action("restore", c.s(R.string.row_restore), icon = R.drawable.ic_download) { a.restore() },
            SettingRow.Header("h_legal", c.s(R.string.row_privacy)),
            SettingRow.Action("privacy", c.s(R.string.row_privacy), icon = R.drawable.ic_shield) { a.showText(R.string.row_privacy, c.s(R.string.row_privacy_text)) },
            SettingRow.Action("licenses", c.s(R.string.row_licenses), icon = R.drawable.ic_info) { a.showText(R.string.row_licenses, c.s(R.string.licenses_text)) },
        )
        return rows
    }
}
