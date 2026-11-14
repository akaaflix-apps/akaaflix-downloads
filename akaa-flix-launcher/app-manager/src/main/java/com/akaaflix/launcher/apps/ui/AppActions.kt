package com.akaaflix.launcher.apps.ui

import com.akaaflix.launcher.core.util.str

import android.graphics.Bitmap
import android.os.Build
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.akaaflix.launcher.apps.AppLists
import com.akaaflix.launcher.apps.AppRepository
import com.akaaflix.launcher.apps.icons.IconCache
import com.akaaflix.launcher.apps.model.AppCategory
import com.akaaflix.launcher.apps.model.AppEntry
import com.akaaflix.launcher.apps.model.AppInstallType
import com.akaaflix.launcher.core.R
import com.akaaflix.launcher.core.nav.launcherHost
import com.akaaflix.launcher.core.ui.dialog.Dialogs
import com.akaaflix.launcher.core.ui.rows.SettingRow
import com.akaaflix.launcher.core.util.SafeIntents
import com.akaaflix.launcher.prefs.SettingsRepository
import kotlinx.coroutines.launch

/** App actions shared by every page: launch (with parental lock), options menu and pickers. */
object AppActions {

    fun open(fragment: Fragment, entry: AppEntry, source: View?) {
        val activity = fragment.activity ?: return
        val prefs = SettingsRepository.get(activity)
        val go = {
            if (AppRepository.get(activity).launch(activity, entry, source)) {
                prefs.editApps { it.recordLaunch(entry.key, System.currentTimeMillis()) }
            } else Toast.makeText(activity, R.string.app_launch_failed, Toast.LENGTH_SHORT).show()
        }
        if (entry.key in prefs.apps.value.locked && prefs.settings.value.hasPin) {
            fragment.launcherHost?.requirePin { go() } ?: go()
        } else go()
    }

    fun iconLoader(fragment: Fragment, entry: AppEntry, sizePx: Int = 96): (ImageView) -> Unit = { iv ->
        iv.tag = entry.key
        val cache = IconCache.get(fragment.requireContext())
        val cached = cache.peekIcon(entry, sizePx, "original")
        if (cached != null) iv.setImageBitmap(cached) else fragment.viewLifecycleOwner.lifecycleScope.launch {
            val b: Bitmap? = cache.icon(entry, sizePx, "original")
            if (iv.tag == entry.key) iv.setImageBitmap(b)
        }
    }

    fun openAppInfo(ctx: android.content.Context, entry: AppEntry) {
        SafeIntents.startOrToast(ctx, SafeIntents.appDetails(entry.packageName))
    }

    /** Adds/removes an app on the Home screen (independent of Favorites). Refuses when Home is full. */
    fun toggleHome(ctx: android.content.Context, entry: AppEntry) {
        val prefs = SettingsRepository.get(ctx)
        val apps = AppRepository.get(ctx).apps.value
        val custom = prefs.apps.value
        if (entry.key in custom.homeApps) {
            prefs.editApps { it.toggleHome(entry.key) }
            Toast.makeText(ctx, ctx.str(R.string.app_removed_home_toast, entry.label), Toast.LENGTH_SHORT).show()
            return
        }
        val onHome = AppLists.homeApps(apps, custom).size
        val cap = HomeCapacity.value
        if (onHome >= cap) { Toast.makeText(ctx, ctx.str(R.string.home_full_fmt, cap), Toast.LENGTH_LONG).show(); return }
        prefs.editApps { it.toggleHome(entry.key) }
        Toast.makeText(ctx, ctx.str(R.string.app_added_home_toast, entry.label), Toast.LENGTH_SHORT).show()
    }

    /** Adds/removes an app in Favorites (independent of the Home screen). */
    fun toggleFavorite(ctx: android.content.Context, entry: AppEntry) {
        val prefs = SettingsRepository.get(ctx)
        val wasFav = entry.key in prefs.apps.value.favorites
        prefs.editApps { it.toggleFavorite(entry.key) }
        Toast.makeText(ctx, ctx.str(if (wasFav) R.string.app_removed_fav_toast else R.string.app_added_fav_toast, entry.label), Toast.LENGTH_SHORT).show()
    }

    fun installTypeLabel(t: AppInstallType) = when (t) {
        AppInstallType.USER -> R.string.app_type_user
        AppInstallType.UPDATED_SYSTEM -> R.string.app_type_updated
        AppInstallType.SYSTEM -> R.string.app_type_system
        AppInstallType.PROTECTED -> R.string.app_type_protected
    }

