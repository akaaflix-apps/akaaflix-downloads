package com.akaaflix.launcher

import com.akaaflix.launcher.core.util.str

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import android.text.format.DateFormat
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.ConfigurationCompat
import androidx.core.os.LocaleListCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.akaaflix.launcher.apps.icons.IconCache
import com.akaaflix.launcher.apps.ui.AllAppsFragment
import com.akaaflix.launcher.apps.ui.FavoritesFragment
import com.akaaflix.launcher.core.R as CoreR
import com.akaaflix.launcher.core.nav.BackHandler
import com.akaaflix.launcher.core.nav.KeyInterceptor
import com.akaaflix.launcher.core.nav.LauncherHost
import com.akaaflix.launcher.core.nav.LauncherPage
import com.akaaflix.launcher.core.nav.LeaveGuard
import com.akaaflix.launcher.core.nav.MenuKeyHandler
import com.akaaflix.launcher.core.nav.PageHeader
import com.akaaflix.launcher.core.perf.DevicePerformance
import com.akaaflix.launcher.core.perf.QualityProfile
import com.akaaflix.launcher.core.ui.LauncherUi
import com.akaaflix.launcher.core.ui.ThemeCatalog
import com.akaaflix.launcher.core.ui.dialog.PinDialog
import com.akaaflix.launcher.core.ui.invalidateTree
import com.akaaflix.launcher.core.util.SafeIntents
import com.akaaflix.launcher.customization.CustomizationFragment
import com.akaaflix.launcher.databinding.ActivityMainBinding
import com.akaaflix.launcher.device.clock.ClockTicker
import com.akaaflix.launcher.device.clock.DateConfig
import com.akaaflix.launcher.device.clock.DateTextFormatter
import com.akaaflix.launcher.device.clock.HijriCalendar
import com.akaaflix.launcher.device.status.BluetoothMonitor
import com.akaaflix.launcher.device.status.NetworkMonitor
import com.akaaflix.launcher.device.system.SystemShortcuts
import com.akaaflix.launcher.device.update.UpdateChecker
import com.akaaflix.launcher.device.update.UpdateResult
import com.akaaflix.launcher.home.HomeFragment
import com.akaaflix.launcher.prefs.LauncherSettings
import com.akaaflix.launcher.prefs.PinHasher
import com.akaaflix.launcher.prefs.SettingsRepository
import com.akaaflix.launcher.remote.Keys
import com.akaaflix.launcher.remote.isAncestorOf
import com.akaaflix.launcher.settings.SettingsFragment
import com.akaaflix.launcher.wallpaper.ThumbnailLoader
import com.akaaflix.launcher.wallpaper.WallpaperCatalog
import com.akaaflix.launcher.wallpaper.WallpaperRepository
import com.akaaflix.launcher.wallpaper.WallpaperRotation
import com.akaaflix.launcher.wallpaper.ui.WallpapersFragment
import kotlinx.coroutines.launch
import java.util.TimeZone

/**
 * The HOME activity. Hosts the shell (wallpaper, sidebar, top bar) and swaps pages. Never exits on
 * Back, keeps Home retained for instant return, and restores focus when the user comes back.
 */
class MainActivity : AppCompatActivity(), LauncherHost {
    private lateinit var b: ActivityMainBinding
    private val prefs by lazy { SettingsRepository.get(this) }
    private val wallpapers by lazy { WallpaperRepository.get(this) }
    private lateinit var clock: ClockTicker
    private lateinit var network: NetworkMonitor
    private lateinit var bluetooth: BluetoothMonitor
    private lateinit var rotation: WallpaperRotation
    private lateinit var quality: QualityProfile
    private var current = LauncherPage.HOME
    private var created: LauncherSettings? = null
    private var applied: LauncherSettings? = null
    private var resumed = false
    private var peeking = false
    private var pinUnlockedUntil = 0L
    private var voiceCallback: ((String) -> Unit)? = null
    private var safeModeShown = false

