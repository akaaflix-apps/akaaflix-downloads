package com.akaaflix.launcher.customization

import android.content.Context
import android.os.Build
import com.akaaflix.launcher.core.R
import com.akaaflix.launcher.core.ui.ThemeCatalog
import com.akaaflix.launcher.core.ui.rows.SettingRow
import com.akaaflix.launcher.prefs.LauncherSettings

/** Option sheets opened from the Customization tiles. All rows edit the draft only. */
object CustomizationSheets {
    fun layout(c: Context, s: LauncherSettings, update: ((LauncherSettings) -> LauncherSettings) -> Unit, chooseCity: () -> Unit): List<SettingRow> {
        val rowsOptions = listOf("1", "2", "3")
        val cols = listOf(0, 5, 6, 7, 8)
        val colLabels = cols.map { if (it == 0) c.getString(R.string.auto) else it.toString() }
        val sizes = LauncherSettings.ICON_SIZES
        val sizeLabels = listOf(c.getString(R.string.size_large))
        val start = listOf("home", "all_apps", "favorites")
        val startLabels = listOf(c.getString(R.string.nav_home), c.getString(R.string.nav_all_apps), c.getString(R.string.nav_favorites))
        val modes = listOf("auto", "tv", "touch")
        val modeLabels = listOf(c.getString(R.string.mode_auto), c.getString(R.string.mode_tv), c.getString(R.string.mode_touch))
        return listOf(
            SettingRow.Header("h_home", c.getString(R.string.row_home_header)),
            SettingRow.Choice("rows", c.getString(R.string.row_home_rows), icon = R.drawable.ic_apps, options = rowsOptions, selected = s.homeRows - 1) { i -> update { it.copy(homeRows = i + 1) } },
            SettingRow.Toggle("favbar", c.getString(R.string.cust_favbar), c.getString(R.string.cust_favbar_sub), R.drawable.ic_star, s.showFavoritesBar) { v -> update { it.copy(showFavoritesBar = v) } },
            SettingRow.Toggle("logo", c.getString(R.string.row_home_logo), icon = R.drawable.ic_logo_mark, checked = s.showHomeLogo) { v -> update { it.copy(showHomeLogo = v) } },
            SettingRow.Toggle("bar", c.getString(R.string.row_bottom_bar), icon = R.drawable.ic_search, checked = s.showBottomBar) { v -> update { it.copy(showBottomBar = v) } },
            SettingRow.Toggle("remote", c.getString(R.string.row_remote_decor), icon = R.drawable.ic_remote, checked = s.showRemoteDecor) { v -> update { it.copy(showRemoteDecor = v) } },
            SettingRow.Toggle("weather", c.getString(R.string.row_weather), c.getString(R.string.row_weather_sub), R.drawable.ic_weather_partly_day, s.weatherEnabled) { v -> update { it.copy(weatherEnabled = v) } },
            SettingRow.Action("city", c.getString(R.string.row_weather_city), icon = R.drawable.ic_location,
                value = s.weatherCity.ifBlank { c.getString(R.string.row_weather_city_none) }) { chooseCity() },
            SettingRow.Header("h_layout", c.getString(R.string.row_layout_header)),
            SettingRow.Choice("cols", c.getString(R.string.row_grid_columns), icon = R.drawable.ic_apps, options = colLabels, selected = cols.indexOf(s.gridColumns).coerceAtLeast(0)) { i -> update { it.copy(gridColumns = cols[i]) } },
            SettingRow.Choice("size", c.getString(R.string.row_icon_size), icon = R.drawable.ic_image, options = sizeLabels, selected = sizes.indexOf(s.iconSize).coerceAtLeast(0)) { i -> update { it.copy(iconSize = sizes[i]) } },
            SettingRow.Toggle("sidebarLabels", c.getString(R.string.row_sidebar_labels), icon = R.drawable.ic_sliders, checked = s.showSidebarLabels) { v -> update { it.copy(showSidebarLabels = v) } },
            SettingRow.Header("h_nav", c.getString(R.string.row_navigation_header)),
            SettingRow.Choice("start", c.getString(R.string.row_start_page), icon = R.drawable.ic_home, options = startLabels, selected = start.indexOf(s.startPage).coerceAtLeast(0)) { i -> update { it.copy(startPage = start[i]) } },
            SettingRow.Choice("mode", c.getString(R.string.row_interface_mode), icon = R.drawable.ic_tv, options = modeLabels, selected = modes.indexOf(s.interfaceMode).coerceAtLeast(0)) { i -> update { it.copy(interfaceMode = modes[i]) } },
        )
    }

    fun colors(c: Context, s: LauncherSettings, update: ((LauncherSettings) -> LauncherSettings) -> Unit): List<SettingRow> {
        val presets = ThemeCatalog.presets
        val accents = ThemeCatalog.accents
        return listOf(
            SettingRow.Choice("theme", c.getString(R.string.row_theme), icon = R.drawable.ic_palette, options = presets.map { c.getString(it.nameRes) },
                selected = presets.indexOfFirst { it.id == s.themeId }.coerceAtLeast(0)) { i -> update { it.copy(themeId = presets[i].id) } },
            SettingRow.Colors("accent", c.getString(R.string.row_accent), R.drawable.ic_palette, accents, accents.indexOf(s.accentColor).coerceAtLeast(0)) { i ->
                update { it.copy(accentColor = accents[i]) }
            },
        )
    }