    /** App options (Menu key / long-press). [onMove] enables the "Move" entry where reordering applies. */
    fun showMenu(fragment: Fragment, entry: AppEntry, source: View?, onMove: (() -> Unit)? = null) {
        val ctx = fragment.requireContext()
        val prefs = SettingsRepository.get(ctx)
        val repo = AppRepository.get(ctx)
        val custom = prefs.apps.value
        val s = prefs.settings.value
        val onHome = entry.key in custom.homeApps
        val isFav = entry.key in custom.favorites
        val locked = entry.key in custom.locked
        var dialog: com.akaaflix.launcher.core.ui.dialog.LauncherDialog? = null
        fun close() = dialog?.dialog?.dismiss()

        val rows = mutableListOf<SettingRow>()
        rows += SettingRow.Action("open", ctx.getString(R.string.app_open), icon = R.drawable.ic_open, showChevron = false) { close(); open(fragment, entry, source) }
        rows += SettingRow.Action("home", ctx.getString(if (onHome) R.string.app_remove_home else R.string.app_add_home),
            icon = R.drawable.ic_home, showChevron = false) { close(); toggleHome(ctx, entry) }
        rows += SettingRow.Action("fav", ctx.getString(if (isFav) R.string.app_remove_favorite else R.string.app_add_favorite),
            icon = if (isFav) R.drawable.ic_star_filled else R.drawable.ic_star, showChevron = false) { close(); toggleFavorite(ctx, entry) }
        if (onMove != null) rows += SettingRow.Action("move", ctx.getString(R.string.app_move), icon = R.drawable.ic_move, showChevron = false) { close(); onMove() }
        rows += SettingRow.Action("hide", ctx.getString(R.string.app_hide), icon = R.drawable.ic_eye_off, showChevron = false) {
            close()
            prefs.editApps { it.copy(hidden = it.hidden + entry.key) }
            Toast.makeText(ctx, ctx.str(R.string.app_hidden_toast, entry.label), Toast.LENGTH_LONG).show()
        }
        rows += SettingRow.Action("lock", ctx.getString(if (locked) R.string.app_unlock else R.string.app_lock),
            icon = if (locked) R.drawable.ic_lock_open else R.drawable.ic_lock, showChevron = false) {
            close()
            when {
                !s.hasPin -> Toast.makeText(ctx, R.string.app_set_pin_first, Toast.LENGTH_LONG).show()
                locked -> fragment.launcherHost?.requirePin { prefs.editApps { it.copy(locked = it.locked - entry.key) } }
                else -> prefs.editApps { it.copy(locked = it.locked + entry.key) }
            }
        }
        val catOptions = listOf(
            ctx.getString(R.string.app_category_auto) + " (" + ctx.getString(categoryLabel(entry.autoCategory)) + ")",
            ctx.getString(R.string.app_category_entertainment), ctx.getString(R.string.app_category_tools), ctx.getString(R.string.app_category_other),
        )
        val current = when (custom.categories[entry.key]) { "entertainment" -> 1; "tools" -> 2; "other" -> 3; else -> 0 }
        rows += SettingRow.Choice("cat", ctx.getString(R.string.app_category), icon = R.drawable.ic_tag, options = catOptions, selected = current) { i ->
            prefs.editApps { c ->
                val m = c.categories.toMutableMap()
                when (i) { 1 -> m[entry.key] = "entertainment"; 2 -> m[entry.key] = "tools"; 3 -> m[entry.key] = "other"; else -> m.remove(entry.key) }
                c.copy(categories = m)
            }
        }

        // Management according to what Android really allows for this app.
        rows += SettingRow.Header("mgmt_h", ctx.getString(R.string.app_manage_header))
        rows += SettingRow.Info("type", ctx.getString(R.string.app_type_title), ctx.getString(installTypeLabel(entry.installType)), R.drawable.ic_shield)
        rows += SettingRow.Action("info", ctx.getString(R.string.app_info), icon = R.drawable.ic_info, showChevron = false) { close(); openAppInfo(ctx, entry) }
        when (entry.installType) {
            AppInstallType.USER -> rows += SettingRow.Action("uninstall", ctx.getString(R.string.app_uninstall), icon = R.drawable.ic_trash, danger = true, showChevron = false) {
                close(); SafeIntents.startOrToast(ctx, repo.uninstallIntent(entry))
            }
            AppInstallType.UPDATED_SYSTEM -> rows += SettingRow.Action("manage", ctx.getString(R.string.app_manage_updated), ctx.getString(R.string.app_manage_if_allowed),
                R.drawable.ic_settings) { close(); openAppInfo(ctx, entry) }
            AppInstallType.SYSTEM -> rows += SettingRow.Action("manage", ctx.getString(R.string.app_manage_system), ctx.getString(R.string.app_manage_if_allowed),
                R.drawable.ic_settings) { close(); openAppInfo(ctx, entry) }
            AppInstallType.PROTECTED -> rows += SettingRow.Info("protected", ctx.getString(R.string.app_protected_note), null, R.drawable.ic_lock)
        }

        val shortcuts = repo.shortcuts(entry)
        if (shortcuts.isNotEmpty() && Build.VERSION.SDK_INT >= 25) {
            rows += SettingRow.Header("sc_h", ctx.getString(R.string.app_shortcuts))
            shortcuts.forEach { sc ->
                rows += SettingRow.Action("sc_" + sc.id, sc.shortLabel ?: sc.id, icon = R.drawable.ic_shortcut, showChevron = false) {
                    close(); repo.startShortcut(sc, source)
                }
            }
        }
        dialog = Dialogs.rows(ctx, entry.label, rows, widthDp = 480) { d, _ ->
            iconLoader(fragment, entry)(d.iconView())
        }
    }

