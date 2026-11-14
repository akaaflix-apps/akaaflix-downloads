package com.akaaflix.launcher.settings

import com.akaaflix.launcher.core.util.str

import android.app.role.RoleManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.akaaflix.launcher.apps.AppLists
import com.akaaflix.launcher.apps.AppRepository
import com.akaaflix.launcher.apps.icons.IconCache
import com.akaaflix.launcher.apps.ui.AppActions
import com.akaaflix.launcher.core.AppConfig
import com.akaaflix.launcher.core.R
import com.akaaflix.launcher.core.nav.BackHandler
import com.akaaflix.launcher.core.nav.LauncherPage
import com.akaaflix.launcher.core.nav.LeaveGuard
import com.akaaflix.launcher.core.nav.NavArgs
import com.akaaflix.launcher.core.nav.PageHeader
import com.akaaflix.launcher.core.nav.launcherHost
import com.akaaflix.launcher.core.perf.DevicePerformance
import com.akaaflix.launcher.core.ui.FocusEffects
import com.akaaflix.launcher.core.ui.GlassDrawable
import com.akaaflix.launcher.core.ui.LauncherUi
import com.akaaflix.launcher.core.ui.dialog.Dialogs
import com.akaaflix.launcher.core.ui.dialog.LauncherDialog
import com.akaaflix.launcher.core.ui.dialog.PinDialog
import com.akaaflix.launcher.core.ui.dpi
import com.akaaflix.launcher.core.ui.glass
import com.akaaflix.launcher.core.ui.rows.SettingRow
import com.akaaflix.launcher.core.ui.rows.SettingRowsAdapter
import com.akaaflix.launcher.core.ui.tint
import com.akaaflix.launcher.core.util.Bytes
import com.akaaflix.launcher.core.util.SafeIntents
import com.akaaflix.launcher.device.info.DeviceInfoProvider
import com.akaaflix.launcher.device.status.BluetoothMonitor
import com.akaaflix.launcher.device.status.NetworkMonitor
import com.akaaflix.launcher.device.system.SystemShortcuts
import com.akaaflix.launcher.device.update.UpdateChecker
import com.akaaflix.launcher.device.update.UpdateResult
import com.akaaflix.launcher.home.ui.HomeDialogs
import com.akaaflix.launcher.home.ui.WeatherSetup
import com.akaaflix.launcher.prefs.LauncherSettings
import com.akaaflix.launcher.prefs.PinHasher
import com.akaaflix.launcher.prefs.SettingsRepository
import com.akaaflix.launcher.remote.Keys
import com.akaaflix.launcher.settings.databinding.FragmentSettingsBinding
import com.akaaflix.launcher.wallpaper.ThumbnailLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Settings page from the reference: 12 section tiles, quick toggles panel and device cards. */
class SettingsFragment : Fragment(), BackHandler, LeaveGuard, SettingsActions {
    private var _b: FragmentSettingsBinding? = null
    private val b get() = _b!!
    private val prefs by lazy { SettingsRepository.get(requireContext()) }
    private val apps by lazy { AppRepository.get(requireContext()) }
    private val network by lazy { NetworkMonitor(requireContext()) }
    private val bluetooth by lazy { BluetoothMonitor(requireContext()) }
    private val info by lazy { DeviceInfoProvider(requireContext()) }
    private val rowsAdapter = SettingRowsAdapter()
    private var section: SettingsSection? = null
    /** Settings → App management → App manager (sub page of the Apps section). */
    private var manager: AppManagerPage? = null
    private val refreshTick = MutableStateFlow(0)
    private var quickDraft: LauncherSettings? = null

