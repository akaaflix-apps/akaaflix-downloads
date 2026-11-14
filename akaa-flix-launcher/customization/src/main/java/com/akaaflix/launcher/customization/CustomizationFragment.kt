package com.akaaflix.launcher.customization

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.akaaflix.launcher.apps.AppLists
import com.akaaflix.launcher.apps.AppRepository
import com.akaaflix.launcher.apps.ui.AppActions
import com.akaaflix.launcher.core.R
import com.akaaflix.launcher.core.nav.BackHandler
import com.akaaflix.launcher.core.nav.LauncherPage
import com.akaaflix.launcher.core.nav.LeaveGuard
import com.akaaflix.launcher.core.nav.NavArgs
import com.akaaflix.launcher.core.nav.PageHeader
import com.akaaflix.launcher.core.nav.launcherHost
import com.akaaflix.launcher.core.ui.FocusEffects
import com.akaaflix.launcher.core.ui.GlassDrawable
import com.akaaflix.launcher.core.ui.LauncherUi
import com.akaaflix.launcher.core.ui.ThemeCatalog
import com.akaaflix.launcher.core.ui.dialog.Dialogs
import com.akaaflix.launcher.core.ui.dialog.LauncherDialog
import com.akaaflix.launcher.core.ui.dpi
import com.akaaflix.launcher.core.ui.glass
import com.akaaflix.launcher.core.ui.rows.SettingRow
import com.akaaflix.launcher.core.ui.tint
import com.akaaflix.launcher.customization.databinding.FragmentCustomizationBinding
import com.akaaflix.launcher.home.ui.WeatherSetup
import com.akaaflix.launcher.prefs.LauncherSettings
import com.akaaflix.launcher.wallpaper.ThumbnailLoader
import com.akaaflix.launcher.wallpaper.WallpaperRepository
import com.akaaflix.launcher.wallpaper.model.Wallpaper
import com.akaaflix.launcher.wallpaper.model.WallpaperCategory
import com.akaaflix.launcher.wallpaper.ui.WallpaperGridAdapter
import com.akaaflix.launcher.wallpaper.ui.WpItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Calendar

/** Customization page from the reference, with a live preview of unsaved changes. */
class CustomizationFragment : Fragment(), BackHandler, LeaveGuard {
    private var _b: FragmentCustomizationBinding? = null
    private val b get() = _b!!
    private val vm: CustomizationViewModel by viewModels()
    private val wallpapers by lazy { WallpaperRepository.get(requireContext()) }
    private lateinit var strip: WallpaperGridAdapter
    private var stripFilter = "all"
    private var previewWallpaperId: String? = null
    private var sheetJob: Job? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _b = FragmentCustomizationBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        launcherHost?.setHeader(PageHeader(getString(R.string.title_customization), getString(R.string.subtitle_customization), R.drawable.ic_brush))
        b.previewCard.glass(GlassDrawable.Variant.PANEL, 18f)
        listOf(b.tileWallpapers, b.tileLayout, b.tileColors, b.tileApps, b.tileCursor, b.tileFonts).forEach {
            it.glass(GlassDrawable.Variant.TILE, 16f, 5f); FocusEffects.attach(it, 1.05f)
        }
        listOf(b.toggleMotion, b.toggleClock, b.toggleFavBar, b.iconStyleCard).forEach { it.glass(GlassDrawable.Variant.PANEL, 16f, 3f) }
        listOf(b.toggleMotion, b.toggleClock, b.toggleFavBar).forEach { FocusEffects.attach(it, 1.04f) }
        listOf(b.stripPrev, b.stripNext).forEach { it.glass(GlassDrawable.Variant.CIRCLE, 18f, 2f); FocusEffects.attach(it, 1.1f) }
        b.restoreButton.glass(GlassDrawable.Variant.CHIP, 22f, 3f)
        b.saveButton.glass(GlassDrawable.Variant.PRIMARY, 22f, 3f)
        listOf(b.restoreButton, b.saveButton).forEach { FocusEffects.attach(it, 1.05f); it.sizeDrawables(18f) }
        b.featuredTitle.sizeDrawables(22f)
        b.iconStyleTitle.sizeDrawables(18f)
        tintTiles()

        b.tileWallpapers.setOnClickListener { leaveThen { launcherHost?.navigate(LauncherPage.WALLPAPERS) } }
        b.tileLayout.setOnClickListener { sheet(R.string.cust_layout, R.drawable.ic_apps) { s -> CustomizationSheets.layout(requireContext(), s, vm::update) { WeatherSetup.chooseCity(this) { vm.update { d -> d.copy(weatherEnabled = true, weatherCity = vm.prefs.settings.value.weatherCity) } } } } }
        b.tileColors.setOnClickListener { sheet(R.string.cust_colors, R.drawable.ic_palette) { s -> CustomizationSheets.colors(requireContext(), s, vm::update) } }
        b.tileApps.setOnClickListener {
            sheet(R.string.cust_apps, R.drawable.ic_apps) { s ->
                CustomizationSheets.apps(requireContext(), s, vm::update, { AppActions.editFavorites(this) }) {
                    leaveThen { launcherHost?.navigate(LauncherPage.FAVORITES, bundleOf(NavArgs.MOVE_MODE to true)) }
                }
            }
        }
        b.tileCursor.setOnClickListener { sheet(R.string.cust_cursor, R.drawable.ic_cursor) { s -> CustomizationSheets.cursor(requireContext(), s, vm::update) } }
        b.tileFonts.setOnClickListener { sheet(R.string.cust_fonts, R.drawable.ic_text_size) { s -> CustomizationSheets.fonts(requireContext(), s, vm::update) } }