    fun apps(c: Context, s: LauncherSettings, update: ((LauncherSettings) -> LauncherSettings) -> Unit,
             editHome: () -> Unit, reorderHome: () -> Unit, editFavorites: () -> Unit): List<SettingRow> {
        val sizes = LauncherSettings.ICON_SIZES
        val sizeLabels = listOf(c.getString(R.string.size_large))
        val cards = listOf("glass", "solid", "outline", "minimal")
        val cardLabels = listOf(R.string.card_glass, R.string.card_solid, R.string.card_outline, R.string.card_minimal).map { c.getString(it) }
        return listOf(
            SettingRow.Choice("size", c.getString(R.string.row_icon_size), icon = R.drawable.ic_image, options = sizeLabels, selected = sizes.indexOf(s.iconSize).coerceAtLeast(0)) { i -> update { it.copy(iconSize = sizes[i]) } },
            SettingRow.Choice("card", c.getString(R.string.row_card_style), icon = R.drawable.ic_layers, options = cardLabels, selected = cards.indexOf(s.cardStyle).coerceAtLeast(0)) { i -> update { it.copy(cardStyle = cards[i]) } },
            SettingRow.Toggle("banners", c.getString(R.string.row_tv_banners), c.getString(R.string.row_tv_banners_sub), R.drawable.ic_tv, s.useTvBanners) { v -> update { it.copy(useTvBanners = v) } },
            SettingRow.Toggle("labels", c.getString(R.string.row_show_labels), icon = R.drawable.ic_text_size, checked = s.showAppLabels) { v -> update { it.copy(showAppLabels = v) } },
            SettingRow.Action("home", c.getString(R.string.row_edit_home_apps), icon = R.drawable.ic_home) { editHome() },
            SettingRow.Action("reorder", c.getString(R.string.row_reorder_home_apps), icon = R.drawable.ic_move) { reorderHome() },
            SettingRow.Action("favorites", c.getString(R.string.row_edit_favorites), icon = R.drawable.ic_star) { editFavorites() },
        )
    }

    fun cursor(c: Context, s: LauncherSettings, update: ((LauncherSettings) -> LauncherSettings) -> Unit): List<SettingRow> {
        val styles = listOf("glow", "border", "scale")
        val styleLabels = listOf(R.string.focus_glow, R.string.focus_border, R.string.focus_scale).map { c.getString(it) }
        val zooms = listOf("off", "small", "medium", "large")
        val zoomLabels = listOf(R.string.zoom_off, R.string.size_small, R.string.size_medium, R.string.size_large).map { c.getString(it) }
        val transitions = listOf("fade", "slide", "none")
        val transitionLabels = listOf(R.string.transition_fade, R.string.transition_slide, R.string.transition_none).map { c.getString(it) }
        val pointers = listOf("system", "neon", "large")
        val pointerLabels = listOf(R.string.pointer_system, R.string.pointer_neon, R.string.pointer_large).map { c.getString(it) }
        val pointerOk = Build.VERSION.SDK_INT >= 24
        return listOf(
            SettingRow.Choice("focus", c.getString(R.string.row_focus_style), icon = R.drawable.ic_cursor, options = styleLabels, selected = styles.indexOf(s.focusStyle).coerceAtLeast(0)) { i -> update { it.copy(focusStyle = styles[i]) } },
            SettingRow.Choice("zoom", c.getString(R.string.row_focus_zoom), icon = R.drawable.ic_fullscreen, options = zoomLabels, selected = zooms.indexOf(s.focusZoom).coerceAtLeast(0)) { i -> update { it.copy(focusZoom = zooms[i]) } },
            SettingRow.Toggle("motion", c.getString(R.string.cust_motion), c.getString(R.string.cust_motion_sub), R.drawable.ic_sparkles, s.motionEnabled) { v -> update { it.copy(motionEnabled = v) } },
            SettingRow.Choice("transition", c.getString(R.string.row_page_transition), icon = R.drawable.ic_layers, options = transitionLabels, selected = transitions.indexOf(s.pageTransition).coerceAtLeast(0)) { i -> update { it.copy(pageTransition = transitions[i]) } },
            SettingRow.Choice("pointer", c.getString(R.string.row_pointer), if (pointerOk) null else c.getString(R.string.pointer_unsupported), R.drawable.ic_cursor,
                pointerLabels, pointers.indexOf(s.pointerStyle).coerceAtLeast(0), enabled = pointerOk) { i -> update { it.copy(pointerStyle = pointers[i]) } },
        )
    }

    fun fonts(c: Context, s: LauncherSettings, update: ((LauncherSettings) -> LauncherSettings) -> Unit): List<SettingRow> {
        val fonts = listOf("tajawal", "cairo", "system")
        val fontLabels = listOf(R.string.font_tajawal, R.string.font_cairo, R.string.font_system).map { c.getString(it) }
        val scales = listOf(0.9f, 1.0f, 1.12f, 1.25f)
        val scaleLabels = listOf(R.string.size_small, R.string.size_medium, R.string.size_large, R.string.size_xlarge).map { c.getString(it) }
        return listOf(
            SettingRow.Choice("font", c.getString(R.string.row_font), c.getString(R.string.font_preview_text), R.drawable.ic_text_size, fontLabels,
                fonts.indexOf(s.fontFamily).coerceAtLeast(0)) { i -> update { it.copy(fontFamily = fonts[i]) } },
            SettingRow.Choice("scale", c.getString(R.string.row_font_size), icon = R.drawable.ic_text_size, options = scaleLabels,
                selected = scales.indexOfFirst { kotlin.math.abs(it - s.fontScale) < 0.01f }.coerceAtLeast(1)) { i -> update { it.copy(fontScale = scales[i]) } },
            SettingRow.Info("note", c.getString(R.string.restart_needed), null, R.drawable.ic_info),
        )
    }
}