    private val createBackup = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) viewLifecycleOwner.lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) { try { requireContext().contentResolver.openOutputStream(uri)?.use { it.write(prefs.exportBackup().toByteArray()) }; true } catch (_: Exception) { false } }
            toast(if (ok) R.string.backup_done else R.string.backup_failed)
        }
    }
    private val openBackup = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewLifecycleOwner.lifecycleScope.launch {
            val text = withContext(Dispatchers.IO) { try { requireContext().contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } } catch (_: Exception) { null } }
            restoreFrom(text)
        }
    }
    private val roleRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { refreshTick.value++ }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _b = FragmentSettingsBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        launcherHost?.setHeader(PageHeader(getString(R.string.title_settings), getString(R.string.subtitle_settings), R.drawable.ic_settings))
        buildTiles()
        b.rightPanel.glass(GlassDrawable.Variant.PANEL, 20f)
        b.quickToggles.glass(GlassDrawable.Variant.PANEL, 14f)
        b.versionPill.glass(GlassDrawable.Variant.CHIP, 14f)
        b.versionPill.text = str(R.string.set_version_fmt, AppConfig.versionName)
        b.versionCheck.glass(GlassDrawable.Variant.CIRCLE, 11f).forceSelected = true
        b.versionCheck.tint(0xFFFFFFFF.toInt())
        listOf(b.storageCard, b.ramCard, b.deviceCard).forEach { it.glass(GlassDrawable.Variant.PANEL, 16f, 3f); FocusEffects.attach(it, 1.04f) }
        listOf(b.storageCardIcon, b.ramCardIcon, b.deviceCardIcon).forEach { it.tint(LauncherUi.palette.accentLight) }
        b.deviceCardProgress.visibility = View.GONE
        listOf(b.qtAutostart, b.qtNetwork, b.qtClock, b.qtMotion, b.qtAnimated).forEach { it.glass(GlassDrawable.Variant.PANEL, 10f, 2f).cardStyle = "minimal" }
        b.checkUpdates.glass(GlassDrawable.Variant.CHIP, 20f, 3f)
        b.restoreDefaults.glass(GlassDrawable.Variant.CHIP, 20f, 3f)
        b.applyChanges.glass(GlassDrawable.Variant.PRIMARY, 20f, 3f)
        listOf(b.checkUpdates, b.restoreDefaults, b.applyChanges).forEach { FocusEffects.attach(it, 1.04f); it.sizeDrawables(16f) }
        b.detailBack.glass(GlassDrawable.Variant.CIRCLE, 20f, 3f)
        FocusEffects.attach(b.detailBack, 1.1f)
        b.detailList.layoutManager = LinearLayoutManager(requireContext())
        b.detailList.adapter = rowsAdapter
        b.detailList.itemAnimator = null

        b.storageCard.setOnClickListener { SystemShortcuts.storage(requireContext()) }
        b.ramCard.setOnClickListener { HomeDialogs.showDeviceInfo(requireContext()) }
        b.deviceCard.setOnClickListener { HomeDialogs.showDeviceInfo(requireContext()) }
        b.checkUpdates.setOnClickListener { checkUpdates() }
        b.restoreDefaults.setOnClickListener {
            Dialogs.confirm(requireContext(), getString(R.string.set_restore_defaults), getString(R.string.set_restore_confirm), getString(R.string.set_restore_defaults)) {
                viewLifecycleOwner.lifecycleScope.launch { prefs.resetSettings(); quickDraft = null; toast(R.string.set_restored) }
            }
        }
        b.applyChanges.setOnClickListener { applyQuick() }
        if (Build.VERSION.SDK_INT >= 29) b.qtAutostart.setOnClickListener { requestDefaultLauncher() }
        val quick = mapOf(
            b.qtAutostart to { s: LauncherSettings -> s.copy(autoStartOnBoot = !s.autoStartOnBoot) },
            b.qtNetwork to { s: LauncherSettings -> s.copy(showNetworkStatus = !s.showNetworkStatus) },
            b.qtClock to { s: LauncherSettings -> s.copy(showClockDate = !s.showClockDate) },
            b.qtMotion to { s: LauncherSettings -> s.copy(motionEnabled = !s.motionEnabled) },
            b.qtAnimated to { s: LauncherSettings -> s.copy(animatedWallpaper = !s.animatedWallpaper) },
        )
        quick.forEach { (v, t) -> if (v !== b.qtAutostart || Build.VERSION.SDK_INT < 29) v.setOnClickListener { quickDraft = t(quickDraft ?: prefs.settings.value); renderQuick() } }
        b.detailBack.setOnClickListener { closeDetail() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                network.start(); bluetooth.start()
                try {
                    launch {
                        combine(prefs.settings, prefs.apps, apps.apps, network.state, bluetooth.state) { s, c, a, n, bt -> listOf(s, c, a, n, bt) }
                            .combine(refreshTick) { l, _ -> l }
                            .collect { renderAll() }
                    }
                    launch { while (true) { renderCards(); kotlinx.coroutines.delay(10_000) } }
                    kotlinx.coroutines.awaitCancellation()
                } finally { network.stop(); bluetooth.stop() }
            }
        }
        SettingsSection.of(arguments?.getString(NavArgs.OPEN_SECTION))?.let { openDetail(it) }
    }

    override fun onResume() { super.onResume(); refreshTick.value++ }

    private fun TextView.sizeDrawables(dp: Float) {
        val s = requireContext().dpi(dp)
        val ds = compoundDrawablesRelative
        ds.forEach { it?.mutate()?.setBounds(0, 0, s, s); it?.setTint(LauncherUi.palette.accentLight) }
        setCompoundDrawablesRelative(ds[0], ds[1], ds[2], ds[3])
    }

    private fun buildTiles() {
        val grid = b.tiles
        grid.removeAllViews()
        val ctx = requireContext()
        SettingsSection.entries.forEach { sec ->
            val tile = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                isFocusable = true; isClickable = true
                contentDescription = getString(sec.title)
                setPadding(ctx.dpi(10f), ctx.dpi(6f), ctx.dpi(10f), ctx.dpi(6f))
                glass(GlassDrawable.Variant.TILE, 16f, 5f)
                FocusEffects.attach(this, 1.05f)
                setOnClickListener { openDetail(sec) }
            }
            tile.addView(android.widget.ImageView(ctx).apply { setImageResource(sec.icon); tint(LauncherUi.palette.accentLight) }, LinearLayout.LayoutParams(ctx.dpi(36f), ctx.dpi(36f)))
            tile.addView(AppCompatTextView(ctx).apply {
                setText(sec.title); setTextAppearance(R.style.TextAppearance_Akaa_Section); gravity = Gravity.CENTER; maxLines = 1
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = ctx.dpi(6f) })
            tile.addView(AppCompatTextView(ctx).apply {
                setText(sec.subtitle); setTextAppearance(R.style.TextAppearance_Akaa_Small); gravity = Gravity.CENTER; maxLines = 1
                textDirection = View.TEXT_DIRECTION_LOCALE
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = ctx.dpi(3f) })
            val lp = android.widget.GridLayout.LayoutParams(
                android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f),
                android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f)).apply { width = 0; height = 0; setMargins(ctx.dpi(4f), ctx.dpi(4f), ctx.dpi(4f), ctx.dpi(4f)) }
            grid.addView(tile, lp)
        }
    }

    private fun state(): SettingsState {
        val ctx = requireContext()
        val home = com.akaaflix.launcher.apps.home.HomeStatus.read(ctx)
        val log = com.akaaflix.launcher.prefs.BootLog.read(ctx)
        val startup = com.akaaflix.launcher.apps.home.Startup.evaluate(home, log, Build.VERSION.SDK_INT, prefs.settings.value.autoStartOnBoot)
        return SettingsState(prefs.settings.value, prefs.apps.value, apps.apps.value, network.state.value, bluetooth.state.value,
            home.isDefault, DevicePerformance.profile(ctx), info.screenTimeoutMs(), home, startup, log,
            com.akaaflix.launcher.apps.privileged.Privileged.status(ctx))
    }

    private fun renderAll() {
        if (_b == null) return
        renderQuick()
        val m = manager
        if (m != null) { rowsAdapter.submitList(m.rows()); return }
        section?.let { rowsAdapter.submitList(SettingsSections.build(requireContext(), it, state(), this)) }
    }

    private fun renderQuick() {
        val s = quickDraft ?: prefs.settings.value
        if (Build.VERSION.SDK_INT >= 29) {
            // Android 10+: start-up is decided by the default Home app – show that real state instead of a switch.
            (b.qtAutostart.getChildAt(0) as? TextView)?.setText(R.string.hs_quick_default)
            b.qtAutostartSwitch.setChecked(com.akaaflix.launcher.apps.home.HomeStatus.read(requireContext()).isDefault, true)
        } else b.qtAutostartSwitch.setChecked(s.autoStartOnBoot, true)
        b.qtNetworkSwitch.setChecked(s.showNetworkStatus, true)
        b.qtClockSwitch.setChecked(s.showClockDate, true)
        b.qtMotionSwitch.setChecked(s.motionEnabled, true)
        b.qtAnimatedSwitch.setChecked(s.animatedWallpaper, true)
        b.applyChanges.alpha = if (quickDraft != null && quickDraft != prefs.settings.value) 1f else 0.75f
    }

    private fun applyQuick() {
        val d = quickDraft ?: return
        val wantsAuto = d.autoStartOnBoot && !prefs.settings.value.autoStartOnBoot
        prefs.edit { it.copy(autoStartOnBoot = d.autoStartOnBoot, showNetworkStatus = d.showNetworkStatus, showClockDate = d.showClockDate,
            motionEnabled = d.motionEnabled, animatedWallpaper = d.animatedWallpaper) }
        quickDraft = null
        toast(R.string.saved_toast)
        if (wantsAuto) explainAutoStartIfNeeded()
    }

    private fun renderCards() {
        val st = info.storage(); val mem = info.memory(); val s = info.summary()
        b.storageCardValue.text = str(R.string.set_size_fmt, Bytes.storage(st.usedBytes), Bytes.storage(st.totalBytes))
        b.storageCardProgress.progress = st.usedFraction
        b.ramCardValue.text = str(R.string.set_size_fmt, Bytes.memory(mem.usedBytes), Bytes.memory(mem.totalBytes))
        b.ramCardProgress.progress = mem.usedFraction
        b.deviceCardValue.text = (if (s.isTv) str(R.string.device_android_tv_fmt, s.androidRelease) else str(R.string.device_android_fmt, s.androidRelease)) + "  |  " + s.deviceName
    }

    private fun openDetail(sec: SettingsSection) {
        section = sec
        b.mainContent.isVisible = false
        b.mainScroll?.isVisible = false
        b.detailContainer.isVisible = true
        b.detailTitle.setText(sec.title)
        b.detailIcon.setImageResource(sec.icon)
        b.detailIcon.tint(LauncherUi.palette.accentLight)
        rowsAdapter.submitList(SettingsSections.build(requireContext(), sec, state(), this)) {
            b.detailList.post { firstFocusableRow()?.requestFocus() ?: b.detailBack.requestFocus() }
        }
    }

    /** First row that can take focus (headers and the About logo block cannot). */
    private fun firstFocusableRow(): View? {
        val list = b.detailList
        for (i in 0 until list.childCount) {
            val child = list.getChildAt(i)
            if (child.isFocusable && child.isShown) return child
            val found = ArrayList<View>()
            child.addFocusables(found, View.FOCUS_DOWN)
            found.firstOrNull { it.isShown }?.let { return it }
        }
        return null
    }

    private fun closeDetail() {
        if (manager != null) {
            manager = null
            section?.let { sec -> b.detailTitle.setText(sec.title); b.detailIcon.setImageResource(sec.icon); b.detailIcon.tint(LauncherUi.palette.accentLight); rowsAdapter.submitList(SettingsSections.build(requireContext(), sec, state(), this)) { b.detailList.post { firstFocusableRow()?.requestFocus() } } }
            return
        }
        val sec = section ?: return
        section = null
        b.detailContainer.isVisible = false
        b.mainContent.isVisible = true
        b.mainScroll?.isVisible = true
        b.tiles.getChildAt(sec.ordinal)?.requestFocus()
    }

    override fun onBackPressed(): Boolean { if (section != null || manager != null) { closeDetail(); return true }; return false }

    override fun canLeave(proceed: () -> Unit): Boolean {
        val d = quickDraft ?: return true
        if (d == prefs.settings.value) return true
        LauncherDialog(requireContext()).title(R.string.unsaved_title).message(R.string.unsaved_message)
            .button(R.string.cancel)
            .button(R.string.discard) { quickDraft = null; proceed() }
            .button(R.string.apply, primary = true) { applyQuick(); proceed() }
            .show()
        return false
    }

    private fun toast(res: Int) = Toast.makeText(requireContext(), res, Toast.LENGTH_SHORT).show()

    // ---- SettingsActions ------------------------------------------------------------------

    override fun edit(t: (LauncherSettings) -> LauncherSettings) = prefs.edit(t)

    override fun editRecreate(t: (LauncherSettings) -> LauncherSettings) {
        viewLifecycleOwner.lifecycleScope.launch { prefs.update(t); launcherHost?.recreateUi() }
    }

    override fun setSort(mode: String) = prefs.editApps { it.copy(sortMode = mode) }

    override fun requestDefaultLauncher() {
        val ctx = requireContext()
        if (Build.VERSION.SDK_INT >= 29) {
            val rm = ctx.getSystemService(RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                try { roleRequest.launch(rm.createRequestRoleIntent(RoleManager.ROLE_HOME)); return } catch (_: Exception) {}
            }
        }
        if (!SystemShortcuts.homeSettings(ctx)) Dialogs.info(ctx, getString(R.string.row_default_launcher), getString(R.string.default_launcher_help))
    }

    override fun setAutoStart(enabled: Boolean) {
        prefs.edit { it.copy(autoStartOnBoot = enabled) }
        if (enabled) explainAutoStartIfNeeded()
    }

    private fun explainAutoStartIfNeeded() {
        val ctx = requireContext()
        if (Build.VERSION.SDK_INT >= 29 && !apps.isDefaultLauncher()) {
            LauncherDialog(ctx).title(R.string.set_quick_autostart).message(R.string.autostart_android10).icon(R.drawable.ic_power)
                .button(R.string.cancel)
                .button(R.string.row_default_launcher, primary = true) { requestDefaultLauncher() }
                .show()
        }
    }

    override fun chooseWeatherCity() = WeatherSetup.open(this)
    override fun openWallpapers() { launcherHost?.navigate(LauncherPage.WALLPAPERS) }
    override fun editHomeApps() = AppActions.editHomeApps(this)
    override fun reorderHomeApps() { launcherHost?.navigate(LauncherPage.HOME, bundleOf(NavArgs.MOVE_MODE to true)) }
    override fun editFavorites() = AppActions.editFavorites(this)
    override fun reorderFavorites() { launcherHost?.navigate(LauncherPage.FAVORITES, bundleOf(NavArgs.MOVE_MODE to true)) }

    override fun editHidden() {
        val run = {
            val all = AppLists.sort(apps.apps.value, prefs.apps.value, "az")
            val custom = prefs.apps.value
            AppActions.pickApps(this, getString(R.string.picker_hidden_title), all, custom.hidden) { set ->
                prefs.editApps { c -> c.copy(hidden = set) }
            }
        }
        if (prefs.settings.value.protectHiddenApps) guarded(run) else run()
    }

    override fun editLocked() {
        if (!prefs.settings.value.hasPin) { toast(R.string.app_set_pin_first); return }
        guarded {
            val all = AppLists.sort(apps.apps.value.filter { it.key !in prefs.apps.value.hidden }, prefs.apps.value, "az")
            AppActions.pickApps(this, getString(R.string.picker_locked_title), all, prefs.apps.value.locked) { set -> prefs.editApps { it.copy(locked = set) } }
        }
    }

    override fun resetOrder() { prefs.editApps { it.copy(customOrder = emptyList()) }; toast(R.string.row_reset_order_done) }
    override fun clearHistory() { prefs.editApps { it.copy(launchCounts = emptyMap(), lastLaunched = emptyMap()) }; toast(R.string.row_clear_history_done) }
    override fun refreshApps() { apps.refresh(0) }

    override fun editProfileName() {
        Dialogs.input(requireContext(), getString(R.string.row_profile_name), getString(R.string.brand_line1), prefs.settings.value.profileName) { name ->
            prefs.edit { it.copy(profileName = name.take(24)) }
        }
    }

    override fun setPin() {
        PinDialog.show(requireContext(), R.string.pin_title_new) { first ->
            if (!PinHasher.isValidPin(first)) return@show false
            requireView().post {
                PinDialog.show(requireContext(), R.string.pin_title_confirm, R.string.pin_mismatch) { second ->
                    if (second != first) false else {
                        val salt = PinHasher.newSalt()
                        prefs.edit { it.copy(pinSalt = salt, pinHash = PinHasher.hash(first, salt)) }
                        toast(R.string.pin_set_done); true
                    }
                }
            }
            true
        }
    }

    override fun changePin() = guarded { setPin() }

    override fun removePin() = guarded {
        prefs.edit { it.copy(pinHash = "", pinSalt = "", lockSettings = false, lockCustomization = false, protectHiddenApps = false) }
        prefs.editApps { it.copy(locked = emptySet()) }
        toast(R.string.pin_removed)
    }

    override fun guarded(action: () -> Unit) {
        val host = launcherHost
        if (host == null) action() else host.requirePin(action)
    }

    override fun remoteTest() {
        val ctx = requireContext()
        val label = AppCompatTextView(ctx).apply {
            setText(R.string.remote_test_waiting)
            setTextAppearance(R.style.TextAppearance_Akaa_Section)
            textSize = 20f; gravity = Gravity.CENTER
            setPadding(0, ctx.dpi(24f), 0, ctx.dpi(24f))
        }
        val d = LauncherDialog(ctx).title(R.string.remote_test_title).icon(R.drawable.ic_remote).content(label).button(R.string.close, primary = true)
        var lastBack = 0L
        d.dialog.setOnKeyListener { dlg, code, ev ->
            if (ev.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener code != KeyEvent.KEYCODE_HOME
            label.text = str(R.string.remote_test_key, Keys.name(code) + if (ev.repeatCount > 0) " (×${ev.repeatCount + 1})" else "")
            if (Keys.isBack(code)) {
                val now = System.currentTimeMillis()
                if (now - lastBack < 1500) dlg.dismiss()
                lastBack = now
            }
            true
        }
        d.show()
    }

    override fun clearCache() {
        IconCache.get(requireContext()).clear()
        ThumbnailLoader.get(requireContext()).clear()
        apps.refresh(0)
        toast(R.string.row_clear_cache_done)
    }

    override fun checkUpdates() {
        val ctx = requireContext()
        Toast.makeText(ctx, R.string.update_checking, Toast.LENGTH_SHORT).show()
        viewLifecycleOwner.lifecycleScope.launch {
            val r = UpdateChecker.check()
            prefs.edit { it.copy(lastUpdateCheck = System.currentTimeMillis()) }
            if (_b == null) return@launch
            when (r) {
                is UpdateResult.Available -> LauncherDialog(ctx).title(R.string.update_available_title).icon(R.drawable.ic_download)
                    .message(str(R.string.update_available_msg, r.version, AppConfig.versionName) + if (r.notes.isNotBlank()) "\n\n" + r.notes.take(600) else "")
                    .button(R.string.close)
                    .button(R.string.update_download, primary = true) {
                        val url = r.apkUrl ?: r.pageUrl
                        if (!SafeIntents.start(ctx, SafeIntents.browse(url))) Dialogs.info(ctx, getString(R.string.update_available_title), str(R.string.update_open_link_fmt, url))
                    }.show()
                UpdateResult.UpToDate -> Dialogs.info(ctx, getString(R.string.set_check_updates), getString(R.string.update_latest))
                UpdateResult.NoRelease -> Dialogs.info(ctx, getString(R.string.set_check_updates), getString(R.string.update_none_published))
                UpdateResult.NotConfigured -> Dialogs.info(ctx, getString(R.string.set_check_updates), getString(R.string.update_not_configured))
                is UpdateResult.Failed -> Dialogs.info(ctx, getString(R.string.set_check_updates), getString(R.string.update_failed))
            }
        }
    }

    private fun backupFileName() = "AkaaFlixLauncher-backup-" + SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date()) + ".json"

    override fun backup() {
        try { createBackup.launch(backupFileName()); return } catch (_: Exception) {}
        // No document picker (common on TV boxes): write to the app's external files folder.
        val ctx = requireContext()
        viewLifecycleOwner.lifecycleScope.launch {
            val f = withContext(Dispatchers.IO) {
                try { File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, backupFileName()).also { it.writeText(prefs.exportBackup()) } } catch (_: Exception) { null }
            }
            if (f != null) Dialogs.info(ctx, getString(R.string.row_backup), str(R.string.backup_saved_to, f.absolutePath)) else toast(R.string.backup_failed)
        }
    }

    override fun restore() {
        try { openBackup.launch(arrayOf("application/json", "text/plain", "application/octet-stream")); return } catch (_: Exception) {}
        val ctx = requireContext()
        val dir = ctx.getExternalFilesDir(null) ?: ctx.filesDir
        val files = dir.listFiles { f -> f.name.endsWith(".json") }?.sortedByDescending { it.lastModified() }.orEmpty()
        if (files.isEmpty()) { Dialogs.info(ctx, getString(R.string.row_restore), str(R.string.restore_none_found, dir.absolutePath)); return }
        Dialogs.choose(ctx, getString(R.string.restore_pick_title), files.map { it.name }, -1) { i ->
            viewLifecycleOwner.lifecycleScope.launch { restoreFrom(withContext(Dispatchers.IO) { try { files[i].readText() } catch (_: Exception) { null } }) }
        }
    }

    private suspend fun restoreFrom(text: String?) {
        val ok = text != null && prefs.importBackup(text)
        toast(if (ok) R.string.restore_done else R.string.restore_failed)
        if (ok) launcherHost?.recreateUi()
    }

    override fun showText(title: Int, text: String) { Dialogs.info(requireContext(), getString(title), text) }

    /** Official website in the user's browser (safe VIEW intent, BROWSABLE category only). */
    override fun openWebsite() {
        val ctx = requireContext()
        val url = getString(R.string.about_website_url)
        if (!SafeIntents.start(ctx, SafeIntents.browse(url))) Dialogs.info(ctx, getString(R.string.about_website_label), str(R.string.about_no_browser_fmt, url))
    }

    override fun showDefaultLauncherGuide() {
        LauncherDialog(requireContext()).title(R.string.row_default_guide).icon(R.drawable.ic_home)
            .message(R.string.default_launcher_steps)
            .button(R.string.close)
            .button(R.string.row_default_launcher, primary = true) { requestDefaultLauncher() }
            .show()
    }

    override fun exitSafeMode() { viewLifecycleOwner.lifecycleScope.launch { prefs.update { it.copy(safeMode = false) }; launcherHost?.recreateUi() } }

    override fun system(which: String) {
        val c: Context = requireContext()
        when (which) {
            "wifi" -> SystemShortcuts.wifi(c)
            "network" -> SystemShortcuts.network(c)
            "vpn" -> SystemShortcuts.vpn(c)
            "bluetooth" -> SystemShortcuts.bluetooth(c)
            "input" -> SystemShortcuts.inputMethods(c)
            "cec" -> SystemShortcuts.hdmiCec(c)
            "date" -> SystemShortcuts.date(c)
            "language" -> SystemShortcuts.appLanguage(c)
            "screensaver" -> SystemShortcuts.screensaver(c)
            "display" -> SystemShortcuts.display(c)
            "battery" -> SystemShortcuts.battery(c)
            "apps" -> SystemShortcuts.apps(c)
            "notifications" -> SystemShortcuts.notifications(c)
        }
    }

    override fun openAppManager() {
        val m = AppManagerPage(this) { if (_b != null && manager != null) renderAll() }
        manager = m
        b.detailTitle.setText(R.string.am_title)
        b.detailIcon.setImageResource(R.drawable.ic_apps)
        b.detailIcon.tint(LauncherUi.palette.accentLight)
        m.reload()
        b.detailList.post { b.detailList.scrollToPosition(0); firstFocusableRow()?.requestFocus() }
    }

    /** Uses the user's Shizuku service to make Akaa Flix the default Home, then verifies with Android. */
    override fun makeDefaultWithShizuku() {
        val ctx = requireContext()
        val pkg = ctx.packageName
        val user = com.akaaflix.launcher.apps.privileged.PackageCommands.currentUser()
        val cmd = if (Build.VERSION.SDK_INT >= 29) com.akaaflix.launcher.apps.privileged.PackageCommands.setHomeRole(pkg, user)
            else com.akaaflix.launcher.apps.privileged.PackageCommands.setHomeActivity("$pkg/com.akaaflix.launcher.MainActivity", user)
        val st = com.akaaflix.launcher.apps.privileged.Privileged.status(ctx)
        if (!st.ready) {
            val home = com.akaaflix.launcher.apps.home.HomeStatus.read(ctx)
            val extra = if (home.googleTvLauncher) "\n" + com.akaaflix.launcher.apps.privileged.PackageCommands.asAdb(listOf("pm", "disable-user", "--user", user.toString(), com.akaaflix.launcher.apps.home.HomeStatus.GOOGLE_TV_LAUNCHER)) else ""
            LauncherDialog(ctx).title(R.string.hs_set_default_shizuku).icon(R.drawable.ic_shield)
                .message(getString(R.string.am_shizuku_help) + "\n\n" + str(R.string.hs_adb_default, com.akaaflix.launcher.apps.privileged.PackageCommands.asAdb(cmd) + extra))
                .button(R.string.close)
                .apply { if (st.state == com.akaaflix.launcher.apps.privileged.Privileged.State.PERMISSION_REQUIRED) button(R.string.weather_allow, primary = true) {
                    com.akaaflix.launcher.apps.privileged.Privileged.requestPermission { ok -> view?.post { if (ok && _b != null) makeDefaultWithShizuku() } }
                } }
                .show()
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val r = com.akaaflix.launcher.apps.privileged.Privileged.exec(ctx, cmd)
            val home = com.akaaflix.launcher.apps.home.HomeStatus.read(ctx)
            if (_b == null) return@launch
            refreshTick.value++
            if (home.isDefault) Dialogs.info(ctx, getString(R.string.hs_set_default_shizuku), getString(R.string.hs_shizuku_verified))
            else Dialogs.info(ctx, getString(R.string.hs_set_default_shizuku),
                str(R.string.hs_shizuku_not_accepted, com.akaaflix.launcher.apps.privileged.PackageCommands.asAdb(cmd), r.output.take(300).ifBlank { r.code.toString() }))
        }
    }

    override fun showDiagnostics() {
        val ctx = requireContext()
        val st = state()
        val rep = apps.report.value
        val s = info.summary()
        val yes = getString(R.string.dg_yes); val no = getString(R.string.dg_no)
        fun t(ms: Long) = if (ms <= 0) getString(R.string.dg_never) else java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(Date(ms))
        val removed = rep.removed.joinToString("\n") { r -> "${r.entry.label} – ${r.entry.packageName} (user ${r.entry.userSerial}, ${r.reason.name.lowercase()})" }.ifBlank { getString(R.string.dg_none) }
        val rows = listOf(
            SettingRow.Info("ver", getString(R.string.row_version), "v${AppConfig.versionName} (${AppConfig.versionCode}) • ${if (AppConfig.isDebug) "debug" else "release"}", R.drawable.ic_logo_mark),
            SettingRow.Info("dev", getString(R.string.info_model), "${s.manufacturer} ${s.model} • Android ${s.androidRelease} (API ${s.sdkInt}) • ${if (s.isTv) "TV" else "phone/tablet"}", R.drawable.ic_tv),
            SettingRow.Info("home", getString(R.string.hs_default_home), (if (st.home.isDefault) yes else no) + " • " + (st.home.currentHomePackage ?: getString(R.string.hs_current_none)) +
                (if (st.home.roleAvailable) " • role ${if (st.home.roleHeld) yes else no}" else "") + (if (st.home.googleTvLauncher) " • Google TV" else ""), R.drawable.ic_home),
            SettingRow.Info("cands", getString(R.string.dg_home_apps), st.home.candidates.joinToString("\n") { "${it.second} (${it.first})" }.ifBlank { getString(R.string.dg_none) }, R.drawable.ic_apps),
            SettingRow.Info("startup", getString(R.string.hs_startup), SettingsSections.startupText(ctx, st), R.drawable.ic_power),
            SettingRow.Info("boot", getString(R.string.dg_boot_broadcast), if (st.bootLog.bootBroadcastThisBoot) yes else no, R.drawable.ic_power),
            SettingRow.Info("lastHome", getString(R.string.dg_last_home), t(st.bootLog.lastHomeIntentAt), R.drawable.ic_home),
            SettingRow.Info("disc", getString(R.string.dg_discovery), str(R.string.dg_discovery_fmt, rep.profiles, rep.launcherActivities, rep.tvActivities, rep.shown), R.drawable.ic_apps),
            SettingRow.Info("dups", getString(R.string.dg_duplicates), removed, R.drawable.ic_eye_off),
            SettingRow.Info("shizuku", getString(R.string.am_shizuku), st.shizuku.state.name.lowercase() + (if (st.shizuku.uid >= 0) " • uid ${st.shizuku.uid}" else ""), R.drawable.ic_shield),
        )
        Dialogs.rows(ctx, getString(R.string.dg_title), rows, icon = R.drawable.ic_cpu, widthDp = 600) { d, _ -> d.button(R.string.close, primary = true) }
    }

    override fun onDestroyView() { com.akaaflix.launcher.apps.privileged.Privileged.release(); super.onDestroyView(); _b = null }
}