        b.toggleMotion.setOnClickListener { vm.update { it.copy(motionEnabled = !it.motionEnabled) } }
        b.toggleClock.setOnClickListener { vm.update { it.copy(showClockDate = !it.showClockDate) } }
        b.toggleFavBar.setOnClickListener { vm.update { it.copy(showFavoritesBar = !it.showFavoritesBar) } }
        buildIconStyles()

        b.restoreButton.setOnClickListener {
            Dialogs.confirm(requireContext(), getString(R.string.cust_restore), getString(R.string.cust_restore_confirm), getString(R.string.cust_restore)) { vm.restoreDefaults() }
        }
        b.saveButton.setOnClickListener { save(null) }

        strip = WallpaperGridAdapter(viewLifecycleOwner.lifecycleScope, ThumbnailLoader.get(requireContext()),
            onClick = { item, _ -> (item as? WpItem.Item)?.let { w -> vm.update { it.copy(wallpaperId = w.wp.id) } } },
            onLongClick = { _, _ -> }, onFocus = {})
        b.strip.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        b.strip.adapter = strip
        b.strip.itemAnimator = null
        b.strip.post {
            val h = b.strip.height.takeIf { it > 0 } ?: requireContext().dpi(68f)
            strip.cellH = h; strip.cellW = (h * 1.95f).toInt()
            strip.notifyItemRangeChanged(0, strip.itemCount)
        }
        b.stripPrev.setOnClickListener { b.strip.smoothScrollBy(-b.strip.width * 3 / 4, 0) }
        b.stripNext.setOnClickListener { b.strip.smoothScrollBy(b.strip.width * 3 / 4, 0) }
        buildChips()

