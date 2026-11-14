package com.akaaflix.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Looper
import android.provider.Settings
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.akaaflix.launcher.apps.AppRepository
import com.akaaflix.launcher.core.nav.LauncherPage
import com.akaaflix.launcher.core.ui.rows.SettingRow
import com.akaaflix.launcher.core.ui.rows.SettingRowsAdapter
import com.akaaflix.launcher.prefs.SettingsRepository
import com.akaaflix.launcher.settings.BackupFiles
import com.akaaflix.launcher.settings.SettingsFragment
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
 * v1.4 changes on several Android versions (Robolectric): top-right gear opens Android Settings,
 * icon size locked to Large, privacy sections removed, backup/restore without a document picker
 * (as on Google TV, whose picker is only a "framework package stubs" placeholder).
 */
@RunWith(RobolectricTestRunner::class)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [24, 29, 31, 34, 36], application = AkaaLauncherApp::class, qualifiers = "en-w960dp-h540dp-land-television-xhdpi")
class FinalChangesTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()

    private fun settle(ms: Long = 1200) {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(30) }
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun launch(): MainActivity {
        TestApps.install(ctx, leanback = true)
        AppRepository.get(ctx).refresh(0)
        val prefs = SettingsRepository.get(ctx)
        prefs.awaitLoaded()
        runBlocking { prefs.update { it.copy(language = "en") } }
        settle(600)
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        settle(2000)
        return activity
    }

    private fun settingsRows(activity: MainActivity, section: String): List<SettingRow> {
        activity.navigate(LauncherPage.SETTINGS, android.os.Bundle().apply { putString("open_section", section) })
        settle(1200)
        val f = activity.supportFragmentManager.findFragmentByTag("page_secondary") as SettingsFragment
        val list = f.requireView().findViewById<RecyclerView>(com.akaaflix.launcher.settings.R.id.detailList)
        return (list.adapter as SettingRowsAdapter).currentList
    }

    @Test fun topRightGearOpensAndroidSettings() {
        val activity = launch()
        shadowOf(ctx as android.app.Application).clearNextStartedActivities()
        activity.findViewById<View>(com.akaaflix.launcher.home.R.id.gearButton).performClick()
        settle(300)
        val started = shadowOf(ctx as android.app.Application).nextStartedActivity
        assertNotNull("gear must open the device settings", started)
        assertEquals(Settings.ACTION_SETTINGS, started.action)
        assertEquals("launcher stays on Home", LauncherPage.HOME, activity.currentPage())
    }

    @Test fun iconSizeIsLockedToLarge() {
        runBlocking { SettingsRepository.get(ctx).apply { awaitLoaded() }.update { it.copy(iconSize = "medium") } }
        assertEquals("a stored Medium value is not accepted any more", "large",
            com.akaaflix.launcher.prefs.LauncherSettings.fromJson(SettingsRepository.get(ctx).settings.value.toJson()).iconSize)
        val activity = launch()
        activity.navigate(LauncherPage.CUSTOMIZATION, null)
        settle(1200)
        val medium = activity.findViewById<View>(com.akaaflix.launcher.customization.R.id.sizeMedium)
        val large = activity.findViewById<View>(com.akaaflix.launcher.customization.R.id.sizeLarge)
        assertEquals("Medium option removed", View.GONE, medium.visibility)
        assertEquals(View.VISIBLE, large.visibility)
        assertTrue(large.isActivated)
        val size = settingsRows(activity, "interface").single { it.id == "size" } as SettingRow.Choice
        assertEquals(listOf(ctx.getString(com.akaaflix.launcher.core.R.string.size_large)), size.options)
    }

    @Test fun privacySectionsAreRemoved() {
        val activity = launch()
        val privacy = ctx.getString(com.akaaflix.launcher.core.R.string.row_privacy)
        val about = settingsRows(activity, "about")
        assertFalse(about.any { it.id == "privacy" })
        assertFalse(about.any { it is SettingRow.Header && it.title == privacy })
        assertTrue("backup and restore stay", about.any { it.id == "backup" } && about.any { it.id == "restore" })
        assertTrue(about.any { it.id == "licenses" } && about.any { it.id == "diagnostics" })
        val notes = settingsRows(activity, "notifications").filterIsInstance<SettingRow.Info>()
        assertFalse(notes.any { it.title == ctx.getString(com.akaaflix.launcher.core.R.string.row_notif_note) })
    }

    @Test fun backupAndRestoreWorkWithoutADocumentPicker() {
        // Google TV: only the stub app answers the picker intents.
        val spm = shadowOf(ctx.packageManager)
        val stub = "com.android.tv.frameworkpackagestubs"
        spm.installPackage(PackageInfo().apply { packageName = stub; applicationInfo = ApplicationInfo().apply { packageName = stub; flags = ApplicationInfo.FLAG_SYSTEM } })
        val cn = ComponentName(stub, "$stub.Stubs\$DocumentsStub")
        spm.addActivityIfNotPresent(cn)
        for (action in listOf(Intent.ACTION_CREATE_DOCUMENT, Intent.ACTION_OPEN_DOCUMENT, Intent.ACTION_GET_CONTENT))
            spm.addIntentFilterForActivity(cn, IntentFilter(action).apply { addCategory(Intent.CATEGORY_DEFAULT); addCategory(Intent.CATEGORY_OPENABLE); addDataType("*/*") })
        assertTrue(ctx.packageManager.queryIntentActivities(BackupFiles.openDocumentIntent(), PackageManager.MATCH_DEFAULT_ONLY).isNotEmpty())
        assertFalse("the stub is not a real picker", BackupFiles.hasRealHandler(ctx, BackupFiles.openDocumentIntent()))
        assertFalse(BackupFiles.hasRealHandler(ctx, BackupFiles.createDocumentIntent("x.json")))

        val activity = launch()
        val prefs = SettingsRepository.get(ctx)
        runBlocking { prefs.update { it.copy(homeRows = 2, clockFormat = "24") } }
        settle(300)
        settingsRows(activity, "about")
        val f = activity.supportFragmentManager.findFragmentByTag("page_secondary") as SettingsFragment
        f.backup()          // "Backup settings" row
        settle(800)
        val saved = BackupFiles.list(ctx)
        assertTrue("backup written without any picker", saved.isNotEmpty())
        assertTrue(saved.first().name.startsWith(BackupFiles.PREFIX))

        runBlocking { prefs.update { it.copy(homeRows = 1, clockFormat = "12") } }
        settle(300)
        val text = saved.first().read()
        assertNotNull(text)
        assertTrue("restore accepts the file", runBlocking { prefs.importBackup(text!!) })
        assertEquals(2, prefs.settings.value.homeRows)
        assertEquals("24", prefs.settings.value.clockFormat)
        assertEquals("2026-11-14 18:30", BackupFiles.label("AkaaFlixLauncher-backup-20261114-183005.json"))
    }
}
