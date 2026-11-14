package com.akaaflix.launcher

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Looper
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.akaaflix.launcher.apps.AppRepository
import com.akaaflix.launcher.apps.ui.AppCardView
import com.akaaflix.launcher.core.nav.LauncherPage
import com.akaaflix.launcher.home.shell.NavItemView
import com.akaaflix.launcher.prefs.SettingsRepository
import com.akaaflix.launcher.remote.isAncestorOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/**
 * End-to-end launcher behaviour on every Android version Robolectric provides
 * (6.0 → 17). Robolectric runs the real framework code of each version on the JVM; it is not a
 * substitute for physical devices (see docs/COMPATIBILITY.md).
 */
@RunWith(RobolectricTestRunner::class)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [24, 26, 28, 29, 30, 31, 33, 34, 35, 36], application = AkaaLauncherApp::class, qualifiers = "en-w960dp-h540dp-land-television-xhdpi")
open class LauncherIntegrationTest {
    /** Robolectric's legacy graphics (API 23) cannot create font families from resources. */
    protected open val fontFamily = "tajawal"

    private fun settle(ms: Long = 1200) {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(30) }
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun <T : View> View.findAll(cls: Class<T>, out: MutableList<T> = mutableListOf()): List<T> {
        if (cls.isInstance(this)) out.add(cls.cast(this)!!)
        if (this is android.view.ViewGroup) for (i in 0 until childCount) getChildAt(i).findAll(cls, out)
        return out
    }

    @Test
    fun registeredAsHomeApp() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val homes = ctx.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
        assertTrue("HOME intent must resolve to MainActivity", homes.any { it.activityInfo.name == MainActivity::class.java.name })
        val tv = ctx.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0)
        assertTrue("Must appear in the Android TV apps row", tv.any { it.activityInfo.name == MainActivity::class.java.name })
    }

    @Test
    fun fullLauncherFlow() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        TestApps.install(ctx, leanback = true)
        AppRepository.get(ctx).refresh(0)
        val prefs = SettingsRepository.get(ctx)
        prefs.awaitLoaded()
        runBlocking { prefs.update { it.copy(language = "en", fontFamily = fontFamily) } }
        settle(600)

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        settle(2500)
        val activity = controller.get()
        val root = activity.findViewById<com.akaaflix.launcher.remote.NavigationRootLayout>(R.id.root)

        // 1. Real installed apps are discovered and shown on Home (favourites + "Add app").
        assertTrue(AppRepository.get(ctx).apps.value.any { it.packageName == "com.netflix.ninja" })
        val cards = root.findAll(AppCardView::class.java).filter { it.isShown }
        assertTrue("home cards: ${cards.size}", cards.size >= 6)
        assertTrue(cards.any { it.contentDescription == "Netflix" })
        assertFalse("launcher itself is never listed", AppRepository.get(ctx).apps.value.any { it.packageName.startsWith("com.akaaflix.launcher") })

        // 2. D-pad routing: content → sidebar lands on the SELECTED item; sidebar → content restores focus.
        val first = cards.first()
        assertTrue(first.requestFocus())
        val left = first.focusSearch(View.FOCUS_LEFT)
        assertNotNull(left)
        assertTrue("LEFT from the first card enters the sidebar", left is NavItemView)
        assertTrue((left as NavItemView).isActivated)
        left.requestFocus()
        val down = left.focusSearch(View.FOCUS_DOWN)
        assertTrue(down is NavItemView && down !== left)
        val backRight = left.focusSearch(View.FOCUS_RIGHT)
        assertTrue("RIGHT from the sidebar restores the last focused card", backRight === first)

        // 3. Every page opens; Back returns to Home; Back on Home never exits.
        for (page in listOf(LauncherPage.ALL_APPS, LauncherPage.FAVORITES, LauncherPage.CUSTOMIZATION, LauncherPage.WALLPAPERS, LauncherPage.SETTINGS)) {
            activity.navigate(page)
            settle(700)
            assertEquals(page, activity.currentPage())
            assertTrue(root.findViewById<View>(R.id.content).let { c -> (c as android.view.ViewGroup).childCount > 0 })
            activity.onBackPressedDispatcher.onBackPressed()
            settle(300)
            assertEquals(LauncherPage.HOME, activity.currentPage())
        }
        activity.onBackPressedDispatcher.onBackPressed()
        settle(200)
        assertFalse("a launcher must not finish on Back", activity.isFinishing)

        // 4. All Apps lists every app (no hard-coded data) and supports filters.
        activity.navigate(LauncherPage.ALL_APPS)
        settle(900)
        val page = activity.supportFragmentManager.findFragmentByTag("page_secondary")!!.requireView()
        val grid = page.findViewById<RecyclerView>(com.akaaflix.launcher.apps.R.id.grid)
        assertTrue("all apps grid items: ${grid.adapter?.itemCount}", (grid.adapter?.itemCount ?: 0) >= 20)

        // 5. HOME intent while on another page goes back to Home.
        controller.newIntent(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
        settle(400)
        assertEquals(LauncherPage.HOME, activity.currentPage())

        // 6. Settings persist across activity recreation.
        runBlocking { prefs.update { it.copy(homeRows = 1, themeId = "royal_blue") } }
        settle(300)
        controller.recreate()
        settle(1500)
        assertEquals(1, prefs.settings.value.homeRows)
        assertEquals("royal_blue", prefs.settings.value.themeId)
        assertEquals(LauncherPage.HOME, controller.get().currentPage())

        controller.pause().stop().destroy()
    }

    @Test
    fun crashGuardEntersSafeModeAfterRepeatedCrashes() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        CrashGuard.reset(ctx)
        val now = System.currentTimeMillis()
        CrashGuard.record(ctx, now - 20_000)
        assertFalse(CrashGuard.shouldEnterSafeMode(ctx, now))
        CrashGuard.record(ctx, now - 10_000)
        CrashGuard.record(ctx, now)
        assertTrue(CrashGuard.shouldEnterSafeMode(ctx, now))
        assertFalse("old crashes expire", CrashGuard.shouldEnterSafeMode(ctx, now + 10 * 60_000))
    }
}
