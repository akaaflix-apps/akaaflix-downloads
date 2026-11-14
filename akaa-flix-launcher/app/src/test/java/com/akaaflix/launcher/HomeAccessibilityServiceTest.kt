package com.akaaflix.launcher

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import androidx.test.core.app.ApplicationProvider
import com.akaaflix.launcher.apps.home.AccessState
import com.akaaflix.launcher.apps.home.HomeAccess
import com.akaaflix.launcher.apps.home.HomeAccessibilityService
import com.akaaflix.launcher.apps.home.HomeStatus
import com.akaaflix.launcher.apps.home.Startup
import com.akaaflix.launcher.apps.home.StartupState
import com.akaaflix.launcher.prefs.BootLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.xmlpull.v1.XmlPullParser
import java.time.Duration

/**
 * Akaa Flix's Home-button accessibility service, run by Robolectric on several Android versions:
 * manifest registration (what makes it appear in Settings → Accessibility → Services), Home key,
 * return from another launcher, start-up after a restart and the status shown to the user.
 * The Android framework's binding of the service is not simulated (Robolectric) – see the device test plan.
 */
@RunWith(RobolectricTestRunner::class)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [24, 28, 29, 31, 34, 35, 36], application = AkaaLauncherApp::class, qualifiers = "en-television")
class HomeAccessibilityServiceTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()
    private val app get() = ApplicationProvider.getApplicationContext<Application>()

    @Before fun setUp() {
        TestApps.grantReceiverPermission(ctx)
        // Services are not unbound between Robolectric tests; start every test with the service disconnected.
        HomeAccessibilityService::class.java.getDeclaredField("connected").apply { isAccessible = true }.setBoolean(null, false)
    }

    /** A Google TV-like launcher that Android prefers for HOME (test data only). */
    private fun installStockLauncher() {
        val spm = shadowOf(ctx.packageManager)
        val ai = ApplicationInfo().apply { packageName = GTV; name = "Google TV Home"; nonLocalizedLabel = "Google TV Home"; enabled = true; flags = ApplicationInfo.FLAG_SYSTEM }
        spm.installPackage(PackageInfo().apply { packageName = GTV; applicationInfo = ai; versionName = "1" })
        for (cls in listOf(GTV_HOME, GTV_SEARCH)) spm.addActivityIfNotPresent(ComponentName(GTV, cls))
        spm.addIntentFilterForActivity(ComponentName(GTV, GTV_HOME), IntentFilter(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME); addCategory(Intent.CATEGORY_DEFAULT); priority = 10
        })
        spm.getInternalMutablePackageInfo(GTV)?.activities?.forEach { it.enabled = true; it.exported = true }
    }

    private fun androidHome() = ctx.packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName

    private fun service() = Robolectric.setupService(HomeAccessibilityService::class.java)
    private fun connect(s: HomeAccessibilityService) =
        HomeAccessibilityService::class.java.getDeclaredMethod("onServiceConnected").apply { isAccessible = true }.invoke(s)
    private fun started(): Intent? = shadowOf(app).nextStartedActivity
    /** onKeyEvent is protected (Android calls it); invoked like Android does. */
    private fun key(s: HomeAccessibilityService, e: KeyEvent) =
        HomeAccessibilityService::class.java.getDeclaredMethod("onKeyEvent", KeyEvent::class.java).apply { isAccessible = true }.invoke(s, e) as Boolean
    private fun windowOf(pkg: String, cls: String) = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED).apply { packageName = pkg; className = cls }
    private fun enableInSettings() = Settings.Secure.putString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        "com.vendor.tv/.TvService:" + HomeAccess.component(ctx).flattenToString())

    @Test fun registeredAsAccessibilityServiceWithMinimalAccess() {
        val found = ctx.packageManager.queryIntentServices(Intent("android.accessibilityservice.AccessibilityService"), PackageManager.GET_META_DATA)
            .map { it.serviceInfo }.single { it.packageName == ctx.packageName }
        assertEquals(HomeAccess.SERVICE_CLASS, found.name)
        assertEquals("only Android can bind it", "android.permission.BIND_ACCESSIBILITY_SERVICE", found.permission)
        val xml = ctx.resources.getXml(found.metaData.getInt("android.accessibilityservice"))
        while (xml.next() != XmlPullParser.START_TAG) Unit
        assertEquals("accessibility-service", xml.name)
        fun attr(n: String) = xml.getAttributeValue(ANDROID_NS, n)
        assertEquals("true", attr("canRequestFilterKeyEvents"))
        assertEquals("never reads window content", "false", attr("canRetrieveWindowContent"))
        assertNotNull(attr("description"))
    }

    @Test fun homeKeyOpensAkaaFlixWhenAndroidWouldOpenAnotherLauncher() {
        installStockLauncher()
        assertEquals("precondition: Android resolves HOME to the stock launcher", GTV, androidHome())
        val s = service()
        assertFalse("other keys always pass through", key(s, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER)))
        assertTrue(key(s, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HOME)))
        assertNull("opens on release", started())
        assertTrue(key(s, KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_HOME)))
        val i = started()!!
        assertEquals(MainActivity::class.java.name, i.component?.className)
        assertTrue(i.hasCategory(Intent.CATEGORY_HOME))
        assertEquals(HomeAccess.SOURCE_HOME_KEY, i.getStringExtra(HomeAccess.EXTRA_SOURCE))
        val log = BootLog.read(ctx)
        assertTrue(log.lastServiceLaunchAt > 0 && log.lastHomeIntentAt > 0)
        assertFalse("a Home key press is not start-up evidence", log.serviceStartThisBoot)
    }

    @Test fun leavesTheHomeKeyToAndroidWhenAkaaFlixIsTheDefault() {
        assertEquals(ctx.packageName, androidHome())
        val s = service()
        assertFalse(key(s, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HOME)))
        assertFalse(key(s, KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_HOME)))
        s.onAccessibilityEvent(windowOf("com.example.other", "com.example.other.Main"))
        assertNull(started())
    }

    @Test fun returnsWhenTheStockLaunchersHomeScreenAppears() {
        installStockLauncher()
        val s = service()
        s.onAccessibilityEvent(windowOf(GTV, GTV_SEARCH))
        s.onAccessibilityEvent(windowOf(GTV, "android.widget.FrameLayout"))
        s.onAccessibilityEvent(windowOf("com.example.videoplayer", "com.example.videoplayer.MainActivity"))
        assertNull("search screens, popups and other apps are left alone", started())
        s.onAccessibilityEvent(windowOf(GTV, GTV_HOME))
        assertEquals(HomeAccess.SOURCE_RETURN, started()!!.getStringExtra(HomeAccess.EXTRA_SOURCE))
        s.onAccessibilityEvent(windowOf(GTV, GTV_HOME))
        assertNull("debounced: one appearance opens Akaa Flix once", started())
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        s.onAccessibilityEvent(windowOf(GTV, GTV_HOME))
        assertNotNull(started())
    }

    @Test fun opensAkaaFlixAtStartUpAndReportsVerifiedOnlyWithEvidence() {
        installStockLauncher()
        enableInSettings()
        val s = service()
        connect(s)   // first activation by the user: nothing is opened
        assertNull(started())
        assertEquals(AccessState.ON_RUNNING, HomeAccess.state(ctx))
        val home = HomeStatus.read(ctx)
        assertFalse(home.isDefault)
        assertEquals(StartupState.SERVICE_PENDING, Startup.evaluate(home, BootLog.read(ctx), Build.VERSION.SDK_INT, false, HomeAccess.state(ctx)))

        // Simulate a restart: the service was on during an earlier boot and connects again.
        ctx.getSharedPreferences("akaa_boot_log", Context.MODE_PRIVATE).edit().putLong("svc_conn_id", BootLog.currentBootId() - 100).commit()
        val s2 = service()
        connect(s2)
        assertEquals(HomeAccess.SOURCE_BOOT, started()!!.getStringExtra(HomeAccess.EXTRA_SOURCE))
        assertTrue(BootLog.read(ctx).serviceStartThisBoot)
        assertEquals(StartupState.VERIFIED_SERVICE, Startup.evaluate(home, BootLog.read(ctx), Build.VERSION.SDK_INT, false, HomeAccess.state(ctx)))

        // Turned off by the user: status follows Android, and turning it on again is not a "restart".
        s2.onUnbind(Intent())
        Settings.Secure.putString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, "com.vendor.tv/.TvService")
        assertEquals(AccessState.OFF, HomeAccess.state(ctx))
        enableInSettings()
        connect(service())
        assertNull(started())
    }

    @Test fun statusIsReadFromAndroidNotAssumed() {
        assertEquals(AccessState.OFF, HomeAccess.state(ctx))
        enableInSettings()
        assertEquals("listed in Settings but not bound by Android", AccessState.ON_NOT_RUNNING, HomeAccess.state(ctx))
    }

    @Test fun serviceLaunchesAreNotCountedAsAndroidHomeStarts() {
        val c = Robolectric.buildActivity(MainActivity::class.java, Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            .setComponent(ComponentName(ctx, MainActivity::class.java)).putExtra(HomeAccess.EXTRA_SOURCE, HomeAccess.SOURCE_BOOT)).setup()
        assertFalse(BootLog.read(ctx).homeStartThisBoot)
        shadowOf(Looper.getMainLooper()).idle()
        c.newIntent(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
        assertTrue("Android's own HOME intent is recorded", BootLog.read(ctx).homeStartThisBoot)
    }

    companion object {
        const val GTV = "com.google.android.apps.tv.launcherx"
        const val GTV_HOME = "$GTV.home.HomeActivity"
        const val GTV_SEARCH = "$GTV.search.SearchActivity"
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }
}
