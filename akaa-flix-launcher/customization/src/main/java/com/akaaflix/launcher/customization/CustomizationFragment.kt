package com.akaaflix.launcher.customization

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
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
        listOf(b.toggleMotion, b.toggleClock, b.toggleFavBar, b.iconSizeCard).forEach { it.glass(GlassDrawable.Variant.PANEL, 16f, 3f) }
        listOf(b.toggleMotion, b.toggleClock, b.toggleFavBar).forEach { FocusEffects.attach(it, 1.04f) }
        listOf(b.sizeMedium, b.sizeLarge).forEach { it.glass(GlassDrawable.Variant.CHIP, 15f, 2f); FocusEffects.attach(it, 1.08f) }
        // Icon size is locked to Large: the Medium option is removed.
        b.sizeMedium.visibility = View.GONE
        b.restoreButton.glass(GlassDrawable.Variant.CHIP, 22f, 3f)
        b.saveButton.glass(GlassDrawable.Variant.PRIMARY, 22f, 3f)
        listOf(b.restoreButton, b.saveButton).forEach { FocusEffects.attach(it, 1.05f); it.sizeDrawables(18f) }
        b.iconSizeTitle.sizeDrawables(18f)
        tintTiles()

        b.tileWallpapers.setOnClickListener { leaveThen { launcherHost?.navigate(LauncherPage.WALLPAPERS) } }
        b.tileLayout.setOnClickListener { sheet(R.string.cust_layout, R.drawable.ic_apps) { s -> CustomizationSheets.layout(requireContext(), s, vm::update) { WeatherSetup.open(this) } } }
        b.tileColors.setOnClickListener { sheet(R.string.cust_colors, R.drawable.ic_palette) { s -> CustomizationSheets.colors(requireContext(), s, vm::update) } }
        b.tileApps.setOnClickListener {
            sheet(R.string.cust_apps, R.drawable.ic_apps) { s ->
                CustomizationSheets.apps(requireContext(), s, vm::update,
                    editHome = { AppActions.editHomeApps(this) },
                    reorderHome = { leaveThen { launcherHost?.navigate(LauncherPage.HOME, bundleOf(NavArgs.MOVE_MODE to true)) } },
                    editFavorites = { AppActions.editFavorites(this) })
            }
        }
        b.tileCursor.setOnClickListener { sheet(R.string.cust_cursor, R.drawable.ic_cursor) { s -> CustomizationSheets.cursor(requireContext(), s, vm::update) } }
        b.tileFonts.setOnClickListener { sheet(R.string.cust_fonts, R.drawable.ic_text_size) { s -> CustomizationSheets.fonts(requireContext(), s, vm::update) } }

        b.toggleMotion.setOnClickListener { vm.update { it.copy(motionEnabled = !it.motionEnabled) } }
        b.toggleClock.setOnClickListener { vm.update { it.copy(showClockDate = !it.showClockDate) } }
        b.toggleFavBar.setOnClickListener { vm.update { it.copy(showFavoritesBar = !it.showFavoritesBar) } }
        b.sizeLarge.setOnClickListener { vm.update { it.copy(iconSize = "large") } }

        b.restoreButton.setOnClickListener {
            Dialogs.confirm(requireContext(), getString(R.string.cust_restore), getString(R.string.cust_restore_confirm), getString(R.string.cust_restore)) { vm.restoreDefaults() }
        }
        b.saveButton.setOnClickListener { save(null) }


        val apps = AppRepository.get(requireContext())
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    combine(vm.draft, apps.apps, vm.prefs.apps) { d, a, c -> Triple(d, a, c) }.collect { (d, a, c) ->
                        render(d)
                        b.preview.apps = AppLists.homeApps(a, c)
                    }
                }
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
        b.sizeLarge.isActivated = true
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
        }
        b.saveButton.alpha = if (vm.isDirty) 1f else 0.8f
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
