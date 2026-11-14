package com.akaaflix.launcher.settings

import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.akaaflix.launcher.apps.AppRepository
import com.akaaflix.launcher.apps.manage.ActionPath
import com.akaaflix.launcher.apps.manage.ActionPlan
import com.akaaflix.launcher.apps.manage.ManagedPackage
import com.akaaflix.launcher.apps.manage.PackageAction
import com.akaaflix.launcher.apps.manage.PackageActions
import com.akaaflix.launcher.apps.manage.PackageIcons
import com.akaaflix.launcher.apps.manage.PackageInventory
import com.akaaflix.launcher.apps.manage.Protection
import com.akaaflix.launcher.apps.privileged.PackageCommands
import com.akaaflix.launcher.apps.privileged.Privileged
import com.akaaflix.launcher.apps.ui.AppActions
import com.akaaflix.launcher.core.R
import com.akaaflix.launcher.core.ui.dialog.Dialogs
import com.akaaflix.launcher.core.ui.dialog.LauncherDialog
import com.akaaflix.launcher.core.ui.dpi
import com.akaaflix.launcher.core.ui.rows.SettingRow
import com.akaaflix.launcher.core.util.Bytes
import com.akaaflix.launcher.core.util.SafeIntents
import com.akaaflix.launcher.core.util.str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/**
 * Settings → App management → App manager: every installed package (and pre-installed packages
 * removed for this user), with the actions Android really allows. Privileged actions run through
 * Shizuku when the user enabled it; otherwise the system App info screen or the exact ADB command
 * is offered. Critical system components are never disabled or removed from here.
 */
class AppManagerPage(private val fragment: Fragment, private val onChanged: () -> Unit) {
    private val ctx: Context get() = fragment.requireContext()
    private var all: List<ManagedPackage> = emptyList()
    private var loading = true
    private var filter = PackageInventory.Filter.ALL
    private var query = ""
    private var shizuku = Privileged.Status(Privileged.State.NOT_INSTALLED)