    private val voice = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { voiceCallback?.invoke(it) }
    }

    override fun attachBaseContext(newBase: Context) {
        val s = SettingsRepository.get(newBase).awaitLoaded()
        if (s.fontScale != 1f && !s.safeMode) {
            val cfg = Configuration(newBase.resources.configuration)
            cfg.fontScale = cfg.fontScale * s.fontScale
            super.attachBaseContext(newBase.createConfigurationContext(cfg))
        } else super.attachBaseContext(newBase)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val s = prefs.awaitLoaded()
        created = s
        setTheme(when {
            s.safeMode || s.fontFamily == "system" -> R.style.Theme_Akaa_Launcher_System
            s.fontFamily == "cairo" -> R.style.Theme_Akaa_Launcher_Cairo
            else -> R.style.Theme_Akaa_Launcher_Tajawal
        })
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        val portrait = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        clock = ClockTicker(this)
        network = NetworkMonitor(this)
        bluetooth = BluetoothMonitor(this)
        rotation = WallpaperRotation(lifecycleScope, wallpapers, prefs) { ::quality.isInitialized && quality.videoWallpapers }

        b.sidebar.setup(horizontal = portrait)
        b.sidebar.onPageSelected = { navigate(it) }
        b.root.navRail = b.sidebar
        b.root.content = b.content
        b.root.navPreferredEntry = { b.sidebar.selectedItem() }
        b.root.navHorizontal = portrait
        ViewCompat.setOnApplyWindowInsetsListener(b.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            b.shell.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        b.topBar.onBrandClick = { if (current == LauncherPage.HOME) navigate(LauncherPage.SETTINGS, Bundle().apply { putString("open_section", "profiles") }) else navigate(LauncherPage.HOME) }
        b.topBar.onWifiClick = { SystemShortcuts.network(this) }
        b.topBar.onBluetoothClick = { SystemShortcuts.bluetooth(this) }
        b.topBar.onSettingsClick = { navigate(LauncherPage.SETTINGS) }
        b.wallpaper.resolver = { id -> wallpapers.resolve(id) }
        b.root.setOnTouchListener { _, e -> if (peeking && e.action == MotionEvent.ACTION_DOWN) { endPeek(); true } else false }

        applySettings(s, initial = true)
        if (savedInstanceState == null) {
            doNavigate(when (s.startPage) { "all_apps" -> LauncherPage.ALL_APPS; "favorites" -> LauncherPage.FAVORITES; else -> LauncherPage.HOME }, null, force = true)
            b.root.post { (supportFragmentManager.findFragmentByTag(TAG_HOME) as? HomeFragment)?.focusDefault() }
        } else {
            current = runCatching { LauncherPage.valueOf(savedInstanceState.getString(KEY_PAGE, "HOME")) }.getOrDefault(LauncherPage.HOME)
            if (supportFragmentManager.findFragmentByTag(TAG_HOME) == null && supportFragmentManager.findFragmentByTag(TAG_PAGE) == null) { current = LauncherPage.HOME; doNavigate(LauncherPage.HOME, null, force = true) }
            updateChrome(current)
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = handleBack()
        })

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { prefs.settings.collect { applySettings(it, initial = false) } }
                launch { clock.now.collect { updateClock(it) } }
                launch { network.state.collect { b.topBar.bindNetwork(it) } }
                launch { bluetooth.state.collect { b.topBar.bindBluetooth(it) } }
            }
        }
        if (s.safeMode && !safeModeShown) { safeModeShown = true; Toast.makeText(this, CoreR.string.safe_mode_toast, Toast.LENGTH_LONG).show() }
        // Mark the run healthy after a while so isolated crashes never trigger safe mode.
        b.root.postDelayed({ CrashGuard.reset(this) }, 60_000)
        // Applied last: when the stored language differs AppCompat recreates the activity, and the
        // lifecycle (onStart/onStop) must still find a fully initialised activity.
        applyLanguage(s.language)
    }

    /** Returns true when a locale change will recreate the activity. */
    private fun applyLanguage(lang: String): Boolean {
        val want = if (lang == "system") LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(lang)
        if (AppCompatDelegate.getApplicationLocales().toLanguageTags() == want.toLanguageTags()) return false
        AppCompatDelegate.setApplicationLocales(want)
        return Build.VERSION.SDK_INT < 33 && !isFinishing
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_PAGE, current.name)
    }

    override fun onStart() {
        super.onStart()
        clock.start(); network.start(); bluetooth.start()
        b.wallpaper.setActive(true)
        rotation.onVisible()
        maybeAutoCheckUpdates()
    }

    override fun onResume() { super.onResume(); resumed = true }
    override fun onPause() { resumed = false; super.onPause() }

    override fun onStop() {
        clock.stop(); network.stop(); bluetooth.stop()
        b.wallpaper.setActive(false)
        rotation.onHidden()
        pinUnlockedUntil = 0L
        if (peeking) endPeek()
        super.onStop()
    }

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (::b.isInitialized && level >= TRIM_MEMORY_BACKGROUND && (quality.mode == "light" || level >= TRIM_MEMORY_COMPLETE)) b.wallpaper.releaseMemory()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && (intent.hasCategory(Intent.CATEGORY_HOME) || intent.hasCategory(Intent.CATEGORY_LAUNCHER) || intent.hasCategory(Intent.CATEGORY_LEANBACK_LAUNCHER))) {
            if (peeking) endPeek()
            if (current != LauncherPage.HOME) navigate(LauncherPage.HOME)
            else if (resumed) homeFragment()?.focusDefault()   // second Home press resets focus
        }
    }

    // ---- settings ---------------------------------------------------------------------------

    private fun applySettings(s: LauncherSettings, initial: Boolean) {
        val prev = applied
        applied = s
        val c = created
        if (!initial && c != null && (c.fontFamily != s.fontFamily || c.fontScale != s.fontScale || c.safeMode != s.safeMode)) { recreate(); return }
        if (!initial && prev != null && prev.language != s.language) { if (applyLanguage(s.language)) return }

        val profile = DevicePerformance.profile(this)
        quality = DevicePerformance.quality(if (s.safeMode) "light" else s.performanceMode, profile)
        com.akaaflix.launcher.core.util.Numerals.preferEastern = s.numerals == "eastern"
        LauncherUi.motionEnabled = s.motionEnabled && quality.motion
        LauncherUi.glowEnabled = quality.glow
        LauncherUi.focusStyle = s.focusStyle
        LauncherUi.animDurationMs = quality.animDurationMs
        LauncherUi.focusScale = 1f + (when (s.focusZoom) { "off" -> 0f; "small" -> 0.04f; "large" -> 0.12f; else -> 0.08f }) * quality.focusScaleFactor
        val oldPalette = LauncherUi.paletteVersion
        LauncherUi.applyPalette(ThemeCatalog.palette(s.themeId, s.accentColor))
        if (oldPalette != LauncherUi.paletteVersion) { b.root.invalidateTree(); b.topBar.refreshPalette(); b.sidebar.refreshPalette() }
        IconCache.get(this).configure(quality.iconMemoryCacheBytes)
        ThumbnailLoader.get(this).configure(quality.thumbnailCacheBytes)

        val w = b.wallpaper
        val allowVideo = quality.videoWallpapers && s.animatedWallpaper
        val allowAnim = quality.animatedWallpapers && s.animatedWallpaper
        val qualityChanged = w.allowVideo != allowVideo || w.allowAnimation != allowAnim || w.rgb565 != quality.wallpaperRgb565 || w.maxEdgePx != quality.maxWallpaperEdgePx
        w.allowVideo = allowVideo; w.allowAnimation = allowAnim; w.rgb565 = quality.wallpaperRgb565
        w.crossfade = quality.wallpaperCrossfade && LauncherUi.motionEnabled; w.maxEdgePx = quality.maxWallpaperEdgePx
        w.setWallpaper(if (s.safeMode) WallpaperCatalog.DEFAULT_ID else s.wallpaperId)
        if (qualityChanged && !initial) w.reload()
        if (!initial && prev != null && (prev.rotationEnabled != s.rotationEnabled || prev.rotationIntervalMin != s.rotationIntervalMin) &&
            lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) rotation.onVisible()

        b.topBar.setClockVisible(s.showClockDate)
        b.topBar.setStatusVisible(s.showNetworkStatus)
        b.sidebar.setLabelsVisible(s.showSidebarLabels)
        updateBrand(s)
        if (Build.VERSION.SDK_INT >= 24) b.root.pointerIcon = PointerIcons.create(this, s.pointerStyle)
        updateClock(System.currentTimeMillis())
    }

    private fun updateBrand(s: LauncherSettings = prefs.settings.value) {
        if (current == LauncherPage.HOME) {
            val (res, image) = when (s.profileAvatar) {
                "anubis" -> CoreR.drawable.avatar_anubis to true
                "pyramids" -> CoreR.drawable.avatar_pyramids to true
                "galaxy" -> CoreR.drawable.avatar_galaxy to true
                "logo" -> CoreR.drawable.ic_logo_mark to false
                else -> CoreR.drawable.ic_avatar to false
            }
            b.topBar.setBrand(res, image, s.profileName)
        } else b.topBar.setBrand(CoreR.drawable.ic_logo_mark, false, s.profileName)
    }

    private fun updateClock(now: Long) {
        if (!::b.isInitialized) return
        val s = prefs.settings.value
        val use24 = when (s.clockFormat) { "24" -> true; "12" -> false; else -> DateFormat.is24HourFormat(this) }
        val method = if (s.hijriMethod == "tabular") HijriCalendar.Method.TABULAR else HijriCalendar.Method.UMM_AL_QURA
        val t = DateTextFormatter.format(now, TimeZone.getDefault(), DateConfig(use24, method, s.hijriAdjust, s.numerals == "eastern"))
        val arabic = ConfigurationCompat.getLocales(resources.configuration)[0]?.language == "ar"
        b.topBar.bindDate(t, arabic, s.bilingualDate, s.showHijri)
    }

    private fun maybeAutoCheckUpdates() {
        val s = prefs.settings.value
        if (!s.autoCheckUpdates || System.currentTimeMillis() - s.lastUpdateCheck < 7L * 24 * 3600 * 1000) return
        lifecycleScope.launch {
            val r = UpdateChecker.check()
            prefs.edit { it.copy(lastUpdateCheck = System.currentTimeMillis()) }
            if (r is UpdateResult.Available) Toast.makeText(this@MainActivity, str(CoreR.string.update_available_msg, r.version, BuildConfig.VERSION_NAME), Toast.LENGTH_LONG).show()
        }
    }

    // ---- navigation -------------------------------------------------------------------------

    private fun pageFragment(): Fragment? = supportFragmentManager.findFragmentByTag(if (current == LauncherPage.HOME) TAG_HOME else TAG_PAGE)
    private fun homeFragment(): HomeFragment? = supportFragmentManager.findFragmentByTag(TAG_HOME) as? HomeFragment

    override fun currentPage() = current

    override fun navigate(page: LauncherPage, args: Bundle?) {
        if (page != current || args != null) {
            val guard = pageFragment() as? LeaveGuard
            if (guard != null && page != current && !guard.canLeave { doNavigateGuarded(page, args) }) return
        }
        doNavigateGuarded(page, args)
    }

    private fun doNavigateGuarded(page: LauncherPage, args: Bundle?) {
        val s = prefs.settings.value
        val locked = s.hasPin && ((page == LauncherPage.SETTINGS && s.lockSettings) || (page == LauncherPage.CUSTOMIZATION && s.lockCustomization))
        if (locked && page != current) requirePin { doNavigate(page, args) } else doNavigate(page, args)
    }

    private fun doNavigate(page: LauncherPage, args: Bundle?, force: Boolean = false) {
        if (!force && page == current && args == null) {
            if (page == LauncherPage.HOME) homeFragment()?.focusDefault()
            return
        }
        val focusInContent = currentFocus?.let { b.content.isAncestorOf(it) } ?: true
        val fm = supportFragmentManager
        val tx = fm.beginTransaction().setReorderingAllowed(true)
        val s = prefs.settings.value
        if (LauncherUi.motionEnabled && s.pageTransition != "none") {
            val enter = if (s.pageTransition == "slide") CoreR.anim.page_slide_in else CoreR.anim.page_fade_in
            tx.setCustomAnimations(enter, CoreR.anim.page_fade_out)
        }
        fm.findFragmentByTag(TAG_PAGE)?.let { tx.remove(it) }
        val home = fm.findFragmentByTag(TAG_HOME)
        if (page == LauncherPage.HOME) {
            if (home == null) tx.add(R.id.content, HomeFragment(), TAG_HOME) else tx.show(home)
        } else {
            if (home != null) tx.hide(home)
            val f: Fragment = when (page) {
                LauncherPage.ALL_APPS -> AllAppsFragment()
                LauncherPage.FAVORITES -> FavoritesFragment()
                LauncherPage.CUSTOMIZATION -> CustomizationFragment()
                LauncherPage.WALLPAPERS -> WallpapersFragment()
                LauncherPage.SETTINGS -> SettingsFragment()
                LauncherPage.HOME -> HomeFragment()
            }
            f.arguments = args
            tx.add(R.id.content, f, TAG_PAGE)
        }
        if (current == LauncherPage.WALLPAPERS && page != LauncherPage.WALLPAPERS) b.wallpaper.preview(null)
        current = page
        tx.commitNowAllowingStateLoss()
        b.root.clearContentMemory()
        b.sidebar.select(page)
        updateChrome(page)
        if (focusInContent) b.content.post { focusFirstInContent(page) }
    }

    private fun focusFirstInContent(page: LauncherPage) {
        if (page == LauncherPage.HOME && homeFragment()?.focusDefault() == true) return
        val f = pageFragment()?.view ?: return
        if (f.hasFocus()) return
        f.requestFocus(View.FOCUS_DOWN)
    }

    private fun updateChrome(page: LauncherPage) {
        val secondary = page != LauncherPage.HOME
        val d = if (LauncherUi.motionEnabled) 220L else 0L
        b.pageScrim.animate().alpha(if (secondary) 1f else 0f).setDuration(d).start()
        b.headerArt.animate().alpha(if (secondary) 1f else 0f).setDuration(d).start()
        if (page == LauncherPage.HOME) b.topBar.setHeader(PageHeader(showLogo = prefs.settings.value.showHomeLogo))
        updateBrand()
    }

    override fun setHeader(header: PageHeader) { b.topBar.setHeader(header) }

    private fun handleBack() {
        if (peeking) { endPeek(); return }
        if ((pageFragment() as? BackHandler)?.onBackPressed() == true) return
        if (current != LauncherPage.HOME) { navigate(LauncherPage.HOME); return }
        // A launcher never exits on Back.
        if (prefs.settings.value.backOnHome == "focus_first") homeFragment()?.focusDefault()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (peeking && event.action == KeyEvent.ACTION_DOWN && event.keyCode != KeyEvent.KEYCODE_HOME) { endPeek(); return true }
        if ((pageFragment() as? KeyInterceptor)?.onInterceptKey(event) == true) return true
        if (Keys.isMenu(event.keyCode)) {
            if (event.action == KeyEvent.ACTION_UP) handleMenuKey()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun handleMenuKey() {
        when (prefs.settings.value.menuKeyAction) {
            "settings" -> navigate(LauncherPage.SETTINGS)
            "customize" -> navigate(LauncherPage.CUSTOMIZATION)
            else -> if ((pageFragment() as? MenuKeyHandler)?.onMenuKey() != true && current == LauncherPage.HOME) navigate(LauncherPage.CUSTOMIZATION)
        }
    }

    // ---- LauncherHost -----------------------------------------------------------------------

    override fun requirePin(onGranted: () -> Unit) {
        val s = prefs.settings.value
        if (!s.hasPin || System.currentTimeMillis() < pinUnlockedUntil) { onGranted(); return }
        PinDialog.show(this, CoreR.string.pin_title_enter) { pin ->
            if (PinHasher.verify(pin, s.pinSalt, s.pinHash)) {
                pinUnlockedUntil = System.currentTimeMillis() + 5 * 60 * 1000L
                b.root.post(onGranted); true
            } else false
        }
    }

    override fun previewWallpaper(wallpaperId: String?) { b.wallpaper.preview(wallpaperId) }

    override fun peekBehindUi() {
        peeking = true
        listOf(b.content, b.topBar, b.sidebar, b.pageScrim, b.headerArt).forEach { it.animate().alpha(0.04f).setDuration(250).start() }
        Toast.makeText(this, CoreR.string.wp_preview_hint, Toast.LENGTH_SHORT).show()
    }

    private fun endPeek() {
        peeking = false
        val secondary = current != LauncherPage.HOME
        b.content.animate().alpha(1f).setDuration(200).start()
        b.topBar.animate().alpha(1f).setDuration(200).start()
        b.sidebar.animate().alpha(1f).setDuration(200).start()
        b.pageScrim.animate().alpha(if (secondary) 1f else 0f).setDuration(200).start()
        b.headerArt.animate().alpha(if (secondary) 1f else 0f).setDuration(200).start()
    }

    override fun startVoiceSearch(onResult: (String) -> Unit) {
        voiceCallback = onResult
        val lang = ConfigurationCompat.getLocales(resources.configuration)[0]?.toLanguageTag() ?: "ar"
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, getString(CoreR.string.voice_prompt))
        try { voice.launch(intent) } catch (_: ActivityNotFoundException) {
            if (!SafeIntents.start(this, Intent(Intent.ACTION_ASSIST), Intent("android.intent.action.VOICE_ASSIST"), Intent(Intent.ACTION_SEARCH_LONG_PRESS)))
                Toast.makeText(this, CoreR.string.voice_not_available, Toast.LENGTH_LONG).show()
        } catch (_: SecurityException) {
            Toast.makeText(this, CoreR.string.voice_not_available, Toast.LENGTH_LONG).show()
        }
    }

    override fun recreateUi() = recreate()

    companion object {
        private const val TAG_HOME = "page_home"
        private const val TAG_PAGE = "page_secondary"
        private const val KEY_PAGE = "current_page"
    }
}