        val apps = AppRepository.get(requireContext())
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    combine(vm.draft, apps.apps, vm.prefs.apps) { d, a, c -> Triple(d, a, c) }.collect { (d, a, c) ->
                        render(d)
                        b.preview.apps = AppLists.favorites(a, c)
                    }
                }
                launch { wallpapers.wallpapers.collect { renderStrip() } }
            }
        }
    }

    private fun TextView.sizeDrawables(dp: Float) {
        val s = requireContext().dpi(dp)
        val ds: Array<Drawable?> = compoundDrawablesRelative
        ds.forEach { it?.mutate()?.setBounds(0, 0, s, s); it?.setTint(LauncherUi.palette.accentLight) }
        setCompoundDrawablesRelative(ds[0], ds[1], ds[2], ds[3])
    }

    private fun tintTiles() {
        val pal = ThemeCatalog.palette(vm.draft.value.themeId, vm.draft.value.accentColor)
        listOf(b.tileWallpapersIcon, b.tileLayoutIcon, b.tileColorsIcon, b.tileAppsIcon, b.tileCursorIcon, b.tileFontsIcon).forEach { it.tint(pal.accentLight) }
    }

    private fun render(d: LauncherSettings) {
        b.preview.settings = d
        b.toggleMotionSwitch.setChecked(d.motionEnabled, true)
        b.toggleClockSwitch.setChecked(d.showClockDate, true)
        b.toggleFavBarSwitch.setChecked(d.showFavoritesBar, true)
        for (i in 0 until b.iconStyles.childCount) b.iconStyles.getChildAt(i).isActivated = b.iconStyles.getChildAt(i).tag == d.iconShape
        val cal = Calendar.getInstance()
        val h = cal.get(Calendar.HOUR_OF_DAY); val m = cal.get(Calendar.MINUTE)
        b.preview.clock = if (d.clockFormat == "24") String.format(java.util.Locale.US, "%02d:%02d", h, m) else "${if (h % 12 == 0) 12 else h % 12}:" + String.format(java.util.Locale.US, "%02d", m)
        b.preview.amPm = if (d.clockFormat == "24") "" else if (h < 12) "AM" else "PM"
        tintTiles()
        if (d.wallpaperId != previewWallpaperId) {
            previewWallpaperId = d.wallpaperId
            val wp = wallpapers.resolve(d.wallpaperId)
            viewLifecycleOwner.lifecycleScope.launch {
                val bmp = ThumbnailLoader.get(requireContext()).load(wp, 640, 360)
                if (previewWallpaperId == wp.id) b.preview.wallpaper = bmp
            }
            renderStrip()
        }
        b.saveButton.alpha = if (vm.isDirty) 1f else 0.8f
    }

    private val pharaonic = setOf("builtin:default_pyramids", "builtin:pyramids_sunset", "builtin:neon_tunnel", "builtin:neon_pulse")

    private fun renderStrip() {
        val list = wallpapers.wallpapers.value.filter { w: Wallpaper ->
            when (stripFilter) {
                "nature" -> w.category == WallpaperCategory.NATURE
                "space" -> w.category == WallpaperCategory.SPACE
                "cinematic" -> w.category == WallpaperCategory.CINEMATIC || w.category == WallpaperCategory.ABSTRACT
                "pharaonic" -> w.id in pharaonic
                "animated" -> w.category == WallpaperCategory.ANIMATED
                else -> true
            }
        }
        val favs = vm.prefs.wallpapers.value.favorites
        strip.submitList(list.map { WpItem.Item(it, it.id in favs, it.id == vm.draft.value.wallpaperId) })
    }

    private fun buildChips() {
        val defs = listOf(
            "all" to R.string.wp_cat_all_plain, "nature" to R.string.wp_cat_nature, "space" to R.string.wp_cat_space_short,
            "cinematic" to R.string.wp_cat_cinematic, "pharaonic" to R.string.wp_cat_pharaonic, "animated" to R.string.wp_cat_animated,
        )
        b.featuredChips.removeAllViews()
        defs.forEach { (key, label) ->
            val chip = AppCompatTextView(requireContext()).apply {
                tag = key
                setText(label)
                setTextAppearance(R.style.TextAppearance_Akaa_LabelBold)
                textSize = 12f
                gravity = android.view.Gravity.CENTER
                isFocusable = true; isClickable = true; isSingleLine = true
                setPadding(requireContext().dpi(16f), 0, requireContext().dpi(16f), 0)
                glass(GlassDrawable.Variant.CHIP, 18f, 2f)
                FocusEffects.attach(this, 1.05f)
                isActivated = key == stripFilter
                setOnClickListener { stripFilter = key; for (i in 0 until b.featuredChips.childCount) b.featuredChips.getChildAt(i).let { it.isActivated = it.tag == key }; renderStrip() }
            }
            b.featuredChips.addView(chip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, requireContext().dpi(34f)).apply { marginStart = requireContext().dpi(8f) })
        }
    }

    private fun buildIconStyles() {
        val shapes = listOf("original" to R.drawable.ic_image, "rounded" to R.drawable.ic_apps, "circle" to R.drawable.ic_avatar, "square" to R.drawable.ic_layers)
        b.iconStyles.removeAllViews()
        shapes.forEach { (shape, icon) ->
            val v = ImageView(requireContext()).apply {
                tag = shape
                setImageResource(icon)
                contentDescription = shape
                val p = requireContext().dpi(5f); setPadding(p, p, p, p)
                isFocusable = true; isClickable = true
                glass(if (shape == "circle") GlassDrawable.Variant.CIRCLE else GlassDrawable.Variant.CHIP, 8f, 2f)
                FocusEffects.attach(this, 1.12f)
                setOnClickListener { vm.update { it.copy(iconShape = shape) } }
            }
            b.iconStyles.addView(v, LinearLayout.LayoutParams(requireContext().dpi(40f), requireContext().dpi(30f)).apply { marginEnd = requireContext().dpi(8f) })
        }
    }

    /** Opens an option sheet whose rows follow the draft live. */
    private fun sheet(titleRes: Int, icon: Int, build: (LauncherSettings) -> List<SettingRow>) {
        var dialog: LauncherDialog? = null
        dialog = Dialogs.rows(requireContext(), getString(titleRes), build(vm.draft.value), icon = icon, widthDp = 560) { d, adapter ->
            d.button(R.string.close, primary = true)
            sheetJob?.cancel()
            sheetJob = viewLifecycleOwner.lifecycleScope.launch { vm.draft.collect { adapter.submitList(build(it)) } }
            d.onDismiss { sheetJob?.cancel() }
        }
    }

    private fun save(then: (() -> Unit)?) {
        viewLifecycleOwner.lifecycleScope.launch {
            val recreate = vm.save()
            Toast.makeText(requireContext(), R.string.saved_toast, Toast.LENGTH_SHORT).show()
            if (recreate) launcherHost?.recreateUi() else then?.invoke()
        }
    }

    /** Runs [action] after resolving unsaved changes (Save / Discard / Cancel). */
    private fun leaveThen(action: () -> Unit) { if (canLeave(action)) action() }

    override fun canLeave(proceed: () -> Unit): Boolean {
        if (!vm.isDirty) return true
        LauncherDialog(requireContext()).title(R.string.unsaved_title).message(R.string.unsaved_message).icon(R.drawable.ic_brush)
            .button(R.string.cancel)
            .button(R.string.discard) { vm.discard(); proceed() }
            .button(R.string.save, primary = true) { save(proceed) }
            .show()
        return false
    }

    override fun onBackPressed(): Boolean = !canLeave { launcherHost?.navigate(LauncherPage.HOME) }

    override fun onDestroyView() { sheetJob?.cancel(); super.onDestroyView(); _b = null }
}
