package com.akaaflix.launcher

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.test.core.app.ApplicationProvider
import com.akaaflix.launcher.core.nav.LauncherPage
import com.akaaflix.launcher.prefs.SettingsRepository
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.io.File
import java.io.FileOutputStream

/**
 * Renders every page to PNG (Robolectric native graphics) for visual comparison with the
 * approved reference images. Output: app/build/screenshots/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], application = AkaaLauncherApp::class)
class ScreenshotTest {
    private val outDir = File("build/screenshots").apply { mkdirs() }

    private fun settle(ms: Long = 1500) {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) {
            // Advance the (paused) Robolectric clock too, so animations such as the neon focus fade complete.
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(40))
            Thread.sleep(40)
        }
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun capture(view: View, name: String) {
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        FileOutputStream(File(outDir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun run(prefix: String, lang: String, leanback: Boolean) {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        TestApps.install(ctx, leanback)
        com.akaaflix.launcher.apps.AppRepository.get(ctx).refresh(0)
        Thread.sleep(800)
        val prefs = SettingsRepository.get(ctx)
        prefs.awaitLoaded()
        // Robolectric reports no RAM (= entry tier, glow off); render the high-quality look of 2 GB+ devices.
        runBlocking { prefs.update { it.copy(language = lang, performanceMode = "high") } }
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(lang))
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        settle(2500)
        val activity = controller.get()
        val root = activity.window.decorView
        capture(root, "${prefix}_01_home")
        for ((i, page) in listOf(LauncherPage.ALL_APPS, LauncherPage.FAVORITES, LauncherPage.CUSTOMIZATION, LauncherPage.WALLPAPERS, LauncherPage.SETTINGS).withIndex()) {
            activity.navigate(page)
            settle(1800)
            capture(root, "${prefix}_0${i + 2}_${page.name.lowercase()}")
        }
        activity.navigate(LauncherPage.SETTINGS, android.os.Bundle().apply { putString("open_section", "datetime") })
        settle(1500)
        capture(root, "${prefix}_07_settings_datetime")
        activity.navigate(LauncherPage.HOME)
        settle(600)
        activity.navigate(LauncherPage.SETTINGS, android.os.Bundle().apply { putString("open_section", "about") })
        settle(1500)
        capture(root, "${prefix}_08_settings_about")
        activity.navigate(LauncherPage.HOME)
        settle(500)
        activity.navigate(LauncherPage.SETTINGS, android.os.Bundle().apply { putString("open_section", "interface") })
        settle(1500)
        capture(root, "${prefix}_10_settings_home_startup")
        (activity.supportFragmentManager.findFragmentByTag("page_secondary") as? com.akaaflix.launcher.settings.SettingsFragment)?.openAppManager()
        settle(2500)
        capture(root, "${prefix}_11_app_manager")
        // Neon focus on a Home card. Robolectric starts in touch mode (no focus is drawn there, as on a
        // phone); requestFocusFromTouch leaves touch mode like the first D-pad press on a TV remote.
        activity.navigate(LauncherPage.HOME)
        settle(800)
        val homeView = activity.supportFragmentManager.findFragmentByTag("page_home")!!.requireView()
        val homeGrid = homeView.findViewById<androidx.recyclerview.widget.RecyclerView>(com.akaaflix.launcher.home.R.id.grid)
        homeGrid.findViewHolderForAdapterPosition(1)?.itemView?.requestFocusFromTouch()
        settle(900)
        capture(root, "${prefix}_09_home_focus")
        controller.pause().stop().destroy()
    }

    @Test @Config(qualifiers = "ar-w960dp-h540dp-land-television-xhdpi")
    fun tv_arabic() = run("tv_ar", "ar", leanback = true)

    @Test @Config(qualifiers = "en-w960dp-h540dp-land-television-xhdpi")
    fun tv_english() = run("tv_en", "en", leanback = true)

    @Test @Config(qualifiers = "fr-w960dp-h540dp-land-television-xhdpi")
    fun tv_french() = run("tv_fr", "fr", leanback = true)

    @Test @Config(qualifiers = "ar-w411dp-h891dp-port-xxhdpi")
    fun phone_arabic() = run("phone_ar", "ar", leanback = false)
}