    fun categoryLabel(c: AppCategory) = when (c) {
        AppCategory.ENTERTAINMENT -> R.string.app_category_entertainment
        AppCategory.TOOLS -> R.string.app_category_tools
        AppCategory.OTHER -> R.string.app_category_other
    }

    /** Multi-select picker (favourites, hidden apps, locked apps). */
    fun pickApps(fragment: Fragment, title: CharSequence, candidates: List<AppEntry>, selected: Set<String>, onDone: (Set<String>) -> Unit) {
        val ctx = fragment.requireContext()
        val chosen = LinkedHashSet(selected)
        val rows = candidates.map { e ->
            SettingRow.Toggle("p_" + e.key, e.label, checked = e.key in selected, iconLoader = iconLoader(fragment, e)) { on ->
                if (on) chosen.add(e.key) else chosen.remove(e.key)
            }
        }
        Dialogs.rows(ctx, title, rows, icon = R.drawable.ic_apps, widthDp = 500) { d, _ ->
            d.button(R.string.cancel).button(R.string.picker_done, primary = true) { onDone(chosen) }
        }
    }

    fun pickOne(fragment: Fragment, title: CharSequence, candidates: List<AppEntry>, onPick: (AppEntry) -> Unit) {
        val ctx = fragment.requireContext()
        var dialog: com.akaaflix.launcher.core.ui.dialog.LauncherDialog? = null
        val rows = candidates.map { e ->
            SettingRow.Action("o_" + e.key, e.label, iconLoader = iconLoader(fragment, e), showChevron = false) { dialog?.dialog?.dismiss(); onPick(e) }
        }
        dialog = Dialogs.rows(ctx, title, rows, icon = R.drawable.ic_plus, widthDp = 480)
    }

    /** Picker for the Home screen apps (Home "Add app" card, Customization, Settings). */
    fun editHomeApps(fragment: Fragment) {
        val ctx = fragment.requireContext()
        val prefs = SettingsRepository.get(ctx)
        val apps = AppRepository.get(ctx).apps.value
        val custom = prefs.apps.value
        val visible = AppLists.sort(apps.filter { it.key !in custom.hidden }, custom, "az")
        val onHomeFirst = AppLists.homeApps(apps, custom) + visible.filter { it.key !in custom.homeApps }
        val cap = HomeCapacity.value
        val title = if (cap < Int.MAX_VALUE) ctx.str(R.string.picker_home_title_fmt, cap) else ctx.getString(R.string.picker_home_title)
        pickApps(fragment, title, onHomeFirst, custom.homeApps.toSet()) { set ->
            val kept = custom.homeApps.filter { it in set }
            var next = kept + set.filter { it !in kept }
            if (next.size > cap) { next = next.take(cap); Toast.makeText(ctx, ctx.str(R.string.home_limit_fmt, cap), Toast.LENGTH_LONG).show() }
            prefs.editApps { c -> c.copy(homeApps = next + c.homeApps.filter { it !in next && it in c.hidden }, homeInitialized = true) }
        }
    }

    /** Picker for Favorites (Favorites page, Customization, Settings). Never touches the Home screen. */
    fun editFavorites(fragment: Fragment) {
        val ctx = fragment.requireContext()
        val prefs = SettingsRepository.get(ctx)
        val apps = AppRepository.get(ctx).apps.value
        val custom = prefs.apps.value
        val visible = AppLists.sort(apps.filter { it.key !in custom.hidden }, custom, "az")
        val favFirst = AppLists.favorites(apps, custom) + visible.filter { it.key !in custom.favorites }
        pickApps(fragment, ctx.getString(R.string.picker_favorites_title), favFirst, custom.favorites.toSet()) { set ->
            prefs.editApps { c ->
                val kept = c.favorites.filter { it in set }
                c.copy(favorites = kept + set.filter { it !in kept })
            }
        }
    }
}

/** Number of apps the Home grid can show (rows × columns − the "Add app" card); set by the Home page. */
object HomeCapacity {
    @Volatile var value: Int = Int.MAX_VALUE
}
