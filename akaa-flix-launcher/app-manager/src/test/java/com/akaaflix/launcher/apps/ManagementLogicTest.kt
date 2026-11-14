package com.akaaflix.launcher.apps

import com.akaaflix.launcher.apps.home.HomeStatus
import com.akaaflix.launcher.apps.home.Startup
import com.akaaflix.launcher.apps.home.StartupState
import com.akaaflix.launcher.apps.manage.ActionPath
import com.akaaflix.launcher.apps.manage.ManagedPackage
import com.akaaflix.launcher.apps.manage.PackageAction
import com.akaaflix.launcher.apps.manage.PackageActions
import com.akaaflix.launcher.apps.manage.PackageInventory
import com.akaaflix.launcher.apps.manage.PackageProtection
import com.akaaflix.launcher.apps.manage.Protection
import com.akaaflix.launcher.apps.model.AppCategory
import com.akaaflix.launcher.apps.model.AppEntry
import com.akaaflix.launcher.apps.privileged.PackageCommands
import com.akaaflix.launcher.prefs.BootLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ManagementLogicTest {
    private fun e(pkg: String, label: String, user: Long = 0, leanback: Boolean = true) =
        AppEntry(AppEntry.key(pkg, "$pkg.Main", user), pkg, "$pkg.Main", user, label, leanback, 0, 0, 1, AppCategory.OTHER, user != 0L)

    // ---- duplicates --------------------------------------------------------------------------

    @Test fun playStoreCopyFromAnotherProfileIsHiddenOnTv() {
        val real = e("com.android.vending", "Play Store")
        val clone = e("com.android.vending", "Play Store", user = 10, leanback = false)
        val r = AppDedup.dedupe(listOf(real, clone, e("com.netflix.ninja", "Netflix")), 0, isTv = true, { 1L }, { null })
        assertEquals(listOf(real.key, "com.netflix.ninja/com.netflix.ninja.Main#0"), r.apps.map { it.key })
        assertEquals(AppDedup.Reason.OTHER_PROFILE, r.removed.single().reason)
    }

    @Test fun onPhonesOnlyProfileCopiesOfInstalledAppsAreHidden() {
        val work = e("com.example.mail", "Mail", user = 10)
        val r = AppDedup.dedupe(listOf(e("com.example.chat", "Chat"), e("com.example.chat", "Chat", user = 10), work), 0, isTv = false, { 1L }, { null })
        assertEquals(setOf("com.example.chat/com.example.chat.Main#0", work.key), r.apps.map { it.key }.toSet())
        assertEquals(AppDedup.Reason.SAME_APP_OTHER_PROFILE, r.removed.single().reason)
    }

    @Test fun sameNameSameDeveloperKeepsTheRealApp() {
        val store = e("com.android.vending", "Play Store")
        val alias = e("com.google.android.storeshortcut", "Play Store", leanback = false)
        val sizes = mapOf(store.key to 60_000_000L, alias.key to 2_000_000L)
        val r = AppDedup.dedupe(listOf(alias, store), 0, true, { sizes[it.key] ?: 0 }, { "google" })
        assertEquals(listOf(store.key), r.apps.map { it.key })
        assertEquals(AppDedup.Reason.SAME_NAME_SAME_DEVELOPER, r.removed.single().reason)
    }

    @Test fun tinyStubWithTheSameNameIsHiddenEvenFromAnotherDeveloper() {
        val store = e("com.android.vending", "Play Store")
        val stub = e("com.vendor.playlink", "Play  Store™")
        val sizes = mapOf(store.key to 60_000_000L, stub.key to 120_000L)
        val r = AppDedup.dedupe(listOf(store, stub), 0, true, { sizes[it.key] ?: 0 }, { if (it.packageName.startsWith("com.android")) "google" else "vendor" })
        assertEquals(listOf(store.key), r.apps.map { it.key })
        assertEquals(AppDedup.Reason.SAME_NAME_STUB, r.removed.single().reason)
    }

    @Test fun differentAppsAreNeverMerged() {
        val yt = e("com.google.android.youtube.tv", "YouTube"); val music = e("com.google.android.youtube.tvmusic", "YouTube Music")
        val a = e("com.example.a", "Settings"); val b = e("com.example.b", "Settings")
        val sizes = mapOf(a.key to 9_000_000L, b.key to 8_000_000L)
        val r = AppDedup.dedupe(listOf(yt, music, a, b), 0, true, { sizes[it.key] ?: 30_000_000L }, { it.packageName })
        assertEquals(4, r.apps.size)   // same name, different developers, both real apps: both stay
        assertTrue(r.removed.isEmpty())
    }

    // ---- protection / actions / commands -----------------------------------------------------

    private fun pkg(name: String, system: Boolean = false, updated: Boolean = false, installed: Boolean = true, enabled: Boolean = true,
                    protection: Protection = if (system) Protection.CAUTION else Protection.NORMAL, home: Boolean = false) =
        ManagedPackage(name, name, "1.0", 1, system, updated, installed, enabled, true, home, 1000, null, protection, 0, 0)

    @Test fun protectionTiers() {
        val own = "com.akaaflix.launcher"
        assertEquals(Protection.CRITICAL, PackageProtection.classify("com.android.systemui", own, true, false, false, emptySet()))
        assertEquals(Protection.CRITICAL, PackageProtection.classify("com.android.providers.media.module", own, true, false, false, emptySet()))
        assertEquals(Protection.CRITICAL, PackageProtection.classify("com.google.android.gms", own, true, false, false, emptySet()))
        assertEquals(Protection.CRITICAL, PackageProtection.classify(own, own, false, false, true, emptySet()))
        assertEquals(Protection.CRITICAL, PackageProtection.classify("com.vendor.keyboard", own, true, false, false, setOf("com.vendor.keyboard")))
        assertEquals(Protection.CRITICAL, PackageProtection.classify("com.vendor.daemon", own, true, true, false, emptySet()))
        assertEquals(Protection.CAUTION, PackageProtection.classify("com.google.android.apps.tv.launcherx", own, true, false, true, emptySet()))
        assertEquals(Protection.CAUTION, PackageProtection.classify("com.vendor.bloat", own, true, false, false, emptySet()))
        assertEquals(Protection.NORMAL, PackageProtection.classify("com.example.app", own, false, false, false, emptySet()))
    }

    @Test fun actionsFollowWhatAndroidAllows() {
        fun actions(p: ManagedPackage, shizuku: Boolean, sdk: Int = 34) = PackageActions.plan(p, shizuku, sdk, user = 0).associate { it.action to it.path }

        val user = actions(pkg("com.example.app"), false)
        assertEquals(ActionPath.DIRECT, user[PackageAction.UNINSTALL])
        assertEquals(ActionPath.SYSTEM_SCREEN, user[PackageAction.DISABLE])
        assertNull(user[PackageAction.REMOVE_FOR_USER])

        val bloatNoShizuku = actions(pkg("com.vendor.bloat", system = true, updated = true), false)
        assertNull("system apps are never offered the normal uninstall", bloatNoShizuku[PackageAction.UNINSTALL])
        assertEquals(ActionPath.ADB_ONLY, bloatNoShizuku[PackageAction.REMOVE_FOR_USER])
        assertEquals(ActionPath.SYSTEM_SCREEN, bloatNoShizuku[PackageAction.UNINSTALL_UPDATES])

        val bloat = actions(pkg("com.vendor.bloat", system = true, updated = true), true)
        assertEquals(ActionPath.SHIZUKU, bloat[PackageAction.REMOVE_FOR_USER])
        assertEquals(ActionPath.SHIZUKU, bloat[PackageAction.DISABLE])
        assertEquals(ActionPath.SHIZUKU, bloat[PackageAction.UNINSTALL_UPDATES])
        assertEquals("pm uninstall-system-updates needs Android 10", ActionPath.SYSTEM_SCREEN, actions(pkg("com.vendor.bloat", system = true, updated = true), true, sdk = 28)[PackageAction.UNINSTALL_UPDATES])

        val removed = actions(pkg("com.vendor.bloat", system = true, installed = false), true)
        assertEquals(setOf(PackageAction.RESTORE), removed.keys)
        assertEquals(ActionPath.SHIZUKU, removed[PackageAction.RESTORE])

        val disabled = actions(pkg("com.vendor.bloat", system = true, enabled = false), true)
        assertEquals(ActionPath.SHIZUKU, disabled[PackageAction.ENABLE])
        assertNull(disabled[PackageAction.LAUNCH])

        val userWithShizuku = PackageActions.plan(pkg("com.example.app"), true, 34, user = 0).single { it.action == PackageAction.UNINSTALL }
        assertEquals("user apps: uninstall for user 0 through Shizuku", ActionPath.SHIZUKU, userWithShizuku.path)
        assertEquals(listOf("pm", "uninstall", "--user", "0", "com.example.app"), userWithShizuku.command)
        assertNull("a real uninstall has no undo command", userWithShizuku.undo)

        val critical = actions(pkg("com.android.systemui", system = true, protection = Protection.CRITICAL), true)
        assertEquals("critical packages: information only", setOf(PackageAction.LAUNCH, PackageAction.APP_INFO), critical.keys)
    }

    @Test fun commandsAreFixedArgvAndRejectInjection() {
        assertEquals(listOf("pm", "disable-user", "--user", "0", "com.vendor.bloat"), PackageCommands.disable("com.vendor.bloat", 0))
        assertEquals(listOf("pm", "uninstall", "-k", "--user", "10", "com.vendor.bloat"), PackageCommands.removeForUser("com.vendor.bloat", 10))
        assertEquals(listOf("pm", "install-existing", "--user", "0", "com.vendor.bloat"), PackageCommands.restore("com.vendor.bloat", 0))
        assertEquals(listOf("pm", "uninstall", "--user", "0", "com.example.app"), PackageCommands.uninstall("com.example.app", 0))
        assertEquals(listOf("cmd", "role", "add-role-holder", "--user", "0", "android.app.role.HOME", "com.akaaflix.launcher"), PackageCommands.setHomeRole("com.akaaflix.launcher", 0))
        assertEquals("adb shell pm enable --user 0 a.b", PackageCommands.asAdb(PackageCommands.enable("a.b", 0)))
        assertFalse(PackageCommands.isValidPackage("a.b; reboot"))
        assertFalse(PackageCommands.isValidPackage("singleword"))
        assertFalse(PackageCommands.isValidPackage("../etc"))
        var threw = false
        try { PackageCommands.disable("x.y && rm -rf /", 0) } catch (_: IllegalArgumentException) { threw = true }
        assertTrue(threw)
        val noCritical = PackageActions.plan(pkg("bad name"), true, 34, 0)
        assertTrue("invalid names never get privileged actions", noCritical.none { it.path == ActionPath.SHIZUKU })
    }

    @Test fun inventoryFilters() {
        val list = listOf(pkg("com.example.app"), pkg("com.vendor.bloat", system = true), pkg("com.vendor.off", system = true, enabled = false),
            pkg("com.vendor.gone", system = true, installed = false))
        assertEquals(3, PackageInventory.filter(list, PackageInventory.Filter.ALL, "").size)
        assertEquals(listOf("com.example.app"), PackageInventory.filter(list, PackageInventory.Filter.USER, "").map { it.packageName })
        assertEquals(listOf("com.vendor.off"), PackageInventory.filter(list, PackageInventory.Filter.DISABLED, "").map { it.packageName })
        assertEquals(listOf("com.vendor.gone"), PackageInventory.filter(list, PackageInventory.Filter.REMOVED, "").map { it.packageName })
        assertEquals(listOf("com.vendor.bloat"), PackageInventory.filter(list, PackageInventory.Filter.SYSTEM, "BLOAT").map { it.packageName })
    }

    // ---- start-up status ---------------------------------------------------------------------

    private fun home(default: Boolean, gtv: Boolean = false) = HomeStatus(default, true, default, if (default) "com.akaaflix.launcher" else "com.vendor.launcher", "X", emptyList(), gtv)
    private fun log(homeThisBoot: Boolean, homeAt: Long = 30_000, fallback: Boolean = false) =
        BootLog.Snapshot(1000, 1000, 5_000, if (homeThisBoot) 1001 else 900, homeAt, if (fallback) 1000 else 0)

    @Test fun startupStatusIsOnlyVerifiedWithEvidence() {
        assertEquals(StartupState.VERIFIED, Startup.evaluate(home(true), log(true), 34, false))
        assertEquals("observed long after boot = not the start-up launch", StartupState.DEFAULT_PENDING, Startup.evaluate(home(true), log(true, homeAt = 3_600_000), 34, false))
        assertEquals(StartupState.DEFAULT_PENDING, Startup.evaluate(home(true), log(false), 34, false))
        assertEquals(StartupState.NOT_DEFAULT, Startup.evaluate(home(false), log(true), 34, true))
        assertEquals(StartupState.MANUFACTURER_STEPS, Startup.evaluate(home(false, gtv = true), log(false), 34, false))
        assertEquals(StartupState.VERIFIED_FALLBACK, Startup.evaluate(home(false), log(false, fallback = true), 28, true))
        assertEquals(StartupState.FALLBACK_PENDING, Startup.evaluate(home(false), log(false), 28, true))
        assertTrue(BootLog.sameBoot(1000, 1003))
        assertFalse(BootLog.sameBoot(1000, 1010))
        assertFalse(BootLog.sameBoot(0, 0))
        assertEquals(100L, BootLog.currentBootId(nowMs = 1_005_000, elapsedMs = 5_000))
    }
}