    fun reload() {
        loading = true
        onChanged()
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            val c = ctx.applicationContext
            val list = withContext(Dispatchers.IO) { PackageInventory.load(c) }
            shizuku = Privileged.status(c)
            all = list; loading = false
            onChanged()
        }
    }

    fun rows(): List<SettingRow> {
        val c = ctx
        val rows = ArrayList<SettingRow>()
        rows += SettingRow.Action("shizuku", c.getString(R.string.am_shizuku), shizukuText(c), R.drawable.ic_shield,
            value = c.getString(shizukuShort(shizuku.state))) { onShizukuClick() }
        val filters = PackageInventory.Filter.entries
        val labels = listOf(R.string.am_filter_all, R.string.am_filter_user, R.string.am_filter_system, R.string.am_filter_disabled, R.string.am_filter_removed).map { c.getString(it) }
        rows += SettingRow.Choice("filter", c.getString(R.string.am_show), icon = R.drawable.ic_sort, options = labels, selected = filters.indexOf(filter)) { i ->
            filter = filters[i]; onChanged()
        }
        rows += SettingRow.Action("search", c.getString(R.string.home_search), icon = R.drawable.ic_search, value = query.ifBlank { null }) {
            Dialogs.input(c, c.getString(R.string.home_search), c.getString(R.string.am_search_hint), query) { q -> query = q; onChanged() }
        }
        if (loading) { rows += SettingRow.Info("loading", c.getString(R.string.am_loading), null, R.drawable.ic_refresh); return rows }
        val shown = PackageInventory.filter(all, filter, query)
        rows += SettingRow.Header("h_list", c.str(R.string.am_count_fmt, shown.size, all.count { it.installed }, all.count { !it.installed }))
        val iconPx = c.dpi(36f)
        val scope = fragment.viewLifecycleOwner.lifecycleScope
        shown.forEach { p ->
            rows += SettingRow.Action("pkg_" + p.packageName, p.label, subtitle(c, p), value = badge(c, p),
                iconLoader = PackageIcons.loader(scope, c.applicationContext, p.packageName, iconPx)) { openPackage(p) }
        }
        return rows
    }

    private fun subtitle(c: Context, p: ManagedPackage): String {
        val parts = mutableListOf(p.packageName)
        if (p.versionName.isNotBlank()) parts += "v" + p.versionName
        parts += c.getString(typeLabel(p))
        if (p.apkBytes > 0) parts += Bytes.storage(p.apkBytes)
        // Each part is direction-isolated so Latin names, sizes and Arabic labels never get mixed up in RTL.
        val bidi = androidx.core.text.BidiFormatter.getInstance()
        return parts.joinToString(" • ") { bidi.unicodeWrap(it) }
    }

    private fun badge(c: Context, p: ManagedPackage): String? = when {
        !p.installed -> c.getString(R.string.am_state_removed)
        !p.enabled -> c.getString(R.string.am_state_disabled)
        p.protection == Protection.CRITICAL -> c.getString(R.string.am_state_protected)
        else -> null
    }

    private fun typeLabel(p: ManagedPackage) = when {
        !p.isSystem -> R.string.am_type_user
        p.isUpdatedSystem -> R.string.am_type_updated
        else -> R.string.am_type_system
    }

    private fun shizukuShort(s: Privileged.State) = when (s) {
        Privileged.State.READY -> R.string.am_shizuku_ready
        Privileged.State.PERMISSION_REQUIRED -> R.string.am_shizuku_permission
        Privileged.State.PERMISSION_DENIED -> R.string.am_shizuku_denied
        Privileged.State.NOT_RUNNING -> R.string.am_shizuku_not_running
        Privileged.State.NOT_INSTALLED -> R.string.am_shizuku_not_installed
        Privileged.State.UNSUPPORTED_ANDROID -> R.string.am_shizuku_unsupported
    }

    private fun shizukuText(c: Context): String = when (shizuku.state) {
        Privileged.State.READY -> c.str(R.string.am_shizuku_ready_sub, if (shizuku.isRoot) "root" else "ADB")
        else -> c.getString(R.string.am_shizuku_sub)
    }

    private fun onShizukuClick() {
        val c = ctx
        when (shizuku.state) {
            Privileged.State.PERMISSION_REQUIRED -> Privileged.requestPermission { granted ->
                fragment.view?.post {
                    if (!fragment.isAdded) return@post
                    shizuku = Privileged.status(c); onChanged()
                    Toast.makeText(c, if (granted) R.string.am_shizuku_granted else R.string.am_shizuku_denied, Toast.LENGTH_LONG).show()
                }
            }
            Privileged.State.READY -> Dialogs.info(c, c.getString(R.string.am_shizuku), shizukuText(c))
            else -> Dialogs.info(c, c.getString(R.string.am_shizuku), c.getString(R.string.am_shizuku_help))
        }
    }

    private fun openPackage(p: ManagedPackage) {
        val c = ctx
        var dialog: LauncherDialog? = null
        fun close() = dialog?.dialog?.dismiss()
        val fmt = DateFormat.getDateInstance(DateFormat.MEDIUM)
        val rows = ArrayList<SettingRow>()
        rows += SettingRow.Info("pkg", c.getString(R.string.am_package), p.packageName, R.drawable.ic_tag)
        rows += SettingRow.Info("ver", c.getString(R.string.row_version), "${p.versionName.ifBlank { "–" }} (${p.versionCode})", R.drawable.ic_info)
        rows += SettingRow.Info("type", c.getString(R.string.app_type_title), c.getString(typeLabel(p)), R.drawable.ic_shield)
        rows += SettingRow.Info("state", c.getString(R.string.am_status), c.getString(when {
            !p.installed -> R.string.am_state_removed_long
            !p.enabled -> R.string.am_state_disabled
            else -> R.string.am_state_installed
        }), R.drawable.ic_apps)
        p.category?.let { rows += SettingRow.Info("cat", c.getString(R.string.app_category), c.getString(AppActions.categoryLabel(it)), R.drawable.ic_tag) }
            ?: run { rows += SettingRow.Info("cat", c.getString(R.string.app_category), c.getString(R.string.am_category_component), R.drawable.ic_tag) }
        if (p.apkBytes > 0) rows += SettingRow.Info("size", c.getString(R.string.am_apk_size), Bytes.storage(p.apkBytes), R.drawable.ic_storage)
        if (p.lastUpdateTime > 0) rows += SettingRow.Info("upd", c.getString(R.string.am_updated), fmt.format(Date(p.lastUpdateTime)), R.drawable.ic_clock)
        when (p.protection) {
            Protection.CRITICAL -> rows += SettingRow.Info("prot", c.getString(R.string.am_critical_note), null, R.drawable.ic_lock)
            Protection.CAUTION -> if (p.isHomeApp) rows += SettingRow.Info("home", c.getString(R.string.am_home_app_note), null, R.drawable.ic_home)
            Protection.NORMAL -> Unit
        }
        rows += SettingRow.Header("h_actions", c.getString(R.string.am_actions))
        PackageActions.plan(p, shizuku.ready, Build.VERSION.SDK_INT).forEach { plan ->
            rows += SettingRow.Action("a_" + plan.action.name, c.getString(actionLabel(plan.action)), pathNote(c, plan), actionIcon(plan.action),
                danger = plan.action in DANGEROUS, showChevron = plan.path != ActionPath.DIRECT) { close(); run(p, plan) }
        }
        dialog = Dialogs.rows(c, p.label, rows, widthDp = 560) { d, _ ->
            PackageIcons.loader(fragment.viewLifecycleOwner.lifecycleScope, c.applicationContext, p.packageName, c.dpi(40f))(d.iconView())
            d.button(R.string.close, primary = true)
        }
    }

    private fun pathNote(c: Context, plan: ActionPlan): String? = when (plan.path) {
        ActionPath.DIRECT -> null
        ActionPath.SHIZUKU -> c.getString(R.string.am_path_shizuku)
        ActionPath.SYSTEM_SCREEN -> c.getString(R.string.am_path_system)
        ActionPath.ADB_ONLY -> c.getString(R.string.am_path_adb)
    }

    private fun actionLabel(a: PackageAction) = when (a) {
        PackageAction.LAUNCH -> R.string.app_open
        PackageAction.APP_INFO -> R.string.app_info
        PackageAction.UNINSTALL -> R.string.app_uninstall
        PackageAction.UNINSTALL_UPDATES -> R.string.am_uninstall_updates
        PackageAction.DISABLE -> R.string.am_disable
        PackageAction.ENABLE -> R.string.am_enable
        PackageAction.REMOVE_FOR_USER -> R.string.am_remove_user
        PackageAction.RESTORE -> R.string.am_restore
    }

    private fun actionIcon(a: PackageAction) = when (a) {
        PackageAction.LAUNCH -> R.drawable.ic_open
        PackageAction.APP_INFO -> R.drawable.ic_info
        PackageAction.UNINSTALL, PackageAction.REMOVE_FOR_USER -> R.drawable.ic_trash
        PackageAction.UNINSTALL_UPDATES -> R.drawable.ic_restore
        PackageAction.DISABLE -> R.drawable.ic_eye_off
        PackageAction.ENABLE, PackageAction.RESTORE -> R.drawable.ic_download
    }

    private fun run(p: ManagedPackage, plan: ActionPlan) {
        val c = ctx
        when (plan.path) {
            ActionPath.DIRECT -> when (plan.action) {
                PackageAction.LAUNCH -> {
                    val pm = c.packageManager
                    val i = pm.getLeanbackLaunchIntentForPackage(p.packageName) ?: pm.getLaunchIntentForPackage(p.packageName)
                    if (i == null || !SafeIntents.start(c, i)) Toast.makeText(c, R.string.app_launch_failed, Toast.LENGTH_SHORT).show()
                }
                PackageAction.APP_INFO -> SafeIntents.startOrToast(c, SafeIntents.appDetails(p.packageName))
                PackageAction.UNINSTALL -> SafeIntents.startOrToast(c, android.content.Intent(android.content.Intent.ACTION_DELETE,
                    android.net.Uri.fromParts("package", p.packageName, null)))
                else -> Unit
            }
            ActionPath.SYSTEM_SCREEN -> {
                // Android's own screen decides; tell the user what to press and how to do it with ADB.
                val adb = plan.command?.let { "\n\n" + c.str(R.string.am_adb_alternative, PackageCommands.asAdb(it)) } ?: ""
                LauncherDialog(c).title(actionLabel(plan.action)).icon(R.drawable.ic_settings)
                    .message(c.getString(R.string.am_system_screen_msg) + adb)
                    .button(R.string.cancel)
                    .button(R.string.app_info, primary = true) { SafeIntents.startOrToast(c, SafeIntents.appDetails(p.packageName)) }
                    .show()
            }
            ActionPath.ADB_ONLY -> {
                val undo = plan.undo?.let { "\n\n" + c.str(R.string.am_adb_undo, PackageCommands.asAdb(it)) } ?: ""
                LauncherDialog(c).title(actionLabel(plan.action)).icon(R.drawable.ic_shield)
                    .message(c.str(R.string.am_adb_only_msg, PackageCommands.asAdb(plan.command!!)) + undo)
                    .button(R.string.close)
                    .button(R.string.am_shizuku, primary = true) { onShizukuClick() }
                    .show()
            }
            ActionPath.SHIZUKU -> confirmThenExec(p, plan)
        }
    }

    private fun confirmThenExec(p: ManagedPackage, plan: ActionPlan) {
        val c = ctx
        val warn = buildString {
            append(c.str(R.string.am_confirm_fmt, c.getString(actionLabel(plan.action)), p.label, p.packageName))
            if (p.protection == Protection.CAUTION) append("\n\n").append(c.getString(R.string.am_caution_system))
            if (p.isHomeApp && plan.action in DANGEROUS) append("\n\n").append(c.getString(R.string.am_caution_home))
            plan.undo?.let { append("\n\n").append(c.str(R.string.am_undo_hint, c.getString(undoLabel(plan.action)))) }
        }
        LauncherDialog(c).title(actionLabel(plan.action)).icon(actionIcon(plan.action)).message(warn)
            .button(R.string.cancel)
            .button(actionLabel(plan.action), primary = true) { exec(p, plan) }
            .show()
    }

    private fun undoLabel(a: PackageAction) = when (a) {
        PackageAction.DISABLE -> R.string.am_enable
        PackageAction.REMOVE_FOR_USER -> R.string.am_restore
        else -> R.string.am_restore
    }

    private fun exec(p: ManagedPackage, plan: ActionPlan) {
        val c = ctx
        val cmd = plan.command ?: return
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            val r = Privileged.exec(c.applicationContext, cmd)
            if (!fragment.isAdded) return@launch
            if (r.ok) Toast.makeText(c, c.str(R.string.am_done_fmt, c.getString(actionLabel(plan.action)), p.label), Toast.LENGTH_LONG).show()
            else Dialogs.info(c, c.getString(R.string.am_failed), c.str(R.string.am_failed_fmt, PackageCommands.asAdb(cmd), r.output.take(400).ifBlank { r.code.toString() }))
            AppRepository.get(c).refresh(0)
            reload()
        }
    }

    companion object {
        private val DANGEROUS = setOf(PackageAction.UNINSTALL, PackageAction.UNINSTALL_UPDATES, PackageAction.DISABLE, PackageAction.REMOVE_FOR_USER)
    }
}
